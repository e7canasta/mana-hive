package com.manahive.sentinel.batch

import com.manahive.contracts.scene.SceneFact
import com.manahive.contracts.scene.personStateFromKind
import com.manahive.kernel.MonitorId
import com.manahive.kernel.StaffId
import com.manahive.sentinel.batch.events.SceneFactEvent
import com.manahive.sentinel.batch.output.LogWriter
import com.manahive.sentinel.batch.output.SignalJsonlWriter
import com.manahive.sentinel.batch.output.SignalOutWriter
import java.time.Instant

/**
 * Shared batch processing logic for sentinel.
 *
 * Fowler: "Extract Method" + "Introduce Parameter Object" —
 * the processEvent() logic was duplicated in RunCommand and VerifyCommand.
 * Now it lives here with a single [BatchContext] parameter.
 *
 * Vernon: "Domain Service" — coordinates the SentinelEvaluator
 * without belonging to any aggregate.
 */
object SentinelBatchProcessor {

    data class ProcessResult(
        val state: BatchState,
        val signalsEmitted: Int,
    )

    fun processEvent(
        event: SceneFactEvent,
        state: BatchState,
        ctx: BatchContext,
        writers: BatchWriters,
    ): ProcessResult {
        val now = ctx.startTime.plus(event.offset.duration)

        // 1. Convert batch event to SceneFact
        val fact = toSceneFact(event, ctx, now)
            ?: return ProcessResult(state = state, signalsEmitted = 0)

        // 2. Run evaluator
        val explained = ctx.evaluator.evaluate(fact, state.ledger, now)

        // 3. Write signals
        for (signal in explained.value.signals) {
            writers.out.write(event.offset, signal, event.lineNumber)
            writers.jsonl.write(signal, event.lineNumber)
            writers.log.info("signal emitted",
                "event" to event.lineNumber,
                "type" to signal::class.simpleName!!,
                "bed" to signal.bed.value,
            )
        }

        // 4. Write explanation to log
        for (step in explained.explanation) {
            writers.log.debug("explanation",
                "event" to event.lineNumber,
                "rule" to step.rule,
                "observed" to step.observed,
                "conclusion" to step.conclusion,
            )
        }

        // 5. Console output
        val signalCount = explained.value.signals.size
        if (signalCount > 0) {
            println("  ✓ t=${formatOffset(event.offset)}  ${event.typeName} → $signalCount signal(s)")
        } else {
            println("  · t=${formatOffset(event.offset)}  ${event.typeName} → no signal")
        }

        return ProcessResult(
            state = state.copy(
                ledger = explained.value.episodes,
                lastTime = now,
                passed = state.passed + 1,
            ),
            signalsEmitted = signalCount,
        )
    }

    /**
     * Converts a batch [SceneFactEvent] to a [SceneFact] for the evaluator.
     *
     * Fowler: "Replace Conditional with Polymorphism" — the `when` is
     * exhaustive over sealed subtypes, no null checks needed.
     */
    private fun toSceneFact(
        event: SceneFactEvent,
        ctx: BatchContext,
        now: Instant,
    ): SceneFact? {
        val bed = ctx.bedId
        val night = ctx.nightId

        return when (event) {
            is SceneFactEvent.Transition -> SceneFact.TransitionDetected(
                bed = bed, night = night, at = now,
                from = personStateFromKind(event.from),
                to = personStateFromKind(event.to),
            )
            is SceneFactEvent.StaffPresent -> SceneFact.StaffPresenceDetected(
                bed = bed, night = night, at = now,
                staff = event.staff?.let { StaffId(it) },
            )
            is SceneFactEvent.DwellExceeded -> SceneFact.DwellExceeded(
                bed = bed, night = night, at = now,
                state = personStateFromKind(event.state),
                threshold = event.threshold,
                since = now.minus(event.threshold),
            )
            is SceneFactEvent.DwellWarning -> SceneFact.DwellWarning(
                bed = bed, night = night, at = now,
                state = personStateFromKind(event.state),
                threshold = event.threshold,
                since = now.minus(event.threshold),
            )
            is SceneFactEvent.SignalLost -> {
                val lastHb = event.lastHeartbeat?.let { Instant.parse(it) } ?: now.minusSeconds(60)
                SceneFact.SignalLost(
                    bed = bed, night = night, at = now,
                    monitor = MonitorId(event.monitor),
                    lastHeartbeat = lastHb,
                )
            }
            is SceneFactEvent.SignalRecovered -> SceneFact.SignalRecovered(
                bed = bed, night = night, at = now,
                monitor = MonitorId(event.monitor),
            )
        }
    }
}

/**
 * All writers needed for batch processing.
 *
 * Fowler: "Introduce Parameter Object" — groups related writers.
 */
data class BatchWriters(
    val jsonl: SignalJsonlWriter,
    val out: SignalOutWriter,
    val log: LogWriter,
)
