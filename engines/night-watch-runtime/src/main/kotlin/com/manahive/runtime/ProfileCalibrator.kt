package com.manahive.runtime

import com.manahive.contracts.policy.ResidentProfile
import com.manahive.politica.profile.ProfileMapper
import com.manahive.politica.profile.ProfileMapping
import com.manahive.politica.profile.ProfileProjection
import com.manahive.profile.api.ResidentProfileDto
import com.manahive.kernel.ResidentId
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalTime

/**
 * El perfil del residente, convertido en las cuatro calibraciones que corren.
 *
 * Es la costura que faltaba. `ProfileMapper` y `ProfileProjection` estaban
 * construidos y probados, y **no los llamaba nadie**: lo que corria seguia
 * siendo plantilla mas parches. Esta clase es lo unico que hacia falta para que
 * el documento que firma el director sea el que vigila a la persona.
 *
 * ## Guarda el perfil, no la calibracion
 *
 * Retiene el [ResidentProfile] de cada residente porque una calibracion es
 * **una foto de una ventana horaria**. A las 22:00 las reglas de Elena cambian,
 * y para volver a proyectar hay que tener el documento, no el resultado de la
 * proyeccion anterior. Sin esto, la baranda —que solo alerta de noche— no
 * alertaria nunca.
 */
class ProfileCalibrator(
    private val runtime: NightWatchRuntime,
    private val census: Census,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** El perfil vigente de cada residente, y la ventana con que se proyecto. */
    private val vigentes = mutableMapOf<ResidentId, Vigente>()

    private data class Vigente(val profile: ResidentProfile, val window: String)

    val size: Int get() = vigentes.size

    /**
     * Acepta un perfil que llego de afuera.
     *
     * Un perfil que no valida **no se aplica ni a medias**: se rechaza entero y
     * se loguean todos los problemas con su ruta. Aplicar la parte que cierra
     * seria reintroducir el modelo de parches por la puerta de atras, y dejaria
     * al residente vigilado con reglas que nadie escribio.
     */
    @Synchronized
    fun accept(dto: ResidentProfileDto): Boolean {
        val profile = when (val mapped = ProfileMapper.map(dto)) {
            is ProfileMapping.Accepted -> mapped.profile
            is ProfileMapping.Rejected -> {
                log.error(
                    "Perfil rechazado para {} (v{}): {} problemas",
                    dto.residentId, dto.version, mapped.problems.size,
                )
                mapped.problems.forEach { log.error("  {} — {}", it.path, it.message) }
                return false
            }
        }

        val anterior = vigentes[profile.residentId]?.profile
        if (anterior != null && profile.version <= anterior.version) {
            // Un perfil viejo que llega tarde no pisa al nuevo. Pasa en cada
            // replay del stream, y sin esto un reinicio podia retroceder la
            // vigilancia a una version que el director ya habia reemplazado.
            log.warn(
                "Perfil v{} para {} descartado: ya rige v{}",
                profile.version, profile.residentId.value, anterior.version,
            )
            return false
        }

        return apply(profile, activeWindow(profile))
    }

    /**
     * Reproyecta a los residentes cuya ventana horaria cambio.
     *
     * Es la re-emision en el borde: a las 22:00 el regimen cambia y sale una
     * calibracion nueva. Los motores siguen teniendo **una sola calibracion
     * vigente** y no se enteran de que existen ventanas.
     *
     * Se llama desde el barrido, o sea cada 30 segundos: el borde se cruza con
     * esa granularidad y no al segundo exacto. Para una ventana de turno noche
     * es de sobra, y el hecho queda en el log con la hora real.
     */
    @Synchronized
    fun reprojectOnWindowEdge(): List<ResidentId> {
        val cambiados = mutableListOf<ResidentId>()
        vigentes.values.toList().forEach { vigente ->
            val ahora = activeWindow(vigente.profile)
            if (ahora != vigente.window) {
                log.info(
                    "Borde horario para {}: '{}' -> '{}'",
                    vigente.profile.residentId.value, vigente.window, ahora,
                )
                if (apply(vigente.profile, ahora)) cambiados += vigente.profile.residentId
            }
        }
        return cambiados
    }

    /** El perfil vigente de un residente, si lo hay. */
    @Synchronized
    fun current(residentId: ResidentId): ResidentProfile? = vigentes[residentId]?.profile

    private fun apply(profile: ResidentProfile, window: String): Boolean {
        val proyectada = ProfileProjection.project(profile, window)
        val existing = runtime.get(profile.residentId)
        val bedId = existing?.bed ?: census.bedFor(profile.residentId)?.bed ?: com.manahive.kernel.BedId("unknown")
        val monitorId = existing?.monitor ?: census.bedFor(profile.residentId)?.monitor ?: com.manahive.kernel.MonitorId("unknown")
        val calibrations = EngineCalibrations.from(proyectada.value, bedId, monitorId)

        // Lo que el perfil dice y la calibracion no sabe transportar se loguea
        // en vez de desaparecer. Mientras esta lista no este vacia, el perfil
        // dice mas de lo que el motor escucha.
        ProfileProjection.unrepresentable(profile, window).forEach {
            log.warn("No transportable ({}): {} — {}", profile.residentId.value, it.path, it.reason)
        }

        if (existing == null) {
            val bed = census.bedFor(profile.residentId)
            if (bed == null) {
                log.error(
                    "Perfil para {} pero no esta en el censo: ninguna cama le corresponde",
                    profile.residentId.value,
                )
                return false
            }
            runtime.register(profile.residentId, bed.bed, bed.night, bed.monitor, calibrations)
            log.info(
                "Alta de {} con perfil v{} (ventana '{}', huella {})",
                profile.residentId.value, profile.version, window, proyectada.value.fingerprint.value,
            )
        } else {
            runtime.recalibrate(profile.residentId, calibrations)
            log.info(
                "Recalibrado {} con perfil v{} (ventana '{}', huella {})",
                profile.residentId.value, profile.version, window, proyectada.value.fingerprint.value,
            )
        }
        vigentes[profile.residentId] = Vigente(profile, window)
        return true
    }

    /**
     * La ventana que rige ahora.
     *
     * Si dos ventanas declaradas se solapan gana la primera declarada, de forma
     * deterministica: un replay tiene que producir la misma calibracion. Cuando
     * ninguna aplica, rige [ResidentProfile.ALWAYS], que es el regimen normal.
     */
    internal fun activeWindow(profile: ResidentProfile): String {
        val ahora = LocalTime.now(clock)
        return profile.windows.firstOrNull { it.isActiveAt(ahora) }?.id
            ?: ResidentProfile.ALWAYS
    }
}
