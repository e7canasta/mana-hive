package com.manahive.scene.config

import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.scene.StateKind
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import java.time.Duration

class SceneConfigSpec : DescribeSpec({

    describe("SceneConfig") {
        it("creates with valid fields") {
            val config = SceneConfig(
                residentId = "maria",
                name = "Maria Garcia",
                bed = "12A",
                heartbeatTimeout = Duration.ofSeconds(90),
                dwellThresholds = mapOf(
                    StateKind.IN_BATHROOM to DwellThreshold(
                        warning = Duration.ofSeconds(20),
                        exceeded = Duration.ofSeconds(45),
                    )
                ),
                confidence = mapOf(
                    StateKind.STANDING to 0.7,
                ),
            )

            config.residentId shouldBe "maria"
            config.name shouldBe "Maria Garcia"
            config.bed shouldBe "12A"
            config.heartbeatTimeout shouldBe Duration.ofSeconds(90)
            config.dwellThresholds[StateKind.IN_BATHROOM]?.warning shouldBe Duration.ofSeconds(20)
            config.confidence[StateKind.STANDING] shouldBe 0.7
        }

        it("rejects blank residentId") {
            shouldThrow<IllegalArgumentException> {
                SceneConfig(
                    residentId = "",
                    name = "Maria Garcia",
                    bed = "12A",
                )
            }
        }

        it("rejects blank name") {
            shouldThrow<IllegalArgumentException> {
                SceneConfig(
                    residentId = "maria",
                    name = "",
                    bed = "12A",
                )
            }
        }

        it("rejects blank bed") {
            shouldThrow<IllegalArgumentException> {
                SceneConfig(
                    residentId = "maria",
                    name = "Maria Garcia",
                    bed = "",
                )
            }
        }

        it("rejects negative heartbeatTimeout") {
            shouldThrow<IllegalArgumentException> {
                SceneConfig(
                    residentId = "maria",
                    name = "Maria Garcia",
                    bed = "12A",
                    heartbeatTimeout = Duration.ofSeconds(-1),
                )
            }
        }

        it("rejects confidence out of range") {
            shouldThrow<IllegalArgumentException> {
                SceneConfig(
                    residentId = "maria",
                    name = "Maria Garcia",
                    bed = "12A",
                    confidence = mapOf(StateKind.STANDING to 1.5),
                )
            }
        }

        it("uses default values") {
            val config = SceneConfig(
                residentId = "maria",
                name = "Maria Garcia",
                bed = "12A",
            )

            config.heartbeatTimeout shouldBe Duration.ofSeconds(90)
            config.dwellThresholds shouldBe emptyMap()
            config.confidence shouldBe emptyMap()
        }
    }
})
