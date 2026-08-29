package com.manahive.recorder

import com.manahive.kernel.EngineVersion
import com.manahive.kernel.Explained
import com.manahive.kernel.ExplanationStep
import java.time.Instant

/**
 * Pure implementation of [RecorderEngine].
 *
 * Created with [RecordingCalibration] — immutable for the engine's lifetime.
 * State flows through [RecordingLedger] — the shell persists it, this engine never does.
 *
 * Fowler: "Extract Method" — each rule type has its own handler.
 * Vernon: "Domain Service" — coordinates across aggregates without owning them.
 */
internal class RecorderEngineImpl(
    private val calibration: RecordingCalibration,
) : RecorderEngine {

    override val version: EngineVersion = EngineVersion(
        name = "recorder",
        semver = "0.1.0",
        buildFingerprint = "dev",
    )

    override fun evaluate(
        trigger: RecordingTrigger,
        ledger: RecordingLedger,
        now: Instant,
    ): Explained<RecordingVerdict> {
        val explanation = mutableListOf<ExplanationStep>()
        val commands = mutableListOf<RecordingCommand>()
        val evidenceRecords = mutableListOf<EvidenceRecord>()
        var currentLedger = ledger

        // Find matching rules
        val matchingRules = calibration.matchingRules(trigger)

        if (matchingRules.isEmpty()) {
            explanation.add(
                ExplanationStep(
                    rule = "no-matching-rule",
                    observed = trigger::class.simpleName ?: "unknown",
                    conclusion = "No recording rule matches this trigger",
                )
            )
            // Y sigue: grabar y dejar evidencia son decisiones distintas.
            //
            // Acá había un `return` que devolvía evidencia vacía, así que las
            // reglas de evidencia sólo se evaluaban cuando además matcheaba una
            // regla de grabación. Un episodio WARNING —que el catálogo quiere
            // que deje evidencia pero no que encienda la cámara— no dejaba
            // ningún rastro. El director pedía una cosa y el motor hacía otra.
        }

        // Process each matching rule
        for (rule in matchingRules) {
            val result = processRule(rule, trigger, currentLedger, now)
            commands.addAll(result.commands)
            evidenceRecords.addAll(result.evidenceRecords)
            currentLedger = result.ledger
            explanation.addAll(result.explanation)
        }

        // Find matching evidence rules
        val matchingEvidenceRules = calibration.matchingEvidenceRules(trigger)
        for (rule in matchingEvidenceRules) {
            val result = processEvidenceRule(rule, trigger, currentLedger, now)
            evidenceRecords.addAll(result.evidenceRecords)
            explanation.addAll(result.explanation)
        }

        return Explained(
            value = RecordingVerdict(commands = commands, evidenceRecords = evidenceRecords, ledger = currentLedger),
            explanation = explanation,
        )
    }

    private fun processRule(
        rule: RecordingRule,
        trigger: RecordingTrigger,
        ledger: RecordingLedger,
        now: Instant,
    ): RuleResult {
        val explanation = mutableListOf<ExplanationStep>()
        var currentLedger = ledger

        val window = rule.recordingWindow
        val startTime = window.startTime(trigger.at)
        val endTime = window.endTime(trigger.at)
        val context = trigger.context

        // Check if we're already recording for this bed/context
        val activeRecording = currentLedger.findActive(trigger.bed, context.episode)
        if (activeRecording != null) {
            explanation.add(
                ExplanationStep(
                    rule = rule.id,
                    observed = "already-recording",
                    conclusion = "Already recording for this bed/context, skipping",
                )
            )
            return RuleResult(emptyList(), emptyList(), currentLedger, explanation)
        }

        // Issue start + stop commands for each monitor
        val commands = mutableListOf<RecordingCommand>()
        for (monitor in rule.monitors) {
            val target = RecordingTarget(trigger.bed, monitor)
            val request = RecordingRequest(
                rule = rule,
                target = target,
                context = context,
                startTime = startTime,
                endTime = endTime,
                now = now,
            )
            val (startCmd, stopCmd) = issueRecording(request)
            commands.add(startCmd)
            commands.add(stopCmd)
            currentLedger = currentLedger.startRecording(startCmd)
            currentLedger = currentLedger.scheduleStop(stopCmd)

            explanation.add(
                ExplanationStep(
                    rule = rule.id,
                    observed = "${trigger.bed.value}:${monitor.value}",
                    conclusion = "Start recording at $startTime (window=${window.totalDuration()})",
                )
            )
        }

        return RuleResult(commands, emptyList(), currentLedger, explanation)
    }

    /**
     * Issue start + stop commands for a single monitor.
     *
     * Fowler: "Introduce Parameter Object" — uses RecordingRequest instead of 6 parameters.
     */
    private fun issueRecording(request: RecordingRequest): Pair<RecordingStarted, RecordingStopped> {
        val config = RecordingConfig(request.startTime, request.rule.quality)
        val started = RecordingStarted(target = request.target, config = config, context = request.context, at = request.now)
        val stopped = RecordingStopped(target = request.target, end = request.endTime, context = request.context, at = request.now)
        return started to stopped
    }

    private fun processEvidenceRule(
        rule: EvidenceRule,
        trigger: RecordingTrigger,
        ledger: RecordingLedger,
        now: Instant,
    ): RuleResult {
        val explanation = mutableListOf<ExplanationStep>()
        val evidenceRecords = mutableListOf<EvidenceRecord>()

        // Get the episode ID from trigger context
        val episodeId = trigger.context.episode

        // Get the monitors from active recordings for this bed
        val activeRecordings = ledger.findActiveForBed(trigger.bed)
        val monitors = activeRecordings.map { it.started.target.monitor }.distinct()

        // Create evidence record based on the trigger type
        val evidenceRecord = when (rule.evidenceType) {
            EvidenceType.INCIDENT -> EvidenceRecordingStarted(
                bed = trigger.bed,
                episode = episodeId,
                monitors = monitors,
                start = now,
                trigger = trigger::class.simpleName ?: "unknown",
                at = now,
            )
            EvidenceType.CLIP -> if (episodeId != null) {
                val window = rule.window
                EvidenceClipCreated(
                    bed = trigger.bed,
                    episode = episodeId,
                    monitors = monitors,
                    start = window.startTime(now),
                    end = window.endTime(now),
                    at = now,
                )
            } else {
                null
            }
        }

        if (evidenceRecord != null) {
            evidenceRecords.add(evidenceRecord)
            explanation.add(
                ExplanationStep(
                    rule = rule.id,
                    observed = trigger::class.simpleName ?: "unknown",
                    conclusion = "Emit evidence record: ${rule.evidenceType}",
                )
            )
        }

        return RuleResult(evidenceRecords = evidenceRecords, ledger = ledger, explanation = explanation)
    }

    private data class RuleResult(
        val commands: List<RecordingCommand> = emptyList(),
        val evidenceRecords: List<EvidenceRecord> = emptyList(),
        val ledger: RecordingLedger,
        val explanation: List<ExplanationStep>,
    )
}
