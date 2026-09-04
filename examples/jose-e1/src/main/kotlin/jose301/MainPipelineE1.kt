package jose301

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.SceneState
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.kernel.*
import com.manahive.profile.api.ResidentProfileDto
import com.manahive.runtime.Census
import com.manahive.runtime.NightWatchRuntime
import com.manahive.runtime.ProfileCalibrator
import com.manahive.scene.calibration.toDwellCatalog
import com.manahive.scene.core.DigitalTwin
import com.manahive.scene.core.SignalHealth
import com.manahive.scene.interpreter.createInterpreter
import com.manahive.scene.sweeper.createSweeper
import com.manahive.scene.sweeper.DwellMarks
import com.manahive.sentinel.createSentinelEvaluator
import java.io.File
import java.time.Duration
import java.time.Instant

fun main() {
    println("═══════════════════════════════════════════════════════════════")
    println("  José E1 aislado — v26 onEntry + events E1 23:15->23:32")
    println("═══════════════════════════════════════════════════════════════")
    val mapper = jacksonObjectMapper().apply { findAndRegisterModules() }

    // 1. Cargar jose-v13.json (ahora v26) desde disco (no classpath)
    val profileFile = File("/home/visiona/workspace/mana-dist/config/mana-hive/profiles/jose-v13.json")
    require(profileFile.exists()) { "No existe ${profileFile.absolutePath}" }
    val dto = mapper.readValue<ResidentProfileDto>(profileFile)
    println("  Perfil: ${dto.profileId} v${dto.version} validFrom=${dto.validFrom}")

    val runtime = NightWatchRuntime()
    val census = Census()
    census.register(BED_4, JOSE, NIGHT, MONITOR)
    val calibrator = ProfileCalibrator(runtime, census)
    check(calibrator.accept(dto)) { "Perfil rechazado" }
    val cal = runtime.get(JOSE)!!.calibrations
    println("  Calibración: SITTING onEntry=${cal.sentinel.transitionRuleFor(com.manahive.contracts.scene.StateKind.SITTING_IN_BED)?.id?.value} SAFE_ONLY=${cal.sentinel.transitionRuleFor(com.manahive.contracts.scene.StateKind.SITTING_IN_BED)?.closureCondition}")

    val start = Instant.parse("2026-09-03T22:00:00Z")
    val interpreter = createInterpreter(cal.scene)
    val sweeper = createSweeper()
    val dwellCatalog = cal.scene.toDwellCatalog()
    var twin = DigitalTwin(
        bed = BED_4, night = NIGHT, occupant = JOSE,
        state = PersonState.Unknown(com.manahive.contracts.scene.UnknownCause.SIGNAL_LOST),
        stateSince = Instant.EPOCH, scene = SceneState(), sceneSince = Instant.EPOCH,
        signal = SignalHealth(monitor = MONITOR, lastHeartbeat = Instant.EPOCH, lost = true),
        calibration = cal.scene
    )
    var marks = DwellMarks.NONE
    val sceneEvents = mutableListOf<SceneEvent>()

    data class Ev(val at: Instant, val kind: ObservationKind)
    val evs = listOf(
        Ev(start, ObservationKind.IN_BED),
        Ev(start.plus(Duration.ofMinutes(75)), ObservationKind.SITTING_IN_BED), // 23:15
        Ev(start.plus(Duration.ofMinutes(92)), ObservationKind.IN_BED), // 23:32
    )

    for (ev in evs) {
        val sweep = sweeper.sweep(listOf(twin), ev.at, dwellCatalog, marks)
        sceneEvents.addAll(sweep.value.facts)
        marks = sweep.value.marks
        val obs = com.manahive.contracts.perception.Observation(
            monitor = MONITOR, bed = BED_4, kind = ev.kind, confidence = 0.92, observedAt = ev.at
        )
        val res = interpreter.interpret(twin, obs, ev.at)
        twin = res.value.twin
        sceneEvents.addAll(res.value.facts)
        println("  OBS ${ev.kind} @ ${ev.at} -> twin=${twin.state::class.simpleName} facts=${res.value.facts.map { it::class.simpleName }}")
    }
    println("\n  SceneEvents: ${sceneEvents.size} -> ${sceneEvents.map { it::class.simpleName }}")

    val evaluator = createSentinelEvaluator(cal.sentinel)
    var ledger = com.manahive.sentinel.EpisodeLedger.empty(JOSE)
    val signals = mutableListOf<SentinelSignal>()
    for (fact in sceneEvents) {
        val r = evaluator.evaluate(fact, ledger, fact.at)
        ledger = r.value.episodes
        signals.addAll(r.value.signals)
        if (r.value.signals.isNotEmpty()) {
            println("    Fact ${fact::class.simpleName} @ ${fact.at} -> signals=${r.value.signals.map { it::class.simpleName to (it as? SentinelSignal.EpisodeOpened)?.severity }} ledger open=${ledger.open.size}")
        }
    }
    println("\n  SentinelSignals: ${signals.size}")
    signals.forEach { s ->
        when (s) {
            is SentinelSignal.EpisodeOpened -> println("    OPENED ${s.at} ${s.trigger} ${s.severity} episode=${s.episode.value}")
            is SentinelSignal.EpisodeClosed -> println("    CLOSED ${s.at} cause=${s.cause} episode=${s.episode.value}")
            is SentinelSignal.UmbrellaEvent -> println("    UMBRELLA ${s.at} ${s.state} triggerOn=${s.triggerOn}")
            is SentinelSignal.AutoRecovery -> println("    AUTO_RECOVERY ${s.at}")
            else -> println("    ${s::class.simpleName} ${s.at}")
        }
    }
    println("\n  Ledger open=${ledger.open.size} closed=${ledger.closed.size}")
    check(signals.filterIsInstance<SentinelSignal.EpisodeOpened>().size == 1) { "Esperaba 1 OPENED" }
    check(signals.filterIsInstance<SentinelSignal.EpisodeClosed>().size == 1) { "Esperaba 1 CLOSED, hallados ${signals.filterIsInstance<SentinelSignal.EpisodeClosed>().size} -> ${signals.map { it::class.simpleName }}" }
    println("\n  ✅ E1 aislado: 1 OPENED 23:15 + 1 CLOSED 23:32")
}
