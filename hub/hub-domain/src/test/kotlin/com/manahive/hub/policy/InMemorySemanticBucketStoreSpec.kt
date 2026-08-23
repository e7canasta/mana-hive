package com.manahive.hub.policy

import com.manahive.contracts.common.Fingerprint
import com.manahive.contracts.policy.CalibrationPayload
import com.manahive.contracts.policy.ConfidenceConfig
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.PolicyCategory
import com.manahive.contracts.policy.ResponsePayload
import com.manahive.contracts.policy.StoredSemanticBucket
import com.manahive.contracts.policy.Version
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

class InMemorySemanticBucketStoreSpec : DescribeSpec({

    val fixedNow = Instant.parse("2026-08-23T12:00:00Z")
    val maria = ResidentId("maria")
    val john = ResidentId("john")

    val calibrationPayload = CalibrationPayload(
        dwellThresholds = mapOf(
            StateKind.IN_BATHROOM to DwellThreshold(
                warning = Duration.ofSeconds(20),
                exceeded = Duration.ofSeconds(45),
            )
        ),
        hysteresis = emptyMap(),
        confidence = ConfidenceConfig(
            minConfidence = emptyMap(),
            heartbeatTimeout = Duration.ofSeconds(90),
        ),
        fingerprint = Fingerprint("abc123"),
    )

    val responsePayload = ResponsePayload(
        rules = emptyList(),
        fingerprint = Fingerprint("def456"),
    )

    val mariaCalibration = StoredSemanticBucket(
        residentId = maria,
        category = PolicyCategory.CALIBRATION,
        version = Version(1),
        payload = calibrationPayload,
        fingerprint = Fingerprint("abc123"),
        storedAt = fixedNow,
    )

    val mariaResponse = StoredSemanticBucket(
        residentId = maria,
        category = PolicyCategory.RESPONSE,
        version = Version(1),
        payload = responsePayload,
        fingerprint = Fingerprint("def456"),
        storedAt = fixedNow,
    )

    val johnCalibration = StoredSemanticBucket(
        residentId = john,
        category = PolicyCategory.CALIBRATION,
        version = Version(1),
        payload = calibrationPayload,
        fingerprint = Fingerprint("abc123"),
        storedAt = fixedNow,
    )

    describe("InMemorySemanticBucketStore") {
        it("stores and retrieves semantic bucket") {
            val store = InMemorySemanticBucketStore()

            store.store(mariaCalibration)

            store.get(maria, PolicyCategory.CALIBRATION) shouldBe mariaCalibration
        }

        it("returns null for non-existent bucket") {
            val store = InMemorySemanticBucketStore()

            store.get(maria, PolicyCategory.CALIBRATION) shouldBe null
        }

        it("gets all buckets for a resident") {
            val store = InMemorySemanticBucketStore()

            store.store(mariaCalibration)
            store.store(mariaResponse)

            val buckets = store.getAllByResident(maria)
            buckets shouldHaveSize 2
        }

        it("gets all buckets for a category") {
            val store = InMemorySemanticBucketStore()

            store.store(mariaCalibration)
            store.store(johnCalibration)

            val buckets = store.getAllByCategory(PolicyCategory.CALIBRATION)
            buckets shouldHaveSize 2
        }

        it("lists all residents") {
            val store = InMemorySemanticBucketStore()

            store.store(mariaCalibration)
            store.store(mariaResponse)
            store.store(johnCalibration)

            val residents = store.listAllResidents()
            residents shouldHaveSize 2
            residents shouldContain maria
            residents shouldContain john
        }

        it("overwrites existing bucket") {
            val store = InMemorySemanticBucketStore()

            store.store(mariaCalibration)

            val updated = mariaCalibration.copy(version = Version(2))
            store.store(updated)

            store.get(maria, PolicyCategory.CALIBRATION) shouldBe updated
            store.size() shouldBe 1
        }

        it("tracks size") {
            val store = InMemorySemanticBucketStore()

            store.size() shouldBe 0

            store.store(mariaCalibration)
            store.size() shouldBe 1

            store.store(mariaResponse)
            store.size() shouldBe 2
        }

        it("clears all buckets") {
            val store = InMemorySemanticBucketStore()

            store.store(mariaCalibration)
            store.store(mariaResponse)

            store.clear()

            store.size() shouldBe 0
            store.listAllResidents() shouldBe emptyList()
            store.get(maria, PolicyCategory.CALIBRATION) shouldBe null
        }
    }
})
