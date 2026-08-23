package com.manahive.infrastructure.config

import com.manahive.contracts.dag.DagNotFoundException
import com.manahive.contracts.dag.SceneState
import com.manahive.contracts.engine.ConfigNotFoundException
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import java.io.File

class TomlSceneDagSourceSpec : DescribeSpec({
    describe("TomlSceneDagSource") {
        it("should load Scene DAG from TOML file") {
            val tmpFile = File.createTempFile("scene-dag", ".toml")
            tmpFile.deleteOnExit()
            tmpFile.writeText("""
                [dag]
                id = "scene-dag-test"
                version = 1

                [[dag.nodes]]
                id = "lying"
                state = "LYING"

                [[dag.nodes]]
                id = "standing"
                state = "STANDING"

                [[dag.edges]]
                from = "lying"
                to = "standing"
            """.trimIndent())

            val source = TomlSceneDagSource(filePath = tmpFile.absolutePath)
            val dag = source.load()

            dag.id.value shouldBe "scene-dag-test"
            dag.nodes shouldHaveSize 2
            dag.edges shouldHaveSize 1
            dag.version.value shouldBe 1

            source.close()
        }

        it("should throw DagNotFoundException for missing file") {
            val source = TomlSceneDagSource(filePath = "/tmp/nonexistent-dag.toml")

            shouldThrow<DagNotFoundException> {
                source.load()
            }

            source.close()
        }

        it("should subscribe to changes") {
            val tmpFile = File.createTempFile("scene-dag", ".toml")
            tmpFile.deleteOnExit()
            tmpFile.writeText("""
                [dag]
                id = "scene-dag-test"
                version = 1

                [[dag.nodes]]
                id = "lying"
                state = "LYING"
            """.trimIndent())

            val source = TomlSceneDagSource(filePath = tmpFile.absolutePath)
            var callCount = 0
            val subscription = source.subscribe { callCount++ }

            subscription.isActive shouldBe true

            source.close()
        }

        it("should unsubscribe") {
            val tmpFile = File.createTempFile("scene-dag", ".toml")
            tmpFile.deleteOnExit()
            tmpFile.writeText("""
                [dag]
                id = "scene-dag-test"
                version = 1

                [[dag.nodes]]
                id = "lying"
                state = "LYING"
            """.trimIndent())

            val source = TomlSceneDagSource(filePath = tmpFile.absolutePath)
            var callCount = 0
            val subscription = source.subscribe { callCount++ }

            subscription.cancel()
            subscription.isActive shouldBe false

            source.close()
        }

        it("should reload from file") {
            val tmpFile = File.createTempFile("scene-dag", ".toml")
            tmpFile.deleteOnExit()
            tmpFile.writeText("""
                [dag]
                id = "scene-dag-test"
                version = 1

                [[dag.nodes]]
                id = "lying"
                state = "LYING"
            """.trimIndent())

            val source = TomlSceneDagSource(filePath = tmpFile.absolutePath)
            val dag1 = source.load()
            dag1.nodes shouldHaveSize 1

            // Update file
            tmpFile.writeText("""
                [dag]
                id = "scene-dag-test"
                version = 2

                [[dag.nodes]]
                id = "lying"
                state = "LYING"

                [[dag.nodes]]
                id = "standing"
                state = "STANDING"
            """.trimIndent())

            val dag2 = source.reload()
            dag2.nodes shouldHaveSize 2
            dag2.version.value shouldBe 2

            source.close()
        }

        it("should check existence") {
            val tmpFile = File.createTempFile("scene-dag", ".toml")
            tmpFile.deleteOnExit()
            tmpFile.writeText("[dag]\nid = \"test\"\n")

            val source = TomlSceneDagSource(filePath = tmpFile.absolutePath)
            source.exists() shouldBe true

            val source2 = TomlSceneDagSource(filePath = "/tmp/nonexistent.toml")
            source2.exists() shouldBe false

            source.close()
            source2.close()
        }
    }
})
