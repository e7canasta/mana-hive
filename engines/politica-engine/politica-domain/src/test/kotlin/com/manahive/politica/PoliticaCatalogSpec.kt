package com.manahive.politica

import com.manahive.contracts.policy.AlarmProfile
import com.manahive.contracts.policy.CatalogVersion
import com.manahive.contracts.policy.CRITICAL_CATALOG
import com.manahive.contracts.policy.FALL_RISK_CATALOG
import com.manahive.contracts.policy.MobilityAid
import com.manahive.contracts.policy.NIGHT_WANDERING_CATALOG
import com.manahive.contracts.policy.PolicyMode
import com.manahive.contracts.policy.PolicySource
import com.manahive.contracts.policy.RiskLevel
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.STANDARD_CATALOG
import com.manahive.contracts.policy.TransitionKey
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Duration
import java.time.Instant
import com.manahive.contracts.policy.TriggerOn

/**
 * Tests the canonical resolution path: DagCatalog + AlarmProfile → PolicyCalibration.
 *
 * Each level catalog is tested against NIVELES-MONITOREO.md timings.
 */
class PoliticaCatalogSpec : BehaviorSpec({

    val now = Instant.parse("2026-01-15T22:00:00Z")

    // ── STANDARD ─────────────────────────────────────────────────────────

    Given("a resident with STANDARD level") {
        val profile = AlarmProfile(
            residentId = ResidentId("elena"),
            riskLevel = RiskLevel.LOW,
            mobilityAid = MobilityAid.NONE,
            autopilot = false,
            mode = PolicyMode.PRESET,
            templateId = com.manahive.contracts.policy.TemplateId("standard"),
            overrides = emptyMap(),
            catalogVersion = CatalogVersion("2.1.0"),
            validFrom = now,
        )

        When("resolved against STANDARD_CATALOG") {
            val result = PolicyResolver.resolve(STANDARD_CATALOG, profile)
            val calibration = result.value

            Then("no alert rules are produced") {
                calibration.sentinel.alertRules.isEmpty() shouldBe true
            }

            Then("no dwell thresholds are produced") {
                calibration.scene.dwellThresholds.isEmpty() shouldBe true
            }

            Then("source is TEMPLATE") {
                PolicyResolver.resolveSource(profile) shouldBe PolicySource.TEMPLATE
            }
        }
    }

    // ── NIGHT-WANDERING ──────────────────────────────────────────────────

    Given("a resident with NIGHT_WANDERING level") {
        val profile = AlarmProfile(
            residentId = ResidentId("jose"),
            riskLevel = RiskLevel.HIGH,
            mobilityAid = MobilityAid.NONE,
            autopilot = false,
            mode = PolicyMode.PRESET,
            templateId = com.manahive.contracts.policy.TemplateId("night-wandering"),
            overrides = emptyMap(),
            catalogVersion = CatalogVersion("2.1.0"),
            validFrom = now,
        )

        When("resolved against NIGHT_WANDERING_CATALOG") {
            val result = PolicyResolver.resolve(NIGHT_WANDERING_CATALOG, profile)
            val calibration = result.value

            Then("SITTING_IN_BED: warning=20min, exceeded=30min") {
                val sitting = calibration.scene.dwellThresholds[StateKind.SITTING_IN_BED]
                sitting shouldNotBe null
                sitting!!.warning shouldBe Duration.ofMinutes(20)
                sitting.exceeded shouldBe Duration.ofMinutes(30)
            }

            Then("IN_BATHROOM: warning=15min, exceeded=25min") {
                val bathroom = calibration.scene.dwellThresholds[StateKind.IN_BATHROOM]
                bathroom shouldNotBe null
                bathroom!!.warning shouldBe Duration.ofMinutes(15)
                bathroom.exceeded shouldBe Duration.ofMinutes(25)
            }

            Then("STANDING: warning=10min, exceeded=15min") {
                val standing = calibration.scene.dwellThresholds[StateKind.STANDING]
                standing shouldNotBe null
                standing!!.warning shouldBe Duration.ofMinutes(10)
                standing.exceeded shouldBe Duration.ofMinutes(15)
            }

            // Antes esto afirmaba que *todas* las reglas eran WARNING. Era una foto
            // del catalogo, no una invariante: un nivel de riesgo de caida en el que
            // nada llega a critico es justamente lo que hay que revisar. Ahora dice
            // lo que quiere decir — las reglas por permanencia avisan, la caida
            // alerta — y deja de romperse cada vez que el catalogo gana un estado.
            Then("las reglas por permanencia avisan; la caida alerta") {
                calibration.sentinel.alertRules
                    .filterKeys { it != StateKind.ON_FLOOR }
                    .values.forEach { rule ->
                        rule.severity shouldBe Severity.WARNING
                    }

                val caida = calibration.sentinel.alertRules[StateKind.ON_FLOOR]
                caida shouldNotBe null
                caida!!.severity shouldBe Severity.CRITICAL
                caida.triggerOn shouldBe TriggerOn.ENTRY
            }

            Then("LYING → STANDING has recording window (2min before, 5min after)") {
                val window = calibration.recorder.transitionWindows[TransitionKey(StateKind.LYING, StateKind.STANDING)]
                window shouldNotBe null
                window!!.before shouldBe Duration.ofMinutes(2)
                window.after shouldBe Duration.ofMinutes(5)
            }
        }
    }

    // ── FALL-RISK ────────────────────────────────────────────────────────

    Given("a resident with FALL_RISK level") {
        val profile = AlarmProfile(
            residentId = ResidentId("maria"),
            riskLevel = RiskLevel.HIGH,
            mobilityAid = MobilityAid.WALKER,
            autopilot = false,
            mode = PolicyMode.PRESET,
            templateId = com.manahive.contracts.policy.TemplateId("fall-risk"),
            overrides = emptyMap(),
            catalogVersion = CatalogVersion("2.1.0"),
            validFrom = now,
        )

        When("resolved against FALL_RISK_CATALOG") {
            val result = PolicyResolver.resolve(FALL_RISK_CATALOG, profile)
            val calibration = result.value

            Then("STANDING: warning=2min, exceeded=3min") {
                val standing = calibration.scene.dwellThresholds[StateKind.STANDING]
                standing shouldNotBe null
                standing!!.warning shouldBe Duration.ofMinutes(2)
                standing.exceeded shouldBe Duration.ofMinutes(3)
            }

            Then("BED_EDGE: warning=1min, exceeded=2min") {
                val bedEdge = calibration.scene.dwellThresholds[StateKind.BED_EDGE]
                bedEdge shouldNotBe null
                bedEdge!!.warning shouldBe Duration.ofMinutes(1)
                bedEdge.exceeded shouldBe Duration.ofMinutes(2)
            }

            Then("SITTING_IN_BED: warning=15min, exceeded=20min") {
                val sitting = calibration.scene.dwellThresholds[StateKind.SITTING_IN_BED]
                sitting shouldNotBe null
                sitting!!.warning shouldBe Duration.ofMinutes(15)
                sitting.exceeded shouldBe Duration.ofMinutes(20)
            }

            // Antes esto afirmaba que *todas* las reglas eran WARNING. Era una foto
            // del catalogo, no una invariante: un nivel de riesgo de caida en el que
            // nada llega a critico es justamente lo que hay que revisar. Ahora dice
            // lo que quiere decir — las reglas por permanencia avisan, la caida
            // alerta — y deja de romperse cada vez que el catalogo gana un estado.
            Then("las reglas por permanencia avisan; la caida alerta") {
                calibration.sentinel.alertRules
                    .filterKeys { it != StateKind.ON_FLOOR }
                    .values.forEach { rule ->
                        rule.severity shouldBe Severity.WARNING
                    }

                val caida = calibration.sentinel.alertRules[StateKind.ON_FLOOR]
                caida shouldNotBe null
                caida!!.severity shouldBe Severity.CRITICAL
                caida.triggerOn shouldBe TriggerOn.ENTRY
            }
        }
    }

    // ── CRITICAL ─────────────────────────────────────────────────────────

    Given("a resident with CRITICAL level") {
        val profile = AlarmProfile(
            residentId = ResidentId("pedro"),
            riskLevel = RiskLevel.HIGH,
            mobilityAid = MobilityAid.WHEELCHAIR,
            autopilot = false,
            mode = PolicyMode.PRESET,
            templateId = com.manahive.contracts.policy.TemplateId("critical"),
            overrides = emptyMap(),
            catalogVersion = CatalogVersion("2.1.0"),
            validFrom = now,
        )

        When("resolved against CRITICAL_CATALOG") {
            val result = PolicyResolver.resolve(CRITICAL_CATALOG, profile)
            val calibration = result.value

            Then("IN_BATHROOM: warning=5min, exceeded=10min") {
                val bathroom = calibration.scene.dwellThresholds[StateKind.IN_BATHROOM]
                bathroom shouldNotBe null
                bathroom!!.warning shouldBe Duration.ofMinutes(5)
                bathroom.exceeded shouldBe Duration.ofMinutes(10)
            }

            Then("ABSENT: warning=2min, exceeded=5min") {
                val absent = calibration.scene.dwellThresholds[StateKind.ABSENT]
                absent shouldNotBe null
                absent!!.warning shouldBe Duration.ofMinutes(2)
                absent.exceeded shouldBe Duration.ofMinutes(5)
            }

            Then("alert rules have CRITICAL severity") {
                calibration.sentinel.alertRules.values.forEach { rule ->
                    rule.severity shouldBe Severity.CRITICAL
                }
            }

            Then("LYING → STANDING has recording window (5min before, 10min after)") {
                val window = calibration.recorder.transitionWindows[TransitionKey(StateKind.LYING, StateKind.STANDING)]
                window shouldNotBe null
                window!!.before shouldBe Duration.ofMinutes(5)
                window.after shouldBe Duration.ofMinutes(10)
            }
        }
    }

    // ── No template (defaults) ───────────────────────────────────────────

    Given("a resident with no template") {
        val profile = AlarmProfile(
            residentId = ResidentId("ana"),
            riskLevel = RiskLevel.LOW,
            mobilityAid = MobilityAid.NONE,
            autopilot = true,
            mode = PolicyMode.CUSTOM,
            templateId = null,
            overrides = emptyMap(),
            catalogVersion = CatalogVersion("2.1.0"),
            validFrom = now,
        )

        When("resolved against STANDARD_CATALOG") {
            val calibration = PolicyResolver.resolve(STANDARD_CATALOG, profile).value

            Then("uses catalog defaults — no alert rules, no dwell") {
                calibration.sentinel.alertRules.isEmpty() shouldBe true
                calibration.scene.dwellThresholds.isEmpty() shouldBe true
            }

            Then("source is CATALOG") {
                PolicyResolver.resolveSource(profile) shouldBe PolicySource.CATALOG
            }
        }
    }

    // ── Harbor and Recorder: documented gaps ────────────────────────────────

    Given("a FALL_RISK catalog with LYING→STANDING record()") {
        val profile = AlarmProfile(
            residentId = ResidentId("gap-test"),
            riskLevel = RiskLevel.HIGH,
            mobilityAid = MobilityAid.NONE,
            autopilot = false,
            mode = PolicyMode.PRESET,
            templateId = null,
            overrides = emptyMap(),
            catalogVersion = CatalogVersion("2.1.0"),
            validFrom = now,
        )

        When("resolved") {
            val calibration = PolicyResolver.resolve(FALL_RISK_CATALOG, profile).value

            Then("Harbor is intentionally empty — adapter provides defaults") {
                // SPEC-04: Harbor channels/escalation are not yet in the DAG catalog.
                // The adapter (toHarborCalibration) falls back to sensible defaults:
                //   INFO → CONSOLE, WARNING → PUSH+TABLET, CRITICAL → all channels.
                // When the DAG gains harbor configuration, this test should change.
                calibration.harbor.defaultChannels.isEmpty() shouldBe true
                calibration.harbor.escalationTimeouts.isEmpty() shouldBe true
            }

            Then("Recorder transition windows come from catalog record() entries") {
                // FALL_RISK_CATALOG defines record(before=2m, after=5m) on LYING→STANDING.
                val lyingToStanding = com.manahive.contracts.policy.TransitionKey(
                    com.manahive.contracts.scene.StateKind.LYING,
                    com.manahive.contracts.scene.StateKind.STANDING,
                )
                val window = calibration.recorder.transitionWindows[lyingToStanding]
                window shouldNotBe null
                window!!.before shouldBe java.time.Duration.ofMinutes(2)
                window.after shouldBe java.time.Duration.ofMinutes(5)
            }
        }
    }
})
