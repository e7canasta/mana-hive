package com.manahive.serialization

import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.NightSummary
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.sentinel.ClosureCause
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.policy.DagCatalog
import com.manahive.contracts.policy.CatalogVersion
import com.manahive.contracts.policy.ResidentStateRule
import com.manahive.contracts.policy.TransitionKey
import com.manahive.contracts.policy.DagTransitionRule
import com.manahive.contracts.policy.RoomStateRule
import com.manahive.contracts.policy.ResidentProfileConfig
import com.manahive.contracts.policy.AlarmProfile
import com.manahive.contracts.policy.RiskLevel
import com.manahive.contracts.policy.MobilityAid
import com.manahive.contracts.policy.PolicyMode
import com.manahive.contracts.policy.TemplateId
import com.manahive.contracts.policy.ProfileStateOverride
import com.manahive.contracts.policy.ProfileTransitionOverride
import com.manahive.harbor.NoticeCommand
import com.manahive.contracts.common.Channel
import com.manahive.kernel.*
import java.time.Duration
import java.time.Instant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SerializationRoundtripTest : FunSpec({

    // ── SceneEvent Roundtrip ─────────────────────────────────────────────────

    test("SceneEvent.TransitionDetected roundtrip") {
        val bed = BedId("bed-301")
        val night = NightId("night-301")
        val at = Instant.parse("2024-01-15T23:15:00Z")

        val original = SceneEvent.TransitionDetected(
            bed = bed,
            night = night,
            at = at,
            from = PersonState.Lying,
            to = PersonState.SittingInBed,
        )

        val json = original.toJson()
        val result = json.toSceneEvent()

        result.isSuccess shouldBe true
        val restored = result.getOrNull()!!
        restored.shouldBeInstanceOf<SceneEvent.TransitionDetected>()
        restored.bed shouldBe bed
        restored.night shouldBe night
        restored.at shouldBe at
        restored.from shouldBe PersonState.Lying
        restored.to shouldBe PersonState.SittingInBed
    }

    test("SceneEvent.DwellWarning roundtrip") {
        val bed = BedId("bed-301")
        val night = NightId("night-301")
        val at = Instant.parse("2024-01-15T23:15:00Z")
        val since = Instant.parse("2024-01-15T22:45:00Z")

        val original = SceneEvent.DwellWarning(
            bed = bed,
            night = night,
            at = at,
            state = PersonState.SittingInBed,
            threshold = Duration.ofMinutes(30),
            since = since,
        )

        val json = original.toJson()
        val result = json.toSceneEvent()

        result.isSuccess shouldBe true
        val restored = result.getOrNull()!!
        restored.shouldBeInstanceOf<SceneEvent.DwellWarning>()
        restored.state shouldBe PersonState.SittingInBed
        restored.threshold shouldBe Duration.ofMinutes(30)
        restored.since shouldBe since
    }

    test("SceneEvent.SignalLost roundtrip") {
        val bed = BedId("bed-301")
        val night = NightId("night-301")
        val at = Instant.parse("2024-01-15T23:15:00Z")
        val lastHeartbeat = Instant.parse("2024-01-15T23:00:00Z")

        val original = SceneEvent.SignalLost(
            bed = bed,
            night = night,
            at = at,
            monitor = MonitorId("CAMERA_MAIN"),
            lastHeartbeat = lastHeartbeat,
        )

        val json = original.toJson()
        val result = json.toSceneEvent()

        result.isSuccess shouldBe true
        val restored = result.getOrNull()!!
        restored.shouldBeInstanceOf<SceneEvent.SignalLost>()
        restored.monitor.value shouldBe "CAMERA_MAIN"
        restored.lastHeartbeat shouldBe lastHeartbeat
    }

    // ── SentinelSignal Roundtrip ─────────────────────────────────────────────

    test("SentinelSignal.EpisodeOpened roundtrip") {
        val bed = BedId("bed-301")
        val resident = ResidentId("jose")
        val at = Instant.parse("2024-01-15T23:15:00Z")
        val episode = EpisodeId("ep-001")
        val rule = RuleId("r-sitting")

        val original = SentinelSignal.EpisodeOpened(
            bed = bed,
            resident = resident,
            at = at,
            rulesFingerprint = "abc123",
            episode = episode,
            rule = rule,
            trigger = StateKind.SITTING_IN_BED,
            severity = Severity.WARNING,
            reversible = true,
            requiresNvr = false,
            confirmationWindow = Duration.ofMinutes(5),
        )

        val json = original.toJson()
        val result = json.toSentinelSignal()

        result.isSuccess shouldBe true
        val restored = result.getOrNull()!!
        restored.shouldBeInstanceOf<SentinelSignal.EpisodeOpened>()
        restored.episode shouldBe episode
        restored.rule shouldBe rule
        restored.severity shouldBe Severity.WARNING
        restored.reversible shouldBe true
    }

    test("SentinelSignal.EpisodeClosed roundtrip") {
        val bed = BedId("bed-301")
        val resident = ResidentId("jose")
        val at = Instant.parse("2024-01-15T23:32:00Z")
        val episode = EpisodeId("ep-001")

        val original = SentinelSignal.EpisodeClosed(
            bed = bed,
            resident = resident,
            at = at,
            rulesFingerprint = "abc123",
            episode = episode,
            cause = ClosureCause.AUTO_RECOVERY,
            gapDuration = Duration.ofMinutes(17),
        )

        val json = original.toJson()
        val result = json.toSentinelSignal()

        result.isSuccess shouldBe true
        val restored = result.getOrNull()!!
        restored.shouldBeInstanceOf<SentinelSignal.EpisodeClosed>()
        restored.episode shouldBe episode
        restored.cause shouldBe ClosureCause.AUTO_RECOVERY
    }

    // ── NoticeCommand Roundtrip ──────────────────────────────────────────────

    test("NoticeCommand.Dispatch roundtrip") {
        val id = NoticeId("notice-001")
        val channels = setOf(Channel.CONSOLE, Channel.PUSH)

        val original = NoticeCommand.Dispatch(id, channels)

        val json = original.toJson()
        val result = json.toNoticeCommand()

        result.isSuccess shouldBe true
        val restored = result.getOrNull()!!
        restored.shouldBeInstanceOf<NoticeCommand.Dispatch>()
        restored.id shouldBe id
        restored.channels shouldBe channels
    }

    // ── DagCatalog Roundtrip ─────────────────────────────────────────────────

    test("DagCatalog roundtrip via TOML") {
        val original = DagCatalog(
            version = CatalogVersion("2.1.0"),
            residentStates = mapOf(
                StateKind.SITTING_IN_BED to ResidentStateRule(
                    state = StateKind.SITTING_IN_BED,
                    warningAfter = Duration.ofMinutes(30),
                    alertAfter = Duration.ofMinutes(45),
                    severity = Severity.WARNING,
                    closureCondition = ClosureCondition.SAFE_ONLY,
                ),
                StateKind.IN_BATHROOM to ResidentStateRule(
                    state = StateKind.IN_BATHROOM,
                    warningAfter = Duration.ofMinutes(20),
                    alertAfter = Duration.ofMinutes(30),
                    severity = Severity.WARNING,
                    closureCondition = ClosureCondition.SAFE_ONLY,
                ),
            ),
            transitions = mapOf(
                TransitionKey(StateKind.LYING, StateKind.STANDING) to DagTransitionRule(
                    from = StateKind.LYING,
                    to = StateKind.STANDING,
                    hysteresis = Duration.ofMillis(2000),
                    recordBefore = Duration.ofMinutes(2),
                    recordAfter = Duration.ofMinutes(5),
                ),
            ),
            roomStates = mapOf(
                "staffEnters" to RoomStateRule(
                    event = "staffEnters",
                    closeEpisode = true,
                )
            ),
        )

        val toml = original.toToml()
        val result = toml.toCatalog()

        result.isSuccess shouldBe true
        val restored = result.getOrNull()!!
        restored.version.value shouldBe "2.1.0"
        restored.residentStates.size shouldBe 2
        restored.residentStates[StateKind.SITTING_IN_BED]?.alertAfter shouldBe Duration.ofMinutes(45)
        restored.transitions.size shouldBe 1
        restored.transitions[TransitionKey(StateKind.LYING, StateKind.STANDING)]?.hysteresis shouldBe Duration.ofMillis(2000)
    }

    // ── ResidentProfileConfig Roundtrip ──────────────────────────────────────

    test("ResidentProfileConfig roundtrip via TOML") {
        val original = ResidentProfileConfig(
            profile = AlarmProfile(
                residentId = ResidentId("jose"),
                riskLevel = RiskLevel.HIGH,
                mobilityAid = MobilityAid.NONE,
                autopilot = false,
                mode = PolicyMode.PRESET,
                templateId = TemplateId("standard"),
                overrides = emptyMap(),
                catalogVersion = CatalogVersion("2.1.0"),
                validFrom = Instant.now(),
            ),
            stateOverrides = mapOf(
                StateKind.SITTING_IN_BED to ProfileStateOverride(
                    alertAfter = Duration.ofMinutes(15),
                    severity = Severity.WARNING,
                ),
                StateKind.IN_BATHROOM to ProfileStateOverride(
                    alertAfter = Duration.ofMinutes(10),
                    severity = Severity.WARNING,
                ),
            ),
            transitionOverrides = mapOf(
                TransitionKey(StateKind.LYING, StateKind.STANDING) to ProfileTransitionOverride(
                    hysteresis = Duration.ofMillis(1000),
                ),
            ),
        )

        val toml = original.toToml()
        val result = toml.toProfile()

        result.isSuccess shouldBe true
        val restored = result.getOrNull()!!
        restored.profile.residentId.value shouldBe "jose"
        restored.profile.riskLevel shouldBe RiskLevel.HIGH
        restored.stateOverrides.size shouldBe 2
        restored.stateOverrides[StateKind.SITTING_IN_BED]?.alertAfter shouldBe Duration.ofMinutes(15)
        restored.transitionOverrides.size shouldBe 1
    }

    // ── Error Handling ───────────────────────────────────────────────────────

    test("Invalid JSON returns ParseError") {
        val invalidJson = "not valid json"
        val result = invalidJson.toSceneEvent()

        result.isFailure shouldBe true
        result.onError { error ->
            error.shouldBeInstanceOf<SerializationError>()
        }
    }

    test("Invalid state returns InvalidState error") {
        val json = """
        {
            "type": "TransitionDetected",
            "at": "2024-01-15T23:15:00Z",
            "bed": "bed-301",
            "night": "night-301",
            "from": "INVALID_STATE",
            "to": "SittingInBed"
        }
        """.trimIndent()

        val result = json.toSceneEvent()
        result.isFailure shouldBe true
    }

    test("Missing field returns MissingField error") {
        val json = """
        {
            "type": "TransitionDetected"
        }
        """.trimIndent()

        val result = json.toSceneEvent()
        result.isFailure shouldBe true
    }

    // ── Extension Functions ──────────────────────────────────────────────────

    test("Extension function toToml works") {
        val catalog = STANDARD_CATALOG
        val toml = catalog.toToml()

        toml shouldContain "version"
        toml shouldContain "resident"
    }

    test("Extension function saveTo and loadCatalog works") {
        val catalog = STANDARD_CATALOG
        val file = java.io.File.createTempFile("test-catalog", ".toml")
        file.deleteOnExit()

        catalog.saveTo(file)
        val loaded = file.loadCatalog()

        loaded.isSuccess shouldBe true
        loaded.getOrNull()?.version?.value shouldBe catalog.version.value
    }
})

// Test catalog
val STANDARD_CATALOG = DagCatalog(
    version = CatalogVersion("2.1.0"),
    residentStates = emptyMap(),
    transitions = emptyMap(),
    roomStates = emptyMap(),
)

// Helper for string containment
infix fun String.shouldContain(substring: String) {
    assert(this.contains(substring)) { "Expected '$this' to contain '$substring'" }
}
