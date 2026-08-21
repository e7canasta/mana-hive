package com.manahive.kernel

/**
 * The uniform shape of every event-sourced aggregate: decide (command -> events)
 * and evolve (state + event -> state). Both pure; the clock always comes in as
 * a parameter. Replay, shadow mode and simulation are the same operation for
 * every Decider: feeding functions with events.
 */
public interface Decider<C, S, E> {
    public val initial: S

    /** Rejects or produces facts. Never mutates, never looks at the world. */
    public fun decide(command: C, state: S): Decision<E>

    /** Total over history: every event ever emitted must be applicable. */
    public fun evolve(state: S, event: E): S
}

public sealed interface Decision<out E> {
    public data class Accepted<E>(public val events: List<E>) : Decision<E>
    public data class Rejected(public val reason: RejectionReason) : Decision<Nothing>
}

/** Each aggregate declares its own typed rejection reasons implementing this. */
public interface RejectionReason {
    public val code: String
}
