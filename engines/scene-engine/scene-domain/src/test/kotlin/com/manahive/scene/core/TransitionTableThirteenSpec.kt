package com.manahive.scene.core

import com.manahive.contracts.shared.ms
import com.manahive.contracts.scene.StateKind
import com.manahive.scene.calibration.dsl.transitionTable
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * SE-19 · TransitionTable 13 states
 *
 * Verifies the DSL works with all 13 states and the transition table
 * is TOTAL — every legal transition has a hysteresis value.
 *
 * Pattern: Specification (Vernon) — transition rules as objects.
 */
class TransitionTableThirteenSpec : BehaviorSpec({

    Given("una tabla construida con el DSL") {
        val table = transitionTable {
            from(LYING) {
                to(BED_EDGE) after 1500.ms
                to(SITTING_IN_BED) after 1500.ms
                to(ATTEMPTING_EXIT) after 1500.ms
            }
            from(ATTEMPTING_EXIT) {
                to(BED_EDGE) after 1000.ms
                to(LYING) after 1000.ms
                to(SITTING_IN_BED) after 1200.ms
                to(STANDING) after 1200.ms
            }
            from(SITTING_IN_BED) {
                to(LYING) after 1000.ms
                to(BED_EDGE) after 1200.ms
                to(ATTEMPTING_EXIT) after 1200.ms
                to(STANDING) after 1500.ms
            }
            from(BED_EDGE) {
                to(LYING) after 1000.ms
                to(SITTING_IN_BED) after 1000.ms
                to(ATTEMPTING_EXIT) after 1000.ms
                to(STANDING) after 1200.ms
            }
            from(STANDING) {
                to(BED_EDGE) after 1200.ms
                to(IN_BATHROOM) after 2000.ms
                to(IN_ROOM) after 2000.ms
                to(IN_HALLWAY) after 2000.ms
                to(OUTDOOR) after 2000.ms
                to(ABSENT) after 2000.ms
                to(IN_CHAIR) after 1500.ms
                to(IN_WHEELCHAIR) after 1500.ms
            }
            from(IN_BATHROOM) {
                to(STANDING) after 2000.ms
                to(IN_ROOM) after 2000.ms
                to(IN_HALLWAY) after 2000.ms
                to(OUTDOOR) after 2000.ms
            }
            from(IN_ROOM) {
                to(STANDING) after 2000.ms
                to(IN_BATHROOM) after 2000.ms
                to(IN_HALLWAY) after 2000.ms
                to(OUTDOOR) after 2000.ms
                to(IN_CHAIR) after 1500.ms
                to(IN_WHEELCHAIR) after 1500.ms
            }
            from(IN_HALLWAY) {
                to(STANDING) after 2000.ms
                to(IN_BATHROOM) after 2000.ms
                to(IN_ROOM) after 2000.ms
                to(OUTDOOR) after 2000.ms
            }
            from(OUTDOOR) {
                to(STANDING) after 2000.ms
                to(IN_ROOM) after 2000.ms
                to(IN_HALLWAY) after 2000.ms
            }
            from(ABSENT) {
                to(STANDING) after 2000.ms
                to(IN_BATHROOM) after 2000.ms
                to(IN_ROOM) after 2000.ms
                to(IN_HALLWAY) after 2000.ms
                to(OUTDOOR) after 2000.ms
            }
            from(IN_CHAIR) {
                to(STANDING) after 1500.ms
                to(IN_WHEELCHAIR) after 2000.ms
                to(IN_ROOM) after 2000.ms
                to(IN_HALLWAY) after 2000.ms
            }
            from(IN_WHEELCHAIR) {
                to(STANDING) after 1500.ms
                to(IN_CHAIR) after 2000.ms
                to(IN_ROOM) after 2000.ms
                to(IN_HALLWAY) after 2000.ms
                to(OUTDOOR) after 2000.ms
            }
            from(UNKNOWN) {
                to(LYING) after 2000.ms
                to(STANDING) after 2000.ms
            }
        }

        Then("todas las transiciones de bed son legales") {
            table.isLegal(StateKind.LYING, StateKind.BED_EDGE) shouldBe true
            table.isLegal(StateKind.LYING, StateKind.SITTING_IN_BED) shouldBe true
            table.isLegal(StateKind.LYING, StateKind.ATTEMPTING_EXIT) shouldBe true
            table.isLegal(StateKind.SITTING_IN_BED, StateKind.LYING) shouldBe true
            table.isLegal(StateKind.SITTING_IN_BED, StateKind.BED_EDGE) shouldBe true
            table.isLegal(StateKind.SITTING_IN_BED, StateKind.ATTEMPTING_EXIT) shouldBe true
            table.isLegal(StateKind.SITTING_IN_BED, StateKind.STANDING) shouldBe true
            table.isLegal(StateKind.ATTEMPTING_EXIT, StateKind.LYING) shouldBe true
            table.isLegal(StateKind.ATTEMPTING_EXIT, StateKind.SITTING_IN_BED) shouldBe true
            table.isLegal(StateKind.ATTEMPTING_EXIT, StateKind.BED_EDGE) shouldBe true
            table.isLegal(StateKind.ATTEMPTING_EXIT, StateKind.STANDING) shouldBe true
            table.isLegal(StateKind.BED_EDGE, StateKind.LYING) shouldBe true
            table.isLegal(StateKind.BED_EDGE, StateKind.SITTING_IN_BED) shouldBe true
            table.isLegal(StateKind.BED_EDGE, StateKind.ATTEMPTING_EXIT) shouldBe true
            table.isLegal(StateKind.BED_EDGE, StateKind.STANDING) shouldBe true
        }

        Then("todas las transiciones de out-of-bed son legales") {
            table.isLegal(StateKind.STANDING, StateKind.IN_BATHROOM) shouldBe true
            table.isLegal(StateKind.STANDING, StateKind.IN_ROOM) shouldBe true
            table.isLegal(StateKind.STANDING, StateKind.IN_HALLWAY) shouldBe true
            table.isLegal(StateKind.STANDING, StateKind.OUTDOOR) shouldBe true
            table.isLegal(StateKind.STANDING, StateKind.ABSENT) shouldBe true
            table.isLegal(StateKind.STANDING, StateKind.IN_CHAIR) shouldBe true
            table.isLegal(StateKind.STANDING, StateKind.IN_WHEELCHAIR) shouldBe true
            table.isLegal(StateKind.IN_BATHROOM, StateKind.STANDING) shouldBe true
            table.isLegal(StateKind.IN_ROOM, StateKind.STANDING) shouldBe true
            table.isLegal(StateKind.IN_HALLWAY, StateKind.STANDING) shouldBe true
            table.isLegal(StateKind.OUTDOOR, StateKind.STANDING) shouldBe true
            table.isLegal(StateKind.ABSENT, StateKind.STANDING) shouldBe true
        }

        Then("todas las transiciones de furniture son legales") {
            table.isLegal(StateKind.IN_CHAIR, StateKind.STANDING) shouldBe true
            table.isLegal(StateKind.IN_CHAIR, StateKind.IN_WHEELCHAIR) shouldBe true
            table.isLegal(StateKind.IN_CHAIR, StateKind.IN_ROOM) shouldBe true
            table.isLegal(StateKind.IN_CHAIR, StateKind.IN_HALLWAY) shouldBe true
            table.isLegal(StateKind.IN_WHEELCHAIR, StateKind.STANDING) shouldBe true
            table.isLegal(StateKind.IN_WHEELCHAIR, StateKind.IN_CHAIR) shouldBe true
            table.isLegal(StateKind.IN_WHEELCHAIR, StateKind.IN_ROOM) shouldBe true
            table.isLegal(StateKind.IN_WHEELCHAIR, StateKind.IN_HALLWAY) shouldBe true
            table.isLegal(StateKind.IN_WHEELCHAIR, StateKind.OUTDOOR) shouldBe true
        }

        Then("unknown recovery es legal") {
            table.isLegal(StateKind.UNKNOWN, StateKind.LYING) shouldBe true
            table.isLegal(StateKind.UNKNOWN, StateKind.STANDING) shouldBe true
        }

        Then("transiciones ilegales fallan") {
            table.isLegal(StateKind.LYING, StateKind.IN_BATHROOM) shouldBe false
            table.isLegal(StateKind.LYING, StateKind.OUTDOOR) shouldBe false
            table.isLegal(StateKind.OUTDOOR, StateKind.LYING) shouldBe false
            table.isLegal(StateKind.OUTDOOR, StateKind.UNKNOWN) shouldBe false
            table.isLegal(StateKind.IN_BATHROOM, StateKind.LYING) shouldBe false
        }

        Then("cada transicion legal tiene hysteresis") {
            table.hysteresis(StateKind.LYING, StateKind.BED_EDGE).toMillis() shouldBe 1500
            table.hysteresis(StateKind.SITTING_IN_BED, StateKind.STANDING).toMillis() shouldBe 1500
            table.hysteresis(StateKind.ATTEMPTING_EXIT, StateKind.STANDING).toMillis() shouldBe 1200
            table.hysteresis(StateKind.STANDING, StateKind.IN_BATHROOM).toMillis() shouldBe 2000
            table.hysteresis(StateKind.IN_CHAIR, StateKind.STANDING).toMillis() shouldBe 1500
            table.hysteresis(StateKind.UNKNOWN, StateKind.LYING).toMillis() shouldBe 2000
        }
    }
})
