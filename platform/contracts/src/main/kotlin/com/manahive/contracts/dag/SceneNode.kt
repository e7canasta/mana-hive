package com.manahive.contracts.dag

import com.manahive.kernel.NodeId

/**
 * A node in the Scene DAG.
 *
 * Represents a physical position/state of a person in a room.
 *
 * Evans: "Value Object" — immutable, equality by value.
 */
public data class SceneNode(
    public val id: NodeId,
    public val state: SceneState,
)

/**
 * Physical states of a person in a room.
 *
 * These are the ONLY states the Scene DAG models.
 * No engine events, no alerts, no notifications — just physical positions.
 *
 * Vernon: "Type-safe enum" — exhaustive when() expressions.
 */
public enum class SceneState {
    /** Lying in bed. */
    LYING,
    /** In bed, not lying (sitting up, etc). */
    IN_BED,
    /** Sitting up in bed, not on the edge. */
    SITTING_IN_BED,

    /**
     * Sentado en el borde de la cama.
     *
     * Es la posicion previa a la caida y la que las plantillas de riesgo marcan
     * como critica al entrar. No estaba en el DAG, asi que el mapeo la devolvia
     * como SITTING_IN_BED: la regla mas importante del catalogo de riesgo de
     * caida perdia su sujeto al cruzar de vuelta.
     */
    BED_EDGE,
    /** Standing next to the bed. */
    STANDING,
    /** Walking/moving. */
    WALKING,
    /** In the bathroom. */
    IN_BATHROOM,
    /** In the hallway. */
    IN_HALLWAY,
    /**
     * En el piso.
     *
     * Que tan grave es esto NO se decide aca. Decia "always critical", y una
     * afirmacion de politica escrita adentro de un contrato de escena es
     * exactamente lo que la separacion politica/mecanismo existe para impedir:
     * el motor observa que la persona esta en el piso, y el perfil del residente
     * dice que hacer al respecto — como con cualquier otro estado.
     *
     * Las plantillas traen una regla por defecto para este estado, y el director
     * la puede cambiar.
     */
    ON_FLOOR,
}
