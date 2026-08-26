package com.manahive.politica.bdd

import com.manahive.contracts.policy.*
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import com.manahive.politica.PolicyResolver
import java.time.Duration
import java.time.Instant

// ── Context ──────────────────────────────────────────────────────────────────

data class PoliticaContext(
    val catalog: AlarmCatalog,
    val profile: AlarmProfile,
)

// ── Assertion results ────────────────────────────────────────────────────────

data class PoliticaScenarioCheck(
    val description: String,
    val passed: Boolean,
    val error: String? = null,
)

data class PoliticaScenarioResult(
    val name: String,
    val calibration: PolicyCalibration,
    val source: PolicySource,
    val checks: List<PoliticaScenarioCheck>,
) {
    val passed: Boolean get() = checks.all { it.passed }

    fun report() {
        println("  ── Scenario: $name ──")
        println("  Source: $source")
        println("  Hysteresis: ${calibration.scene.hysteresis.size} transitions")
        println("  Dwell:      ${calibration.scene.dwellThresholds.size} states")
        println()
        checks.forEach { check ->
            val status = if (check.passed) "✅" else "❌"
            println("  $status ${check.description}")
            if (check.error != null) println("     ${check.error}")
        }
        println()
    }
}

// ── Scenario Builder ─────────────────────────────────────────────────────────

class PoliticaScenarioBuilder(private val ctx: PoliticaContext) {
    private val assertions = mutableListOf<Pair<String, () -> Unit>>()
    private var result: PolicyCalibration? = null
    private var source: PolicySource? = null

    // ── Assertion builders ────────────────────────────────────────────────────

    fun thenExpectHysteresis(from: StateKind, to: StateKind, expected: Duration) {
        assertions.add("hysteresis $from→$to = $expected" to {
            val cal = result!!
            val actual = cal.scene.hysteresis[TransitionKey(from, to)]
            check(actual == expected) { "Expected $expected, got $actual" }
        })
    }

    fun thenExpectDwell(state: StateKind, warning: Duration, exceeded: Duration) {
        assertions.add("dwell $state warning=$warning exceeded=$exceeded" to {
            val cal = result!!
            val actual = cal.scene.dwellThresholds[state]
            check(actual != null) { "No dwell threshold for $state" }
            check(actual!!.warning == warning) { "Expected warning $warning, got ${actual.warning}" }
            check(actual.exceeded == exceeded) { "Expected exceeded $exceeded, got ${actual.exceeded}" }
        })
    }

    fun thenExpectDwellNotPresent(state: StateKind) {
        assertions.add("dwell $state not present" to {
            val cal = result!!
            check(!cal.scene.dwellThresholds.containsKey(state)) { "Dwell threshold for $state should not be present" }
        })
    }

    fun thenExpectSource(expected: PolicySource) {
        assertions.add("source = $expected" to {
            check(source == expected) { "Expected source $expected, got $source" }
        })
    }

    fun thenExpectConfidenceHeartbeat(expected: Duration) {
        assertions.add("heartbeat timeout = $expected" to {
            val cal = result!!
            check(cal.scene.confidence.heartbeatTimeout == expected) {
                "Expected heartbeat $expected, got ${cal.scene.confidence.heartbeatTimeout}"
            }
        })
    }

    fun thenExpectHysteresisCount(count: Int) {
        assertions.add("hysteresis count = $count" to {
            val cal = result!!
            check(cal.scene.hysteresis.size == count) { "Expected $count hysteresis, got ${cal.scene.hysteresis.size}" }
        })
    }

    fun thenExpectDwellCount(count: Int) {
        assertions.add("dwell count = $count" to {
            val cal = result!!
            check(cal.scene.dwellThresholds.size == count) { "Expected $count dwell, got ${cal.scene.dwellThresholds.size}" }
        })
    }

    // ── Run ───────────────────────────────────────────────────────────────────

    fun run(): PoliticaScenarioResult {
        val resolved = PolicyResolver.resolve(ctx.catalog, ctx.profile)
        val resolvedSource = PolicyResolver.resolveSource(ctx.profile)
        result = resolved
        source = resolvedSource

        val checks = assertions.map { (desc, check) ->
            try {
                check()
                PoliticaScenarioCheck(desc, true)
            } catch (e: AssertionError) {
                PoliticaScenarioCheck(desc, false, e.message)
            }
        }

        return PoliticaScenarioResult(
            name = "",
            calibration = resolved,
            source = resolvedSource,
            checks = checks,
        )
    }
}

// ── Top-level DSL ────────────────────────────────────────────────────────────

fun PoliticaContext.scenario(
    name: String,
    block: PoliticaScenarioBuilder.() -> Unit,
): PoliticaScenarioResult {
    val builder = PoliticaScenarioBuilder(this)
    builder.block()
    return builder.run().let { it.copy(name = name) }
}
