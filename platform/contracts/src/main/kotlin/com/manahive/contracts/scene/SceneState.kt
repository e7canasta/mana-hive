package com.manahive.contracts.scene

import java.time.Duration
import java.time.Instant

/**
 * Scene State — orthogonal dimension to PersonState.
 *
 * While PersonState is a FSM (mutually exclusive states),
 * SceneState is a collection of independent flags that describe
 * the environment around the resident.
 *
 * Each object type has its own hierarchy:
 * - PresenceState: Unknown, NotPresent → Present → InReach (InReach ⊂ Present)
 * - RailState: Unknown, Down → Up → Cover (Cover ⊂ Up)
 *
 * Bitmask representation (10 bits):
 * ```
 * [0-1]   staff       (00=NotPresent, 01=Present, 10=InReach, 11=Unknown)
 * [2-3]   wheelchair
 * [4-5]   walker
 * [6-7]   bed.left    (00=Down, 01=Up, 10=Cover, 11=Unknown)
 * [8-9]   bed.right
 * ```
 *
 * ## El reloj es por campo, no del conjunto
 *
 * [since] guarda desde cuándo vale cada campo, por separado. Un solo timestamp
 * para todo el compuesto no alcanza: si la baranda baja a las 3:00 y la silla se
 * mueve a las 3:10, un reloj compartido se resetea y se pierde que la baranda
 * lleva diez minutos abajo. La permanencia por campo —que es justamente lo que
 * el director configura— solo se puede calcular con un reloj por campo.
 *
 * ## Desconocido es un estado, y es el inicial
 *
 * Todos los campos arrancan en `Unknown`. Un default que afirma "no hay silla,
 * ambas barandas abajo" sin que ningún sensor haya mirado es una mentira barata
 * que se paga cara: la regla "baranda abajo con la residente acostada" dispararía
 * en todas las camas al arrancar el sistema.
 */
public data class SceneState(
    val staff: PresenceState = PresenceState.Unknown,
    val wheelchair: PresenceState = PresenceState.Unknown,
    val walker: PresenceState = PresenceState.Unknown,
    val bed: BedState = BedState(),
    /** Desde cuándo vale cada campo. Clave: los nombres de [FIELDS]. */
    val since: Map<String, Instant> = emptyMap(),
) {

    /** El estado actual de cada campo, por su nombre. */
    public val fields: Map<String, SceneObjectState>
        get() = mapOf(
            STAFF to staff,
            WHEELCHAIR to wheelchair,
            WALKER to walker,
            BED_LEFT to bed.left,
            BED_RIGHT to bed.right,
        )

    /** El estado de [field], o null si el nombre no existe. */
    public fun stateOf(field: String): SceneObjectState? = fields[field]

    /** Desde cuándo vale [field]. Null si nunca se observó. */
    public fun sinceOf(field: String): Instant? = since[field]

    /**
     * Cuánto lleva [field] en su estado actual.
     *
     * Null cuando el campo nunca fue observado: no es cero. Un campo que nadie
     * miró no lleva cero tiempo en un estado, no tiene estado — y una regla de
     * permanencia sobre él no puede evaluarse todavía.
     */
    public fun durationIn(field: String, now: Instant): Duration? =
        since[field]?.let { Duration.between(it, now) }

    /**
     * Este estado, con el reloj de los campos que cambiaron puesto en [at].
     *
     * Los campos que no cambiaron conservan su reloj: es exactamente lo que hace
     * que la permanencia de la baranda sobreviva a que se mueva la silla.
     */
    public fun stamped(previous: SceneState, at: Instant): SceneState {
        val changed = previous.diff(this)
        if (changed.isEmpty()) return copy(since = previous.since)
        return copy(since = previous.since + changed.associate { it.field to at })
    }

    /**
     * Convert to bitmask for serialization/comparison.
     * 10 bits → UShort.
     */
    public fun toBitmask(): UShort {
        var bits = 0
        bits = bits or (staff.level shl 0)
        bits = bits or (wheelchair.level shl 2)
        bits = bits or (walker.level shl 4)
        bits = bits or (bed.left.level shl 6)
        bits = bits or (bed.right.level shl 8)
        return bits.toUShort()
    }

    /**
     * Detect what changed between this and another SceneState.
     * Returns a list of field changes (Fowler: "Feature Envy" → Move Method).
     */
    public fun diff(other: SceneState): List<SceneFieldChange> = buildList {
        if (staff != other.staff) add(SceneFieldChange(STAFF, staff, other.staff))
        if (wheelchair != other.wheelchair) add(SceneFieldChange(WHEELCHAIR, wheelchair, other.wheelchair))
        if (walker != other.walker) add(SceneFieldChange(WALKER, walker, other.walker))
        if (bed.left != other.bed.left) add(SceneFieldChange(BED_LEFT, bed.left, other.bed.left))
        if (bed.right != other.bed.right) add(SceneFieldChange(BED_RIGHT, bed.right, other.bed.right))
    }

    public companion object {
        // La identidad de un campo de escena es `sujeto.aspecto`, la misma forma
        // que usa el perfil del residente. Una sola convencion en las dos puntas
        // significa que no hace falta traducir, y una traduccion es justamente
        // donde se pierden cosas: el perfil dice `bed.left` y el motor escucha
        // `bed.left`.
        //
        // El aspecto no es decorativo. Un sujeto tiene varios ejes ortogonales
        // —la silla esta presente *y* fuera de alcance a la vez— y una clave
        // plana como "wheelchair" no puede distinguirlos. Nombrarlo desde el
        // principio deja lugar a `wheelchair.reach` sin renombrar nada.
        public const val STAFF: String = "staff.presence"
        public const val WHEELCHAIR: String = "wheelchair.presence"
        public const val WALKER: String = "walker.presence"
        public const val BED_LEFT: String = "bed.left"
        public const val BED_RIGHT: String = "bed.right"

        /** Los campos que este estado rastrea, en orden de bitmask. */
        public val FIELDS: List<String> = listOf(STAFF, WHEELCHAIR, WALKER, BED_LEFT, BED_RIGHT)

        /**
         * Convert from bitmask.
         *
         * No reconstruye [since]: la huella de bits dice qué es cada campo, no
         * desde cuándo. Quien la use para rehidratar tiene que traer los relojes
         * aparte, o aceptar que arranca sin permanencia acumulada.
         */
        public fun fromBitmask(bits: UShort): SceneState {
            val staff = PresenceState.fromLevel((bits.toInt() shr 0) and 0b11)
            val wheelchair = PresenceState.fromLevel((bits.toInt() shr 2) and 0b11)
            val walker = PresenceState.fromLevel((bits.toInt() shr 4) and 0b11)
            val left = RailState.fromLevel((bits.toInt() shr 6) and 0b11)
            val right = RailState.fromLevel((bits.toInt() shr 8) and 0b11)

            return SceneState(
                staff = staff,
                wheelchair = wheelchair,
                walker = walker,
                bed = BedState(left = left, right = right),
            )
        }
    }
}

/**
 * Represents a change in a single SceneState field.
 * Used by [SceneState.diff] to describe what changed.
 */
public data class SceneFieldChange(
    val field: String,
    val from: Any,
    val to: Any,
)

/**
 * Base interface for all scene objects.
 */
public sealed interface SceneObjectState {
    /**
     * Si algún sensor informó este campo alguna vez.
     *
     * Todo lo que decida alertar tiene que preguntar esto primero: un campo
     * desconocido nunca es motivo de alerta clínica. Que un sensor lleve mucho
     * tiempo mudo es un problema de mantenimiento, y se avisa por otro lado.
     */
    public val isKnown: Boolean
}

/**
 * A state with a numeric level for bitmask serialization.
 * Shared by PresenceState and RailState (Fowler: Extract Superclass).
 */
public sealed interface LeveledState {
    public val level: Int
}

/**
 * Presence state for personnel and mobility aids.
 *
 * Hierarchy: NotPresent → Present → InReach
 * (InReach ⊂ Present: InReach implies Present, but Present ≠ InReach)
 *
 * [Unknown] está fuera de esa jerarquía a propósito: no es "menos presente que
 * NotPresent", es la ausencia de observación. No se ordena con las demás.
 */
public sealed interface PresenceState : SceneObjectState, LeveledState {
    /**
     * Si el objeto está presente.
     *
     * `Unknown` es false: no sabemos que esté, y afirmar que está porque "no es
     * NotPresent" es exactamente el error que este tipo existe para evitar.
     */
    public val isPresent: Boolean get() = this is Present || this is InReach

    public data object NotPresent : PresenceState {
        override val level: Int = 0b00
        override val isKnown: Boolean = true
    }

    public data object Present : PresenceState {
        override val level: Int = 0b01
        override val isKnown: Boolean = true
    }

    public data object InReach : PresenceState {
        override val level: Int = 0b10
        override val isKnown: Boolean = true
    }

    /** Ningún sensor informó todavía. Es el estado inicial. */
    public data object Unknown : PresenceState {
        override val level: Int = 0b11
        override val isKnown: Boolean = false
    }

    public companion object {
        public fun fromLevel(level: Int): PresenceState = when (level) {
            0b00 -> NotPresent
            0b01 -> Present
            0b10 -> InReach
            else -> Unknown
        }
    }
}

/**
 * Rail state for bed rails.
 *
 * Hierarchy: Down → Up → Cover
 * (Cover ⊂ Up: Cover implies Up, but Up ≠ Cover)
 *
 * [Unknown] no participa de esa jerarquía: no es "menos que Down".
 */
public sealed interface RailState : SceneObjectState, LeveledState {
    public data object Down : RailState {
        override val level: Int = 0b00
        override val isKnown: Boolean = true
    }

    public data object Up : RailState {
        override val level: Int = 0b01
        override val isKnown: Boolean = true
    }

    public data object Cover : RailState {
        override val level: Int = 0b10
        override val isKnown: Boolean = true
    }

    /** Ningún sensor informó todavía. Es el estado inicial. */
    public data object Unknown : RailState {
        override val level: Int = 0b11
        override val isKnown: Boolean = false
    }

    public companion object {
        public fun fromLevel(level: Int): RailState = when (level) {
            0b00 -> Down
            0b01 -> Up
            0b10 -> Cover
            else -> Unknown
        }
    }
}

/**
 * Bed state — composition of left and right rail states.
 *
 * Cover requires both rails Up (enforced by semantics, not by type).
 * The `hasCover` property checks this invariant.
 *
 * Cada predicado de acá afirma algo sobre la cama, y por eso ninguno puede ser
 * true con una baranda desconocida: "las barandas están arriba" y "no sabemos
 * dónde está una baranda" son respuestas distintas, y confundirlas es lo que
 * hace que un sensor apagado parezca una cama segura.
 */
public data class BedState(
    val left: RailState = RailState.Unknown,
    val right: RailState = RailState.Unknown,
) {
    /** Si las dos barandas fueron observadas. */
    public val isKnown: Boolean
        get() = left.isKnown && right.isKnown

    /** Both rails up (with or without cover). */
    public val isRailsUp: Boolean
        get() = isKnown && left != RailState.Down && right != RailState.Down

    /** Both rails up with cover. */
    public val hasCover: Boolean
        get() = left == RailState.Cover && right == RailState.Cover

    /** At least one rail down. */
    public val hasDownRail: Boolean
        get() = left == RailState.Down || right == RailState.Down
}

// ── DSL ─────────────────────────────────────────────────────────────────────

/**
 * Type-safe DSL for building [SceneState] instances.
 *
 * Example:
 * ```kotlin
 * val scene = sceneState {
 *     staff = PresenceState.InReach
 *     wheelchair = PresenceState.Present
 *     bed {
 *         left = RailState.Up
 *         right = RailState.Up
 *     }
 * }
 * ```
 *
 * Los relojes no se ponen acá: los pone [SceneState.stamped] cuando el estado
 * evoluciona, que es el único momento en que se sabe cuándo cambió cada campo.
 */
public fun sceneState(init: SceneStateBuilder.() -> Unit): SceneState = SceneStateBuilder().apply(init).build()

@SceneStateDsl
public class SceneStateBuilder {
    public var staff: PresenceState = PresenceState.Unknown
    public var wheelchair: PresenceState = PresenceState.Unknown
    public var walker: PresenceState = PresenceState.Unknown
    private var bedBuilder = BedStateBuilder()

    public fun bed(init: BedStateBuilder.() -> Unit) {
        bedBuilder.apply(init)
    }

    public fun build(): SceneState = SceneState(
        staff = staff,
        wheelchair = wheelchair,
        walker = walker,
        bed = bedBuilder.build(),
    )
}

@SceneStateDsl
public class BedStateBuilder {
    public var left: RailState = RailState.Unknown
    public var right: RailState = RailState.Unknown

    public fun build(): BedState = BedState(left = left, right = right)
}

@DslMarker
public annotation class SceneStateDsl
