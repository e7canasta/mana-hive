package com.manahive.runtime.control

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.kernel.ResidentId
import com.manahive.profile.api.ResidentProfileDto
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Adapter for ProfileFetcher — HTTP GET hub.
 * Fowler: Gateway implementation. No business logic.
 */
class HubProfileFetcherAdapter(
    private val hubUrl: String = System.getenv("HUB_URL") ?: System.getenv("HUBURL") ?: "http://mana-hub:8080",
) : ProfileFetcher {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    private val mapper = jacksonObjectMapper().apply { findAndRegisterModules() }

    override fun fetch(residentId: ResidentId): ResidentProfileDto {
        val candidates = listOf(hubUrl, "http://mana-hub:8080", "http://hub-service:8080", "http://localhost:8080", "http://mana-hub-dev:8080").distinct()
        var lastEx: Exception? = null
        for (base in candidates) {
            val url = "$base/api/profiles/${residentId.value}"
            try {
                log.info("Fetching profile for {} from {}", residentId.value, url)
                val req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build()
                val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
                if (resp.statusCode() == 200) {
                    return mapper.readValue(resp.body())
                }
                log.warn("Hub GET {} -> {} {}", url, resp.statusCode(), resp.body().take(200))
            } catch (e: Exception) {
                log.warn("Hub fetch {} failed: {}", base, e.message)
                lastEx = e
            }
        }
        throw IllegalStateException("Hub fetch failed for ${residentId.value} candidates=$candidates", lastEx)
    }
}
