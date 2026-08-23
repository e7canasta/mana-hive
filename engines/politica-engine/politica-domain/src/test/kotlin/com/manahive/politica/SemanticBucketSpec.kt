package com.manahive.politica

import com.manahive.contracts.common.Fingerprint
import com.manahive.contracts.policy.CalibrationPayload
import com.manahive.contracts.policy.ConfidenceConfig
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.PolicyCategory
import com.manahive.contracts.policy.ResponsePayload
import com.manahive.contracts.policy.Version
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import java.time.Duration
import java.time.Instant

class SemanticBucketSpec : DescribeSpec({

    val fixedNow = Instant.parse("2026-08-23T12:00:00Z")
    val maria = ResidentId("maria")

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

    describe("SemanticBucket") {
        it("creates bucket with correct fields") {
            val bucket = SemanticBucket(
                residentId = maria,
                category = PolicyCategory.CALIBRATION,
                version = Version(1),
                payload = calibrationPayload,
            )

            bucket.residentId shouldBe maria
            bucket.category shouldBe PolicyCategory.CALIBRATION
            bucket.version shouldBe Version(1)
            bucket.payload shouldBe calibrationPayload
        }

        it("rejects zero version") {
            shouldThrow<IllegalArgumentException> {
                SemanticBucket(
                    residentId = maria,
                    category = PolicyCategory.CALIBRATION,
                    version = Version(0),
                    payload = calibrationPayload,
                )
            }
        }

        it("rejects negative version") {
            shouldThrow<IllegalArgumentException> {
                SemanticBucket(
                    residentId = maria,
                    category = PolicyCategory.CALIBRATION,
                    version = Version(-1),
                    payload = calibrationPayload,
                )
            }
        }
    }

    describe("PolicyBucketMapper") {
        it("converts CalibrationPayload to CalibrationChanged") {
            val bucket = SemanticBucket(
                residentId = maria,
                category = PolicyCategory.CALIBRATION,
                version = Version(1),
                payload = calibrationPayload,
            )

            val event = PolicyBucketMapper.toEvent(bucket, fixedNow)
            event shouldBe com.manahive.contracts.policy.CalibrationChanged(
                residentId = maria,
                at = fixedNow,
                version = Version(1),
                fingerprint = Fingerprint("abc123"),
                calibration = calibrationPayload.toPolicyCalibration(maria),
            )
        }

        it("converts ResponsePayload to ResponseChanged") {
            val bucket = SemanticBucket(
                residentId = maria,
                category = PolicyCategory.RESPONSE,
                version = Version(1),
                payload = responsePayload,
            )

            val event = PolicyBucketMapper.toEvent(bucket, fixedNow)
            event shouldBe com.manahive.contracts.policy.ResponseChanged(
                residentId = maria,
                at = fixedNow,
                version = Version(1),
                fingerprint = Fingerprint("def456"),
                rules = emptyList(),
            )
        }
    }
})
