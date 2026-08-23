package com.manahive.hub.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
public class HealthController {

    @GetMapping("/health")
    public fun health(): HealthResponse {
        return upResponse()
    }

    @GetMapping("/health/ready")
    public fun ready(): HealthResponse {
        return upResponse()
    }

    @GetMapping("/health/live")
    public fun live(): HealthResponse {
        return upResponse()
    }

    private fun upResponse(): HealthResponse {
        return HealthResponse(
            status = "UP",
            timestamp = Instant.now().toString(),
        )
    }
}
