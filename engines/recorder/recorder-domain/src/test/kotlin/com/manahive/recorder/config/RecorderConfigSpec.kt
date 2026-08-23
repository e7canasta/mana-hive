package com.manahive.recorder.config

import com.manahive.contracts.policy.RecordingQuality
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import java.time.Duration

class RecorderConfigSpec : DescribeSpec({

    describe("RecorderConfig") {
        it("creates with valid fields") {
            val config = RecorderConfig(
                residentId = "maria",
                enabled = true,
                preEventWindow = Duration.ofSeconds(30),
                postEventWindow = Duration.ofMinutes(2),
                quality = RecordingQuality.HIGH,
                fingerprint = "abc123",
            )

            config.residentId shouldBe "maria"
            config.enabled shouldBe true
            config.preEventWindow shouldBe Duration.ofSeconds(30)
            config.postEventWindow shouldBe Duration.ofMinutes(2)
            config.quality shouldBe RecordingQuality.HIGH
            config.fingerprint shouldBe "abc123"
        }

        it("rejects blank residentId") {
            shouldThrow<IllegalArgumentException> {
                RecorderConfig(residentId = "")
            }
        }

        it("rejects negative preEventWindow") {
            shouldThrow<IllegalArgumentException> {
                RecorderConfig(
                    residentId = "maria",
                    preEventWindow = Duration.ofSeconds(-1),
                )
            }
        }

        it("rejects negative postEventWindow") {
            shouldThrow<IllegalArgumentException> {
                RecorderConfig(
                    residentId = "maria",
                    postEventWindow = Duration.ofSeconds(-1),
                )
            }
        }

        it("uses default values") {
            val config = RecorderConfig(residentId = "maria")

            config.enabled shouldBe false
            config.preEventWindow shouldBe Duration.ofSeconds(30)
            config.postEventWindow shouldBe Duration.ofMinutes(2)
            config.quality shouldBe RecordingQuality.MEDIUM
            config.fingerprint shouldBe ""
        }

        it("calculates total window") {
            val config = RecorderConfig(
                residentId = "maria",
                preEventWindow = Duration.ofSeconds(30),
                postEventWindow = Duration.ofMinutes(2),
            )

            config.totalWindow() shouldBe Duration.ofSeconds(150) // 30s + 2m = 150s
        }

        it("calculates total window with zero values") {
            val config = RecorderConfig(
                residentId = "maria",
                preEventWindow = Duration.ZERO,
                postEventWindow = Duration.ZERO,
            )

            config.totalWindow() shouldBe Duration.ZERO
        }
    }
})
