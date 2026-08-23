package com.manahive.contracts.dag

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow

class DagVersionSpec : DescribeSpec({
    describe("DagVersion") {
        it("should create a valid version") {
            val version = DagVersion(1)
            version.value shouldBe 1
        }

        it("should increment version") {
            val v1 = DagVersion(1)
            val v2 = v1.next()
            v2.value shouldBe 2
        }

        it("should reject zero version") {
            shouldThrow<IllegalArgumentException> {
                DagVersion(0)
            }
        }

        it("should reject negative version") {
            shouldThrow<IllegalArgumentException> {
                DagVersion(-1)
            }
        }

        it("should have correct string representation") {
            val version = DagVersion(42)
            version.toString() shouldBe "DagVersion(42)"
        }

        it("should reject overflow on next()") {
            val maxVersion = DagVersion(Int.MAX_VALUE)
            shouldThrow<IllegalArgumentException> {
                maxVersion.next()
            }
        }
    }
})
