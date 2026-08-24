package com.manahive.scene.sweeper

import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.SceneEvent.DwellExceeded
import com.manahive.contracts.scene.SceneEvent.DwellWarning
import com.manahive.contracts.scene.SceneEvent.ComeBackExceeded
import com.manahive.contracts.scene.SceneEvent.ComeBackWarning
import com.manahive.contracts.scene.SceneEvent.SceneDwellExceeded
import com.manahive.contracts.scene.SceneEvent.SceneDwellWarning
import com.manahive.contracts.scene.SceneEvent.SignalLost
import com.manahive.contracts.scene.kind
import com.manahive.kernel.EngineVersion
import com.manahive.kernel.Explained
import com.manahive.scene.calibration.DwellCatalog
import com.manahive.scene.calibration.toDwellCatalog
import com.manahive.scene.core.DigitalTwin
import java.time.Duration
import java.time.Instant

/**
 * The patrolman of silence: produces facts only the passage of time reveals.
 * Pure domain — no Spring, no IO, no side effects.
 *
 * Follows the Domain Event pattern (Vernon): DwellWarning, DwellExceeded, SignalLost
 * are facts about the world, not commands.
 *
 * Follows Idempotency via Marks (Fowler): DwellMarks prevent duplicate facts
 * across consecutive ticks.
 */
internal class ClockSweeperImpl : ClockSweeper {

    override val version: EngineVersion = EngineVersion(
        name = "clock-sweeper",
        semver = "1.0.0",
        buildFingerprint = "local-dev",
    )

    override fun sweep(
        twins: Collection<DigitalTwin>,
        now: Instant,
        thresholds: DwellCatalog,
        marks: DwellMarks,
    ): Explained<SweepResult> {
        val ctx = SweepContext(now, thresholds)
        val allFacts = mutableListOf<SceneEvent>()
        val newPersonMarks = mutableSetOf<DwellMarkKey>()
        val newSceneMarks = mutableSetOf<SceneDwellMarkKey>()

        for (twin in twins) {
            val catalog = twin.calibration?.toDwellCatalog() ?: thresholds
            val twinCtx = ctx.copy(thresholds = catalog)

            val (dwellFacts, dwellMarks) = checkDwell(twin, twinCtx, marks)
            allFacts += dwellFacts
            newPersonMarks += dwellMarks

            val (comeBackFacts, comeBackMarks) = checkComeBack(twin, twinCtx, marks)
            allFacts += comeBackFacts
            newPersonMarks += comeBackMarks

            val (signalFacts, signalMarks) = checkSignalLost(twin, twinCtx, marks)
            allFacts += signalFacts
            newPersonMarks += signalMarks

            val (sceneDwellFacts, sceneDwellMarks) = checkSceneDwell(twin, twinCtx)
            allFacts += sceneDwellFacts
            newSceneMarks += sceneDwellMarks
        }

        return Explained(
            value = SweepResult(
                facts = allFacts,
                marks = DwellMarks(marks.emitted + newPersonMarks),
            ),
            explanation = emptyList(),
            discards = emptyList(),
        )
    }

    // ── Sweep Context (Fowler: Introduce Parameter Object) ──────────────────

    private data class SweepContext(
        val now: Instant,
        val thresholds: DwellCatalog,
    )

    // ── Person State Dwell Check ────────────────────────────────────────────

    private data class DwellCheckResult(
        val facts: List<SceneEvent>,
        val marks: Set<DwellMarkKey>,
    )

    private fun checkDwell(
        twin: DigitalTwin,
        ctx: SweepContext,
        marks: DwellMarks,
    ): DwellCheckResult {
        val stateKind = twin.state.kind
        val dwellThreshold = ctx.thresholds.byState[stateKind] ?: return DwellCheckResult(emptyList(), emptySet())

        val duration = twin.durationInState(ctx.now)
        val markKey = twin.toDwellMarkKey()

        val facts = mutableListOf<SceneEvent>()
        val newMarks = mutableSetOf<DwellMarkKey>()

        checkDwellThreshold(
            config = DwellThresholdConfig(
                duration = duration,
                exceeded = dwellThreshold.exceeded,
                warning = dwellThreshold.warning,
                markKey = markKey,
                toWarningMark = { it.copy(warning = true) },
            ),
            emittedMarks = marks.emitted,
            emitExceeded = { twin.emitDwellExceeded(dwellThreshold.exceeded, ctx.now) },
            emitWarning = { twin.emitDwellWarning(dwellThreshold.warning, ctx.now) },
            isExceeded = { it is DwellExceeded },
            facts = facts,
            newMarks = newMarks,
        )

        return DwellCheckResult(facts, newMarks)
    }

    // ── ComeBack Check (Inverse Dwell — the mine) ────────────────────────

    /**
     * Checks come-back (inverse dwell) for a twin.
     *
     * The mine is planted when the person LEAVES the baseline state.
     * It explodes if they don't return within the threshold.
     * It's disarmed if they return before it explodes.
     *
     * Uses the same DwellMarkKey mechanism as normal dwell for idempotency:
     * the mark key uses the baseline state and leftStateAt as identity.
     */
    private fun checkComeBack(
        twin: DigitalTwin,
        ctx: SweepContext,
        marks: DwellMarks,
    ): DwellCheckResult {
        val baselineKind = twin.baselineState.kind
        val comeBackThreshold = ctx.thresholds.comeBackByBaseline[baselineKind]
            ?: return DwellCheckResult(emptyList(), emptySet())

        // Mine not planted: person IS in baseline state
        val duration = twin.durationSinceLeftBaseline(ctx.now)
            ?: return DwellCheckResult(emptyList(), emptySet())

        // Mark key: identity = (bed, baseline, leftStateAt, warning)
        // This ensures the mark is unique per departure event
        val leftAt = twin.leftStateAt ?: return DwellCheckResult(emptyList(), emptySet())
        val markKey = DwellMarkKey(
            bed = twin.bed,
            state = baselineKind,
            since = leftAt,
            warning = false,
        )

        val facts = mutableListOf<SceneEvent>()
        val newMarks = mutableSetOf<DwellMarkKey>()

        checkDwellThreshold(
            config = DwellThresholdConfig(
                duration = duration,
                exceeded = comeBackThreshold.exceeded,
                warning = comeBackThreshold.warning,
                markKey = markKey,
                toWarningMark = { it.copy(warning = true) },
            ),
            emittedMarks = marks.emitted,
            emitExceeded = { twin.emitComeBackExceeded(comeBackThreshold.exceeded, ctx.now) },
            emitWarning = { twin.emitComeBackWarning(comeBackThreshold.warning, ctx.now) },
            isExceeded = { it is ComeBackExceeded },
            facts = facts,
            newMarks = newMarks,
        )

        return DwellCheckResult(facts, newMarks)
    }

    // ── Scene State Dwell Check ─────────────────────────────────────────────

    private data class SceneDwellCheckResult(
        val facts: List<SceneEvent>,
        val marks: Set<SceneDwellMarkKey>,
    )

    private fun checkSceneDwell(
        twin: DigitalTwin,
        ctx: SweepContext,
    ): SceneDwellCheckResult {
        val facts = mutableListOf<SceneEvent>()
        val newMarks = mutableSetOf<SceneDwellMarkKey>()

        checkSceneFieldDwell(twin, "staff", twin.sceneSince, ctx, facts, newMarks)
        checkSceneFieldDwell(twin, "wheelchair", twin.sceneSince, ctx, facts, newMarks)
        checkSceneFieldDwell(twin, "walker", twin.sceneSince, ctx, facts, newMarks)
        checkSceneFieldDwell(twin, "bed.left", twin.sceneSince, ctx, facts, newMarks)
        checkSceneFieldDwell(twin, "bed.right", twin.sceneSince, ctx, facts, newMarks)

        return SceneDwellCheckResult(facts, newMarks)
    }

    private fun checkSceneFieldDwell(
        twin: DigitalTwin,
        field: String,
        since: Instant,
        ctx: SweepContext,
        facts: MutableList<SceneEvent>,
        newMarks: MutableSet<SceneDwellMarkKey>,
    ) {
        val dwellThreshold = ctx.thresholds.sceneThresholds[field] ?: return
        val duration = Duration.between(since, ctx.now)
        val markKey = SceneDwellMarkKey(bed = twin.bed, field = field, since = since, warning = false)

        checkDwellThreshold(
            config = DwellThresholdConfig(
                duration = duration,
                exceeded = dwellThreshold.exceeded,
                warning = dwellThreshold.warning,
                markKey = markKey,
                toWarningMark = { it.copy(warning = true) },
            ),
            emittedMarks = emptySet(),
            emitExceeded = {
                SceneDwellExceeded(
                    bed = twin.bed, night = twin.night, at = ctx.now,
                    field = field, threshold = dwellThreshold.exceeded, since = since,
                )
            },
            emitWarning = {
                SceneDwellWarning(
                    bed = twin.bed, night = twin.night, at = ctx.now,
                    field = field, threshold = dwellThreshold.exceeded, since = since,
                )
            },
            isExceeded = { it is SceneDwellExceeded && it.field == field },
            facts = facts,
            newMarks = newMarks,
        )
    }

    // ── Dwell Threshold Check (Fowler: Extract Method + Introduce Parameter Object) ──

    private data class DwellThresholdConfig<K>(
        val duration: Duration,
        val exceeded: Duration,
        val warning: Duration,
        val markKey: K,
        val toWarningMark: (K) -> K,
    )

    private fun <K> checkDwellThreshold(
        config: DwellThresholdConfig<K>,
        emittedMarks: Set<K>,
        emitExceeded: () -> SceneEvent,
        emitWarning: () -> SceneEvent,
        isExceeded: (SceneEvent) -> Boolean,
        facts: MutableList<SceneEvent>,
        newMarks: MutableSet<K>,
    ) {
        if (config.duration >= config.exceeded) {
            if (!emittedMarks.contains(config.markKey) && !newMarks.contains(config.markKey)) {
                facts += emitExceeded()
                newMarks += config.markKey
            }
        }

        if (config.duration >= config.warning) {
            val warningMark = config.toWarningMark(config.markKey)
            if (!emittedMarks.contains(warningMark) && !newMarks.contains(warningMark)) {
                if (!facts.any(isExceeded)) {
                    facts += emitWarning()
                    newMarks += warningMark
                }
            }
        }
    }

    // ── Signal Lost Check ──────────────────────────────────────────────────

    private fun checkSignalLost(
        twin: DigitalTwin,
        ctx: SweepContext,
        marks: DwellMarks,
    ): DwellCheckResult {
        val heartbeatTimeout = ctx.thresholds.heartbeatTimeout

        val timeSinceHeartbeat = Duration.between(twin.signal.lastHeartbeat, ctx.now)
        if (timeSinceHeartbeat < heartbeatTimeout) {
            return DwellCheckResult(emptyList(), emptySet())
        }

        val markKey = twin.toDwellMarkKey()

        if (marks.emitted.contains(markKey)) {
            return DwellCheckResult(emptyList(), emptySet())
        }

        val fact = twin.emitSignalLost(ctx.now)

        return DwellCheckResult(listOf(fact), setOf(markKey))
    }
}
