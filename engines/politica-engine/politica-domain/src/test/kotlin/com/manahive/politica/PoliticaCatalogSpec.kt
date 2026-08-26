package com.manahive.politica

import com.manahive.contracts.policy.*
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Duration
import java.time.Instant

/**
 * BDD tests for Politica Engine template resolution.
 *
 * Tests the flow: AlarmProfile + AlarmCatalog → PolicyCalibration
 * Verifies that each template produces the correct calibration.
 *
 * Fowler: "Given-When-Then" — business-readable tests.
 * Vernon: "Specification by Example" — concrete examples as specifications.
 */
class PoliticaCatalogSpec : BehaviorSpec({

    val catalog = PRODUCTION_CATALOG
    val now = Instant.parse("2026-01-15T22:00:00Z")

    // ── STANDARD template ─────────────────────────────────────────────

    Given("a resident with STANDARD template") {
        val profile = AlarmProfile(
            residentId = ResidentId("jose"),
            riskLevel = RiskLevel.LOW,
            mobilityAid = MobilityAid.NONE,
            autopilot = false,
            mode = PolicyMode.PRESET,
            templateId = TemplateId("standard"),
            overrides = emptyMap(),
            catalogVersion = catalog.version,
            validFrom = now,
        )

        When("resolved against the catalog") {
            val calibration = PolicyResolver.resolve(catalog, profile)

            Then("uses default hysteresis values") {
                calibration.scene.hysteresis[TransitionKey(StateKind.LYING, StateKind.SITTING_IN_BED)] shouldBe Duration.ofMillis(1500)
                calibration.scene.hysteresis[TransitionKey(StateKind.LYING, StateKind.BED_EDGE)] shouldBe Duration.ofMillis(1500)
                calibration.scene.hysteresis[TransitionKey(StateKind.STANDING, StateKind.IN_BATHROOM)] shouldBe Duration.ofMillis(2000)
            }

            Then("uses default dwell thresholds") {
                val sitting = calibration.scene.dwellThresholds[StateKind.SITTING_IN_BED]
                sitting shouldNotBe null
                sitting!!.warning shouldBe Duration.ofMinutes(30)
                sitting.exceeded shouldBe Duration.ofMinutes(45)

                val bathroom = calibration.scene.dwellThresholds[StateKind.IN_BATHROOM]
                bathroom shouldNotBe null
                bathroom!!.warning shouldBe Duration.ofMinutes(20)
                bathroom.exceeded shouldBe Duration.ofMinutes(30)
            }

            Then("source is TEMPLATE (not CATALOG)") {
                val source = PolicyResolver.resolveSource(profile)
                source shouldBe PolicySource.TEMPLATE
            }
        }
    }

    // ── NIGHT-WANDERING template ──────────────────────────────────────

    Given("a resident with NIGHT-WANDERING template") {
        val profile = AlarmProfile(
            residentId = ResidentId("jose"),
            riskLevel = RiskLevel.HIGH,
            mobilityAid = MobilityAid.NONE,
            autopilot = false,
            mode = PolicyMode.PRESET,
            templateId = TemplateId("night-wandering"),
            overrides = emptyMap(),
            catalogVersion = catalog.version,
            validFrom = now,
        )

        When("resolved against the catalog") {
            val calibration = PolicyResolver.resolve(catalog, profile)

            Then("uses faster hysteresis for bed transitions") {
                calibration.scene.hysteresis[TransitionKey(StateKind.LYING, StateKind.SITTING_IN_BED)] shouldBe Duration.ofMillis(1000)
                calibration.scene.hysteresis[TransitionKey(StateKind.LYING, StateKind.BED_EDGE)] shouldBe Duration.ofMillis(1000)
                calibration.scene.hysteresis[TransitionKey(StateKind.LYING, StateKind.STANDING)] shouldBe Duration.ofMillis(1000)
            }

            Then("uses shorter dwell thresholds") {
                val sitting = calibration.scene.dwellThresholds[StateKind.SITTING_IN_BED]
                sitting shouldNotBe null
                sitting!!.warning shouldBe Duration.ofMinutes(20)
                sitting.exceeded shouldBe Duration.ofMinutes(30)

                val bathroom = calibration.scene.dwellThresholds[StateKind.IN_BATHROOM]
                bathroom shouldNotBe null
                bathroom!!.warning shouldBe Duration.ofMinutes(15)
                bathroom.exceeded shouldBe Duration.ofMinutes(25)

                val standing = calibration.scene.dwellThresholds[StateKind.STANDING]
                standing shouldNotBe null
                standing!!.warning shouldBe Duration.ofMinutes(10)
                standing.exceeded shouldBe Duration.ofMinutes(15)
            }

            Then("source is TEMPLATE") {
                val source = PolicyResolver.resolveSource(profile)
                source shouldBe PolicySource.TEMPLATE
            }
        }
    }

    // ── FALL-RISK template ────────────────────────────────────────────

    Given("a resident with FALL-RISK template") {
        val profile = AlarmProfile(
            residentId = ResidentId("maria"),
            riskLevel = RiskLevel.HIGH,
            mobilityAid = MobilityAid.WALKER,
            autopilot = false,
            mode = PolicyMode.PRESET,
            templateId = TemplateId("fall-risk"),
            overrides = emptyMap(),
            catalogVersion = catalog.version,
            validFrom = now,
        )

        When("resolved against the catalog") {
            val calibration = PolicyResolver.resolve(catalog, profile)

            Then("uses longer hysteresis for bed transitions") {
                calibration.scene.hysteresis[TransitionKey(StateKind.LYING, StateKind.SITTING_IN_BED)] shouldBe Duration.ofMillis(2000)
                calibration.scene.hysteresis[TransitionKey(StateKind.LYING, StateKind.STANDING)] shouldBe Duration.ofMillis(3000)
            }

            Then("uses very short dwell for standing and bed edge") {
                val standing = calibration.scene.dwellThresholds[StateKind.STANDING]
                standing shouldNotBe null
                standing!!.warning shouldBe Duration.ofMinutes(2)
                standing.exceeded shouldBe Duration.ofMinutes(3)

                val bedEdge = calibration.scene.dwellThresholds[StateKind.BED_EDGE]
                bedEdge shouldNotBe null
                bedEdge!!.warning shouldBe Duration.ofMinutes(1)
                bedEdge.exceeded shouldBe Duration.ofMinutes(2)
            }
        }
    }

    // ── LOW-MOBILITY template ─────────────────────────────────────────

    Given("a resident with LOW-MOBILITY template") {
        val profile = AlarmProfile(
            residentId = ResidentId("pedro"),
            riskLevel = RiskLevel.MEDIUM,
            mobilityAid = MobilityAid.WHEELCHAIR,
            autopilot = false,
            mode = PolicyMode.PRESET,
            templateId = TemplateId("low-mobility"),
            overrides = emptyMap(),
            catalogVersion = catalog.version,
            validFrom = now,
        )

        When("resolved against the catalog") {
            val calibration = PolicyResolver.resolve(catalog, profile)

            Then("uses longer hysteresis for all transitions") {
                calibration.scene.hysteresis[TransitionKey(StateKind.LYING, StateKind.SITTING_IN_BED)] shouldBe Duration.ofMillis(2500)
                calibration.scene.hysteresis[TransitionKey(StateKind.LYING, StateKind.STANDING)] shouldBe Duration.ofMillis(3000)
            }

            Then("uses extended dwell thresholds") {
                val sitting = calibration.scene.dwellThresholds[StateKind.SITTING_IN_BED]
                sitting shouldNotBe null
                sitting!!.warning shouldBe Duration.ofMinutes(45)
                sitting.exceeded shouldBe Duration.ofMinutes(60)

                val bathroom = calibration.scene.dwellThresholds[StateKind.IN_BATHROOM]
                bathroom shouldNotBe null
                bathroom!!.warning shouldBe Duration.ofMinutes(30)
                bathroom.exceeded shouldBe Duration.ofMinutes(45)
            }
        }
    }

    // ── No template (defaults only) ───────────────────────────────────

    Given("a resident with no template") {
        val profile = AlarmProfile(
            residentId = ResidentId("ana"),
            riskLevel = RiskLevel.LOW,
            mobilityAid = MobilityAid.NONE,
            autopilot = true,
            mode = PolicyMode.CUSTOM,
            templateId = null,
            overrides = emptyMap(),
            catalogVersion = catalog.version,
            validFrom = now,
        )

        When("resolved against the catalog") {
            val calibration = PolicyResolver.resolve(catalog, profile)

            Then("uses catalog defaults") {
                calibration.scene.hysteresis[TransitionKey(StateKind.LYING, StateKind.SITTING_IN_BED)] shouldBe Duration.ofMillis(1500)
                val sitting = calibration.scene.dwellThresholds[StateKind.SITTING_IN_BED]
                sitting shouldNotBe null
                sitting!!.warning shouldBe Duration.ofMinutes(30)
            }

            Then("source is CATALOG") {
                val source = PolicyResolver.resolveSource(profile)
                source shouldBe PolicySource.CATALOG
            }
        }
    }
})
