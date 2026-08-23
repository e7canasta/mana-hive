package com.manahive.harbor.config

import com.manahive.contracts.policy.Severity
import com.manahive.harbor.Channel
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import java.time.Duration

class HarborConfigSpec : DescribeSpec({

    describe("HarborConfig") {
        it("creates with valid fields") {
            val config = HarborConfig(
                residentId = "maria",
                channels = mapOf(
                    Severity.INFO to setOf(Channel.CONSOLE),
                    Severity.WARNING to setOf(Channel.PUSH, Channel.TABLET),
                    Severity.CRITICAL to setOf(Channel.PUSH, Channel.TABLET, Channel.WARD_BOARD, Channel.CONSOLE),
                ),
                escalationTimeouts = mapOf(
                    Severity.INFO to Duration.ofMinutes(30),
                    Severity.WARNING to Duration.ofMinutes(5),
                    Severity.CRITICAL to Duration.ZERO,
                ),
                fingerprint = "abc123",
            )

            config.residentId shouldBe "maria"
            config.channels.size shouldBe 3
            config.fingerprint shouldBe "abc123"
        }

        it("rejects blank residentId") {
            shouldThrow<IllegalArgumentException> {
                HarborConfig(residentId = "")
            }
        }

        it("rejects empty channels") {
            shouldThrow<IllegalArgumentException> {
                HarborConfig(
                    residentId = "maria",
                    channels = mapOf(Severity.INFO to emptySet()),
                )
            }
        }

        it("rejects negative escalation timeout") {
            shouldThrow<IllegalArgumentException> {
                HarborConfig(
                    residentId = "maria",
                    channels = mapOf(Severity.INFO to setOf(Channel.CONSOLE)),
                    escalationTimeouts = mapOf(Severity.INFO to Duration.ofSeconds(-1)),
                )
            }
        }

        it("uses default values") {
            val config = HarborConfig(residentId = "maria")

            config.channels shouldBe emptyMap()
            config.escalationTimeouts shouldBe emptyMap()
            config.fingerprint shouldBe ""
        }

        it("gets channels for severity") {
            val config = HarborConfig(
                residentId = "maria",
                channels = mapOf(
                    Severity.INFO to setOf(Channel.CONSOLE),
                    Severity.WARNING to setOf(Channel.PUSH),
                ),
            )

            config.channelsFor(Severity.INFO) shouldBe setOf(Channel.CONSOLE)
            config.channelsFor(Severity.WARNING) shouldBe setOf(Channel.PUSH)
            config.channelsFor(Severity.CRITICAL) shouldBe emptySet()
        }

        it("gets escalation timeout for severity") {
            val config = HarborConfig(
                residentId = "maria",
                escalationTimeouts = mapOf(
                    Severity.INFO to Duration.ofMinutes(30),
                    Severity.WARNING to Duration.ofMinutes(5),
                ),
            )

            config.escalationTimeoutFor(Severity.INFO) shouldBe Duration.ofMinutes(30)
            config.escalationTimeoutFor(Severity.WARNING) shouldBe Duration.ofMinutes(5)
            config.escalationTimeoutFor(Severity.CRITICAL) shouldBe Duration.ZERO
        }

        it("gets confirmation channels") {
            val config = HarborConfig(
                residentId = "maria",
                channels = mapOf(
                    Severity.INFO to setOf(Channel.CONSOLE),
                    Severity.WARNING to setOf(Channel.PUSH, Channel.TABLET),
                ),
            )

            config.confirmationChannels shouldBe setOf(Channel.PUSH, Channel.TABLET)
        }
    }
})
