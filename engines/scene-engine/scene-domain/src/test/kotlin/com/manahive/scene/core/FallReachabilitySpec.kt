package com.manahive.scene.core

import com.manahive.contracts.scene.StateKind
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * La caida tiene que ser alcanzable **en la tabla base**, no solo cuando alguien
 * carga el catalogo correcto.
 *
 * `RELEASE_2` no tenia ninguna arista hacia `ON_FLOOR`. Las aristas vivian en
 * `ProductionDagCatalog`, que se aplica como override, asi que la caida
 * funcionaba por el camino de produccion y desaparecia en silencio en cualquier
 * uso de la tabla sola — el default de `SceneCalibration`, el de `BatchConfig`,
 * `Main.kt`. Un motor arrancado asi descartaba la observacion por transicion
 * ilegal y no dejaba rastro de por que.
 *
 * Es el mismo defecto que ya se arreglo dos veces (`ON_FLOOR` ausente de
 * `StateKind`, `BED_EDGE` ausente del DAG) reapareciendo un nivel mas abajo.
 * Este spec es para que no haya una tercera.
 */
class FallReachabilitySpec : DescribeSpec({

    val tabla = TransitionTable.RELEASE_2

    /** Todas las posiciones fisicas desde las que una persona se puede caer. */
    val posiciones = listOf(
        StateKind.LYING,
        StateKind.SITTING_IN_BED,
        StateKind.ATTEMPTING_EXIT,
        StateKind.BED_EDGE,
        StateKind.STANDING,
        StateKind.IN_BATHROOM,
        StateKind.IN_ROOM,
        StateKind.IN_HALLWAY,
        StateKind.OUTDOOR,
        StateKind.IN_CHAIR,
        StateKind.IN_WHEELCHAIR,
    )

    describe("uno se cae desde cualquier lado") {
        posiciones.forEach { desde ->
            it("$desde -> ON_FLOOR es legal") {
                tabla.isLegal(desde, StateKind.ON_FLOOR) shouldBe true
            }
        }

        it("caerse de la silla de ruedas tambien es caerse") {
            // El catalogo de produccion solo contemplaba caidas desde la cama y
            // desde parado. Una caida desde la silla es de las mas frecuentes.
            tabla.isLegal(StateKind.IN_WHEELCHAIR, StateKind.ON_FLOOR) shouldBe true
        }
    }

    describe("y se sale del piso") {
        it("se levanta solo") {
            tabla.isLegal(StateKind.ON_FLOOR, StateKind.STANDING) shouldBe true
        }

        it("o alguien lo levanta y lo acuesta o lo sienta") {
            tabla.isLegal(StateKind.ON_FLOOR, StateKind.LYING) shouldBe true
            tabla.isLegal(StateKind.ON_FLOOR, StateKind.IN_CHAIR) shouldBe true
            tabla.isLegal(StateKind.ON_FLOOR, StateKind.IN_WHEELCHAIR) shouldBe true
        }

        it("el piso no es un pozo: sin salida el episodio no cierra por estado seguro") {
            posiciones.any { tabla.isLegal(StateKind.ON_FLOOR, it) } shouldBe true
        }
    }

    describe("la caida se cree rapido") {
        it("800 ms: no cero, para no morder ruido del sensor") {
            tabla.hysteresis(StateKind.STANDING, StateKind.ON_FLOOR) shouldBe
                java.time.Duration.ofMillis(800)
        }

        it("levantarse se confirma mas lento que caerse") {
            val caer = tabla.hysteresis(StateKind.STANDING, StateKind.ON_FLOOR)
            val levantarse = tabla.hysteresis(StateKind.ON_FLOOR, StateKind.STANDING)
            (levantarse > caer) shouldBe true
        }
    }

    describe("un sensor que duda no puede tapar una caida") {
        it("se puede recuperar en el piso desde UNKNOWN") {
            tabla.isLegal(StateKind.UNKNOWN, StateKind.ON_FLOOR) shouldBe true
        }
    }
})
