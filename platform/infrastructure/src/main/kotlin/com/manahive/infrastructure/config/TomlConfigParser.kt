package com.manahive.infrastructure.config

import com.manahive.contracts.policy.ConfidenceConfig
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.RecordingQuality
import com.manahive.contracts.policy.StaffAssistMode
import com.manahive.contracts.scene.StateKind
import java.io.File
import java.time.Duration

/**
 * TOML configuration parser for local engine configuration.
 *
 * Parses TOML files from /etc/mana-hive/residents/ directory.
 * Each resident has a separate TOML file named by their ID.
 *
 * Fowler: "Data Mapper" — maps between TOML structure and domain model.
 *
 * Vernon: "Infrastructure Service" — provides technical capability
 * for parsing TOML files.
 *
 * Validation rules are enforced here, not in the DTO.
 */
public object TomlConfigParser {
    /**
     * Parse a TOML file into a LocalConfig.
     *
     * @param file The TOML file to parse
     * @return The parsed LocalConfig
     * @throws IllegalArgumentException if file cannot be parsed
     */
    public fun parse(file: File): LocalConfig {
        require(file.exists()) { "TOML file does not exist: ${file.absolutePath}" }
        require(file.isFile) { "Path is not a file: ${file.absolutePath}" }

        val content = file.readText()
        return parse(content)
    }

    /**
     * Parse a TOML string into a LocalConfig.
     *
     * @param tomlContent The TOML content to parse
     * @return The parsed LocalConfig
     * @throws IllegalArgumentException if content cannot be parsed or validation fails
     */
    public fun parse(tomlContent: String): LocalConfig {
        require(tomlContent.isNotBlank()) { "TOML content must not be blank" }

        val lines = tomlContent.lines()
        val sections = mutableMapOf<String, MutableMap<String, Any>>()
        var currentSection = ""

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            if (trimmed.startsWith("[")) {
                val sectionName = trimmed.removePrefix("[").removeSuffix("]").trim()
                currentSection = sectionName
                sections[currentSection] = mutableMapOf()
            } else if (trimmed.contains("=")) {
                val parts = trimmed.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim().removeSurrounding("\"")
                    sections[currentSection]?.set(key, value)
                }
            }
        }

        return sectionsToLocalConfig(sections)
    }

    /**
     * Parse duration string (e.g., "90s", "5m", "2h", "500ms").
     *
     * @param durationStr The duration string
     * @return The parsed Duration
     * @throws IllegalArgumentException if duration string is invalid
     */
    public fun parseDuration(durationStr: String): Duration {
        require(durationStr.isNotBlank()) { "Duration string must not be blank" }

        val regex = Regex("^(-?\\d+)(ms|s|m|h|d)$")
        val match = regex.matchEntire(durationStr)
            ?: throw IllegalArgumentException("Invalid duration format: $durationStr")

        val value = match.groupValues[1].toLong()
        val unit = match.groupValues[2]

        return when (unit) {
            "ms" -> Duration.ofMillis(value)
            "s" -> Duration.ofSeconds(value)
            "m" -> Duration.ofMinutes(value)
            "h" -> Duration.ofHours(value)
            "d" -> Duration.ofDays(value)
            else -> throw IllegalArgumentException("Unknown duration unit: $unit")
        }
    }

    /**
     * Parse StateKind from string.
     *
     * @param stateStr The state kind string
     * @return The parsed StateKind
     * @throws IllegalArgumentException if state string is invalid
     */
    public fun parseStateKind(stateStr: String): StateKind {
        require(stateStr.isNotBlank()) { "State string must not be blank" }

        return try {
            StateKind.valueOf(stateStr)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid state kind: $stateStr", e)
        }
    }

    /**
     * Parse StaffAssistMode from string.
     *
     * @param modeStr The staff assist mode string
     * @return The parsed StaffAssistMode
     * @throws IllegalArgumentException if mode string is invalid
     */
    public fun parseStaffAssistMode(modeStr: String): StaffAssistMode {
        require(modeStr.isNotBlank()) { "Mode string must not be blank" }

        return try {
            StaffAssistMode.valueOf(modeStr)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid staff assist mode: $modeStr", e)
        }
    }

    /**
     * Parse RecordingQuality from string.
     *
     * @param qualityStr The recording quality string
     * @return The parsed RecordingQuality
     * @throws IllegalArgumentException if quality string is invalid
     */
    public fun parseRecordingQuality(qualityStr: String): RecordingQuality {
        require(qualityStr.isNotBlank()) { "Quality string must not be blank" }

        return try {
            RecordingQuality.valueOf(qualityStr)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid recording quality: $qualityStr", e)
        }
    }

    /**
     * Convert parsed sections to LocalConfig with validation.
     *
     * @param sections The parsed sections
     * @return The validated LocalConfig
     * @throws IllegalArgumentException if validation fails
     */
    private fun sectionsToLocalConfig(sections: Map<String, Map<String, Any>>): LocalConfig {
        val residentSection = sections["resident"]
            ?: throw IllegalArgumentException("Missing [resident] section")

        val name = residentSection["name"] as? String
            ?: throw IllegalArgumentException("Missing resident.name")
        val bed = residentSection["bed"] as? String
            ?: throw IllegalArgumentException("Missing resident.bed")

        // Validate resident info
        require(name.isNotBlank()) { "Resident name must not be blank" }
        require(bed.isNotBlank()) { "Bed must not be blank" }

        val calibrationSection = sections["calibration"] ?: emptyMap()
        val calibration = parseCalibrationSection(calibrationSection, sections)

        val escalationSection = sections["escalation"] ?: emptyMap()
        val escalation = parseEscalationSection(escalationSection)

        val recordingSection = sections["recording"] ?: emptyMap()
        val recording = parseRecordingSection(recordingSection)

        return LocalConfig(
            resident = LocalConfig.ResidentInfo(name = name, bed = bed),
            calibration = calibration,
            escalation = escalation,
            recording = recording,
        )
    }

    /**
     * Parse calibration section with validation.
     *
     * @param section The calibration section
     * @param sections All sections for nested lookups
     * @return The validated CalibrationConfig
     * @throws IllegalArgumentException if validation fails
     */
    private fun parseCalibrationSection(
        section: Map<String, Any>,
        sections: Map<String, Map<String, Any>>,
    ): LocalConfig.CalibrationConfig {
        val heartbeatTimeoutStr = section["heartbeatTimeout"] as? String ?: "90s"
        val heartbeatTimeout = parseDuration(heartbeatTimeoutStr)

        // Validate heartbeat timeout
        require(heartbeatTimeout >= Duration.ZERO) { "Heartbeat timeout must not be negative" }

        // Parse dwell thresholds
        val dwellThresholds = mutableMapOf<StateKind, DwellThreshold>()
        for ((key, _) in sections) {
            if (key.startsWith("calibration.dwell.")) {
                val stateStr = key.removePrefix("calibration.dwell.")
                val state = try {
                    parseStateKind(stateStr)
                } catch (e: IllegalArgumentException) {
                    continue
                }

                val dwellSection = sections[key] ?: continue
                val warningStr = dwellSection["warning"] as? String ?: continue
                val exceededStr = dwellSection["exceeded"] as? String ?: continue

                val warning = parseDuration(warningStr)
                val exceeded = parseDuration(exceededStr)

                // Validate dwell thresholds
                require(warning < exceeded) {
                    "Warning ($warning) must be less than exceeded ($exceeded) for $state"
                }

                dwellThresholds[state] = DwellThreshold(warning = warning, exceeded = exceeded)
            }
        }

        // Parse confidence
        val minConfidence = mutableMapOf<StateKind, Double>()
        for ((key, _) in sections) {
            if (key.startsWith("calibration.confidence.")) {
                val stateStr = key.removePrefix("calibration.confidence.")
                val state = try {
                    parseStateKind(stateStr)
                } catch (e: IllegalArgumentException) {
                    continue
                }

                val confidenceSection = sections[key] ?: continue
                val min = when (val minValue = confidenceSection["min"]) {
                    is Double -> minValue
                    is String -> minValue.toDoubleOrNull() ?: continue
                    else -> continue
                }

                // Validate confidence range
                require(min in 0.0..1.0) { "Confidence must be in 0.0..1.0, got $min for $state" }

                minConfidence[state] = min
            }
        }

        val confidence = ConfidenceConfig(
            minConfidence = minConfidence.toMap(),
            heartbeatTimeout = heartbeatTimeout,
        )

        return LocalConfig.CalibrationConfig(
            heartbeatTimeout = heartbeatTimeout,
            dwellThresholds = dwellThresholds.toMap(),
            confidence = confidence,
        )
    }

    /**
     * Parse escalation section with validation.
     *
     * @param section The escalation section
     * @return The validated EscalationConfig
     * @throws IllegalArgumentException if validation fails
     */
    private fun parseEscalationSection(section: Map<String, Any>): LocalConfig.EscalationConfig {
        val escalationDelayStr = section["escalationDelay"] as? String ?: "5m"
        val escalationDelay = parseDuration(escalationDelayStr)

        // Validate escalation delay
        require(escalationDelay >= Duration.ZERO) { "Escalation delay must not be negative" }

        val staffAssistStr = section["staffAssist"] as? String ?: "NONE"
        val staffAssist = parseStaffAssistMode(staffAssistStr)

        val maxLevel = when (val value = section["maxLevel"]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 3
            else -> 3
        }

        // Validate max level
        require(maxLevel > 0) { "Max level must be positive" }

        return LocalConfig.EscalationConfig(
            escalationDelay = escalationDelay,
            staffAssist = staffAssist,
            maxLevel = maxLevel,
        )
    }

    /**
     * Parse recording section with validation.
     *
     * @param section The recording section
     * @return The validated RecordingConfig
     * @throws IllegalArgumentException if validation fails
     */
    private fun parseRecordingSection(section: Map<String, Any>): LocalConfig.RecordingConfig {
        val enabled = when (val value = section["enabled"]) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull() ?: false
            else -> false
        }

        val preEventWindowStr = section["preEventWindow"] as? String ?: "30s"
        val preEventWindow = parseDuration(preEventWindowStr)

        // Validate pre-event window
        require(preEventWindow >= Duration.ZERO) { "Pre-event window must not be negative" }

        val postEventWindowStr = section["postEventWindow"] as? String ?: "2m"
        val postEventWindow = parseDuration(postEventWindowStr)

        // Validate post-event window
        require(postEventWindow >= Duration.ZERO) { "Post-event window must not be negative" }

        val qualityStr = section["quality"] as? String ?: "MEDIUM"
        val quality = parseRecordingQuality(qualityStr)

        return LocalConfig.RecordingConfig(
            enabled = enabled,
            preEventWindow = preEventWindow,
            postEventWindow = postEventWindow,
            quality = quality,
        )
    }
}
