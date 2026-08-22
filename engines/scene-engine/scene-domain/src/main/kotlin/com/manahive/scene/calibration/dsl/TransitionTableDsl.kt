package com.manahive.scene.calibration.dsl

import com.manahive.contracts.policy.TransitionKey
import com.manahive.contracts.scene.StateKind
import com.manahive.scene.core.TransitionTable
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
public class TransitionTableBuilder : StateKindDsl {
    private val rules = mutableMapOf<TransitionKey, Duration>()

    public fun from(from: StateKind, block: FromBuilder.() -> Unit) {
        FromBuilder(from, rules).block()
    }

    internal fun build(): TransitionTable = TransitionTable(rules.toMap())
}

@SceneDsl
public class FromBuilder(
    private val from: StateKind,
    private val rules: MutableMap<TransitionKey, Duration>,
) : StateKindDsl {
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
