package com.manahive.hub.policy

import com.manahive.contracts.policy.EffectiveRules
import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.contracts.policy.catalogFor
import com.manahive.kernel.Explained
import com.manahive.kernel.ResidentId
import com.manahive.politica.PolicyResolver
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Resuelve la política vigente de un residente.
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
) {
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
}

/**
 * El puerto hacia el almacén event-sourced de capas.
 *
 * Devuelve `null` cuando el residente no tiene ninguna capa registrada — que es
 * distinto de tener una política vacía.
 */
public fun interface PolicyLayerStore {
    public fun layersFor(residentId: ResidentId, at: Instant): PolicyLayers?
}

/** Un residente sin capas de política. Es un error operativo, no un default. */
public class NoPolicyForResident(residentId: ResidentId) : IllegalStateException(
    "resident ${residentId.value} has no policy layers — " +
        "nobody chose a watch level for them; refusing to invent one",
)
