package com.manahive.scene.batch

import com.manahive.contracts.perception.Observation
import com.manahive.contracts.scene.kind
import com.manahive.scene.batch.config.formatOffset
import com.manahive.scene.batch.events.Event
import com.manahive.scene.batch.output.FactsOutWriter
import com.manahive.scene.batch.output.FactsWriter
import com.manahive.scene.batch.output.LogWriter
import com.manahive.scene.sweeper.DwellMarks
import java.time.Instant

/**
 * Shared batch processing logic.
 *
 * Fowler: "Extract Method" + "Introduce Parameter Object" —
 * the processEvent() logic was duplicated in RunCommand and VerifyCommand.
 * Now it lives here with a single [BatchContext] parameter.
 *
 * Vernon: "Domain Service" — coordinates multiple aggregates
 * (DigitalTwin, ClockSweeper, SceneInterpreter) without belonging to any.
 */
object BatchProcessor {

    data class ProcessResult(
        val state: BatchState,
        val accepted: Boolean,
        val facts: List<com.manahive.contracts.scene.SceneEvent> = emptyList(),
    )

    fun processEvent(
        event: Event,
        state: BatchState,
        ctx: BatchContext,
        writers: BatchWriters,
    ): ProcessResult {
        val now = ctx.startTime.plus(event.offset.duration)

        // Every observation refreshes the heartbeat, even before sweeper runs.
        // This prevents false SIGNAL_LOST when the sensor is transmitting.
        val heartbeatTwin = state.twin.copy(
            signal = state.twin.signal.copy(lastHeartbeat = now),
        )

        // 1. Run sweeper between last event and this event
        val sweepResult = ctx.sweeper.sweep(
            twins = listOf(heartbeatTwin),
            now = now,
            thresholds = ctx.dwellCatalog,
            marks = state.marks,
        )

        for (fact in sweepResult.value.facts) {
            writers.jsonl.write(fact, event.offset, event.lineNumber)
            writers.out.write(event.offset, fact, event.lineNumber)
            writers.log.info("sweeper fact",
                "event" to event.lineNumber,
                "type" to fact::class.simpleName!!,
            )
            println("  ⏰ t=${formatOffset(event.offset)}  ${fact::class.simpleName}")
        }

        // 2. Run interpreter for this event
        val obs = Observation(
            sourceEventId = "batch-${event.lineNumber}",
            monitor = ctx.config.monitorId,
            bed = ctx.config.bedId,
            kind = event.kind,
            confidence = event.confidence,
            observedAt = now,
        )

        val result = ctx.interpreter.interpret(heartbeatTwin, obs, now)

        return if (result.discards.isNotEmpty()) {
            for (discard in result.discards) {
                writers.log.warn("observation discarded",
                    "event" to event.lineNumber,
                    "offset" to event.offset.toString(),
                    "kind" to event.kind.name,
                    "cause" to discard.cause.name,
                    "subject" to discard.subject,
                )
                println("  ⚠ t=${formatOffset(event.offset)}  ${state.twin.state.kind.name} → ${event.kind}  DISCARD: ${discard.cause.name}")
            }
            ProcessResult(
                state = state.copy(
                    twin = result.value.twin,
                    marks = sweepResult.value.marks,
                    lastTime = now,
                    discarded = state.discarded + 1,
                ),
                accepted = false,
                facts = sweepResult.value.facts + result.value.facts,
            )
        } else {
            writers.jsonl.writeAll(result.value.facts, event.offset, event.lineNumber)
            for (fact in result.value.facts) {
                writers.out.write(event.offset, fact, event.lineNumber)
                writers.log.info("interpreter fact",
                    "event" to event.lineNumber,
                    "type" to fact::class.simpleName!!,
                )
            }
            println("  ✓ t=${formatOffset(event.offset)}  ${state.twin.state.kind.name} → ${result.value.twin.state.kind.name}")

            ProcessResult(
                state = state.copy(
                    twin = result.value.twin,
                    marks = sweepResult.value.marks,
                    lastTime = now,
                    passed = state.passed + 1,
                ),
                accepted = true,
                facts = sweepResult.value.facts + result.value.facts,
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
    val jsonl: FactsWriter,
    val out: FactsOutWriter,
    val log: LogWriter,
)
