package com.manahive.scene.config

import com.manahive.contracts.engine.ConfigNotFoundException
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import java.io.File
import java.time.Duration

class TomlSceneConfigSourceSpec : DescribeSpec({

    describe("TomlSceneConfigSource") {
        describe("load") {
            it("loads from valid TOML file") {
                val tempDir = File.createTempFile("mana-hive-test", "").apply {
                    delete()
                    mkdirs()
                }
                try {
                    val tomlFile = File(tempDir, "maria.toml")
                    tomlFile.writeText("""
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
                    """.trimIndent())

                    val source = TomlSceneConfigSource(tempDir.absolutePath)
                    val config = source.load(ResidentId("maria"))

                    config.residentId shouldBe "maria"
                    config.name shouldBe "Maria Garcia"
                    config.bed shouldBe "12A"
                    config.heartbeatTimeout shouldBe Duration.ofSeconds(90)
                    config.dwellThresholds[StateKind.IN_BATHROOM]?.warning shouldBe Duration.ofSeconds(20)
                    config.confidence[StateKind.STANDING] shouldBe 0.7
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("throws ConfigNotFoundException for non-existent file") {
                val tempDir = File.createTempFile("mana-hive-test", "").apply {
                    delete()
                    mkdirs()
                }
                try {
                    val source = TomlSceneConfigSource(tempDir.absolutePath)
                    shouldThrow<ConfigNotFoundException> {
                        source.load(ResidentId("nonexistent"))
                    }
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("loadAll") {
            it("loads all TOML files in directory") {
                val tempDir = File.createTempFile("mana-hive-test", "").apply {
                    delete()
                    mkdirs()
                }
                try {
                    File(tempDir, "maria.toml").writeText("""
                        [resident]
                        name = "Maria Garcia"
                        bed = "12A"
                    """.trimIndent())

                    File(tempDir, "john.toml").writeText("""
                        [resident]
                        name = "John Smith"
                        bed = "12B"
                    """.trimIndent())

                    val source = TomlSceneConfigSource(tempDir.absolutePath)
                    val configs = source.loadAll()

                    configs.size shouldBe 2
                    configs[ResidentId("maria")]?.name shouldBe "Maria Garcia"
                    configs[ResidentId("john")]?.name shouldBe "John Smith"
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("exists") {
            it("returns true for existing file") {
                val tempDir = File.createTempFile("mana-hive-test", "").apply {
                    delete()
                    mkdirs()
                }
                try {
                    val tomlFile = File(tempDir, "maria.toml")
                    tomlFile.writeText("""
                        [resident]
                        name = "Maria Garcia"
                        bed = "12A"
                    """.trimIndent())

                    val source = TomlSceneConfigSource(tempDir.absolutePath)
                    source.exists("maria") shouldBe true
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("returns false for non-existent file") {
                val tempDir = File.createTempFile("mana-hive-test", "").apply {
                    delete()
                    mkdirs()
                }
                try {
                    val source = TomlSceneConfigSource(tempDir.absolutePath)
                    source.exists("nonexistent") shouldBe false
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("reload") {
            it("reloads from TOML file") {
                val tempDir = File.createTempFile("mana-hive-test", "").apply {
                    delete()
                    mkdirs()
                }
                try {
                    val tomlFile = File(tempDir, "maria.toml")
                    tomlFile.writeText("""
                        [resident]
                        name = "Maria Garcia"
                        bed = "12A"
                    """.trimIndent())

                    val source = TomlSceneConfigSource(tempDir.absolutePath)
                    val config1 = source.load(ResidentId("maria"))
                    config1.name shouldBe "Maria Garcia"

                    // Update file
                    tomlFile.writeText("""
                        [resident]
                        name = "Maria Garcia Updated"
                        bed = "12B"
                    """.trimIndent())

                    val config2 = source.reload("maria")
                    config2.name shouldBe "Maria Garcia Updated"
                    config2.bed shouldBe "12B"
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("subscribe") {
            it("registers callback") {
                val tempDir = File.createTempFile("mana-hive-test", "").apply {
                    delete()
                    mkdirs()
                }
                try {
                    val tomlFile = File(tempDir, "maria.toml")
                    tomlFile.writeText("""
                        [resident]
                        name = "Maria Garcia"
                        bed = "12A"
                    """.trimIndent())

                    val source = TomlSceneConfigSource(tempDir.absolutePath)
                    var callbackInvoked = false
                    source.subscribeCalibration("maria") { callbackInvoked = true }

                    source.reload("maria")
                    callbackInvoked shouldBe true
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }
    }
})
