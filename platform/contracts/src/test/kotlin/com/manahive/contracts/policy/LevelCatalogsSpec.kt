package com.manahive.contracts.policy

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * ADR-001 · Los cuatro niveles del director tienen catálogo.
 *
 * Un nivel sin catálogo es un residente sin reglas. El enum y el índice tienen
 * que moverse juntos, y nada lo garantiza salvo este test: agregar un nivel sin
 * su catálogo compila perfectamente y falla de noche.
 */
class LevelCatalogsSpec : StringSpec({

    "cada WatchLevel tiene catálogo" {
        WatchLevel.entries.filterNot { it in CATALOG_BY_LEVEL } .shouldBeEmpty()
    }

    "catalogFor devuelve el catálogo del nivel" {
        catalogFor(WatchLevel.STANDARD) shouldBe STANDARD_CATALOG
        catalogFor(WatchLevel.NIGHT_WANDERING) shouldBe NIGHT_WANDERING_CATALOG
        catalogFor(WatchLevel.FALL_RISK) shouldBe FALL_RISK_CATALOG
        catalogFor(WatchLevel.CRITICAL) shouldBe CRITICAL_CATALOG
    }

    "las etiquetas son las que usan los perfiles y el TOML" {
        WatchLevel.entries.map { it.label } shouldBe
            listOf("standard", "night-wandering", "fall-risk", "critical")
    }

    "fromLabel es inversa de label" {
        WatchLevel.entries.forEach { WatchLevel.fromLabel(it.label) shouldBe it }
        WatchLevel.fromLabel("enhanced") shouldBe null
    }

    "STANDARD observa sin alertar; los otros tres alertan" {
        // El cero de STANDARD tiene que venir del nivel, no de un catálogo vacío
        // por accidente: los otros tres deben tener reglas que disparen.
        STANDARD_CATALOG.residentStates.values.none { it.alerts } shouldBe true
        listOf(NIGHT_WANDERING_CATALOG, FALL_RISK_CATALOG, CRITICAL_CATALOG).forEach { catalog ->
            catalog.residentStates.values.any { it.alerts } shouldBe true
        }
    }

    "protección creciente: cada nivel avisa antes o igual que el anterior" {
        val orden = listOf(NIGHT_WANDERING_CATALOG, FALL_RISK_CATALOG, CRITICAL_CATALOG)
        orden.zipWithNext().forEach { (laxo, estricto) ->
            laxo.residentStates.forEach { (state, reglaLaxa) ->
                val reglaEstricta = estricto.residentStates[state] ?: return@forEach
                val a = reglaLaxa.alertAfter
                val b = reglaEstricta.alertAfter
                if (a != null && b != null) {
                    withClue(state) { (b <= a) shouldBe true }
                }
            }
        }
    }
})

private inline fun <T> withClue(clue: Any?, block: () -> T): T =
    io.kotest.assertions.withClue(clue, block)
