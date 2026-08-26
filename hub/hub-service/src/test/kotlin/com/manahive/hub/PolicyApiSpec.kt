package com.manahive.hub

import com.fasterxml.jackson.databind.ObjectMapper
import com.manahive.contracts.policy.WatchLevel
import com.manahive.hub.api.PolicyController
import com.manahive.hub.policy.InMemoryPolicyLayerStore
import com.manahive.hub.policy.PolicyService
import com.manahive.hub.policy.WatchLevelAssigned
import com.manahive.kernel.ResidentId
import com.manahive.kernel.StaffId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import java.time.Instant

@SpringBootTest(classes = [HubApplication::class])
@ActiveProfiles("test")
class PolicyApiSpec {

    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var policyService: PolicyService

    @Autowired
    lateinit var layerStore: InMemoryPolicyLayerStore

    @Autowired
    lateinit var policyController: PolicyController

    @Autowired
    lateinit var mapper: ObjectMapper

    val jose = "jose"
    val t0 = Instant.parse("2026-08-22T00:00:00Z")

    @BeforeEach
    fun setUp() {
        layerStore.clear()
        mockMvc = MockMvcBuilders.standaloneSetup(policyController).build()
    }

    // ── GET /api/policies/{id} ────────────────────────────────────────────

    @Test
    fun `GET policies for resident without layers returns 404`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/policies/$jose")
                .param("at", t0.toString()),
        ).andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound,
        )
    }

    @Test
    fun `GET policies for resident with layers returns rules`() {
        layerStore.applyEvent(WatchLevelAssigned(
            ResidentId(jose), WatchLevel.FALL_RISK, StaffId("dr-garcia"), t0, "post-op",
        ))

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/policies/$jose")
                .param("at", t0.toString()),
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.residentId").value(jose))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.fingerprint").isNotEmpty)
    }

    // ── PUT /api/policies/{id}/watch-level ─────────────────────────────────

    @Test
    fun `PUT watch-level without motivo returns 400`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/policies/$jose/watch-level")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("level" to "FALL_RISK"))),
        ).andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest,
        )
    }

    @Test
    fun `PUT watch-level with motivo returns updated rules`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/policies/$jose/watch-level")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf(
                    "level" to "FALL_RISK",
                    "reason" to "post-operatorio de cadera",
                ))),
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.residentId").value(jose))
    }

    // ── POST /api/policies/{id}/adjustments ────────────────────────────────

    @Test
    fun `POST adjustment without motivo returns 400`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/policies/$jose/adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf(
                    "state" to "LYING",
                    "warningMinutes" to 13,
                    "exceededMinutes" to 15,
                ))),
        ).andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest,
        )
    }

    @Test
    fun `POST adjustment with motivo returns 201`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/policies/$jose/adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf(
                    "state" to "LYING",
                    "warningMinutes" to 13,
                    "exceededMinutes" to 15,
                    "reason" to "ajuste para José",
                ))),
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.residentId").value(jose))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.adjustmentsCount").value(1))
    }

    // ── DELETE /api/policies/{id}/adjustments/{adjId} ──────────────────────

    @Test
    fun `DELETE adjustment removes it`() {
        layerStore.applyEvent(WatchLevelAssigned(
            ResidentId(jose), WatchLevel.FALL_RISK, StaffId("dr-garcia"), t0, "nivel base",
        ))
        layerStore.applyEvent(com.manahive.hub.policy.ManualAdjustmentAdded(
            ResidentId(jose), "adj-1",
            com.manahive.contracts.scene.StateKind.LYING,
            com.manahive.contracts.policy.DwellThreshold(
                java.time.Duration.ofMinutes(13),
                java.time.Duration.ofMinutes(15),
            ),
            StaffId("dr-garcia"), t0, "ajuste",
        ))

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/policies/$jose/adjustments/adj-1"),
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.adjustmentsCount").value(0))
    }

    // ── GET /api/policies/{id}/history ─────────────────────────────────────

    @Test
    fun `GET history returns events in order`() {
        layerStore.applyEvent(WatchLevelAssigned(
            ResidentId(jose), WatchLevel.STANDARD, StaffId("dr-garcia"), t0, "inicio",
        ))
        layerStore.applyEvent(WatchLevelAssigned(
            ResidentId(jose), WatchLevel.FALL_RISK, StaffId("enfermera"),
            t0.plusSeconds(300), "cambio",
        ))

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/policies/$jose/history")
                .accept(MediaType.APPLICATION_JSON),
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.residentId").value(jose))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.events").isArray)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.events.length()").value(2))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.events[0].type").value("WatchLevelAssigned"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.events[0].details.level").value("STANDARD"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.events[1].details.level").value("FALL_RISK"))
    }

    @Test
    fun `GET history for unknown resident returns empty list`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/policies/unknown/history")
                .accept(MediaType.APPLICATION_JSON),
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.events").isEmpty)
    }
}
