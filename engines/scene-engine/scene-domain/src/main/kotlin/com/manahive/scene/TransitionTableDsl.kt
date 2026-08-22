package com.manahive.scene

import com.manahive.contracts.policy.TransitionKey
import com.manahive.contracts.scene.StateKind
import java.time.Duration

/**
 * DSL for building [TransitionTable] instances.
 *
 * ```kotlin
 * val table = transitionTable {
 *     from(LYING) {
 *         to(BED_EDGE) after 1500.ms
 *         to(STANDING) after 2000.ms
 *     }
 *     from(BED_EDGE) {
 *         to(STANDING) after 1000.ms
 *         to(LYING) after 500.ms
 *     }
 *     from(STANDING) {
 *         to(LYING) after 800.ms
 *     }
 * }
 * ```
 *
 * Fowler: "Primitive Obsession" → Use TransitionKey instead of Pair.
 */
public fun transitionTable(block: TransitionTableBuilder.() -> Unit): TransitionTable =
    TransitionTableBuilder().apply(block).build()

@SceneDsl
public class TransitionTableBuilder {
    private val rules = mutableMapOf<TransitionKey, Duration>()

    // ── State properties ────────────────────────────────────────────────

    public val LYING: StateKind get() = StateKind.LYING
    public val SITTING_IN_BED: StateKind get() = StateKind.SITTING_IN_BED
    public val ATTEMPTING_EXIT: StateKind get() = StateKind.ATTEMPTING_EXIT
    public val BED_EDGE: StateKind get() = StateKind.BED_EDGE
    public val STANDING: StateKind get() = StateKind.STANDING
    public val IN_BATHROOM: StateKind get() = StateKind.IN_BATHROOM
    public val IN_ROOM: StateKind get() = StateKind.IN_ROOM
    public val IN_HALLWAY: StateKind get() = StateKind.IN_HALLWAY
    public val OUTDOOR: StateKind get() = StateKind.OUTDOOR
    public val ABSENT: StateKind get() = StateKind.ABSENT
    public val IN_CHAIR: StateKind get() = StateKind.IN_CHAIR
    public val IN_WHEELCHAIR: StateKind get() = StateKind.IN_WHEELCHAIR
    public val UNKNOWN: StateKind get() = StateKind.UNKNOWN

    public fun from(from: StateKind, block: FromBuilder.() -> Unit) {
        FromBuilder(from, rules).block()
    }

    internal fun build(): TransitionTable = TransitionTable(rules.toMap())
}

@SceneDsl
public class FromBuilder(
    private val from: StateKind,
    private val rules: MutableMap<TransitionKey, Duration>,
) {
    // ── State properties (same as TransitionTableBuilder) ───────────────

    public val LYING: StateKind get() = StateKind.LYING
    public val SITTING_IN_BED: StateKind get() = StateKind.SITTING_IN_BED
    public val ATTEMPTING_EXIT: StateKind get() = StateKind.ATTEMPTING_EXIT
    public val BED_EDGE: StateKind get() = StateKind.BED_EDGE
    public val STANDING: StateKind get() = StateKind.STANDING
    public val IN_BATHROOM: StateKind get() = StateKind.IN_BATHROOM
    public val IN_ROOM: StateKind get() = StateKind.IN_ROOM
    public val IN_HALLWAY: StateKind get() = StateKind.IN_HALLWAY
    public val OUTDOOR: StateKind get() = StateKind.OUTDOOR
    public val ABSENT: StateKind get() = StateKind.ABSENT
    public val IN_CHAIR: StateKind get() = StateKind.IN_CHAIR
    public val IN_WHEELCHAIR: StateKind get() = StateKind.IN_WHEELCHAIR
    public val UNKNOWN: StateKind get() = StateKind.UNKNOWN

    public infix fun to(to: StateKind): TransitionRuleBuilder =
        TransitionRuleBuilder(from, to, rules)
}

@SceneDsl
public class TransitionRuleBuilder(
    private val from: StateKind,
    private val to: StateKind,
    private val rules: MutableMap<TransitionKey, Duration>,
) {
    public infix fun after(duration: Duration) {
        rules[TransitionKey(from, to)] = duration
    }
}
