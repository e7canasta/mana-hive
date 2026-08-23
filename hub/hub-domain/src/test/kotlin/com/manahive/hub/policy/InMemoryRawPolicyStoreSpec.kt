package com.manahive.hub.policy

import com.manahive.contracts.policy.RawPolicy
import com.manahive.contracts.policy.Version
import com.manahive.kernel.ResidentId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Instant

class InMemoryRawPolicyStoreSpec : DescribeSpec({

    val fixedNow = Instant.parse("2026-08-23T12:00:00Z")
    val maria = ResidentId("maria")
    val john = ResidentId("john")

    val mariaRawPolicy = RawPolicy(
        residentId = maria,
        version = Version(1),
        payload = mapOf(
            "calibration" to mapOf(
                "dwell" to mapOf(
                    "bathroom_visit" to mapOf(
                        "sensitivity" to "m",
                        "hysteresis" to "+0s",
                    )
                )
            )
        ),
        receivedAt = fixedNow,
    )

    val johnRawPolicy = RawPolicy(
        residentId = john,
        version = Version(1),
        payload = mapOf(
            "response" to mapOf(
                "severity" to "high"
            )
        ),
        receivedAt = fixedNow,
    )

    describe("InMemoryRawPolicyStore") {
        it("stores and retrieves raw policy") {
            val store = InMemoryRawPolicyStore()

            store.store(maria, mariaRawPolicy)

            store.get(maria) shouldBe mariaRawPolicy
        }

        it("returns null for non-existent resident") {
            val store = InMemoryRawPolicyStore()

            store.get(maria) shouldBe null
        }

        it("lists all residents") {
            val store = InMemoryRawPolicyStore()

            store.store(maria, mariaRawPolicy)
            store.store(john, johnRawPolicy)

            val residents = store.listAll()
            residents shouldHaveSize 2
            residents shouldContain maria
            residents shouldContain john
        }

        it("overwrites existing raw policy") {
            val store = InMemoryRawPolicyStore()

            store.store(maria, mariaRawPolicy)

            val updatedPolicy = mariaRawPolicy.copy(version = Version(2))
            store.store(maria, updatedPolicy)

            store.get(maria) shouldBe updatedPolicy
            store.size() shouldBe 1
        }

        it("tracks size") {
            val store = InMemoryRawPolicyStore()

            store.size() shouldBe 0

            store.store(maria, mariaRawPolicy)
            store.size() shouldBe 1

            store.store(john, johnRawPolicy)
            store.size() shouldBe 2
        }

        it("clears all raw policies") {
            val store = InMemoryRawPolicyStore()

            store.store(maria, mariaRawPolicy)
            store.store(john, johnRawPolicy)

            store.clear()

            store.size() shouldBe 0
            store.listAll() shouldBe emptyList()
            store.get(maria) shouldBe null
        }
    }
})
