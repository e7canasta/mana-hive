package com.manahive.contracts.dag

import com.manahive.contracts.scene.StateKind

/**
 * El puente entre las dos identidades de estado que conviven hoy.
 *
 * [SceneState] es el vocabulario del DAG de escena —posiciones fisicas— y
 * [StateKind] es el que usan las politicas. Que existan dos es una deuda: la
 * identidad deberia ser una sola y abierta. Mientras eso no pase, el puente vive
 * aca, publico y en un solo lugar, porque el mapeo *es* una decision de dominio
 * y no un detalle de la construccion de una tabla de transiciones.
 *
 * ## Lo que este mapeo todavia pierde
 *
 * De [StateKind] hacia el DAG hay colapsos que siguen sin resolverse, y conviene
 * tenerlos a la vista en vez de enterrados en un `when`:
 *
 * | StateKind | va a | por que se pierde |
 * |---|---|---|
 * | `ATTEMPTING_EXIT` | `STANDING` | el intento de salir no es una posicion del DAG |
 * | `IN_CHAIR`, `IN_WHEELCHAIR` | `STANDING` | el DAG no distingue en que esta sentado |
 * | `OUTDOOR` | `IN_HALLWAY` | el DAG no modela el afuera |
 * | `IN_ROOM` | `WALKING` | dos nombres para lo mismo, con matiz distinto |
 *
 * Ninguno es tan grave como los dos que ya se arreglaron —una caida viajaba como
 * `STANDING` y el borde de la cama volvia como `SITTING_IN_BED`— pero todos
 * desaparecen el dia que la identidad sea una sola.
 */
public fun SceneState.toStateKind(): StateKind = when (this) {
    SceneState.LYING -> StateKind.LYING
    SceneState.IN_BED -> StateKind.LYING
    SceneState.SITTING_IN_BED -> StateKind.SITTING_IN_BED
    SceneState.BED_EDGE -> StateKind.BED_EDGE
    SceneState.STANDING -> StateKind.STANDING
    SceneState.WALKING -> StateKind.IN_ROOM
    SceneState.IN_BATHROOM -> StateKind.IN_BATHROOM
    SceneState.IN_HALLWAY -> StateKind.IN_HALLWAY
    SceneState.ON_FLOOR -> StateKind.ON_FLOOR
}

/**
 * De la identidad de politicas al vocabulario del DAG.
 *
 * Null cuando el estado no tiene lugar en el DAG: `ABSENT` y `UNKNOWN` no son
 * posiciones fisicas, y forzarlos contra la mas parecida es exactamente el error
 * que degradaba una caida a "parado".
 */
public fun StateKind.toSceneState(): SceneState? = when (this) {
    StateKind.LYING -> SceneState.LYING
    StateKind.SITTING_IN_BED -> SceneState.SITTING_IN_BED
    StateKind.BED_EDGE -> SceneState.BED_EDGE
    StateKind.STANDING -> SceneState.STANDING
    StateKind.ON_FLOOR -> SceneState.ON_FLOOR
    StateKind.IN_BATHROOM -> SceneState.IN_BATHROOM
    StateKind.IN_ROOM -> SceneState.WALKING
    StateKind.IN_HALLWAY -> SceneState.IN_HALLWAY
    StateKind.ATTEMPTING_EXIT -> SceneState.STANDING
    StateKind.OUTDOOR -> SceneState.IN_HALLWAY
    StateKind.IN_CHAIR -> SceneState.STANDING
    StateKind.IN_WHEELCHAIR -> SceneState.STANDING
    StateKind.ABSENT -> null
    StateKind.UNKNOWN -> null
}
