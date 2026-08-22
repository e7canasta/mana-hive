package com.manahive.harbor

import com.manahive.contracts.policy.Severity
import com.manahive.contracts.sentinel.ClosureCause
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.BedId
import com.manahive.kernel.EpisodeId
import com.manahive.kernel.NoticeId
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

/**
 * HarborEngine tests.
 *
 * Covers: episode opened, episode closed, auto-recovery, umbrella events, suppressed signals.
 */
class HarborEngineSpec : BehaviorSpec({

    val now = Instant.parse("2026-08-22T02:00:00Z")
    val bed = BedId("301")
    val resident = ResidentId("maria")
    val episode = EpisodeId("301-ep-001")

    // ── Helper: create a standard signal ──

    fun episodeOpenedSignal(
        severity: Severity = Severity.WARNING,
        reversible: Boolean = true,
    ) = SentinelSignal.EpisodeOpened(
        bed = bed,
        resident = resident,
        at = now,
        rulesFingerprint = "test-fingerprint",
        episode = episode,
        rule = RuleId("r-test"),
        trigger = StateKind.SITTING_IN_BED,
        severity = severity,
        reversible = reversible,
        requiresNvr = severity == Severity.CRITICAL,
        confirmationWindow = when (severity) {
            Severity.INFO -> null
            Severity.WARNING -> Duration.ofMinutes(5)
            Severity.CRITICAL -> Duration.ZERO
        },
    )

    // ── 1. Episode opened → creates notice ──

    Given("an EpisodeOpened signal with WARNING severity") {
        val engine = createHarborEngine(HarborCalibration.default())
        val registry = NoticeRegistry()
        val signal = episodeOpenedSignal(severity = Severity.WARNING)

        When("evaluated") {
            val result = engine.evaluate(signal, registry, now)

            Then("creates a notice") {
                val commands = result.value.commands
                commands.shouldHaveSize(1)
                val dispatch = commands[0] as NoticeCommand.Dispatch
                dispatch.channels shouldBe setOf(Channel.PUSH, Channel.TABLET)
            }

            Then("notice is in registry") {
                val notice = result.value.registry.get(episode)
                notice.shouldNotBeNull()
                notice.severity shouldBe Severity.WARNING
            }
        }
    }

    // ── 2. Episode opened with CRITICAL → all channels ──

    Given("an EpisodeOpened signal with CRITICAL severity") {
        val engine = createHarborEngine(HarborCalibration.default())
        val registry = NoticeRegistry()
        val signal = episodeOpenedSignal(severity = Severity.CRITICAL)

        When("evaluated") {
            val result = engine.evaluate(signal, registry, now)

            Then("dispatches to all channels") {
                val dispatch = result.value.commands[0] as NoticeCommand.Dispatch
                dispatch.channels shouldHaveSize 4
            }
        }
    }

    // ── 3. Duplicate episode → no new notice ──

    Given("an EpisodeOpened signal for an existing episode") {
        val engine = createHarborEngine(HarborCalibration.default())
        val existingNotice = Notice.from(episodeOpenedSignal())
        val registry = NoticeRegistry(active = mapOf(episode to existingNotice))
        val signal = episodeOpenedSignal()

        When("evaluated") {
            val result = engine.evaluate(signal, registry, now)

            Then("no new notice created") {
                result.value.commands.shouldBeEmpty()
            }
        }
    }

    // ── 4. Episode closed → resolves notice ──

    Given("an EpisodeClosed signal with STAFF_AND_SAFE") {
        val engine = createHarborEngine(HarborCalibration.default())
        val existingNotice = Notice.from(episodeOpenedSignal())
        val registry = NoticeRegistry(active = mapOf(episode to existingNotice))

        val signal = SentinelSignal.EpisodeClosed(
            bed = bed,
            resident = resident,
            at = now,
            rulesFingerprint = "test-fingerprint",
            episode = episode,
            cause = ClosureCause.STAFF_AND_SAFE,
            gapDuration = Duration.ofSeconds(30),
        )

        When("evaluated") {
            val result = engine.evaluate(signal, registry, now)

            Then("resolves notice with STAFF_PRESENT") {
                val resolve = result.value.commands[0] as NoticeCommand.Resolve
                resolve.resolution shouldBe Resolution.STAFF_PRESENT
            }

            Then("notice removed from registry") {
                result.value.registry.get(episode) shouldBe null
            }
        }
    }

    // ── 5. Auto-recovery reversible → resolves ──

    Given("an AutoRecovery signal with reversible=true") {
        val engine = createHarborEngine(HarborCalibration.default())
        val existingNotice = Notice.from(episodeOpenedSignal(reversible = true))
        val registry = NoticeRegistry(active = mapOf(episode to existingNotice))

        val signal = SentinelSignal.AutoRecovery(
            bed = bed,
            resident = resident,
            at = now,
            rulesFingerprint = "test-fingerprint",
            episode = episode,
            reversible = true,
            requiresConfirmation = false,
        )

        When("evaluated") {
            val result = engine.evaluate(signal, registry, now)

            Then("resolves notice with AUTO_RECOVERY") {
                val resolve = result.value.commands[0] as NoticeCommand.Resolve
                resolve.resolution shouldBe Resolution.AUTO_RECOVERY
            }
        }
    }

    // ── 6. Auto-recovery non-reversible → sends confirmation alert ──

    Given("an AutoRecovery signal with reversible=false") {
        val engine = createHarborEngine(HarborCalibration.default())
        val existingNotice = Notice.from(episodeOpenedSignal(reversible = false))
        val registry = NoticeRegistry(active = mapOf(episode to existingNotice))

        val signal = SentinelSignal.AutoRecovery(
            bed = bed,
            resident = resident,
            at = now,
            rulesFingerprint = "test-fingerprint",
            episode = episode,
            reversible = false,
            requiresConfirmation = true,
        )

        When("evaluated") {
            val result = engine.evaluate(signal, registry, now)

            Then("sends confirmation alert") {
                val dispatch = result.value.commands[0] as NoticeCommand.Dispatch
                dispatch.channels shouldBe setOf(Channel.PUSH, Channel.TABLET)
            }

            Then("notice remains open") {
                result.value.registry.get(episode).shouldNotBeNull()
            }
        }
    }

    // ── 7. Umbrella event → no new notice ──

    Given("an UmbrellaEvent signal") {
        val engine = createHarborEngine(HarborCalibration.default())
        val registry = NoticeRegistry()

        val signal = SentinelSignal.UmbrellaEvent(
            bed = bed,
            resident = resident,
            at = now,
            rulesFingerprint = "test-fingerprint",
            episode = episode,
            state = StateKind.STANDING,
            originalSeverity = Severity.WARNING,
        )

        When("evaluated") {
            val result = engine.evaluate(signal, registry, now)

            Then("no commands produced") {
                result.value.commands.shouldBeEmpty()
            }
        }
    }

    // ── 8. Suppressed signal → no notice ──

    Given("a SuppressedWithRecord signal") {
        val engine = createHarborEngine(HarborCalibration.default())
        val registry = NoticeRegistry()

        val signal = SentinelSignal.SuppressedWithRecord(
            bed = bed,
            resident = resident,
            at = now,
            rulesFingerprint = "test-fingerprint",
            rule = RuleId("r-test"),
            cause = com.manahive.contracts.sentinel.SuppressionCause.STAFF_PRESENT,
            evidence = com.manahive.kernel.EventRef(stream = "test", seq = 1),
        )

        When("evaluated") {
            val result = engine.evaluate(signal, registry, now)

            Then("no commands produced") {
                result.value.commands.shouldBeEmpty()
            }
        }
    }
})
