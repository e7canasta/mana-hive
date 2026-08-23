package com.manahive.hub.policy

import com.manahive.contracts.policy.DimensionDescriptor
import com.manahive.contracts.policy.DimensionType
import com.manahive.contracts.policy.EventClass
import com.manahive.contracts.policy.EventDescriptor
import com.manahive.contracts.policy.PolicyCategory
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class InMemoryPolicyCatalogSpec : DescribeSpec({

    val fallEvent = EventDescriptor(
        id = "fall",
        group = "fall_prevention",
        description = "Caída detectada",
        eventClass = EventClass.TRANSITION,
        category = PolicyCategory.RESPONSE,
    )

    val bathroomDwellEvent = EventDescriptor(
        id = "bathroom_dwell",
        group = "location",
        description = "Mucho tiempo en baño",
        eventClass = EventClass.DWELL,
        category = PolicyCategory.CALIBRATION,
    )

    val sensitivityDimension = DimensionDescriptor(
        id = "sensitivity",
        description = "Qué tan sensible detectar",
        dimensionType = DimensionType.ENUM,
        category = PolicyCategory.CALIBRATION,
        allowedValues = listOf("s", "m", "l"),
        defaultValue = "m",
        required = true,
    )

    val criticityDimension = DimensionDescriptor(
        id = "criticity",
        description = "Qué tan grave",
        dimensionType = DimensionType.ENUM,
        category = PolicyCategory.RESPONSE,
        allowedValues = listOf("-", "notif", "alert", "incident"),
        defaultValue = "notif",
        required = true,
    )

    describe("InMemoryPolicyCatalog") {
        describe("events") {
            it("returns all events sorted by id") {
                val catalog = InMemoryPolicyCatalog(
                    events = listOf(bathroomDwellEvent, fallEvent),
                )

                val events = catalog.getAllEvents()
                events shouldHaveSize 2
                events[0].id shouldBe "bathroom_dwell"
                events[1].id shouldBe "fall"
            }

            it("gets event by id") {
                val catalog = InMemoryPolicyCatalog(events = listOf(fallEvent))

                catalog.getEvent("fall") shouldBe fallEvent
            }

            it("returns null for non-existent event") {
                val catalog = InMemoryPolicyCatalog()

                catalog.getEvent("nonexistent") shouldBe null
            }

            it("gets events by category") {
                val catalog = InMemoryPolicyCatalog(
                    events = listOf(fallEvent, bathroomDwellEvent),
                )

                val calibrationEvents = catalog.getEventsByCategory(PolicyCategory.CALIBRATION)
                calibrationEvents shouldHaveSize 1
                calibrationEvents[0].id shouldBe "bathroom_dwell"
            }

            it("gets events by group") {
                val catalog = InMemoryPolicyCatalog(
                    events = listOf(fallEvent, bathroomDwellEvent),
                )

                val fallPreventionEvents = catalog.getEventsByGroup("fall_prevention")
                fallPreventionEvents shouldHaveSize 1
                fallPreventionEvents[0].id shouldBe "fall"
            }

            it("tracks event count") {
                val catalog = InMemoryPolicyCatalog(
                    events = listOf(fallEvent, bathroomDwellEvent),
                )

                catalog.eventCount() shouldBe 2
            }
        }

        describe("dimensions") {
            it("returns all dimensions sorted by id") {
                val catalog = InMemoryPolicyCatalog(
                    dimensions = listOf(criticityDimension, sensitivityDimension),
                )

                val dimensions = catalog.getAllDimensions()
                dimensions shouldHaveSize 2
                dimensions[0].id shouldBe "criticity"
                dimensions[1].id shouldBe "sensitivity"
            }

            it("gets dimension by id") {
                val catalog = InMemoryPolicyCatalog(
                    dimensions = listOf(sensitivityDimension),
                )

                catalog.getDimension("sensitivity") shouldBe sensitivityDimension
            }

            it("returns null for non-existent dimension") {
                val catalog = InMemoryPolicyCatalog()

                catalog.getDimension("nonexistent") shouldBe null
            }

            it("gets dimensions by category") {
                val catalog = InMemoryPolicyCatalog(
                    dimensions = listOf(sensitivityDimension, criticityDimension),
                )

                val calibrationDimensions = catalog.getDimensionsByCategory(PolicyCategory.CALIBRATION)
                calibrationDimensions shouldHaveSize 1
                calibrationDimensions[0].id shouldBe "sensitivity"
            }

            it("tracks dimension count") {
                val catalog = InMemoryPolicyCatalog(
                    dimensions = listOf(sensitivityDimension, criticityDimension),
                )

                catalog.dimensionCount() shouldBe 2
            }
        }

        describe("empty catalog") {
            it("returns empty lists") {
                val catalog = InMemoryPolicyCatalog()

                catalog.getAllEvents() shouldBe emptyList()
                catalog.getAllDimensions() shouldBe emptyList()
                catalog.eventCount() shouldBe 0
                catalog.dimensionCount() shouldBe 0
            }
        }
    }
})
