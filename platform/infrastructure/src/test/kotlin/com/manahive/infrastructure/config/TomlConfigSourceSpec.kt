package com.manahive.infrastructure.config

import com.manahive.contracts.engine.ConfigNotFoundException
import com.manahive.kernel.ResidentId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import java.io.File
import java.time.Duration

class TomlConfigSourceSpec : DescribeSpec({

    describe("TomlConfigSource") {
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
                    """.trimIndent())

                    val source = TomlConfigSource(tempDir.absolutePath)
                    val config = source.load(ResidentId("maria"))

                    config.resident.name shouldBe "Maria Garcia"
                    config.resident.bed shouldBe "12A"
                    config.calibration.heartbeatTimeout shouldBe Duration.ofSeconds(90)
                } finally {
                    tempDir.deleteRecursively()
                }
            }

            it("rejects blank resident ID") {
                val source = TomlConfigSource()
                shouldThrow<IllegalArgumentException> {
                    source.load(ResidentId(""))
                }
            }

            it("throws ConfigNotFoundException for non-existent file") {
                val tempDir = File.createTempFile("mana-hive-test", "").apply {
                    delete()
                    mkdirs()
                }
                try {
                    val source = TomlConfigSource(tempDir.absolutePath)
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

                    val source = TomlConfigSource(tempDir.absolutePath)
                    val configs = source.loadAll()

                    configs.size shouldBe 2
                    configs[ResidentId("maria")]?.resident?.name shouldBe "Maria Garcia"
                    configs[ResidentId("john")]?.resident?.name shouldBe "John Smith"
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

                    val source = TomlConfigSource(tempDir.absolutePath)
                    source.exists(ResidentId("maria")) shouldBe true
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
                    val source = TomlConfigSource(tempDir.absolutePath)
                    source.exists(ResidentId("nonexistent")) shouldBe false
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

                    val source = TomlConfigSource(tempDir.absolutePath)
                    val config1 = source.load(ResidentId("maria"))
                    config1.resident.name shouldBe "Maria Garcia"

                    // Update file
                    tomlFile.writeText("""
                        [resident]
                        name = "Maria Garcia Updated"
                        bed = "12B"
                    """.trimIndent())

                    val config2 = source.reload(ResidentId("maria"))
                    config2.resident.name shouldBe "Maria Garcia Updated"
                    config2.resident.bed shouldBe "12B"
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

                    val source = TomlConfigSource(tempDir.absolutePath)
                    var callbackInvoked = false
                    source.subscribe(ResidentId("maria")) { callbackInvoked = true }

                    source.reload(ResidentId("maria"))
                    callbackInvoked shouldBe true
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("unsubscribe") {
            it("removes callback") {
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

                    val source = TomlConfigSource(tempDir.absolutePath)
                    var callbackInvoked = false
                    source.subscribe(ResidentId("maria")) { callbackInvoked = true }
                    source.unsubscribe(ResidentId("maria"))

                    source.reload(ResidentId("maria"))
                    callbackInvoked shouldBe false
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }

        describe("cache") {
            it("caches loaded config") {
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

                    val source = TomlConfigSource(tempDir.absolutePath)
                    val config1 = source.load(ResidentId("maria"))
                    val config2 = source.load(ResidentId("maria"))

                    config1 shouldBe config2
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }
    }
})
