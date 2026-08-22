package com.manahive.scene

import com.manahive.contracts.scene.SceneFact
import com.manahive.contracts.scene.SceneFact.DwellExceeded
import com.manahive.contracts.scene.SceneFact.DwellWarning
import com.manahive.contracts.scene.SceneFact.SignalLost
import com.manahive.contracts.scene.kind
import com.manahive.kernel.EngineVersion
import com.manahive.kernel.Explained
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
public class ClockSweeperImpl : ClockSweeper {

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
        val allFacts = mutableListOf<SceneFact>()
        val newMarks = mutableSetOf<DwellMarkKey>()

        for (twin in twins) {
            // SE-18: Each resident has its own dwell thresholds via calibration.
            // Fallback to the default catalog if twin has no calibration.
            val catalog = twin.calibration?.toDwellCatalog() ?: thresholds

            val dwellResult = checkDwell(twin, now, catalog, marks)
            allFacts += dwellResult.facts
            newMarks += dwellResult.marks

            val signalResult = checkSignalLost(twin, now, catalog, marks)
            allFacts += signalResult.facts
            newMarks += signalResult.marks
        }

        return Explained(
            value = SweepResult(
                facts = allFacts,
                marks = DwellMarks(marks.emitted + newMarks),
            ),
            explanation = emptyList(),
            discards = emptyList(),
        )
    }

    // ── Dwell Check ────────────────────────────────────────────────────────

    private data class DwellCheckResult(
        val facts: List<SceneFact>,
        val marks: Set<DwellMarkKey>,
    )

    private fun checkDwell(
        twin: DigitalTwin,
        now: Instant,
        thresholds: DwellCatalog,
        marks: DwellMarks,
    ): DwellCheckResult {
        val stateKind = twin.state.kind
        val dwellThreshold = thresholds.byState[stateKind] ?: return DwellCheckResult(emptyList(), emptySet())
        val threshold = dwellThreshold.exceeded

        val duration = Duration.between(twin.stateSince, now)
        val markKey = markKeyFor(twin)

        val facts = mutableListOf<SceneFact>()
        val newMarks = mutableSetOf<DwellMarkKey>()

        // Check DwellExceeded first (higher priority)
        if (duration >= threshold) {
            if (!marks.emitted.contains(markKey)) {
                facts += DwellExceeded(
                    bed = twin.bed,
                    night = twin.night,
                    at = now,
                    state = twin.state,
                    threshold = threshold,
                    since = twin.stateSince,
                )
                newMarks += markKey
            }
        }

        // Check DwellWarning — explicit threshold from DwellThreshold
        val warningThreshold = dwellThreshold.warning
        if (duration >= warningThreshold) {
            val warningMark = markKey.copy(warning = true)
            if (!marks.emitted.contains(warningMark)) {
                // Only emit warning if we haven't emitted exceeded
                if (!facts.any { it is DwellExceeded }) {
                    facts += DwellWarning(
                        bed = twin.bed,
                        night = twin.night,
                        at = now,
                        state = twin.state,
                        threshold = threshold,
                        since = twin.stateSince,
                    )
                    newMarks += warningMark
                }
            }
        }

        return DwellCheckResult(facts, newMarks)
    }

    // ── Signal Lost Check ──────────────────────────────────────────────────

    private fun checkSignalLost(
        twin: DigitalTwin,
        now: Instant,
        thresholds: DwellCatalog,
        marks: DwellMarks,
    ): DwellCheckResult {
        val heartbeatTimeout = thresholds.heartbeatTimeout

        val timeSinceHeartbeat = Duration.between(twin.signal.lastHeartbeat, now)
        if (timeSinceHeartbeat < heartbeatTimeout) {
            return DwellCheckResult(emptyList(), emptySet())
        }

        val markKey = markKeyFor(twin)

        if (marks.emitted.contains(markKey)) {
            return DwellCheckResult(emptyList(), emptySet())
        }

        val fact = SignalLost(
            bed = twin.bed,
            night = twin.night,
            at = now,
            monitor = twin.signal.monitor,
            lastHeartbeat = twin.signal.lastHeartbeat,
        )

        return DwellCheckResult(listOf(fact), setOf(markKey))
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun markKeyFor(twin: DigitalTwin): DwellMarkKey = DwellMarkKey(
        bed = twin.bed,
        state = twin.state.kind,
        since = twin.stateSince,
        warning = false,
    )
}
