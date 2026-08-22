package com.manahive.politica

import com.manahive.contracts.policy.AlarmCatalog
import com.manahive.contracts.policy.AlarmProfile
import com.manahive.contracts.policy.CatalogVersion
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.MobilityAid
import com.manahive.contracts.policy.PolicyMode
import com.manahive.contracts.policy.PolicyOverride
import com.manahive.contracts.policy.PolicySource
import com.manahive.contracts.policy.RiskLevel
import com.manahive.contracts.policy.Template
import com.manahive.contracts.policy.TemplateId
import com.manahive.contracts.policy.TransitionKey
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.time.Duration
import java.time.Instant

/**
 * PE-2 · PolicyResolver resuelve PolicyCalibration
 *
 * Patron: Service Pattern (Fowler)
 * TDD: Red-Green-Refactor (Beck)
 */
class PolicyResolverSpec : BehaviorSpec({

    Given("un catálogo con plantillas") {
        val catalog = AlarmCatalog(
            transitions = mapOf(
                TransitionKey(StateKind.LYING, StateKind.BED_EDGE) to Duration.ofMillis(1500),
                TransitionKey(StateKind.BED_EDGE, StateKind.STANDING) to Duration.ofMillis(1500),
            ),
            dwellThresholds = mapOf(
                StateKind.STANDING to DwellThreshold(
                    warning = Duration.ofMinutes(4),
                    exceeded = Duration.ofMinutes(5),
                ),
            ),
            templates = mapOf(
                TemplateId("night_wandering") to Template(
                    id = TemplateId("night_wandering"),
                    hysteresis = mapOf(
                        TransitionKey(StateKind.LYING, StateKind.BED_EDGE) to Duration.ofMillis(2000),
                    ),
                    dwellThresholds = mapOf(
                        StateKind.STANDING to DwellThreshold(
                            warning = Duration.ofMinutes(3),
                            exceeded = Duration.ofMinutes(4),
                        ),
                    ),
                ),
            ),
            version = CatalogVersion("1.0.0"),
        )

        And("un perfil con template night_wandering") {
            val profile = AlarmProfile(
                residentId = ResidentId("maria"),
                riskLevel = RiskLevel.HIGH,
                mobilityAid = MobilityAid.WALKER,
                autopilot = false,
                mode = PolicyMode.PRESET,
                templateId = TemplateId("night_wandering"),
                overrides = emptyMap(),
                catalogVersion = CatalogVersion("1.0.0"),
                validFrom = Instant.parse("2026-08-21T03:00:00Z"),
            )

            When("resuelvo las reglas") {
                val calibration = PolicyResolver.resolve(catalog, profile)

                Then("la calibración no es null") {
                    calibration shouldNotBe null
                }

                Then("la histeresis LYING → BED_EDGE viene del template (2s)") {
                    calibration.hysteresis[TransitionKey(StateKind.LYING, StateKind.BED_EDGE)] shouldBe Duration.ofMillis(2000)
                }

                Then("el dwell STANDING viene del template (3 min warning, 4 min exceeded)") {
                    calibration.dwellThresholds[StateKind.STANDING]?.warning shouldBe Duration.ofMinutes(3)
                    calibration.dwellThresholds[StateKind.STANDING]?.exceeded shouldBe Duration.ofMinutes(4)
                }

                Then("la fuente es TEMPLATE") {
                    PolicyResolver.resolveSource(profile) shouldBe PolicySource.TEMPLATE
                }

                Then("el residentId es correcto") {
                    calibration.residentId shouldBe ResidentId("maria")
                }
            }
        }

        And("un perfil sin template (usa catálogo base)") {
            val profile = AlarmProfile(
                residentId = ResidentId("jose"),
                riskLevel = RiskLevel.LOW,
                mobilityAid = MobilityAid.NONE,
                autopilot = true,
                mode = PolicyMode.PRESET,
                templateId = null,
                overrides = emptyMap(),
                catalogVersion = CatalogVersion("1.0.0"),
                validFrom = Instant.parse("2026-08-21T03:00:00Z"),
            )

            When("resuelvo las reglas") {
                val calibration = PolicyResolver.resolve(catalog, profile)

                Then("la histeresis LYING → BED_EDGE viene del catálogo base (1.5s)") {
                    calibration.hysteresis[TransitionKey(StateKind.LYING, StateKind.BED_EDGE)] shouldBe Duration.ofMillis(1500)
                }

                Then("el dwell STANDING viene del catálogo base (4 min warning, 5 min exceeded)") {
                    calibration.dwellThresholds[StateKind.STANDING]?.warning shouldBe Duration.ofMinutes(4)
                    calibration.dwellThresholds[StateKind.STANDING]?.exceeded shouldBe Duration.ofMinutes(5)
                }

                Then("la fuente es CATALOG") {
                    PolicyResolver.resolveSource(profile) shouldBe PolicySource.CATALOG
                }
            }
        }

        And("un perfil con override de histeresis") {
            val profile = AlarmProfile(
                residentId = ResidentId("pedro"),
                riskLevel = RiskLevel.MEDIUM,
                mobilityAid = MobilityAid.WHEELCHAIR,
                autopilot = false,
                mode = PolicyMode.CUSTOM,
                templateId = null,
                overrides = mapOf(
                    RuleId("hysteresis LYING→BED_EDGE") to PolicyOverride.HysteresisOverride(
                        ruleId = RuleId("hysteresis LYING→BED_EDGE"),
                        key = TransitionKey(StateKind.LYING, StateKind.BED_EDGE),
                        value = Duration.ofMillis(3000),
                    ),
                ),
                catalogVersion = CatalogVersion("1.0.0"),
                validFrom = Instant.parse("2026-08-21T03:00:00Z"),
            )

            When("resuelvo las reglas") {
                val calibration = PolicyResolver.resolve(catalog, profile)

                Then("la histeresis LYING → BED_EDGE viene del override (3s)") {
                    calibration.hysteresis[TransitionKey(StateKind.LYING, StateKind.BED_EDGE)] shouldBe Duration.ofMillis(3000)
                }

                Then("la histeresis BED_EDGE → STANDING viene del catálogo base (1.5s)") {
                    calibration.hysteresis[TransitionKey(StateKind.BED_EDGE, StateKind.STANDING)] shouldBe Duration.ofMillis(1500)
                }

                Then("la fuente es OVERRIDE") {
                    PolicyResolver.resolveSource(profile) shouldBe PolicySource.OVERRIDE
                }
            }
        }

        And("un perfil con override de dwell") {
            val profile = AlarmProfile(
                residentId = ResidentId("laura"),
                riskLevel = RiskLevel.HIGH,
                mobilityAid = MobilityAid.WALKER,
                autopilot = false,
                mode = PolicyMode.CUSTOM,
                templateId = null,
                overrides = mapOf(
                    RuleId("dwell STANDING") to PolicyOverride.DwellOverride(
                        ruleId = RuleId("dwell STANDING"),
                        state = StateKind.STANDING,
                        value = DwellThreshold(
                            warning = Duration.ofMinutes(2),
                            exceeded = Duration.ofMinutes(3),
                        ),
                    ),
                ),
                catalogVersion = CatalogVersion("1.0.0"),
                validFrom = Instant.parse("2026-08-21T03:00:00Z"),
            )

            When("resuelvo las reglas") {
                val calibration = PolicyResolver.resolve(catalog, profile)

                Then("el dwell STANDING viene del override (2 min warning, 3 min exceeded)") {
                    calibration.dwellThresholds[StateKind.STANDING]?.warning shouldBe Duration.ofMinutes(2)
                    calibration.dwellThresholds[StateKind.STANDING]?.exceeded shouldBe Duration.ofMinutes(3)
                }

                Then("la fuente es OVERRIDE") {
                    PolicyResolver.resolveSource(profile) shouldBe PolicySource.OVERRIDE
                }
            }
        }
    }

    Given("un catálogo vacío") {
        val catalog = AlarmCatalog(
            transitions = emptyMap(),
            dwellThresholds = emptyMap(),
            templates = emptyMap(),
            version = CatalogVersion("1.0.0"),
        )

        And("un perfil sin template") {
            val profile = AlarmProfile(
                residentId = ResidentId("empty"),
                riskLevel = RiskLevel.LOW,
                mobilityAid = MobilityAid.NONE,
                autopilot = true,
                mode = PolicyMode.PRESET,
                templateId = null,
                overrides = emptyMap(),
                catalogVersion = CatalogVersion("1.0.0"),
                validFrom = Instant.parse("2026-08-21T03:00:00Z"),
            )

            When("resuelvo las reglas") {
                val calibration = PolicyResolver.resolve(catalog, profile)

                Then("la histeresis está vacía") {
                    calibration.hysteresis.isEmpty() shouldBe true
                }

                Then("los dwell thresholds están vacíos") {
                    calibration.dwellThresholds.isEmpty() shouldBe true
                }

                Then("la fuente es CATALOG") {
                    PolicyResolver.resolveSource(profile) shouldBe PolicySource.CATALOG
                }
            }
        }
    }

    Given("un catálogo con plantillas") {
        val catalog = AlarmCatalog(
            transitions = mapOf(
                TransitionKey(StateKind.LYING, StateKind.BED_EDGE) to Duration.ofMillis(1500),
            ),
            dwellThresholds = emptyMap(),
            templates = mapOf(
                TemplateId("night_wandering") to Template(
                    id = TemplateId("night_wandering"),
                    hysteresis = mapOf(
                        TransitionKey(StateKind.LYING, StateKind.BED_EDGE) to Duration.ofMillis(2000),
                    ),
                    dwellThresholds = emptyMap(),
                ),
            ),
            version = CatalogVersion("1.0.0"),
        )

        And("un perfil con template inexistente") {
            val profile = AlarmProfile(
                residentId = ResidentId("maria"),
                riskLevel = RiskLevel.HIGH,
                mobilityAid = MobilityAid.WALKER,
                autopilot = false,
                mode = PolicyMode.PRESET,
                templateId = TemplateId("nonexistent"),
                overrides = emptyMap(),
                catalogVersion = CatalogVersion("1.0.0"),
                validFrom = Instant.parse("2026-08-21T03:00:00Z"),
            )

            When("resuelvo las reglas") {
                Then("lanza IllegalArgumentException") {
                    val exception = io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                        PolicyResolver.resolve(catalog, profile)
                    }
                    exception.message shouldContain "nonexistent"
                }
            }
        }
    }
})
