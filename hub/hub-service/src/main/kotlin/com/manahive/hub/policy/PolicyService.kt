package com.manahive.hub.policy

import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.EffectiveRules
import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.contracts.policy.WatchLevel
import com.manahive.contracts.policy.catalogFor
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.Explained
import com.manahive.kernel.ResidentId
import com.manahive.kernel.StaffId
import com.manahive.politica.PolicyResolver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Resuelve la política vigente de un residente y administra sus capas.
 *
 * El hub no decide: **proyecta y delega**. Guarda las capas — nivel, plantilla,
 * ajustes firmados, ventanas horarias — las pliega a un perfil, y Política las
 * resuelve contra el catálogo del nivel. Ver `docs/adr/ADR-001`.
 *
 *     capas (SoR)  →  AlarmProfile  →  PolicyCalibration
 *                  ↑                ↑
 *            hub (procedencia)   politica (precedencia)
 *
 * Las dos explicaciones se concatenan en ese orden, así que la respuesta se lee
 * de arriba abajo: primero quién lo decidió, después cómo se resolvió.
 */
@Service
public class PolicyService(
    private val layerStore: PolicyLayerStore,
    /**
     * El publicador llega como [ObjectProvider] y no como dependencia directa.
     *
     * Leer la política de un residente es una consulta pura sobre las capas más
     * un resolvedor puro: no tiene por qué necesitar el bus. Inyectado directo,
     * Spring resolvía el publicador al construir este bean, así que un egress
     * que no se puede crear tiraba abajo `policyService` → `policyController` y
     * con ellos `GET /api/policies/{id}`: sin bus no se podían leer las reglas
     * de nadie. Diferir la resolución hasta el momento de publicar desacopla la
     * lectura de la disponibilidad del bus.
     */
    private val publisherProvider: ObjectProvider<PolicyEventPublisher>,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // ── Read ──────────────────────────────────────────────────────────────

    /**
     * La calibración vigente para un residente, con su procedencia completa.
     *
     * @throws NoPolicyForResident si el residente no tiene capas. Un residente
     * sin política es un error operativo que alguien tiene que ver — no un
     * residente en STANDARD. Devolver un default silencioso significaría vigilar
     * a alguien con reglas que nadie eligió.
     */
    public fun resolve(residentId: ResidentId, at: Instant): Explained<PolicyCalibration> {
        val layers = layerStore.layersFor(residentId, at)
            ?: throw NoPolicyForResident(residentId)

        val profile = layers.toAlarmProfile(residentId, at)
        val calibration = PolicyResolver.resolve(catalogFor(layers.level), profile.value)

        return Explained(
            value = calibration.value,
            explanation = profile.explanation + calibration.explanation,
        )
    }

    /** Las reglas que Sentinel necesita, extraídas de la calibración vigente. */
    public fun resolveEffectiveRules(residentId: ResidentId, at: Instant): Explained<EffectiveRules> {
        val resolved = resolve(residentId, at)
        return Explained(
            value = EffectiveRules(
                residentId = residentId,
                rules = resolved.value.sentinel.alertRules.values.toList(),
                fingerprint = resolved.value.fingerprint.value,
            ),
            explanation = resolved.explanation,
        )
    }

    /** La historia de eventos de política de un residente. */
    public fun historyFor(residentId: ResidentId): List<PolicyEvent> =
        layerStore.eventsFor(residentId)

    // ── Write ─────────────────────────────────────────────────────────────

    /**
     * Cambia el nivel de vigilancia de un residente.
     *
     * @throws MissingMotivo si el motivo está vacío.
     */
    public fun changeWatchLevel(
        residentId: ResidentId,
        level: WatchLevel,
        actor: StaffId,
        at: Instant,
        reason: String,
    ): PolicyLayers {
        require(reason.isNotBlank()) { throw MissingMotivo("watch-level") }
        val event = WatchLevelAssigned(residentId, level, actor, at, reason)
        layerStore.applyEvent(event)
        val layers = layerStore.layersFor(residentId, at)
            ?: throw NoPolicyForResident(residentId)
        publishIfPossible(residentId, at)
        return layers
    }

    /**
     * Agrega un ajuste manual sobre un estado concreto.
     *
     * @throws MissingMotivo si el motivo está vacío.
     */
    public fun addAdjustment(
        residentId: ResidentId,
        adjustmentId: String,
        state: StateKind,
        threshold: DwellThreshold,
        actor: StaffId,
        at: Instant,
        reason: String,
    ): PolicyLayers {
        require(reason.isNotBlank()) { throw MissingMotivo("adjustment") }
        val event = ManualAdjustmentAdded(
            residentId, adjustmentId, state, threshold, actor, at, reason,
        )
        layerStore.applyEvent(event)
        val layers = layerStore.layersFor(residentId, at)
            ?: throw NoPolicyForResident(residentId)
        publishIfPossible(residentId, at)
        return layers
    }

    /**
     * Revoca un ajuste manual previo.
     */
    public fun revokeAdjustment(
        residentId: ResidentId,
        adjustmentId: String,
        actor: StaffId,
        at: Instant,
    ): PolicyLayers {
        val event = ManualAdjustmentRevoked(residentId, adjustmentId, actor, at)
        layerStore.applyEvent(event)
        val layers = layerStore.layersFor(residentId, at)
            ?: throw NoPolicyForResident(residentId)
        publishIfPossible(residentId, at)
        return layers
    }

    // ── Internal ──────────────────────────────────────────────────────────

    /**
     * Resuelve la política vigente y publica el cambio al bus.
     *
     * Best-effort respecto de la escritura —que ya está commiteada— pero **no**
     * silencioso. Si esto falla, los motores siguen calibrados con las reglas
     * viejas: el director cambió el nivel de un residente y la vigilancia no se
     * enteró. Eso tiene que quedar en el log a nivel ERROR, porque es la clase
     * de divergencia que nadie descubre hasta que hace falta la alarma.
     *
     * El `catch` estaba vacío y el comentario decía que logueaba.
     */
    private fun publishIfPossible(residentId: ResidentId, at: Instant) {
        try {
            val publisher = publisherProvider.getIfAvailable() ?: return
            val layers = layerStore.layersFor(residentId, at) ?: return
            val profile = layers.toAlarmProfile(residentId, at)
            publisher.publishPolicyChange(residentId, profile.value, at)
        } catch (e: Exception) {
            log.error(
                "La política de {} cambió pero no se pudo publicar al bus: {}. " +
                    "Los motores siguen con las reglas anteriores.",
                residentId.value,
                e.message,
                e,
            )
        }
    }
}

/**
 * El puerto hacia el almacén event-sourced de capas.
 *
 * Devuelve `null` cuando el residente no tiene ninguna capa registrada — que es
 * distinto de tener una política vacía.
 */
public interface PolicyLayerStore {
    public fun layersFor(residentId: ResidentId, at: Instant): PolicyLayers?
    public fun eventsFor(residentId: ResidentId): List<PolicyEvent>
    public fun applyEvent(event: PolicyEvent)
}

/** Un residente sin capas de política. Es un error operativo, no un default. */
public class NoPolicyForResident(residentId: ResidentId) : IllegalStateException(
    "resident ${residentId.value} has no policy layers — " +
        "nobody chose a watch level for them; refusing to invent one",
)

/** Falta el motivo en una operación de escritura. */
public class MissingMotivo(operation: String) : IllegalArgumentException(
    "$operation requires a reason — a policy change nobody can explain",
)
