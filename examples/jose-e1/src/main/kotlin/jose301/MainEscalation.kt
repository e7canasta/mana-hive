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
    println("  José ESCALATION — Pipeline in-process: WARNING → CRITICAL")
    println("═══════════════════════════════════════════════════════════════")
    println()

    // ── 1. Cargar perfil de escalado ─────────────────────────────────
    val profilePath = args.firstOrNull()
        ?: "/home/visiona/workspace/mana-dist/config/mana-hive/profiles/jose-v22.json"
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

    // ── 2.1 Debug: mostrar reglas de sentinel ────────────────────────
    println("  ── Sentinel Rules ──")
    println("    transitionRules: ${sentinelCal.transitionRules.keys}")
    println("    dwellRules: ${sentinelCal.dwellRules.keys}")
    println("    comeBackRules: ${sentinelCal.comeBackRules.keys}")
    for ((state, rule) in sentinelCal.dwellRules) {
        println("    Dwell rule for $state: severity=${rule.severity}, trigger=${rule.trigger}, triggerOn=${rule.triggerOn}, closure=${rule.closureCondition}")
    }
    println()

    // ── 2.2 Debug: mostrar dwell catalog ─────────────────────────────
    val dwellCatalog = sceneCal.toDwellCatalog()
    println("  ── Dwell Catalog (Scene Engine) ──")
    println("    byState keys: ${dwellCatalog.byState.keys}")
    for ((state, threshold) in dwellCatalog.byState) {
        println("    Threshold for $state: warning=${threshold.warning}, exceeded=${threshold.exceeded}")
    }
    println()

    // ── 3. Scene Engine ──────────────────────────────────────────────
    println("  Stage 1: Scene Engine")
    val interpreter = createInterpreter(sceneCal)
    val sweeper = createSweeper()

    var twin = initialTwin()
    var dwellMarks = DwellMarks.NONE
    val allSceneEvents = mutableListOf<SceneEvent>()

    // ── 4. Sentinel Engine ───────────────────────────────────────────
    println("  Stage 2: Sentinel Engine")
    val sentinel = createSentinelEvaluator(sentinelCal)
    var ledger = EpisodeLedger.empty(JOSE)
    val allSignals = mutableListOf<SentinelSignal>()

    // ── 5. Observaciones del escenario de escalado ───────────────────
    // IN_BED → SITTING_IN_BED → STANDING (3 min) → STAFF → IN_BED
    val start = Instant.parse("2024-01-15T22:00:00Z")

    data class ObsStep(val time: Instant, val kind: ObservationKind, val label: String)
    val steps = listOf(
        ObsStep(start, ObservationKind.IN_BED, "IN_BED (baseline)"),
        ObsStep(start.plusSeconds(5 * 60), ObservationKind.SITTING_IN_BED, "SITTING_IN_BED (sentado)"),
        ObsStep(start.plusSeconds(6 * 60), ObservationKind.STANDING, "STANDING (parado)"),
        // Simular 3 minutos en STANDING - el sweeper debería generar DwellExceeded
        ObsStep(start.plusSeconds(7 * 60), ObservationKind.STANDING, "STANDING (1 min)"),
        ObsStep(start.plusSeconds(8 * 60), ObservationKind.STANDING, "STANDING (2 min)"),
        ObsStep(start.plusSeconds(9 * 60), ObservationKind.STANDING, "STANDING (3 min) - debería escalar"),
        ObsStep(start.plusSeconds(10 * 60), ObservationKind.STAFF_ENTERED, "STAFF_ENTERED"),
        ObsStep(start.plusSeconds(12 * 60), ObservationKind.IN_BED, "IN_BED (volvió a acostar)"),
    )

    for ((i, step) in steps.withIndex()) {
        println("  ── Obs ${i + 1}: ${step.label} @ ${step.time} ──")

        // Sweeper
        val sweepResult = sweeper.sweep(listOf(twin), step.time, dwellCatalog, dwellMarks)
        dwellMarks = sweepResult.value.marks
        if (sweepResult.value.facts.isNotEmpty()) {
            println("    Sweeper facts:")
            for (fact in sweepResult.value.facts) {
                when (fact) {
                    is SceneEvent.DwellExceeded -> println("      💥 DwellExceeded: ${fact.state} (threshold=${fact.threshold})")
                    is SceneEvent.DwellWarning -> println("      ⚠️ DwellWarning: ${fact.state} (threshold=${fact.threshold})")
                    is SceneEvent.ComeBackExceeded -> println("      💥 ComeBackExceeded: ${fact.baseline}")
                    else -> println("      ${fact::class.simpleName}")
                }
            }
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

        println("    PersonState: ${twin.state::class.simpleName} (since ${twin.stateSince})")
        println("    Scene facts: ${sceneFacts.map { it::class.simpleName }}")
        for (fact in sceneFacts) {
            when (fact) {
                is SceneEvent.TransitionDetected -> println("      → TransitionDetected: ${fact.from} → ${fact.to}")
                is SceneEvent.SceneStateChanged -> println("      → SceneStateChanged: ${fact.from} → ${fact.to}")
                is SceneEvent.StaffPresenceDetected -> println("      → StaffPresenceDetected: ${fact.bed}")
                else -> println("      → ${fact::class.simpleName}")
            }
        }

        // Sentinel - evaluar TODOS los facts (sweeper + interpreter)
        val allFacts = sweepResult.value.facts + sceneFacts
        for (fact in allFacts) {
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

            // Debug: mostrar explicación
            if (result.explanation.isNotEmpty()) {
                for (exp in result.explanation) {
                    println("    📝 Explain: rule=${exp.rule} → ${exp.conclusion}")
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
            is SceneEvent.DwellExceeded -> println("    ${e::class.simpleName}: ${e.state} (threshold=${e.threshold})")
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
