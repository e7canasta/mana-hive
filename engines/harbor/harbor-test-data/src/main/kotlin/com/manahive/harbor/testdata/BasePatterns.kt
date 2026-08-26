package com.manahive.harbor.testdata

import com.manahive.contracts.common.Channel
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.harbor.*
import com.manahive.kernel.*
import java.time.Duration
import java.time.Instant

// ── Helpers ──────────────────────────────────────────────────────────────────

fun episodeOpened(
    bed: BedId,
    resident: ResidentId,
    night: NightId,
    at: Instant,
    episodeId: String,
    ruleId: String,
    trigger: com.manahive.contracts.scene.StateKind,
    severity: Severity,
    reversible: Boolean = true,
): SentinelSignal.EpisodeOpened = SentinelSignal.EpisodeOpened(
    bed = bed,
    resident = resident,
    at = at,
    rulesFingerprint = "test",
    episode = EpisodeId(episodeId),
    rule = RuleId(ruleId),
    trigger = trigger,
    severity = severity,
    reversible = reversible,
    requiresNvr = severity == Severity.CRITICAL,
    confirmationWindow = null,
)

fun episodeClosed(
    bed: BedId,
    resident: ResidentId,
    night: NightId,
    at: Instant,
    episodeId: String,
    cause: com.manahive.contracts.sentinel.ClosureCause = com.manahive.contracts.sentinel.ClosureCause.AUTO_RECOVERY,
): SentinelSignal.EpisodeClosed = SentinelSignal.EpisodeClosed(
    bed = bed,
    resident = resident,
    at = at,
    rulesFingerprint = "test",
    episode = EpisodeId(episodeId),
    cause = cause,
    gapDuration = null,
)

// ── Base Patterns ────────────────────────────────────────────────────────────

/**
 * Standard calibration for testing budget scenarios.
 */
fun testCalibration(
    residentId: String = "test",
    warningMax: Int = 5,
    infoMax: Int = 3,
): HarborCalibration = harborCalibration {
    resident(residentId)
    budget {
        warning(warningMax)
        info(infoMax)
    }
    notice {
        channels = setOf(Channel.CONSOLE)
        escalationTimeout = Duration.ofMinutes(30)
    }
    alert {
        channels = setOf(Channel.PUSH, Channel.TABLET)
        escalationTimeout = Duration.ofMinutes(5)
    }
    incident {
        channels = setOf(Channel.PUSH, Channel.TABLET, Channel.WARD_BOARD, Channel.CONSOLE)
        escalationTimeout = Duration.ZERO
    }
}
