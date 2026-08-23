package com.manahive.hub.api

import com.manahive.contracts.ledger.LedgerPort
import com.manahive.contracts.ledger.WatermarkPort
import com.manahive.hub.ledger.EventStore
import com.manahive.hub.ledger.StreamCatalog
import com.manahive.hub.ledger.WatermarkCatalog
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
public class LedgerController(
    private val eventStore: EventStore,
    private val ledger: LedgerPort,
    private val watermarkStore: WatermarkPort,
    private val streamCatalog: StreamCatalog,
    private val watermarkCatalog: WatermarkCatalog,
) {

    @GetMapping("/api/events")
    public fun listEvents(
        @RequestParam(name = "from", defaultValue = "0") from: Long,
        @RequestParam(name = "limit", defaultValue = "100") limit: Int,
    ): EventsResponse {
        val events = eventStore.replay(from, limit)
        return EventsResponse(
            events = events,
            total = events.size,
            from = from,
        )
    }

    @GetMapping("/api/events/stream/{stream}")
    public fun listStreamEvents(
        @PathVariable stream: String,
        @RequestParam(name = "limit", defaultValue = "100") limit: Int,
    ): StreamEventsResponse {
        val events = ledger.read(stream).take(limit)
        return StreamEventsResponse(
            stream = stream,
            events = events,
            total = events.size,
        )
    }

    @GetMapping("/api/watermarks")
    public fun listWatermarks(): WatermarksResponse {
        val watermarks = watermarkCatalog.names().associateWith { consumer ->
            WatermarkResponse(
                consumer = consumer,
                position = watermarkStore.of(consumer),
            )
        }
        return WatermarksResponse(
            watermarks = watermarks,
            total = watermarks.size,
        )
    }

    @GetMapping("/api/watermarks/{consumer}")
    public fun getWatermark(
        @PathVariable consumer: String,
    ): WatermarkResponse {
        val position = watermarkStore.of(consumer)
        return WatermarkResponse(
            consumer = consumer,
            position = position,
        )
    }

    @GetMapping("/api/status")
    public fun status(): StatusResponse {
        val streamCounts = streamCatalog.names().associateWith { stream ->
            ledger.count(stream)
        }
        val totalEvents = streamCounts.values.sum()

        return StatusResponse(
            status = "UP",
            timestamp = Instant.now().toString(),
            totalEvents = totalEvents,
            globalSequence = ledger.globalSeq(),
            streams = streamCounts,
        )
    }
}
