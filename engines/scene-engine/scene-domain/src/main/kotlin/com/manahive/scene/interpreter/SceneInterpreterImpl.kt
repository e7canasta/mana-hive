package com.manahive.scene.interpreter

import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneFact
import com.manahive.contracts.scene.SceneFact.SceneStateChanged
import com.manahive.contracts.scene.SceneFact.SignalRecovered
import com.manahive.contracts.scene.SceneFact.TransitionDetected
import com.manahive.contracts.scene.kind
import com.manahive.contracts.scene.toPersonState
import com.manahive.contracts.scene.toSceneStateChange
import com.manahive.kernel.Discard
import com.manahive.kernel.DiscardCause
import com.manahive.kernel.DiscardCause.CONFIDENCE_TOO_LOW
import com.manahive.kernel.DiscardCause.DUPLICATE
import com.manahive.kernel.DiscardCause.HYSTERESIS_NOT_MET
import com.manahive.kernel.DiscardCause.ILLEGAL_TRANSITION
import com.manahive.kernel.EngineVersion
import com.manahive.kernel.Explained
import com.manahive.kernel.ExplanationStep
import com.manahive.scene.calibration.SceneCalibration
import com.manahive.scene.core.DigitalTwin
import java.time.Duration
import java.time.Instant

/**
 * The brain that interprets observations into scene facts.
 *
 * Calibration is injected via constructor — one interpreter, one set of rules.
 * The [SceneCalibration] Value Object encapsulates all business rules.
 *
 * Follows the Specification Pattern (Vernon):
 * - ConfidenceSpec: observation.confidence >= minConfidence[kind]
 * - LegalTransitionSpec: table.isLegal(from, to)
 * - HysteresisSpec: duration >= table.hysteresis(from, to)
 *
 * Follows SRP (Martin): only interprets, doesn't decide alerts.
 * Follows DIP (Martin): depends on abstractions (SceneCalibration Value Object).
 */
internal class SceneInterpreterImpl(
    private val calibration: SceneCalibration,
) : SceneInterpreter {

    override val version: EngineVersion = EngineVersion(
        name = "scene-interpreter",
        semver = "1.0.0",
        buildFingerprint = "local-dev",
    )

    override fun interpret(
        twin: DigitalTwin,
        observation: Observation,
        now: Instant,
    ): Explained<SceneVerdict> {
        // Check if this is a scene state event (orthogonal to person state)
        val sceneChange = observation.kind.toSceneStateChange()
        if (sceneChange != null) {
            return interpretSceneEvent(twin, observation, sceneChange, now)
        }

        // Person state interpretation pipeline
        val targetState = observation.kind.toPersonState()

        // 1. CONFIANZA — Specification: confidence >= minConfidence[kind]
        val confidenceCheck = checkConfidence(twin, observation, targetState)
        if (confidenceCheck != null) return confidenceCheck

        // 2. RECUPERACION DE SENSOR — Chain of Responsibility: no corta el pipeline
        val (recoveredTwin, recoveryFacts) = recoverSensor(twin, observation, now)

        // 3. DUPLICADO — Idempotency Check: same state = no-op
        val duplicateCheck = checkDuplicate(recoveredTwin, targetState, recoveryFacts)
        if (duplicateCheck != null) return duplicateCheck

        // 4. TRANSICION ILEGAL — Specification: table.isLegal(from, to)
        val illegalCheck = checkIllegalTransition(recoveredTwin, targetState, recoveryFacts)
        if (illegalCheck != null) return illegalCheck

        // 5. HYSTERESIS — Temporal Specification: duration >= hysteresis
        val hysteresisCheck = checkHysteresis(recoveredTwin, targetState, now, recoveryFacts)
        if (hysteresisCheck != null) return hysteresisCheck

        // 6. TRANSICION VALIDA — Domain Event: emit TransitionDetected
        return emitTransition(recoveredTwin, targetState, now, recoveryFacts)
    }

    // ── Scene State Interpretation ──────────────────────────────────────────

    private fun interpretSceneEvent(
        twin: DigitalTwin,
        observation: Observation,
        sceneChange: (com.manahive.contracts.scene.SceneState) -> com.manahive.contracts.scene.SceneState,
        now: Instant,
    ): Explained<SceneVerdict> {
        // 1. CONFIANZA — Check confidence for scene events
        val confidenceCheck = checkSceneConfidence(twin, observation)
        if (confidenceCheck != null) return confidenceCheck

        // 2. DUPLICADO — Check if scene state would change
        val oldScene = twin.scene
        val newScene = sceneChange(oldScene)
        if (newScene == oldScene) {
            return discarded(
                twin = twin,
                subject = "duplicate scene state",
                cause = DUPLICATE,
            )
        }

        // 3. HYSTERESIS — Check hysteresis for scene state changes
        val field = determineField(oldScene, newScene)
        val hysteresisCheck = checkSceneHysteresis(twin, field, now)
        if (hysteresisCheck != null) return hysteresisCheck

        // 4. TRANSICION VALIDA — Evolve twin and emit fact
        val updatedTwin = twin.evolveScene(sceneChange, now)
        val fact = updatedTwin.emitSceneStateChanged(
            field = field,
            from = oldScene.toBitmask().toString(),
            to = newScene.toBitmask().toString(),
            at = now,
        )

        val step = ExplanationStep(
            rule = "scene-state",
            observed = "$field: ${oldScene.toBitmask()} -> ${newScene.toBitmask()}",
            conclusion = "scene state change accepted",
        )

        return Explained(
            value = SceneVerdict(updatedTwin, listOf(fact)),
            explanation = listOf(step),
            discards = emptyList(),
        )
    }

    private fun checkSceneConfidence(
        twin: DigitalTwin,
        observation: Observation,
    ): Explained<SceneVerdict>? {
        val minConf = calibration.sceneConfidenceFor(observation.kind)
        if (observation.confidence >= minConf.value) return null
        return discarded(
            twin = twin,
            subject = "confidence ${observation.confidence} < $minConf",
            cause = CONFIDENCE_TOO_LOW,
        )
    }

    private fun checkSceneHysteresis(
        twin: DigitalTwin,
        field: String,
        now: Instant,
    ): Explained<SceneVerdict>? {
        val minHysteresis = calibration.sceneHysteresisFor(field)
        if (minHysteresis == Duration.ZERO) return null  // No hysteresis configured

        val durationInSceneState = twin.durationInSceneState(now)
        if (durationInSceneState >= minHysteresis) return null

        return discarded(
            twin = twin,
            subject = "scene hysteresis $durationInSceneState < $minHysteresis",
            cause = HYSTERESIS_NOT_MET,
        )
    }

    private fun determineField(
        oldScene: com.manahive.contracts.scene.SceneState,
        newScene: com.manahive.contracts.scene.SceneState,
    ): String = when {
        oldScene.staff != newScene.staff -> "staff"
        oldScene.wheelchair != newScene.wheelchair -> "wheelchair"
        oldScene.walker != newScene.walker -> "walker"
        oldScene.bed != newScene.bed -> "bed"
        else -> "unknown"
    }

    // ── Specification: Confidence ──────────────────────────────────────────

    private fun checkConfidence(
        twin: DigitalTwin,
        observation: Observation,
        targetState: PersonState,
    ): Explained<SceneVerdict>? {
        val minConf = calibration.confidence.forState(targetState.kind)
        if (observation.confidence >= minConf.value) return null
        return discarded(
            twin = twin,
            subject = "confidence ${observation.confidence} < $minConf",
            cause = CONFIDENCE_TOO_LOW,
        )
    }

    // ── Chain of Responsibility: Sensor Recovery ───────────────────────────

    private data class SensorRecovery(
        val twin: DigitalTwin,
        val facts: List<SceneFact>,
    )

    private fun recoverSensor(
        twin: DigitalTwin,
        observation: Observation,
        now: Instant,
    ): SensorRecovery {
        if (!twin.signal.lost || observation.kind == ObservationKind.HEARTBEAT) {
            return SensorRecovery(twin, emptyList())
        }
        val recoveredTwin = twin.copy(signal = twin.signal.copy(lost = false))
        val fact = twin.emitSignalRecovered(now)
        return SensorRecovery(recoveredTwin, listOf(fact))
    }

    // ── Idempotency Check: Duplicate ───────────────────────────────────────

    private fun checkDuplicate(
        twin: DigitalTwin,
        targetState: PersonState,
        recoveryFacts: List<SceneFact>,
    ): Explained<SceneVerdict>? {
        if (targetState != twin.state) return null
        return discarded(
            twin = twin,
            subject = "duplicate state ${twin.state.kind}",
            cause = DUPLICATE,
            facts = recoveryFacts,
        )
    }

    // ── Specification: Legal Transition ────────────────────────────────────

    private fun checkIllegalTransition(
        twin: DigitalTwin,
        targetState: PersonState,
        recoveryFacts: List<SceneFact>,
    ): Explained<SceneVerdict>? {
        if (calibration.table.isLegal(twin.state.kind, targetState.kind)) return null
        return discarded(
            twin = twin,
            subject = "illegal ${twin.state.kind} -> ${targetState.kind}",
            cause = ILLEGAL_TRANSITION,
            facts = recoveryFacts,
        )
    }

    // ── Temporal Specification: Hysteresis ─────────────────────────────────

    private fun checkHysteresis(
        twin: DigitalTwin,
        targetState: PersonState,
        now: Instant,
        recoveryFacts: List<SceneFact>,
    ): Explained<SceneVerdict>? {
        val durationInState = twin.durationInState(now)
        val minHysteresis = calibration.table.hysteresis(twin.state.kind, targetState.kind)
        if (durationInState >= minHysteresis) return null
        return discarded(
            twin = twin,
            subject = "hysteresis $durationInState < $minHysteresis",
            cause = HYSTERESIS_NOT_MET,
            facts = recoveryFacts,
        )
    }

    // ── Domain Event: Valid Transition ─────────────────────────────────────

    private fun emitTransition(
        twin: DigitalTwin,
        targetState: PersonState,
        now: Instant,
        recoveryFacts: List<SceneFact>,
    ): Explained<SceneVerdict> {
        val updatedTwin = twin.copy(state = targetState, stateSince = now)
        val fact = twin.emitTransition(targetState, now)
        val step = ExplanationStep(
            rule = "transition-table",
            observed = "${twin.state.kind} -> ${targetState.kind}",
            conclusion = "transition legal accepted",
        )
        return Explained(
            value = SceneVerdict(updatedTwin, recoveryFacts + fact),
            explanation = listOf(step),
            discards = emptyList(),
        )
    }

    // ── Factory: Discarded Result ──────────────────────────────────────────

    private fun discarded(
        twin: DigitalTwin,
        subject: String,
        cause: DiscardCause,
        facts: List<SceneFact> = emptyList(),
    ): Explained<SceneVerdict> = Explained(
        value = SceneVerdict(twin, facts),
        explanation = emptyList(),
        discards = listOf(Discard(subject, cause)),
    )

}
