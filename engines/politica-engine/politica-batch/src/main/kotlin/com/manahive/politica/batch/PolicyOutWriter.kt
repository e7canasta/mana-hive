package com.manahive.politica.batch

import com.manahive.politica.PolicyChangeResult
import java.io.File

/**
 * Writes PolicyChangeResult to .out for diff.
 *
 * Format:
 * ```
 * CALIBRATION_CHANGED resident=maria source=CATALOG dwell=STANDING:PT4M/PT5M
 * ```
 */
object PolicyOutWriter {

    fun write(results: List<PolicyChangeResult>, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(results.joinToString("\n") { formatResult(it) } + "\n")
    }

    private fun formatResult(result: PolicyChangeResult): String {
        val eventType = result.emittedEvents.firstOrNull()?.let { it::class.simpleName } ?: "UNKNOWN"
        val dwell = result.calibration.dwellThresholds.entries.joinToString(",") { (k, v) ->
            "${k.name}:${v.warning}/${v.exceeded}"
        }
        return "CALIBRATION_CHANGED resident=${result.residentId.value} event=$eventType dwell=$dwell"
    }
}
