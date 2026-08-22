package com.manahive.politica.batch

import com.manahive.contracts.policy.AlarmProfile
import com.manahive.contracts.policy.PolicyChangeDetected
import com.manahive.contracts.policy.RiskLevel
import com.manahive.contracts.policy.MobilityAid
import com.manahive.contracts.policy.PolicyMode
import com.manahive.contracts.policy.CatalogVersion
import com.manahive.kernel.ResidentId
import java.io.File
import java.time.Instant

/**
 * Parses events from a .dat file.
 *
 * Format (one event per line):
 * ```
 * resident maria risk HIGH mobility WALKER autopilot false mode PRESET at 2026-08-21T03:00:00Z
 * resident jose risk LOW mobility NONE autopilot true mode PRESET at 2026-08-21T03:00:01Z
 * ```
 */
object PolicyEventParser {

    fun parse(file: File): List<PolicyChangeDetected> =
        file.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { parseLine(it) }

    private fun parseLine(line: String): PolicyChangeDetected {
        val tokens = line.trim().split("\\s+".toRegex())
        require(tokens.size >= 2 && tokens.size % 2 == 0) {
            "Expected key-value pairs (even number of tokens), got ${tokens.size}: $line"
        }
        val map = mutableMapOf<String, String>()
        var i = 0
        while (i < tokens.size) {
            map[tokens[i]] = tokens[i + 1]
            i += 2
        }

        val residentId = map["resident"] ?: error("Missing 'resident' in: $line")
        val riskLevel = RiskLevel.valueOf(map["risk"] ?: "LOW")
        val mobilityAid = MobilityAid.valueOf(map["mobility"] ?: "NONE")
        val autopilot = map["autopilot"]?.toBooleanStrict() ?: false
        val mode = PolicyMode.valueOf(map["mode"] ?: "PRESET")
        val at = Instant.parse(map["at"] ?: error("Missing 'at' in: $line"))

        return PolicyChangeDetected(
            residentId = ResidentId(residentId),
            at = at,
            snapshot = AlarmProfile(
                residentId = ResidentId(residentId),
                riskLevel = riskLevel,
                mobilityAid = mobilityAid,
                autopilot = autopilot,
                mode = mode,
                templateId = null,
                overrides = emptyMap(),
                catalogVersion = CatalogVersion("1.0.0"),
                validFrom = at,
            ),
        )
    }
}
