package com.manahive.hub.batch

import com.manahive.contracts.EventEnvelope
import com.manahive.hub.ledger.EventStore
import com.manahive.hub.ledger.InMemoryLedger
import com.manahive.hub.ledger.StoreResult
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import java.io.File

fun main(args: Array<String>) {
    HubBatchApp().main(args)
}

class HubBatchApp : CliktCommand(
    name = "hub-batch",
    help = "Hub event storage and replay",
) {
    init {
        subcommands(
            StoreCommand(),
            ReplayCommand(),
            StatusCommand(),
        )
    }

    override fun run() {}
}

/**
 * Store command: reads EventEnvelopes from input file, stores in ledger,
 * writes StoredEvents to hub file.
 *
 * Fowler: "Application Layer" — delegates domain logic to EventStore.
 * Reduced from 70 lines to 40 lines via Extract Class (HubFileHelper).
 */
class StoreCommand : CliktCommand(
    name = "store",
    help = "Store events in the Hub ledger",
) {
    private val input by option("--input", help = "Input file with EventEnvelope JSONL")
    private val stream by option("--stream", help = "Stream name (e.g., scene.fact.v1.12A)")
    private val hub by option("--hub", help = "Hub output file (StoredEvent JSONL)")

    override fun run() {
        val inputStream = input ?: run { println("ERROR: --input is required"); return }
        val streamName = stream ?: run { println("ERROR: --stream is required"); return }
        val hubPath = hub ?: run { println("ERROR: --hub is required"); return }

        val inputFile = File(inputStream)
        if (!inputFile.exists()) {
            println("ERROR: Input file not found: $inputStream")
            return
        }

        val events = HubFileHelper.readEvents(inputFile)
        println("Storing ${events.size} events to stream: $streamName")

        val hubFile = File(hubPath)
        val existingEvents = HubFileHelper.readStoredEvents(hubFile)
        if (existingEvents.isNotEmpty()) {
            println("Loaded ${existingEvents.size} existing events from hub")
        }

        val result = storeEvents(streamName, events, existingEvents)
        handleStoreResult(result, hubFile, hubPath)
    }

    private fun storeEvents(
        streamName: String,
        events: List<EventEnvelope>,
        existingEvents: List<com.manahive.contracts.ledger.StoredEvent>,
    ): StoreResult {
        val ledger = InMemoryLedger()
        val eventStore = EventStore(ledger)
        return eventStore.storeMerged(streamName, events, existingEvents)
    }

    private fun handleStoreResult(
        result: StoreResult,
        hubFile: File,
        hubPath: String,
    ) {
        when (result) {
            is StoreResult.Merged -> {
                HubFileHelper.writeStoredEvents(hubFile, result.mergedEvents)
                println("OK: Stored ${result.newCount} new events, lastGlobalSeq=${result.lastGlobalSeq}")
                val streams = result.mergedEvents.map { it.stream }.distinct()
                println("Hub file: $hubPath (${result.totalCount} total events across ${streams.size} streams)")
            }
            is StoreResult.Conflict -> println("ERROR: ${result.message}")
            is StoreResult.Duplicate -> println("ERROR: ${result.message}")
            is StoreResult.Stored -> println("OK: Stored ${result.count} events")
        }
    }
}

/**
 * Replay command: reads StoredEvents from hub file.
 *
 * Fowler: "Extract Class" — file reading delegated to HubFileHelper.
 */
class ReplayCommand : CliktCommand(
    name = "replay",
    help = "Replay events from the Hub ledger",
) {
    private val hub by option("--hub", help = "Hub ledger file (StoredEvent JSONL)")
    private val from by option("--from", help = "Start from global sequence (default: 0)").default("0")
    private val limit by option("--limit", help = "Maximum events to replay").int().default(100)
    private val output by option("--output", help = "Output file (default: stdout)")
    private val stream by option("--stream", help = "Filter by stream name")

    override fun run() {
        val hubPath = hub ?: run { println("ERROR: --hub is required"); return }
        val hubFile = File(hubPath)

        if (!hubFile.exists()) {
            println("ERROR: Hub file not found: $hubPath")
            return
        }

        val storedEvents = HubFileHelper.readStoredEvents(hubFile)
        val replayEvents = filterEvents(storedEvents)

        println("Replaying ${replayEvents.size} events from globalSeq=${from.toLong()}" +
            if (stream != null) " (stream=$stream)" else "")

        if (output != null) {
            HubFileHelper.writeStoredEvents(File(output!!), replayEvents)
            println("OK: Wrote ${replayEvents.size} events to $output")
        } else {
            replayEvents.forEach { stored ->
                println("  [${stored.globalSeq}] ${stored.stream}:${stored.streamSeq} ${stored.envelope.type}")
            }
        }
    }

    private fun filterEvents(events: List<com.manahive.contracts.ledger.StoredEvent>): List<com.manahive.contracts.ledger.StoredEvent> {
        val fromSeq = from.toLong()
        var filtered = events.filter { it.globalSeq >= fromSeq }.sortedBy { it.globalSeq }
        if (stream != null) {
            filtered = filtered.filter { it.stream == stream }
        }
        return filtered.take(limit)
    }
}

/**
 * Status command: reads StoredEvents from hub file.
 *
 * Fowler: "Extract Class" — file reading delegated to HubFileHelper.
 */
class StatusCommand : CliktCommand(
    name = "status",
    help = "Show Hub ledger status",
) {
    private val hub by option("--hub", help = "Hub ledger file (StoredEvent JSONL)")

    override fun run() {
        val hubPath = hub ?: run { println("ERROR: --hub is required"); return }
        val hubFile = File(hubPath)

        if (!hubFile.exists()) {
            println("ERROR: Hub file not found: $hubPath")
            return
        }

        val storedEvents = HubFileHelper.readStoredEvents(hubFile)
        printStatus(storedEvents)
    }

    private fun printStatus(events: List<com.manahive.contracts.ledger.StoredEvent>) {
        val streams = events.groupBy { it.stream }
        val totalEvents = events.size.toLong()
        val maxGlobalSeq = events.maxOfOrNull { it.globalSeq } ?: 0L

        println("Hub Status:")
        println("  Total events: $totalEvents")
        println("  Global sequence: $maxGlobalSeq")
        println("  Streams: ${streams.size}")
        streams.toSortedMap().forEach { (streamName, streamEvents) ->
            println("    $streamName: ${streamEvents.size} events")
        }
    }
}
