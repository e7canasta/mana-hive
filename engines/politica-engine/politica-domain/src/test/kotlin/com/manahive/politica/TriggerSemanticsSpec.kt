package com.manahive.politica

import com.manahive.contracts.policy.AlarmProfile
import com.manahive.contracts.policy.CatalogVersion
import com.manahive.contracts.policy.MobilityAid
import com.manahive.contracts.policy.PolicyMode
import com.manahive.contracts.policy.RiskLevel
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.TriggerOn
import com.manahive.contracts.policy.buildDagCatalog
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Duration
import java.time.Instant

/**
 * SPEC-01 · Con qué hecho se abre el episodio.
 *
 * La frase del director "si se queda sentado más de quince minutos, avísenme"
 * tiene que producir una regla que espere quince minutos — no una que dispare
 * apenas se siente. Y la frase "avísenme apenas pise el borde de la cama" tiene
 * que producir una que dispare en la transición.
 *
 * Este spec fija esa correspondencia, que es la que se rompió una vez.
 */
class TriggerSemanticsSpec : BehaviorSpec({

    fun perfil(residente: String) = AlarmProfile(
        residentId = ResidentId(residente),
        catalogVersion = CatalogVersion("2.1.0"),
        riskLevel = RiskLevel.MEDIUM,
        mobilityAid = MobilityAid.NONE,
        autopilot = false,
        mode = PolicyMode.PRESET,
        templateId = null,
        overrides = emptyMap(),
        validFrom = Instant.EPOCH,
    )

    Given("un catálogo donde el director pide aviso POR TIEMPO") {
        val catalogo = buildDagCatalog {
            version("2.1.0")
            resident {
                sitting {
                    alertAfter(Duration.ofMinutes(15))
                    severity(Severity.WARNING)
                }
            }
            room { }
            transitions { }
        }

        When("resuelvo la política") {
            val calibracion = PolicyResolver.resolve(catalogo, perfil("jose"))
            val regla = calibracion.sentinel.alertRules[StateKind.SITTING_IN_BED]

            Then("la regla existe") {
                regla.shouldNotBeNull()
            }

            Then("la regla se dispara por permanencia, no por entrada") {
                regla!!.triggerOn shouldBe TriggerOn.DWELL
            }

            Then("un plazo sin preaviso explícito coloca el preaviso a la mitad") {
                // El director escribió sólo "avísenme a los 15 minutos".
                // Antes esto hacía warning == exceeded y reventaba el resolver.
                val umbral = calibracion.scene.dwellThresholds[StateKind.SITTING_IN_BED]
                umbral.shouldNotBeNull()
                umbral.exceeded shouldBe Duration.ofMinutes(15)
                umbral.warning shouldBe Duration.ofMinutes(7).plusSeconds(30)
            }

        }
    }

    Given("un catálogo donde el director pide aviso INMEDIATO") {
        val catalogo = buildDagCatalog {
            version("2.1.0")
            resident {
                bedEdge {
                    alertOnEntry()
                    severity(Severity.CRITICAL)
                }
            }
            room { }
            transitions { }
        }

        When("resuelvo la política") {
            val calibracion = PolicyResolver.resolve(catalogo, perfil("ana"))
            val regla = calibracion.sentinel.alertRules[StateKind.BED_EDGE]

            Then("la regla existe aunque no tenga plazo") {
                regla.shouldNotBeNull()
            }

            Then("la regla se dispara por entrada") {
                regla!!.triggerOn shouldBe TriggerOn.ENTRY
            }

        }
    }

    Given("un estado que el director deja sin alerta (nivel STANDARD)") {
        val catalogo = buildDagCatalog {
            version("2.1.0")
            resident { sitting { } }
            room { }
            transitions { }
        }

        When("resuelvo la política") {
            val calibracion = PolicyResolver.resolve(catalogo, perfil("susan"))

            Then("no se produce ninguna regla — observar no es alarmar") {
                calibracion.sentinel.alertRules[StateKind.SITTING_IN_BED].shouldBeNull()
            }
        }
    }

    Given("un catálogo que pide las dos cosas para el mismo estado") {
        Then("el DSL lo rechaza al construirse, no en producción") {
            val error = shouldThrow<IllegalArgumentException> {
                buildDagCatalog {
                    version("2.1.0")
                    resident {
                        sitting {
                            alertAfter(Duration.ofMinutes(15))
                            alertOnEntry()
                        }
                    }
                    room { }
                    transitions { }
                }
            }
            error.message!! shouldContain "excluyentes"
        }
    }
})
