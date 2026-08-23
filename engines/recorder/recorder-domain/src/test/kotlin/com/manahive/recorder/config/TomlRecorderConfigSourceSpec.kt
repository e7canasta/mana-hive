package com.manahive.recorder.config

import com.manahive.contracts.engine.ConfigNotFoundException
import com.manahive.kernel.ResidentId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import java.io.File
import java.time.Duration

class TomlRecorderConfigSourceSpec : DescribeSpec({

    describe("TomlRecorderConfigSource") {
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
                        
                        [recording]
                        enabled = true
                        preEventWindow = "30s"
                        postEventWindow = "2m"
                        quality = "HIGH"
                    """.trimIndent())

                    val source = TomlRecorderConfigSource(tempDir.absolutePath)
                    val config = source.load(ResidentId("maria"))

                    config.residentId shouldBe "maria"
                    config.enabled shouldBe true
                    config.preEventWindow shouldBe Duration.ofSeconds(30)
                    config.postEventWindow shouldBe Duration.ofMinutes(2)
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
                    val source = TomlRecorderConfigSource(tempDir.absolutePath)
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

                    val source = TomlRecorderConfigSource(tempDir.absolutePath)
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

                    val source = TomlRecorderConfigSource(tempDir.absolutePath)
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
                    val source = TomlRecorderConfigSource(tempDir.absolutePath)
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
                        
                        [recording]
                        enabled = false
                    """.trimIndent())

                    val source = TomlRecorderConfigSource(tempDir.absolutePath)
                    val config1 = source.load(ResidentId("maria"))
                    config1.enabled shouldBe false

                    // Update file
                    tomlFile.writeText("""
                        [resident]
                        name = "Maria Garcia"
                        bed = "12A"
                        
                        [recording]
                        enabled = true
                    """.trimIndent())

                    val config2 = source.reload("maria")
                    config2.enabled shouldBe true
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

                    val source = TomlRecorderConfigSource(tempDir.absolutePath)
                    var callbackInvoked = false
                    source.subscribeRecording("maria") { callbackInvoked = true }

                    source.reload("maria")
                    callbackInvoked shouldBe true
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }
    }
})
