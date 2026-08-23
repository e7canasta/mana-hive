package com.manahive.infrastructure.config

import com.manahive.contracts.policy.ConfidenceConfig
import com.manahive.contracts.policy.RecordingQuality
import com.manahive.contracts.policy.StaffAssistMode
import com.manahive.contracts.scene.StateKind
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import java.io.File
import java.time.Duration

class TomlConfigParserSpec : DescribeSpec({

    describe("TomlConfigParser") {
        describe("parseDuration") {
            it("parses seconds") {
                TomlConfigParser.parseDuration("30s") shouldBe Duration.ofSeconds(30)
            }

            it("parses minutes") {
                TomlConfigParser.parseDuration("5m") shouldBe Duration.ofMinutes(5)
            }

            it("parses hours") {
                TomlConfigParser.parseDuration("2h") shouldBe Duration.ofHours(2)
            }

            it("parses days") {
                TomlConfigParser.parseDuration("1d") shouldBe Duration.ofDays(1)
            }

            it("parses milliseconds") {
                TomlConfigParser.parseDuration("500ms") shouldBe Duration.ofMillis(500)
            }

            it("parses negative duration") {
                TomlConfigParser.parseDuration("-30s") shouldBe Duration.ofSeconds(-30)
            }

            it("rejects blank string") {
                shouldThrow<IllegalArgumentException> {
                    TomlConfigParser.parseDuration("")
                }
            }

            it("rejects invalid format") {
                shouldThrow<IllegalArgumentException> {
                    TomlConfigParser.parseDuration("invalid")
                }
            }
        }

        describe("parseStateKind") {
            it("parses valid state kind") {
                TomlConfigParser.parseStateKind("IN_BATHROOM") shouldBe StateKind.IN_BATHROOM
            }

            it("rejects blank string") {
                shouldThrow<IllegalArgumentException> {
                    TomlConfigParser.parseStateKind("")
                }
            }

            it("rejects invalid state kind") {
                shouldThrow<IllegalArgumentException> {
                    TomlConfigParser.parseStateKind("INVALID")
                }
            }
        }

        describe("parseStaffAssistMode") {
            it("parses valid mode") {
                TomlConfigParser.parseStaffAssistMode("OBLIGATORY") shouldBe StaffAssistMode.OBLIGATORY
            }

            it("rejects blank string") {
                shouldThrow<IllegalArgumentException> {
                    TomlConfigParser.parseStaffAssistMode("")
                }
            }

            it("rejects invalid mode") {
                shouldThrow<IllegalArgumentException> {
                    TomlConfigParser.parseStaffAssistMode("INVALID")
                }
            }
        }

        describe("parseRecordingQuality") {
            it("parses valid quality") {
                TomlConfigParser.parseRecordingQuality("HIGH") shouldBe RecordingQuality.HIGH
            }

            it("rejects blank string") {
                shouldThrow<IllegalArgumentException> {
                    TomlConfigParser.parseRecordingQuality("")
                }
            }

            it("rejects invalid quality") {
                shouldThrow<IllegalArgumentException> {
                    TomlConfigParser.parseRecordingQuality("INVALID")
                }
            }
        }

        describe("parse") {
            it("parses valid TOML") {
                val toml = """
                    [resident]
                    name = "Maria Garcia"
                    bed = "12A"
                    
                    [calibration]
                    heartbeatTimeout = "90s"
                    
                    [calibration.dwell.IN_BATHROOM]
                    warning = "20s"
                    exceeded = "45s"
                    
                    [calibration.confidence.STANDING]
                    min = 0.7
                    
                    [escalation]
                    escalationDelay = "5m"
                    staffAssist = "OBLIGATORY"
                    maxLevel = 3
                    
                    [recording]
                    enabled = true
                    preEventWindow = "30s"
                    postEventWindow = "2m"
                    quality = "HIGH"
                """.trimIndent()

                val config = TomlConfigParser.parse(toml)

                config.resident.name shouldBe "Maria Garcia"
                config.resident.bed shouldBe "12A"
                config.calibration.heartbeatTimeout shouldBe Duration.ofSeconds(90)
                config.calibration.dwellThresholds[StateKind.IN_BATHROOM]?.warning shouldBe Duration.ofSeconds(20)
                config.calibration.dwellThresholds[StateKind.IN_BATHROOM]?.exceeded shouldBe Duration.ofSeconds(45)
                config.calibration.confidence.minConfidence[StateKind.STANDING] shouldBe 0.7
                config.calibration.confidence.heartbeatTimeout shouldBe Duration.ofSeconds(90)
                config.escalation.escalationDelay shouldBe Duration.ofMinutes(5)
                config.escalation.staffAssist shouldBe StaffAssistMode.OBLIGATORY
                config.escalation.maxLevel shouldBe 3
                config.recording.enabled shouldBe true
                config.recording.preEventWindow shouldBe Duration.ofSeconds(30)
                config.recording.postEventWindow shouldBe Duration.ofMinutes(2)
                config.recording.quality shouldBe RecordingQuality.HIGH
            }

            it("rejects blank content") {
                shouldThrow<IllegalArgumentException> {
                    TomlConfigParser.parse("")
                }
            }

            it("rejects missing resident section") {
                val toml = """
                    [calibration]
                    heartbeatTimeout = "90s"
                """.trimIndent()

                shouldThrow<IllegalArgumentException> {
                    TomlConfigParser.parse(toml)
                }
            }

            it("rejects missing resident name") {
                val toml = """
                    [resident]
                    bed = "12A"
                """.trimIndent()

                shouldThrow<IllegalArgumentException> {
                    TomlConfigParser.parse(toml)
                }
            }

            it("rejects blank resident name") {
                val toml = """
                    [resident]
                    name = ""
                    bed = "12A"
                """.trimIndent()

                shouldThrow<IllegalArgumentException> {
                    TomlConfigParser.parse(toml)
                }
            }

            it("rejects invalid confidence range") {
                val toml = """
                    [resident]
                    name = "Maria Garcia"
                    bed = "12A"
                    
                    [calibration.confidence.STANDING]
                    min = 1.5
                """.trimIndent()

                shouldThrow<IllegalArgumentException> {
                    TomlConfigParser.parse(toml)
                }
            }
        }
    }
})
