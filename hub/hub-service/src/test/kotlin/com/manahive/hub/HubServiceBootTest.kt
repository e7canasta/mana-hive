package com.manahive.hub

import com.manahive.hub.ledger.EventStore
import com.manahive.hub.ledger.InMemoryLedger
import com.manahive.hub.policy.InMemoryPolicyLayerStore
import com.manahive.hub.policy.PolicyService
import com.manahive.contracts.policy.PolicyCatalog
import com.manahive.contracts.policy.RawPolicyStore
import com.manahive.contracts.policy.SemanticBucketStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Loads the full hub context without requiring NATS or Postgres.
 *
 * This is the hub's equivalent of the scene blueprint: making the thing
 * *can* fail. If a bean is missing, a constructor arg is unsatisfied, or
 * a conditional annotation misfires, this test catches it.
 *
 * NATS is disabled (`nats.enabled=false`) so no running server is required.
 * DataSource auto-configuration is excluded — no Postgres in unit tests.
 */
@SpringBootTest(classes = [HubApplication::class])
@ActiveProfiles("test")
class HubServiceBootTest {

    @Autowired
    lateinit var policyService: PolicyService

    @Autowired
    lateinit var policyLayerStore: InMemoryPolicyLayerStore

    @Autowired
    lateinit var eventStore: EventStore

    @Autowired
    lateinit var ledger: InMemoryLedger

    @Autowired
    lateinit var rawPolicyStore: RawPolicyStore

    @Autowired
    lateinit var semanticBucketStore: SemanticBucketStore

    @Autowired
    lateinit var policyCatalog: PolicyCatalog

    @Test
    fun `context loads — all beans are wired`() {
        assertNotNull(policyService)
        assertNotNull(policyLayerStore)
        assertNotNull(eventStore)
        assertNotNull(ledger)
        assertNotNull(rawPolicyStore)
        assertNotNull(semanticBucketStore)
        assertNotNull(policyCatalog)
    }

    @Test
    fun `policy layer store starts empty`() {
        assertEquals(0, policyLayerStore.size())
    }

    @Test
    fun `event store starts empty`() {
        assertEquals(0L, ledger.currentGlobalSeq())
    }
}
