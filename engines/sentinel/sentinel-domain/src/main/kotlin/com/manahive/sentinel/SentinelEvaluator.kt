package com.manahive.sentinel

import com.manahive.contracts.policy.EffectiveRules
import com.manahive.contracts.scene.SceneFact
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.kernel.Engine
import com.manahive.kernel.Explained
import java.time.Instant

/**
 * The one that decides to bother a human — the most expensive decision in the
 * system. Turns (scene fact + effective rules + episode state) into signals:
 * incident, occurrence, or suppression with record.
 *
 * Responsible for: the episode algebra (an episode opens when the resident
 * leaves the safe group, closes on sustained return or staff presence; ONE
 * alert per (bed, rule, episode); closed episode = alarm rearmed);
 * suppression by staff presence WITH RECORD; the fatigue budget (INFO never
 * interrupts — it aggregates into the round digest; budget exceeded -> digest,
 * and the explanation says so). Unknown(SIGNAL_LOST) sustained escalates as a
 * TECHNICAL problem, not as resident risk — two different alarms.
 *
 * NOT responsible for: routing or delivering anything (vigia), resolving
 * policy layers (hub).
 */
public interface SentinelEvaluator : Engine {
    public fun evaluate(
        fact: SceneFact,
        rules: EffectiveRules,
        episodes: EpisodeLedger,
        now: Instant,
    ): Explained<SentinelVerdict>
}

public data class SentinelVerdict(
    public val signals: List<SentinelSignal>,
    /** Next episode state, immutable — the shell persists it, the engine never does. */
    public val episodes: EpisodeLedger,
)
