package com.manahive.infrastructure.config

import com.manahive.contracts.dag.SceneState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow

class HubSceneDagSourceSpec : DescribeSpec({
    describe("HubSceneDagSource") {
        it("should throw UnsupportedOperationException on load") {
            val source = HubSceneDagSource()

            shouldThrow<UnsupportedOperationException> {
                source.load()
            }
        }

        it("should throw UnsupportedOperationException on subscribe") {
            val source = HubSceneDagSource()

            shouldThrow<UnsupportedOperationException> {
                source.subscribe { }
            }
        }

        it("should throw UnsupportedOperationException on unsubscribe") {
            val source = HubSceneDagSource()

            shouldThrow<UnsupportedOperationException> {
                source.unsubscribe()
            }
        }
    }
})
