package com.manahive.scene.batch

import com.manahive.scene.batch.commands.DiffCommand
import com.manahive.scene.batch.commands.RunCommand
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * Integration tests for scene-batch.
 *
 * Verifies the full pipeline: run → diff → verify.
 * TDD: Red-Green-Refactor (Beck).
 */
class SceneBatchIntegrationSpec : BehaviorSpec({

    val baseDir = File(System.getProperty("java.io.tmpdir"), "scene-batch-test-${System.nanoTime()}")
    val testDir = File(baseDir, "scenario")
    val configFile = File(testDir, "run.yaml")
    val eventsFile = File(testDir, "events.dat")
    val outputDir = File(testDir, "output")

    beforeSpec {
        testDir.mkdirs()
        outputDir.mkdirs()

        configFile.writeText("""
            scene:
              bed: "bed-1"
              night: "night-1"
              resident: "maria"
              monitor: "m1"
            
            calibration:
              transitions: RELEASE_2
              confidence:
                BED_EDGE: 0.8
                STANDING: 0.7
              dwell:
                STANDING: { warning: 4m, exceeded: 5m }
              heartbeat:
                timeout: 90s
            
            events:
              source: "events.dat"
              output: "output"
              start: "2024-01-01T03:00:00Z"
        """.trimIndent())

        eventsFile.writeText("""
            # Test scenario: simple transition
            t=0s    OBS IN_BED confidence=0.95
            t=2s    OBS BED_EDGE confidence=0.92
            t=4s    OBS STANDING confidence=0.90
        """.trimIndent())
    }

    afterSpec {
        baseDir.deleteRecursively()
    }

    Given("a valid scenario config") {
        When("running the batch") {
            Then("it should produce facts.out and facts.jsonl") {
                RunCommand().execute(configFile.absolutePath)

                val factsOut = File(outputDir, "facts.out")
                val factsJsonl = File(outputDir, "facts.jsonl")
                val engineLog = File(outputDir, "engine.log")

                factsOut.exists() shouldBe true
                factsJsonl.exists() shouldBe true
                engineLog.exists() shouldBe true

                factsOut.readText().shouldContain("TRANSITION")
                factsJsonl.readText().shouldContain("TransitionDetected")
            }
        }
    }

    Given("a matching expected.out") {
        val expectedFile = File(testDir, "expected.out")

        When("running diff") {
            Then("it should show all match") {
                // Run first to generate output
                RunCommand().execute(configFile.absolutePath)

                // Create matching expected file
                val actualFacts = File(outputDir, "facts.out").readText()
                expectedFile.writeText(actualFacts)

                DiffCommand().execute(expectedFile.absolutePath, File(outputDir, "facts.out").absolutePath)
            }
        }
    }

    Given("a non-matching expected.out") {
        val expectedFile = File(testDir, "expected-wrong.out")

        When("running diff") {
            Then("it should throw DiffFound") {
                // Run first to generate output
                RunCommand().execute(configFile.absolutePath)

                // Create wrong expected file
                expectedFile.writeText("""
                    t=2s      TRANSITION LYING → STANDING                       # ← evento 2
                """.trimIndent())

                val diff = DiffCommand()
                val file = File(outputDir, "facts.out")
                try {
                    diff.execute(expectedFile.absolutePath, file.absolutePath)
                    throw AssertionError("Expected DiffFound to be thrown")
                } catch (e: BatchError.DiffFound) {
                    e.mismatched shouldBe 1
                } finally {
                    expectedFile.delete()
                }
            }
        }
    }

    Given("a missing config file") {
        When("running the batch") {
            Then("it should throw ConfigNotFound") {
                try {
                    RunCommand().execute("/nonexistent/config.yaml")
                    throw AssertionError("Expected ConfigNotFound to be thrown")
                } catch (e: BatchError.ConfigNotFound) {
                    e.path shouldBe "/nonexistent/config.yaml"
                }
            }
        }
    }

    Given("a missing events file") {
        val badConfig = File(testDir, "bad-run.yaml")

        When("running the batch") {
            Then("it should throw EventsNotFound") {
                badConfig.writeText("""
                    scene:
                      bed: "bed-1"
                      night: "night-1"
                      resident: "maria"
                      monitor: "m1"
                    
                    calibration:
                      transitions: RELEASE_2
                      confidence: {}
                      dwell: {}
                      heartbeat:
                        timeout: 90s
                    
                    events:
                      source: "nonexistent.dat"
                      output: "output"
                """.trimIndent())

                try {
                    RunCommand().execute(badConfig.absolutePath)
                    throw AssertionError("Expected EventsNotFound to be thrown")
                } catch (e: BatchError.EventsNotFound) {
                    e.path shouldContain "nonexistent.dat"
                } finally {
                    badConfig.delete()
                }
            }
        }
    }
})
