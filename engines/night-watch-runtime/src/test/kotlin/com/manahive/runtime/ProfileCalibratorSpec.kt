package com.manahive.runtime

import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.SceneState
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.profile.api.ProfileExamples
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * El documento que firma el director es el que vigila a la persona.
 *
 * Cierra la costura que faltaba: `ProfileMapper` y `ProfileProjection` estaban
 * construidos y probados, y **no los llamaba nadie** — lo que corria seguia
 * siendo plantilla mas parches.
 */
class ProfileCalibratorSpec {

    private val elena = ResidentId("elena")
    private val cama = BedId("301")

    private fun aLas(hora: String): Clock =
        Clock.fixed(Instant.parse("2026-08-30T$hora:00:00Z"), ZoneOffset.UTC)

    private fun montado(clock: Clock): Pair<ProfileCalibrator, NightWatchRuntime> {
        val runtime = NightWatchRuntime()
        val census = Census()
        census.register(cama, elena, NightId("2026-08-29"), MonitorId("mon-1"))
        return ProfileCalibrator(runtime, census, clock) to runtime
    }

    // ── un perfil entra y el residente queda vigilado ────────────────────

    @Test
    fun `un perfil valido da de alta al residente`() {
        val (cal, runtime) = montado(aLas("03"))
        assertTrue(cal.accept(ProfileExamples.ELENA))
        assertNotNull(runtime.get(elena))
    }

    @Test
    fun `queda el perfil, no solo la calibracion`() {
        // Hace falta el documento para reproyectar en el borde horario: una
        // calibracion es una foto de una ventana.
        val (cal, _) = montado(aLas("03"))
        cal.accept(ProfileExamples.ELENA)
        assertEquals(8, cal.current(elena)!!.version)
    }

    // ── la ventana horaria la decide el reloj ────────────────────────────

    @Test
    fun `a las 03 rige la noche y la baranda alerta`() {
        val (cal, runtime) = montado(aLas("03"))
        cal.accept(ProfileExamples.ELENA)

        val baranda = runtime.get(elena)!!.calibrations.sentinel
            .sceneStateRuleFor(SceneState.BED_LEFT)
        assertNotNull(baranda)
        assertEquals(Severity.HIGH, baranda!!.severity)
        assertEquals("DOWN", baranda.state)
    }

    @Test
    fun `a las 12 rige el dia y la baranda no alerta`() {
        val (cal, runtime) = montado(aLas("12"))
        cal.accept(ProfileExamples.ELENA)
        assertNull(
            runtime.get(elena)!!.calibrations.sentinel.sceneStateRuleFor(SceneState.BED_LEFT),
        )
    }

    @Test
    fun `el bano se estira menos de noche que de dia`() {
        val (noche, rtNoche) = montado(aLas("03"))
        noche.accept(ProfileExamples.ELENA)
        val (dia, rtDia) = montado(aLas("12"))
        dia.accept(ProfileExamples.ELENA)

        assertEquals(
            Duration.ofMinutes(8),
            rtNoche.get(elena)!!.calibrations.scene
                .dwellThresholds[StateKind.IN_BATHROOM]!!.exceeded,
        )
        assertEquals(
            Duration.ofMinutes(15),
            rtDia.get(elena)!!.calibrations.scene
                .dwellThresholds[StateKind.IN_BATHROOM]!!.exceeded,
        )
    }

    // ── el borde horario re-emite ────────────────────────────────────────

    @Test
    fun `cruzar las 22 reproyecta sin que nadie reenvie el perfil`() {
        val runtime = NightWatchRuntime()
        val census = Census()
        census.register(cama, elena, NightId("2026-08-29"), MonitorId("mon-1"))

        var ahora = Instant.parse("2026-08-30T12:00:00Z")
        val reloj = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId?): Clock = this
            override fun instant(): Instant = ahora
        }
        val cal = ProfileCalibrator(runtime, census, reloj)
        cal.accept(ProfileExamples.ELENA)

        assertNull(
            runtime.get(elena)!!.calibrations.sentinel.sceneStateRuleFor(SceneState.BED_LEFT),
            "de dia la baranda no alerta",
        )

        ahora = Instant.parse("2026-08-30T23:00:00Z")
        assertEquals(listOf(elena), cal.reprojectOnWindowEdge())

        assertNotNull(
            runtime.get(elena)!!.calibrations.sentinel.sceneStateRuleFor(SceneState.BED_LEFT),
            "cruzado el borde, la baranda alerta",
        )
    }

    @Test
    fun `sin cruzar el borde no reproyecta nada`() {
        val (cal, _) = montado(aLas("03"))
        cal.accept(ProfileExamples.ELENA)
        assertEquals(emptyList<ResidentId>(), cal.reprojectOnWindowEdge())
    }

    // ── un perfil que no cierra no se aplica ni a medias ─────────────────

    @Test
    fun `un perfil invalido se rechaza entero`() {
        val (cal, runtime) = montado(aLas("03"))
        val roto = ProfileExamples.ELENA.copy(
            provenance = ProfileExamples.ELENA.provenance.copy(reason = "  "),
        )
        assertFalse(cal.accept(roto))
        assertNull(runtime.get(elena))
    }

    @Test
    fun `un perfil invalido no pisa al que ya regia`() {
        val (cal, _) = montado(aLas("03"))
        assertTrue(cal.accept(ProfileExamples.ELENA))

        assertFalse(
            cal.accept(
                ProfileExamples.ELENA.copy(
                    version = 9, supersedes = 8,
                    provenance = ProfileExamples.ELENA.provenance.copy(reason = ""),
                ),
            ),
        )
        assertEquals(8, cal.current(elena)!!.version)
    }

    @Test
    fun `una version vieja que llega tarde no retrocede la vigilancia`() {
        // Pasa en cada replay del stream.
        val (cal, _) = montado(aLas("03"))
        assertTrue(cal.accept(ProfileExamples.ELENA))
        assertFalse(cal.accept(ProfileExamples.ELENA.copy(version = 7, supersedes = 6)))
        assertEquals(8, cal.current(elena)!!.version)
    }

    @Test
    fun `un residente que no esta en el censo no se da de alta`() {
        val runtime = NightWatchRuntime()
        val cal = ProfileCalibrator(runtime, Census(), aLas("03"))
        assertFalse(cal.accept(ProfileExamples.ELENA))
        assertNull(runtime.get(elena))
    }
}
