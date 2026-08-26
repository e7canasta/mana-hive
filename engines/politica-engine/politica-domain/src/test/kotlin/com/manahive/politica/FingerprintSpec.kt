package com.manahive.politica

import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.MobilityAid
import com.manahive.contracts.policy.RiskLevel
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.WatchLevel
import com.manahive.contracts.policy.buildDagCatalog
import com.manahive.contracts.policy.buildResidentProfile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Duration

/**
 * SPEC-06, criterio 7: dos catálogos de versión distinta producen huellas
 * distintas, aunque las reglas resueltas coincidan.
 *
 * La huella es lo que hace reproducible un replay: la pregunta auditable es
 * *"qué reglas decidieron esto"*, no *"qué reglas se le parecen"*. Dos
 * catálogos que resuelven igual hoy pueden diferir en algo que todavía no se
 * ejercita, y una decisión archivada tiene que poder señalar cuál de los dos
 * la produjo.
 */
class FingerprintSpec : BehaviorSpec({

    fun catalogoVersion(v: String) = buildDagCatalog {
        version(v)
        resident {
            sitting {
                warningAfter(Duration.ofMinutes(10))
                alertAfter(Duration.ofMinutes(15))
                severity(Severity.WARNING)
                closure(ClosureCondition.SAFE_ONLY)
            }
        }
    }

    val perfil = buildResidentProfile("jose") {
        risk(RiskLevel.LOW)
        mobility(MobilityAid.NONE)
        level(WatchLevel.STANDARD)
    }.profile

    Given("dos catálogos idénticos salvo la versión") {
        val v1 = PolicyResolver.resolve(catalogoVersion("1.0.0"), perfil).value
        val v2 = PolicyResolver.resolve(catalogoVersion("2.0.0"), perfil).value

        Then("resuelven las mismas reglas") {
            v1.sentinel.alertRules.keys shouldBe v2.sentinel.alertRules.keys
            v1.scene.dwellThresholds shouldBe v2.scene.dwellThresholds
        }

        Then("pero sus huellas son distintas") {
            v1.fingerprint shouldNotBe v2.fingerprint
        }
    }

    Given("el mismo catálogo resuelto dos veces") {
        val a = PolicyResolver.resolve(catalogoVersion("1.0.0"), perfil).value
        val b = PolicyResolver.resolve(catalogoVersion("1.0.0"), perfil).value

        Then("la huella es estable — si no, el replay no sirve") {
            a.fingerprint shouldBe b.fingerprint
        }
    }
})
