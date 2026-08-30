package com.manahive.scene.core

import com.manahive.contracts.dag.SceneState
import com.manahive.contracts.dag.toSceneState
import com.manahive.contracts.dag.toStateKind
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.scene.kind
import com.manahive.contracts.scene.personStateFromKind
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Una caida no puede viajar disfrazada de persona parada.
 *
 * El DAG de escena modela ON_FLOOR desde el principio y lo documenta como
 * "always critical", pero [StateKind] no tenia ese valor. El mapeo entre los dos
 * enums lo resolvia asi:
 *
 * ```
 * SceneState.ON_FLOOR -> StateKind.STANDING  // "Closest mapping"
 * ```
 *
 * Es decir: el estado mas grave que el sistema puede observar se convertia en
 * uno benigno al cruzar hacia las politicas. Ninguna regla escrita sobre una
 * caida podia dispararse, porque para el motor de politicas la caida no existia.
 */
class OnFloorIdentitySpec : DescribeSpec({

    describe("una caida cruza el sistema sin degradarse") {

        it("del DAG de escena a la identidad de politicas") {
            SceneState.ON_FLOOR.toStateKind() shouldBe StateKind.ON_FLOOR
        }

        it("y no se confunde con estar parado") {
            SceneState.ON_FLOOR.toStateKind() shouldNotBe StateKind.STANDING
        }

        it("vuelve entera: ida y vuelta sin perdida") {
            SceneState.ON_FLOOR.toStateKind().toSceneState() shouldBe SceneState.ON_FLOOR
        }

        it("tiene su propio PersonState") {
            personStateFromKind(StateKind.ON_FLOOR) shouldBe PersonState.OnFloor
            PersonState.OnFloor.kind shouldBe StateKind.ON_FLOOR
        }
    }

    describe("el borde de la cama tampoco se degrada") {
        it("BED_EDGE existe en el DAG y vuelve entero") {
            SceneState.BED_EDGE.toStateKind() shouldBe StateKind.BED_EDGE
            StateKind.BED_EDGE.toSceneState() shouldBe SceneState.BED_EDGE
        }

        it("y ya no vuelve convertido en SITTING_IN_BED") {
            StateKind.BED_EDGE.toSceneState() shouldNotBe SceneState.SITTING_IN_BED
        }
    }

    describe("el resto del mapeo sigue en pie") {
        it("cada StateKind con equivalente en el DAG vuelve a si mismo") {
            listOf(
                StateKind.LYING, StateKind.SITTING_IN_BED, StateKind.STANDING,
                StateKind.IN_BATHROOM, StateKind.IN_HALLWAY, StateKind.ON_FLOOR,
                StateKind.BED_EDGE,
            ).forEach { kind ->
                kind.toSceneState()?.toStateKind() shouldBe kind
            }
        }
    }
})
