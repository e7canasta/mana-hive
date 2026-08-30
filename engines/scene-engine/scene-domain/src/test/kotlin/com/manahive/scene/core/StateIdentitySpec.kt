package com.manahive.scene.core

import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.scene.kind
import com.manahive.contracts.scene.personStateFromKind
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * Hay **una sola** identidad de estado, y no pierde nada al ir y volver.
 *
 * Antes habia dos vocabularios en paralelo —`StateKind` y un
 * `contracts.dag.SceneState` de nueve valores— unidos por una tabla de
 * traduccion que aplastaba: una caida viajaba como `STANDING`, el borde de la
 * cama volvia como `SITTING_IN_BED`, sentado en la silla volvia como parado.
 *
 * Ese segundo vocabulario se borro. Con el se fue la tabla, y con la tabla la
 * unica parte del sistema que podia degradar un estado en silencio.
 *
 * De paso murio `SceneDag`, que ademas **no podia** modelar la escena: exigia
 * `require(!hasCycles())` y la escena tiene ciclos por naturaleza — uno se para
 * y se vuelve a sentar. El grafo que corre es `TransitionTable`, que los admite.
 */
class StateIdentitySpec : DescribeSpec({

    describe("ida y vuelta sin perdida") {
        it("todo StateKind vuelve a si mismo") {
            StateKind.entries.forEach { kind ->
                personStateFromKind(kind).kind shouldBe kind
            }
        }
    }

    describe("los estados que la tabla vieja degradaba") {

        it("una caida es una caida, no una persona parada") {
            PersonState.OnFloor.kind shouldBe StateKind.ON_FLOOR
            personStateFromKind(StateKind.ON_FLOOR) shouldBe PersonState.OnFloor
        }

        it("el borde de la cama no vuelve como sentado en la cama") {
            personStateFromKind(StateKind.BED_EDGE) shouldBe PersonState.BedEdge
        }

        it("sentado en la silla no es estar parado") {
            personStateFromKind(StateKind.IN_CHAIR) shouldBe PersonState.InChair
            personStateFromKind(StateKind.IN_WHEELCHAIR) shouldBe PersonState.InWheelchair
        }

        it("el intento de salir de la cama sobrevive: es la señal previa a la caida") {
            personStateFromKind(StateKind.ATTEMPTING_EXIT) shouldBe PersonState.AttemptingExit
        }

        it("afuera del edificio no es el pasillo") {
            personStateFromKind(StateKind.OUTDOOR) shouldBe PersonState.Outdoor
        }
    }

    describe("y el grafo que corre los conoce a todos") {
        it("la escena tiene ciclos, que es lo que un DAG no podia tener") {
            TransitionTable.RELEASE_2.isLegal(StateKind.STANDING, StateKind.IN_CHAIR) shouldBe true
            TransitionTable.RELEASE_2.isLegal(StateKind.IN_CHAIR, StateKind.STANDING) shouldBe true
        }
    }
})
