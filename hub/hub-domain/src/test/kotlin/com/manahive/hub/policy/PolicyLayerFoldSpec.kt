package com.manahive.hub.policy

import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.WatchLevel
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import com.manahive.kernel.StaffId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/**
 * 06b · El fold que reconstruye PolicyLayers desde eventos.
 *
 * Cada prueba documenta una propiedad del fold, no un caso de uso.
 * El fold es una función pura: mismos eventos → mismo resultado.
 */
class PolicyLayerFoldSpec : BehaviorSpec({

    val jose = ResidentId("jose")
    val drGarcia = StaffId("dr-garcia")
    val enfermera = StaffId("enfermera-sanchez")
    val t0 = Instant.parse("2026-08-22T00:00:00Z")
    val t1 = t0 + Duration.ofMinutes(5)
    val t2 = t0 + Duration.ofMinutes(10)
    val t3 = t0 + Duration.ofMinutes(15)

    fun threshold(exceeded: Long, warning: Long = exceeded - 2): DwellThreshold =
        DwellThreshold(
            warning = Duration.ofMinutes(warning),
            exceeded = Duration.ofMinutes(exceeded),
        )

    Given("an empty event stream") {
        Then("the fold returns default layers") {
            val layers = foldPolicyLayers(emptyList())
            layers.level shouldBe WatchLevel.STANDARD
            layers.template.id shouldBe "default"
            layers.adjustments.shouldBeEmpty()
            layers.windows.shouldBeEmpty()
        }
    }

    Given("a single WatchLevelAssigned") {
        val events = listOf(
            WatchLevelAssigned(
                residentId = jose,
                level = WatchLevel.FALL_RISK,
                actor = drGarcia,
                at = t0,
                reason = "post-operatorio de cadera",
            ),
        )
        Then("the fold sets the level and creates a matching template") {
            val layers = foldPolicyLayers(events)
            layers.level shouldBe WatchLevel.FALL_RISK
            layers.template.id shouldBe WatchLevel.FALL_RISK.label
            layers.template.level shouldBe WatchLevel.FALL_RISK
        }
    }

    Given("two WatchLevelAssigned events") {
        val events = listOf(
            WatchLevelAssigned(jose, WatchLevel.STANDARD, drGarcia, t0, "alta"),
            WatchLevelAssigned(jose, WatchLevel.NIGHT_WANDERING, drGarcia, t1, "cambio de juicio"),
        )
        Then("the fold keeps the last one") {
            val layers = foldPolicyLayers(events)
            layers.level shouldBe WatchLevel.NIGHT_WANDERING
        }
    }

    Given("a ManualAdjustmentAdded") {
        val events = listOf(
            WatchLevelAssigned(jose, WatchLevel.FALL_RISK, drGarcia, t0, "nivel base"),
            ManualAdjustmentAdded(
                residentId = jose,
                adjustmentId = "adj-1",
                state = StateKind.SITTING_IN_BED,
                threshold = threshold(15),
                actor = drGarcia,
                at = t1,
                reason = "sentado, avisen a los 15",
            ),
        )
        Then("the adjustment appears in the layers") {
            val layers = foldPolicyLayers(events)
            layers.adjustments shouldHaveSize 1
            layers.adjustments[0].id shouldBe "adj-1"
            layers.adjustments[0].state shouldBe StateKind.SITTING_IN_BED
            layers.adjustments[0].reason shouldBe "sentado, avisen a los 15"
        }
    }

    Given("two ManualAdjustmentAdded with different IDs") {
        val events = listOf(
            WatchLevelAssigned(jose, WatchLevel.FALL_RISK, drGarcia, t0, "nivel base"),
            ManualAdjustmentAdded(jose, "adj-1", StateKind.SITTING_IN_BED,
                threshold(15), drGarcia, t1, "ajuste 1"),
            ManualAdjustmentAdded(jose, "adj-2", StateKind.LYING,
                threshold(20), enfermera, t2, "ajuste 2"),
        )
        Then("both adjustments are present") {
            val layers = foldPolicyLayers(events)
            layers.adjustments shouldHaveSize 2
        }
    }

    Given("a ManualAdjustmentAdded then revoked") {
        val events = listOf(
            WatchLevelAssigned(jose, WatchLevel.FALL_RISK, drGarcia, t0, "nivel base"),
            ManualAdjustmentAdded(jose, "adj-1", StateKind.SITTING_IN_BED,
                threshold(15), drGarcia, t1, "ajuste"),
            ManualAdjustmentRevoked(jose, "adj-1", drGarcia, t2),
        )
        Then("the adjustment is removed") {
            val layers = foldPolicyLayers(events)
            layers.adjustments.shouldBeEmpty()
        }
    }

    Given("ManualAdjustmentAdded with same ID replaces previous") {
        val events = listOf(
            WatchLevelAssigned(jose, WatchLevel.FALL_RISK, drGarcia, t0, "nivel base"),
            ManualAdjustmentAdded(jose, "adj-1", StateKind.SITTING_IN_BED,
                threshold(15), drGarcia, t1, "primera vez"),
            ManualAdjustmentAdded(jose, "adj-1", StateKind.SITTING_IN_BED,
                threshold(10), drGarcia, t2, "corrección"),
        )
        Then("the last one wins") {
            val layers = foldPolicyLayers(events)
            layers.adjustments shouldHaveSize 1
            layers.adjustments[0].threshold.exceeded shouldBe Duration.ofMinutes(10)
            layers.adjustments[0].reason shouldBe "corrección"
        }
    }

    Given("a TimeWindowDefined") {
        val events = listOf(
            WatchLevelAssigned(jose, WatchLevel.FALL_RISK, drGarcia, t0, "nivel base"),
            TimeWindowDefined(
                residentId = jose,
                windowId = "noche-22-07",
                from = LocalTime.of(22, 0),
                to = LocalTime.of(7, 0),
                adjustments = listOf(
                    StateAdjustment(StateKind.LYING, threshold(10)),
                ),
                actor = drGarcia,
                at = t1,
            ),
        )
        Then("the window appears in the layers") {
            val layers = foldPolicyLayers(events)
            layers.windows shouldHaveSize 1
            layers.windows[0].id shouldBe "noche-22-07"
            layers.windows[0].from shouldBe LocalTime.of(22, 0)
        }
    }

    Given("TimeWindowDefined with same ID replaces previous") {
        val events = listOf(
            WatchLevelAssigned(jose, WatchLevel.FALL_RISK, drGarcia, t0, "nivel base"),
            TimeWindowDefined(jose, "noche", LocalTime.of(22, 0), LocalTime.of(7, 0),
                listOf(StateAdjustment(StateKind.LYING, threshold(10))),
                drGarcia, t1),
            TimeWindowDefined(jose, "noche", LocalTime.of(23, 0), LocalTime.of(6, 0),
                listOf(StateAdjustment(StateKind.LYING, threshold(8))),
                drGarcia, t2),
        )
        Then("the last one wins") {
            val layers = foldPolicyLayers(events)
            layers.windows shouldHaveSize 1
            layers.windows[0].from shouldBe LocalTime.of(23, 0)
        }
    }

    Given("events in mixed order (adjustment before level)") {
        val events = listOf(
            ManualAdjustmentAdded(jose, "adj-1", StateKind.SITTING_IN_BED,
                threshold(15), drGarcia, t1, "ajuste sin nivel"),
        )
        Then("the fold applies them in order — adjustment exists with default level") {
            val layers = foldPolicyLayers(events)
            layers.level shouldBe WatchLevel.STANDARD
            layers.adjustments shouldHaveSize 1
        }
    }

    Given("a full lifecycle: assign, adjust, revoke adjustment, change level") {
        val events = listOf(
            WatchLevelAssigned(jose, WatchLevel.STANDARD, drGarcia, t0, "inicio"),
            ManualAdjustmentAdded(jose, "adj-1", StateKind.SITTING_IN_BED,
                threshold(15), drGarcia, t1, "ajuste 1"),
            ManualAdjustmentRevoked(jose, "adj-1", drGarcia, t2),
            WatchLevelAssigned(jose, WatchLevel.FALL_RISK, enfermera, t3, "empeoró"),
        )
        Then("the fold reflects the final state") {
            val layers = foldPolicyLayers(events)
            layers.level shouldBe WatchLevel.FALL_RISK
            layers.adjustments.shouldBeEmpty()
        }
    }

    Given("events for different residents") {
        val maria = ResidentId("maria")
        val events = listOf(
            WatchLevelAssigned(jose, WatchLevel.FALL_RISK, drGarcia, t0, "jose"),
            WatchLevelAssigned(maria, WatchLevel.CRITICAL, drGarcia, t1, "maria"),
        )
        Then("the fold only processes events for the given stream") {
            val joseEvents = events.filter { it.residentId == jose }
            val mariaEvents = events.filter { it.residentId == maria }
            foldPolicyLayers(joseEvents).level shouldBe WatchLevel.FALL_RISK
            foldPolicyLayers(mariaEvents).level shouldBe WatchLevel.CRITICAL
        }
    }
})
