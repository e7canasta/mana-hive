package com.manahive.recorder.batch

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.scene.NightSummary
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.scene.personStateFromKind
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.TriggerOn
import com.manahive.contracts.sentinel.ClosureCause
import com.manahive.contracts.sentinel.SuppressionCause
import com.manahive.kernel.BedId
import com.manahive.kernel.EpisodeId
import com.manahive.kernel.EventRef
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import com.manahive.kernel.StaffId
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Parses SceneEvent and SentinelSignal from JSONL/out files.
 *
 * Same parser as Harbor batch — shared contract.
 */
object SignalParser {

    private val mapper = jacksonObjectMapper()

    // ── SceneEvent Parsing ─────────────────────────────────────────

    /** Parse Scene.out file (one SceneEvent per line). */
    fun parseSceneEvents(file: File): List<SceneEvent> {
        if (!file.exists()) throw IllegalArgumentException("File not found: ${file.absolutePath}")

        return file.readLines()
            .mapIndexed { index, line -> parseSceneEventLine(line, index + 1) }
            .filterNotNull()
    }

    private fun parseSceneEventLine(line: String, lineNumber: Int): SceneEvent? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        return try {
            val json = mapper.readTree(trimmed)
            val type = json.get("type")?.asText() ?: return null

            when (type) {
                "NightOpened" -> parseNightOpened(json)
                "TransitionDetected" -> parseTransitionDetected(json)
                "DwellWarning" -> parseDwellWarning(json)
                "DwellExceeded" -> parseDwellExceeded(json)
                "SceneStateChanged" -> parseSceneStateChanged(json)
                "SceneDwellWarning" -> parseSceneDwellWarning(json)
                "SceneDwellExceeded" -> parseSceneDwellExceeded(json)
                "StaffPresenceDetected" -> parseStaffPresenceDetected(json)
                "SignalLost" -> parseSignalLost(json)
                "SignalRecovered" -> parseSignalRecovered(json)
                "NightClosed" -> parseNightClosed(json)
                else -> null
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Parse error at line $lineNumber: ${e.message}")
        }
    }

    private fun parseNightOpened(json: com.fasterxml.jackson.databind.JsonNode): SceneEvent.NightOpened {
        return SceneEvent.NightOpened(
            bed = BedId(json.get("bed").asText()),
            night = NightId(json.get("night").asText()),
            at = Instant.parse(json.get("at").asText()),
            occupant = json.get("occupant")?.asText()?.let { ResidentId(it) },
            initialState = personStateFromKind(
                StateKind.valueOf(json.get("initialState")?.asText() ?: "UNKNOWN")
            ),
            stateSince = json.get("stateSince")?.asText()?.let { Instant.parse(it) } ?: Instant.parse(json.get("at").asText()),
        )
    }

    private fun parseTransitionDetected(json: com.fasterxml.jackson.databind.JsonNode): SceneEvent.TransitionDetected {
        return SceneEvent.TransitionDetected(
            bed = BedId(json.get("bed").asText()),
            night = NightId(json.get("night").asText()),
            at = Instant.parse(json.get("at").asText()),
            from = personStateFromKind(StateKind.valueOf(json.get("from").asText())),
            to = personStateFromKind(StateKind.valueOf(json.get("to").asText())),
        )
    }

    private fun parseDwellWarning(json: com.fasterxml.jackson.databind.JsonNode): SceneEvent.DwellWarning {
        return SceneEvent.DwellWarning(
            bed = BedId(json.get("bed").asText()),
            night = NightId(json.get("night").asText()),
            at = Instant.parse(json.get("at").asText()),
            state = personStateFromKind(StateKind.valueOf(json.get("state").asText())),
            threshold = Duration.parse(json.get("threshold").asText()),
            since = Instant.parse(json.get("since").asText()),
        )
    }

    private fun parseDwellExceeded(json: com.fasterxml.jackson.databind.JsonNode): SceneEvent.DwellExceeded {
        return SceneEvent.DwellExceeded(
            bed = BedId(json.get("bed").asText()),
            night = NightId(json.get("night").asText()),
            at = Instant.parse(json.get("at").asText()),
            state = personStateFromKind(StateKind.valueOf(json.get("state").asText())),
            threshold = Duration.parse(json.get("threshold").asText()),
            since = Instant.parse(json.get("since").asText()),
        )
    }

    private fun parseSceneStateChanged(json: com.fasterxml.jackson.databind.JsonNode): SceneEvent.SceneStateChanged {
        return SceneEvent.SceneStateChanged(
            bed = BedId(json.get("bed").asText()),
            night = NightId(json.get("night").asText()),
            at = Instant.parse(json.get("at").asText()),
            field = json.get("field").asText(),
            from = json.get("from").asText(),
            to = json.get("to").asText(),
        )
    }

    private fun parseSceneDwellWarning(json: com.fasterxml.jackson.databind.JsonNode): SceneEvent.SceneDwellWarning {
        return SceneEvent.SceneDwellWarning(
            bed = BedId(json.get("bed").asText()),
            night = NightId(json.get("night").asText()),
            at = Instant.parse(json.get("at").asText()),
            field = json.get("field").asText(),
            threshold = Duration.parse(json.get("threshold").asText()),
            since = Instant.parse(json.get("since").asText()),
        )
    }

    private fun parseSceneDwellExceeded(json: com.fasterxml.jackson.databind.JsonNode): SceneEvent.SceneDwellExceeded {
        return SceneEvent.SceneDwellExceeded(
            bed = BedId(json.get("bed").asText()),
            night = NightId(json.get("night").asText()),
            at = Instant.parse(json.get("at").asText()),
            field = json.get("field").asText(),
            threshold = Duration.parse(json.get("threshold").asText()),
            since = Instant.parse(json.get("since").asText()),
        )
    }

    private fun parseStaffPresenceDetected(json: com.fasterxml.jackson.databind.JsonNode): SceneEvent.StaffPresenceDetected {
        return SceneEvent.StaffPresenceDetected(
            bed = BedId(json.get("bed").asText()),
            night = NightId(json.get("night").asText()),
            at = Instant.parse(json.get("at").asText()),
            staff = json.get("staff")?.asText()?.let { StaffId(it) },
        )
    }

    private fun parseSignalLost(json: com.fasterxml.jackson.databind.JsonNode): SceneEvent.SignalLost {
        return SceneEvent.SignalLost(
            bed = BedId(json.get("bed").asText()),
            night = NightId(json.get("night").asText()),
            at = Instant.parse(json.get("at").asText()),
            monitor = MonitorId(json.get("monitor").asText()),
            lastHeartbeat = Instant.parse(json.get("lastHeartbeat").asText()),
        )
    }

    private fun parseSignalRecovered(json: com.fasterxml.jackson.databind.JsonNode): SceneEvent.SignalRecovered {
        return SceneEvent.SignalRecovered(
            bed = BedId(json.get("bed").asText()),
            night = NightId(json.get("night").asText()),
            at = Instant.parse(json.get("at").asText()),
            monitor = MonitorId(json.get("monitor").asText()),
        )
    }

    private fun parseNightClosed(json: com.fasterxml.jackson.databind.JsonNode): SceneEvent.NightClosed {
        val summaryNode = json.get("summary")
        return SceneEvent.NightClosed(
            bed = BedId(json.get("bed").asText()),
            night = NightId(json.get("night").asText()),
            at = Instant.parse(json.get("at").asText()),
            summary = NightSummary(
                transitions = summaryNode?.get("transitions")?.asInt() ?: 0,
                minutesUnknown = summaryNode?.get("minutesUnknown")?.asLong() ?: 0L,
                episodes = summaryNode?.get("episodes")?.asInt() ?: 0,
            ),
        )
    }

    // ── SentinelSignal Parsing ────────────────────────────────────

    /** Parse Sentinel.out file (one SentinelSignal per line). */
    fun parseSentinelSignals(file: File): List<SentinelSignal> {
        if (!file.exists()) throw IllegalArgumentException("File not found: ${file.absolutePath}")

        return file.readLines()
            .mapIndexed { index, line -> parseSentinelLine(line, index + 1) }
            .filterNotNull()
    }

    /** Parse file and return raw signals. */
    fun parse(file: File): List<SentinelSignal> {
        if (!file.exists()) throw IllegalArgumentException("File not found: ${file.absolutePath}")

        return file.readLines()
            .mapIndexed { index, line -> parseSentinelLine(line, index + 1) }
            .filterNotNull()
    }

    /** Parse file and return signals with line numbers. */
    fun parseWithLineNumbers(file: File): List<ParsedSignal> {
        if (!file.exists()) throw IllegalArgumentException("File not found: ${file.absolutePath}")

        return file.readLines()
            .mapIndexed { index, line -> parseSentinelLineWithNumber(line, index + 1) }
            .filterNotNull()
    }

    private fun parseSentinelLine(line: String, lineNumber: Int): SentinelSignal? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        return try {
            val json = mapper.readTree(trimmed)
            val type = json.get("type")?.asText() ?: return null

            when (type) {
                "EpisodeOpened" -> parseEpisodeOpened(json)
                "EpisodeClosed" -> parseEpisodeClosed(json)
                "AutoRecovery" -> parseAutoRecovery(json)
                "UmbrellaEvent" -> parseUmbrellaEvent(json)
                "SuppressedWithRecord" -> parseSuppressed(json)
                else -> null
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Parse error at line $lineNumber: ${e.message}")
        }
    }

    private fun parseSentinelLineWithNumber(line: String, lineNumber: Int): ParsedSignal? {
        val signal = parseSentinelLine(line, lineNumber) ?: return null
        return ParsedSignal(signal, lineNumber)
    }

    private fun parseEpisodeOpened(json: com.fasterxml.jackson.databind.JsonNode): SentinelSignal.EpisodeOpened {
        return SentinelSignal.EpisodeOpened(
            bed = BedId(json.get("bed").asText()),
            resident = json.get("resident")?.asText()?.let { ResidentId(it) },
            at = Instant.parse(json.get("at").asText()),
            rulesFingerprint = json.get("rulesFingerprint")?.asText() ?: "",
            episode = EpisodeId(json.get("episode").asText()),
            rule = RuleId(json.get("rule").asText()),
            trigger = StateKind.valueOf(json.get("trigger").asText()),
            severity = Severity.valueOf(json.get("severity").asText()),
            reversible = json.get("reversible")?.asBoolean() ?: true,
            requiresNvr = json.get("nvr")?.asBoolean() ?: false,
            confirmationWindow = json.get("confirmationWindow")?.asText()?.let {
                Duration.parse(it)
            },
        )
    }

    private fun parseEpisodeClosed(json: com.fasterxml.jackson.databind.JsonNode): SentinelSignal.EpisodeClosed {
        return SentinelSignal.EpisodeClosed(
            bed = BedId(json.get("bed").asText()),
            resident = json.get("resident")?.asText()?.let { ResidentId(it) },
            at = Instant.parse(json.get("at").asText()),
            rulesFingerprint = json.get("rulesFingerprint")?.asText() ?: "",
            episode = EpisodeId(json.get("episode").asText()),
            cause = ClosureCause.valueOf(json.get("cause").asText()),
            gapDuration = json.get("gapDuration")?.let { node ->
                if (node.isNull) null else Duration.parse(node.asText())
            },
        )
    }

    private fun parseAutoRecovery(json: com.fasterxml.jackson.databind.JsonNode): SentinelSignal.AutoRecovery {
        return SentinelSignal.AutoRecovery(
            bed = BedId(json.get("bed").asText()),
            resident = json.get("resident")?.asText()?.let { ResidentId(it) },
            at = Instant.parse(json.get("at").asText()),
            rulesFingerprint = json.get("rulesFingerprint")?.asText() ?: "",
            episode = EpisodeId(json.get("episode").asText()),
            reversible = json.get("reversible")?.asBoolean() ?: true,
            requiresConfirmation = json.get("requiresConfirmation")?.asBoolean() ?: false,
        )
    }

    private fun parseUmbrellaEvent(json: com.fasterxml.jackson.databind.JsonNode): SentinelSignal.UmbrellaEvent {
        return SentinelSignal.UmbrellaEvent(
            bed = BedId(json.get("bed").asText()),
            resident = json.get("resident")?.asText()?.let { ResidentId(it) },
            at = Instant.parse(json.get("at").asText()),
            rulesFingerprint = json.get("rulesFingerprint")?.asText() ?: "",
            episode = EpisodeId(json.get("episode").asText()),
            state = StateKind.valueOf(json.get("state").asText()),
            // Ausente en lineas escritas antes de que UmbrellaEvent distinguiera
            // familias: esas sólo podían venir de una transición o un dwell, y en
            // ambas el residente ESTA en el estado. DWELL preserva esa lectura.
            triggerOn = json.get("triggerOn")?.asText()?.let { TriggerOn.valueOf(it) }
                ?: TriggerOn.DWELL,
            originalSeverity = Severity.valueOf(json.get("originalSeverity").asText()),
        )
    }

    private fun parseSuppressed(json: com.fasterxml.jackson.databind.JsonNode): SentinelSignal.SuppressedWithRecord {
        return SentinelSignal.SuppressedWithRecord(
            bed = BedId(json.get("bed").asText()),
            resident = json.get("resident")?.asText()?.let { ResidentId(it) },
            at = Instant.parse(json.get("at").asText()),
            rulesFingerprint = json.get("rulesFingerprint")?.asText() ?: "",
            rule = RuleId(json.get("rule").asText()),
            cause = SuppressionCause.valueOf(json.get("cause").asText()),
            evidence = EventRef(
                stream = json.get("evidenceStream")?.asText() ?: "unknown",
                seq = json.get("evidenceSeq")?.asLong() ?: 0L,
            ),
        )
    }
}

data class ParsedSignal(
    val signal: SentinelSignal,
    val lineNumber: Int,
)
