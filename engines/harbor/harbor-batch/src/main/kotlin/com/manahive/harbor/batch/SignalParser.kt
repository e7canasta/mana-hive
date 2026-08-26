package com.manahive.harbor.batch

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.TriggerOn
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.sentinel.ClosureCause
import com.manahive.contracts.sentinel.SuppressionCause
import com.manahive.kernel.BedId
import com.manahive.kernel.EpisodeId
import com.manahive.kernel.EventRef
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Parses SentinelSignal from JSONL files (sentinel.out or JSONL).
 *
 * Fowler: "Eliminate Duplication" — single parser for both formats.
 * The format is identical; only the file name differs.
 */
object SignalParser {

    private val mapper = jacksonObjectMapper()

    /** Parse file and return raw signals (for data.out mode). */
    fun parse(file: File): List<SentinelSignal> {
        if (!file.exists()) throw HarborError.SignalsNotFound(file.absolutePath)

        return file.readLines()
            .mapIndexed { index, line -> parseLine(line, index + 1) }
            .filterNotNull()
    }

    /** Parse file and return signals with line numbers (for JSONL mode). */
    fun parseWithLineNumbers(file: File): List<ParsedSignal> {
        if (!file.exists()) throw HarborError.SignalsNotFound(file.absolutePath)

        return file.readLines()
            .mapIndexed { index, line -> parseLineWithNumber(line, index + 1) }
            .filterNotNull()
    }

    private fun parseLine(line: String, lineNumber: Int): SentinelSignal? {
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
            throw HarborError.ParseError(lineNumber, e.message ?: "unknown error")
        }
    }

    private fun parseLineWithNumber(line: String, lineNumber: Int): ParsedSignal? {
        val signal = parseLine(line, lineNumber) ?: return null
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
