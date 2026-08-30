package jose301

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.blueprint.BlueprintOutcome
import com.manahive.contracts.scene.StateKind
import com.manahive.profile.api.ResidentProfileDto
import com.manahive.runtime.Census
import com.manahive.runtime.NightWatchRuntime
import com.manahive.runtime.ProfileCalibrator
import com.manahive.scene.bdd.scenario
import com.manahive.scene.bdd.to

private val mapper = jacksonObjectMapper()

fun main() {
    println("═══════════════════════════════════════════════════════════════")
    println("  José 301 — Arranque en Frío desde JSON (vs DSL)")
    println("═══════════════════════════════════════════════════════════════")
    println()

    // ── 1. Arranque en frío: cargar JSON desde classpath ──────────────
    val runtime = NightWatchRuntime()
    val census = Census()
    census.register(BED_4, JOSE, NIGHT, MONITOR)
    val calibrator = ProfileCalibrator(runtime, census)

    // Buscar en el classpath (resources)
    val cpStream = object {}::class.java.getResourceAsStream("/profiles/jose.json")
    if (cpStream == null) {
        println("  ❌ No se encontró profiles/jose.json en el classpath")
        return
    }
    
    val dto = mapper.readValue<ResidentProfileDto>(cpStream)
    cpStream.close()
    val accepted = calibrator.accept(dto)
    println("  Perfil cargado desde classpath: $accepted")
    println("  Versión: ${dto.version}")
    println()

    // ── 2. Obtener calibración generada ───────────────────────────────
    val calibrations = runtime.get(JOSE)!!.calibrations
    val sceneCalFromJson = calibrations.scene

    println("  Calibración generada desde JSON:")
    val comeBackRule = sceneCalFromJson.comeBackThresholds[StateKind.LYING]
    println("    ComeBack warning: ${comeBackRule?.warning}")
    println("    ComeBack exceeded: ${comeBackRule?.exceeded}")
    println()

    // ── 3. Comparar con configBasica del blueprint ────────────────────
    println("── Comparación con Blueprint Original ──")
    println()
    println("  Configuración DSL (configBasica):")
    val originalRule = configBasica.comeBackThresholds[StateKind.LYING]
    println("    ComeBack warning: ${originalRule?.warning}")
    println("    ComeBack exceeded: ${originalRule?.exceeded}")
    println()

    val warningMatch = comeBackRule?.warning == originalRule?.warning
    val exceededMatch = comeBackRule?.exceeded == originalRule?.exceeded
    
    if (warningMatch && exceededMatch) {
        println("  ✅ Las calibraciones COINCIDEN")
    } else {
        println("  ❌ Las calibraciones son DIFERENTES")
    }
    println()

    // ── 4. Ejecutar escenario E1 con calibración del JSON ─────────────
    println("── Escenario E1: Arranque en Frío ──")
    println()

    jose.scenario("E1: 17 min sin acostarse (desde JSON)") {
        given { calibration(sceneCalFromJson) }
        includes(e1)
        thenExpectTransitions(3)
        thenExpectTransition(Unknown to Lying)
        thenExpectTransition(Lying to SittingInBed)
        thenExpectTransition(SittingInBed to Lying)
    }.report()

    // ── 5. Ejecutar escenario E1 con calibración del blueprint ────────
    println("── Escenario E1: Blueprint Original ──")
    println()

    jose.scenario("E1: 17 min sin acostarse (DSL original)") {
        given { calibration(configBasica) }
        includes(e1)
        thenExpectTransitions(3)
        thenExpectTransition(Unknown to Lying)
        thenExpectTransition(Lying to SittingInBed)
        thenExpectTransition(SittingInBed to Lying)
    }.report()

    // ── 6. Resultado final ────────────────────────────────────────────
    println("═══════════════════════════════════════════════════════════════")
    if (warningMatch && exceededMatch) {
        println("  ✅ ARRANQUE EN FRÍO: Las calibraciones coinciden")
        println("  ✅ AMBOS MÉTODOS PRODUCEN LA MISMA CONFIGURACIÓN")
    } else {
        println("  ❌ Las calibraciones NO coinciden - revisar JSON")
    }
    println("═══════════════════════════════════════════════════════════════")

    BlueprintOutcome.summarize()
}
