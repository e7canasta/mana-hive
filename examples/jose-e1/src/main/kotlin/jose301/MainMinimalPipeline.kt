package jose301

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.kind
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.kernel.*
import com.manahive.profile.api.ResidentProfileDto
import com.manahive.runtime.Census
import com.manahive.runtime.NightWatchRuntime
import com.manahive.runtime.ProfileCalibrator
import com.manahive.scene.calibration.sceneCalibration
import com.manahive.scene.calibration.toDwellCatalog
import com.manahive.scene.core.DigitalTwin
import com.manahive.contracts.scene.SceneState
import com.manahive.scene.core.SignalHealth
import com.manahive.scene.interpreter.createInterpreter
import com.manahive.scene.sweeper.DwellMarks
import com.manahive.scene.sweeper.createSweeper
import com.manahive.sentinel.*
import java.io.File
import java.time.Instant

private val mapper = jacksonObjectMapper()

fun main(args: Array<String>) {
    println("═══════════════════════════════════════════════════════════════")
    println("  José MIN — Pipeline in-process: perfil mínimo apertura/cierre")
    println("═══════════════════════════════════════════════════════════════")
    println()

    // ── 1. Cargar perfil mínimo ──────────────────────────────────────
    val profilePath = args.firstOrNull()
        ?: "/home/visiona/workspace/mana-dist/config/mana-hive/profiles/jose-v-min.json"
    val dto = mapper.readValue<ResidentProfileDto>(File(profilePath))
    println("  Perfil: ${dto.profileId} (v${dto.version})")
    println()

    // ── 2. Calibración ──────────────────────────────────────────────
    val runtime = NightWatchRuntime()
    val census = Census()
    census.register(BED_4, JOSE, NIGHT, MONITOR)
    val calibrator = ProfileCalibrator(runtime, census)
    calibrator.accept(dto)

    val calibrations = runtime.get(JOSE)!!.calibrations
    val sceneCal = calibrations.scene
    val sentinelCal = calibrations.sentinel

    println("  Calibración OK")
    println()

    // ── 3. Scene Engine ──────────────────────────────────────────────
    println("  Stage 1: Scene Engine")
    val interpreter = createInterpreter(sceneCal)
    val sweeper = createSweeper()
    val dwellCatalog = sceneCal.toDwellCatalog()

    var twin = initialTwin()
    var dwellMarks = DwellMarks.NONE
    val allSceneEvents = mutableListOf<SceneEvent>()

    // ── 4. Sentinel Engine ───────────────────────────────────────────
    println("  Stage 2: Sentinel Engine")
    val sentinel = createSentinelEvaluator(sentinelCal)
    var ledger = EpisodeLedger.empty(JOSE)
    val allSignals = mutableListOf<SentinelSignal>()

    // ── 5. Observaciones del escenario E1 ────────────────────────────
    val start = Instant.parse("2024-01-15T22:00:00Z")

    data class ObsStep(val time: Instant, val kind: ObservationKind, val label: String)
    val steps = listOf(
        ObsStep(start, ObservationKind.IN_BED, "IN_BED (baseline)"),
        ObsStep(start.plusSeconds(75 * 60), ObservationKind.SITTING_IN_BED, "SITTING_IN_BED (sentado)"),
        ObsStep(start.plusSeconds(75 * 60 + 17 * 60), ObservationKind.IN_BED, "IN_BED (volvió a acostar)"),
    )

    for ((i, step) in steps.withIndex()) {
        println("  ── Obs ${i + 1}: ${step.label} @ ${step.time} ──")

        // Sweeper
        val sweepResult = sweeper.sweep(listOf(twin), step.time, dwellCatalog, dwellMarks)
        dwellMarks = sweepResult.value.marks
        if (sweepResult.value.facts.isNotEmpty()) {
            println("    Sweeper facts: ${sweepResult.value.facts.map { it::class.simpleName }}")
        }

        // Interpreter
        val obs = Observation(
            monitor = MONITOR,
            bed = BED_4,
            kind = step.kind,
            confidence = 0.95,
            observedAt = step.time,
        )
        val interpResult = interpreter.interpret(twin, obs, step.time)
        twin = interpResult.value.twin
        val sceneFacts = interpResult.value.facts
        allSceneEvents.addAll(sceneFacts)

        println("    PersonState: ${twin.state::class.simpleName}")
        println("    Scene facts: ${sceneFacts.map { it::class.simpleName }}")
        for (fact in sceneFacts) {
            when (fact) {
                is SceneEvent.TransitionDetected -> println("      → TransitionDetected: ${fact.from} → ${fact.to}")
                is SceneEvent.SceneStateChanged -> println("      → SceneStateChanged: ${fact.from} → ${fact.to}")
                else -> println("      → ${fact::class.simpleName}")
            }
        }

        // Sentinel
        for (fact in sceneFacts) {
            val result = sentinel.evaluate(fact, ledger, fact.at)
            ledger = result.value.episodes
            val newSignals = result.value.signals
            allSignals.addAll(newSignals)

            if (newSignals.isNotEmpty()) {
                for (sig in newSignals) {
                    when (sig) {
                        is SentinelSignal.EpisodeOpened -> println("    ⚡ SENTINEL: EpisodeOpened id=${sig.episode.value} severity=${sig.severity}")
                        is SentinelSignal.EpisodeClosed -> println("    ⚡ SENTINEL: EpisodeClosed id=${sig.episode.value} cause=${sig.cause}")
                        is SentinelSignal.UmbrellaEvent -> println("    ⚡ SENTINEL: UmbrellaEvent state=${sig.state} episode=${sig.episode.value}")
                        is SentinelSignal.AutoRecovery -> println("    ⚡ SENTINEL: AutoRecovery episode=${sig.episode.value} reversible=${sig.reversible}")
                        else -> println("    ⚡ SENTINEL: ${sig::class.simpleName}")
                    }
                }
            }
        }

        // Estado del episode
        val open = ledger.openForBed(BED_4)
        if (open != null) {
            println("    📋 Episode ABIERTO: id=${open.id} severity=${open.severity} closure=${open.closureCondition}")
        } else {
            println("    📋 Episode: ninguno abierto")
        }
        println()
    }

    // ── 6. Resumen ───────────────────────────────────────────────────
    println("═══════════════════════════════════════════════════════════════")
    println("  RESUMEN")
    println("═══════════════════════════════════════════════════════════════")
    println("  SceneEvents: ${allSceneEvents.size}")
    for (e in allSceneEvents) {
        when (e) {
            is SceneEvent.TransitionDetected -> println("    ${e::class.simpleName}: ${e.from} → ${e.to}")
            else -> println("    ${e::class.simpleName}")
        }
    }
    println()
    println("  SentinelSignals: ${allSignals.size}")
    for (s in allSignals) {
        when (s) {
            is SentinelSignal.EpisodeOpened -> println("    EPISODE_OPENED id=${s.episode.value} severity=${s.severity}")
            is SentinelSignal.EpisodeClosed -> println("    EPISODE_CLOSED id=${s.episode.value} cause=${s.cause}")
            is SentinelSignal.UmbrellaEvent -> println("    UMBRELLA_EVENT state=${s.state} episode=${s.episode.value}")
            else -> println("    ${s::class.simpleName}")
        }
    }
    println()

    val openEpisode = ledger.openForBed(BED_4)
    if (openEpisode != null) {
        println("  ❌ Episode ABIERTO al final: ${openEpisode.id} severity=${openEpisode.severity}")
    } else {
        println("  ✅ Todos los episodes cerrados al final")
    }
    println("═══════════════════════════════════════════════════════════════")
}
