package com.manahive.sentinel.config

import com.manahive.contracts.engine.ConfigNotFoundException
import com.manahive.kernel.ResidentId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import java.io.File
import java.time.Duration

class TomlSentinelConfigSourceSpec : DescribeSpec({

    describe("TomlSentinelConfigSource") {
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
                    """.trimIndent())

                    val source = TomlSentinelConfigSource(tempDir.absolutePath)
                    val config = source.load(ResidentId("maria"))

                    config.residentId shouldBe "maria"
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
                    val source = TomlSentinelConfigSource(tempDir.absolutePath)
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

                    val source = TomlSentinelConfigSource(tempDir.absolutePath)
                    val configs = source.loadAll()

                    configs.size shouldBe 2
                    configs[ResidentId("maria")]?.residentId shouldBe "maria"
                    configs[ResidentId("john")]?.residentId shouldBe "john"
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

                    val source = TomlSentinelConfigSource(tempDir.absolutePath)
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
                    val source = TomlSentinelConfigSource(tempDir.absolutePath)
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

                    val source = TomlSentinelConfigSource(tempDir.absolutePath)
                    val config1 = source.load(ResidentId("maria"))
                    config1.residentId shouldBe "maria"

                    // Update file
                    tomlFile.writeText("""
                        [resident]
                        name = "Maria Garcia Updated"
                        bed = "12B"
                    """.trimIndent())

                    val config2 = source.reload("maria")
                    config2.residentId shouldBe "maria"
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

                    val source = TomlSentinelConfigSource(tempDir.absolutePath)
                    var callbackInvoked = false
                    source.subscribeResponseRules("maria") { callbackInvoked = true }

                    source.reload("maria")
                    callbackInvoked shouldBe true
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }
    }
})
