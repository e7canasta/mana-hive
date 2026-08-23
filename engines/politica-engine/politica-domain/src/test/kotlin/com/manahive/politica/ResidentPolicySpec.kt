package com.manahive.politica

import com.manahive.contracts.common.Fingerprint
import com.manahive.contracts.policy.CalibrationPayload
import com.manahive.contracts.policy.ConfidenceConfig
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.EscalationPayload
import com.manahive.contracts.policy.EscalationConfig
import com.manahive.contracts.policy.PolicyCategory
import com.manahive.contracts.policy.RecordingPayload
import com.manahive.contracts.policy.RecordingConfig
import com.manahive.contracts.policy.RecordingQuality
import com.manahive.contracts.policy.ResponsePayload
import com.manahive.contracts.policy.StaffAssistMode
import com.manahive.contracts.policy.Version
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.assertions.throwables.shouldThrow
import java.time.Duration
import java.time.Instant

class ResidentPolicySpec : DescribeSpec({

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

    val escalationPayload = EscalationPayload(
        config = EscalationConfig(
            escalationDelay = Duration.ofMinutes(5),
            staffAssist = StaffAssistMode.OBLIGATORY,
            maxLevel = 3,
        ),
        fingerprint = Fingerprint("ghi789"),
    )

    val recordingPayload = RecordingPayload(
        config = RecordingConfig(
            enabled = true,
            preEventWindow = Duration.ofSeconds(30),
            postEventWindow = Duration.ofMinutes(2),
            quality = RecordingQuality.HIGH,
        ),
        fingerprint = Fingerprint("jkl012"),
    )

    describe("ResidentPolicy") {
        it("creates aggregate with residentId") {
            val policy = ResidentPolicy(maria)
            policy.residentId shouldBe maria
        }

        it("applies bucket with matching residentId") {
            val policy = ResidentPolicy(maria)
            val bucket = SemanticBucket(
                residentId = maria,
                category = PolicyCategory.CALIBRATION,
                version = Version(1),
                payload = calibrationPayload,
            )

            policy.apply(bucket)

            policy.get(PolicyCategory.CALIBRATION) shouldBe bucket
        }

        it("rejects bucket with different residentId") {
            val policy = ResidentPolicy(maria)
            val wrongBucket = SemanticBucket(
                residentId = john,
                category = PolicyCategory.CALIBRATION,
                version = Version(1),
                payload = calibrationPayload,
            )

            shouldThrow<IllegalArgumentException> {
                policy.apply(wrongBucket)
            }
        }

        it("returns all buckets") {
            val policy = ResidentPolicy(maria)

            policy.apply(SemanticBucket(maria, PolicyCategory.CALIBRATION, Version(1), calibrationPayload))
            policy.apply(SemanticBucket(maria, PolicyCategory.RESPONSE, Version(1), responsePayload))

            policy.all() shouldHaveSize 2
        }

        it("returns categories") {
            val policy = ResidentPolicy(maria)

            policy.apply(SemanticBucket(maria, PolicyCategory.CALIBRATION, Version(1), calibrationPayload))
            policy.apply(SemanticBucket(maria, PolicyCategory.RESPONSE, Version(1), responsePayload))

            policy.categories() shouldBe setOf(
                PolicyCategory.CALIBRATION,
                PolicyCategory.RESPONSE,
            )
        }

        it("checks completeness") {
            val policy = ResidentPolicy(maria)

            policy.isComplete() shouldBe false

            policy.apply(SemanticBucket(maria, PolicyCategory.CALIBRATION, Version(1), calibrationPayload))
            policy.apply(SemanticBucket(maria, PolicyCategory.RESPONSE, Version(1), responsePayload))
            policy.apply(SemanticBucket(maria, PolicyCategory.ESCALATION, Version(1), escalationPayload))
            policy.apply(SemanticBucket(maria, PolicyCategory.RECORDING, Version(1), recordingPayload))

            policy.isComplete() shouldBe true
        }

        it("generates fingerprint from all buckets") {
            val policy = ResidentPolicy(maria)

            policy.apply(SemanticBucket(maria, PolicyCategory.CALIBRATION, Version(1), calibrationPayload))
            policy.apply(SemanticBucket(maria, PolicyCategory.RESPONSE, Version(1), responsePayload))

            policy.fingerprint().value shouldNotBe ""
        }

        it("converts to events") {
            val policy = ResidentPolicy(maria)

            policy.apply(SemanticBucket(maria, PolicyCategory.CALIBRATION, Version(1), calibrationPayload))
            policy.apply(SemanticBucket(maria, PolicyCategory.RESPONSE, Version(1), responsePayload))

            val events = policy.toEvents(fixedNow)
            events shouldHaveSize 2
        }

        it("returns size") {
            val policy = ResidentPolicy(maria)

            policy.size() shouldBe 0

            policy.apply(SemanticBucket(maria, PolicyCategory.CALIBRATION, Version(1), calibrationPayload))
            policy.size() shouldBe 1
        }
    }

    describe("ResidentPolicy.from") {
        it("creates from list of buckets") {
            val policy = ResidentPolicy.from(listOf(
                SemanticBucket(maria, PolicyCategory.CALIBRATION, Version(1), calibrationPayload),
                SemanticBucket(maria, PolicyCategory.RESPONSE, Version(1), responsePayload),
                SemanticBucket(maria, PolicyCategory.ESCALATION, Version(1), escalationPayload),
                SemanticBucket(maria, PolicyCategory.RECORDING, Version(1), recordingPayload),
            ))

            policy.residentId shouldBe maria
            policy.all() shouldHaveSize 4
        }

        it("rejects empty buckets list") {
            shouldThrow<IllegalArgumentException> {
                ResidentPolicy.from(emptyList())
            }
        }

        it("rejects mixed residentIds") {
            shouldThrow<IllegalArgumentException> {
                ResidentPolicy.from(listOf(
                    SemanticBucket(maria, PolicyCategory.CALIBRATION, Version(1), calibrationPayload),
                    SemanticBucket(john, PolicyCategory.RESPONSE, Version(1), responsePayload),
                ))
            }
        }
    }
})
