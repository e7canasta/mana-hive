package jose301policies

import com.manahive.contracts.policy.*
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.RuleId
import com.manahive.politica.bdd.PoliticaContext
import com.manahive.politica.bdd.scenario
import com.manahive.politica.testdata.*
import java.time.Duration

fun main() {
    val catalog = testCatalog()
    val fallRiskTemplate = fallRiskTemplate()

    val catalogWithTemplates = testCatalog(
        templates = mapOf(
            TemplateId("t-senior") to testTemplate(),
            TemplateId("t-fall-risk") to fallRiskTemplate,
        ),
    )

    println("═══════════════════════════════════════════════════════════════")
    println("  José 301 — Policy Resolution")
    println("═══════════════════════════════════════════════════════════════")
    println()

    // ── 1. Preset profile → template resolution ────────────────────────────

    PoliticaContext(
        catalog = catalogWithTemplates,
        profile = presetProfile("jose", templateId = "t-fall-risk"),
    ).scenario("Preset profile usa template fall-risk") {
        thenExpectSource(PolicySource.TEMPLATE)
        thenExpectHysteresis(StateKind.LYING, StateKind.SITTING_IN_BED, Duration.ofSeconds(5))
        thenExpectDwell(StateKind.STANDING, Duration.ofMinutes(2), Duration.ofMinutes(3))
    }.report()

    // ── 2. Preset profile → default template ───────────────────────────────

    PoliticaContext(
        catalog = catalogWithTemplates,
        profile = presetProfile("jose", templateId = "t-senior"),
    ).scenario("Preset profile usa template senior") {
        thenExpectSource(PolicySource.TEMPLATE)
        thenExpectHysteresis(StateKind.LYING, StateKind.SITTING_IN_BED, Duration.ofSeconds(3))
        thenExpectDwell(StateKind.STANDING, Duration.ofMinutes(4), Duration.ofMinutes(5))
    }.report()

    // ── 3. Custom profile → catalog base ───────────────────────────────────

    PoliticaContext(
        catalog = catalog,
        profile = customProfile("jose"),
    ).scenario("Custom profile sin template usa catalog base") {
        thenExpectSource(PolicySource.CATALOG)
        thenExpectHysteresis(StateKind.LYING, StateKind.SITTING_IN_BED, Duration.ofSeconds(3))
        thenExpectDwellCount(4)
    }.report()

    // ── 4. Custom profile with hysteresis override ─────────────────────────

    PoliticaContext(
        catalog = catalog,
        profile = customProfile("jose", overrides = mapOf(
            hysteresisOverride("r-hyst-1", StateKind.LYING, StateKind.SITTING_IN_BED, Duration.ofSeconds(10)),
        )),
    ).scenario("Override hysteresis LYING→SITTING_IN_BED") {
        thenExpectSource(PolicySource.OVERRIDE)
        thenExpectHysteresis(StateKind.LYING, StateKind.SITTING_IN_BED, Duration.ofSeconds(10))
        // Other transitions unchanged
        thenExpectHysteresis(StateKind.SITTING_IN_BED, StateKind.STANDING, Duration.ofSeconds(3))
    }.report()

    // ── 5. Custom profile with dwell override ──────────────────────────────

    PoliticaContext(
        catalog = catalog,
        profile = customProfile("jose", overrides = mapOf(
            dwellOverride("r-dwell-1", StateKind.STANDING, warning = Duration.ofMinutes(1), exceeded = Duration.ofMinutes(2)),
        )),
    ).scenario("Override dwell STANDING") {
        thenExpectSource(PolicySource.OVERRIDE)
        thenExpectDwell(StateKind.STANDING, Duration.ofMinutes(1), Duration.ofMinutes(2))
        // Other dwell thresholds unchanged
        thenExpectDwell(StateKind.IN_BATHROOM, Duration.ofMinutes(20), Duration.ofMinutes(30))
    }.report()

    // ── 6. Template + override → override wins ─────────────────────────────

    PoliticaContext(
        catalog = catalogWithTemplates,
        profile = testProfile(
            residentId = "jose",
            mode = PolicyMode.CUSTOM,
            templateId = TemplateId("t-fall-risk"),
            overrides = mapOf(
                hysteresisOverride("r-hyst-2", StateKind.LYING, StateKind.SITTING_IN_BED, Duration.ofSeconds(20)),
            ),
        ),
    ).scenario("Template + override → override gana") {
        thenExpectSource(PolicySource.OVERRIDE)
        thenExpectHysteresis(StateKind.LYING, StateKind.SITTING_IN_BED, Duration.ofSeconds(20))
        // Template values for non-overridden transitions
        thenExpectHysteresis(StateKind.SITTING_IN_BED, StateKind.STANDING, Duration.ofSeconds(5))
    }.report()

    // ── 7. Heartbeat timeout always default ────────────────────────────────

    PoliticaContext(
        catalog = catalog,
        profile = customProfile("jose"),
    ).scenario("Heartbeat timeout siempre default") {
        thenExpectConfidenceHeartbeat(Duration.ofSeconds(90))
    }.report()

    // ── 8. Multiple overrides ──────────────────────────────────────────────

    PoliticaContext(
        catalog = catalog,
        profile = customProfile("jose", overrides = mapOf(
            hysteresisOverride("r-hyst-3", StateKind.LYING, StateKind.SITTING_IN_BED, Duration.ofSeconds(8)),
            hysteresisOverride("r-hyst-4", StateKind.STANDING, StateKind.BED_EDGE, Duration.ofSeconds(8)),
            dwellOverride("r-dwell-2", StateKind.BED_EDGE, warning = Duration.ofMinutes(1), exceeded = Duration.ofMinutes(2)),
        )),
    ).scenario("Multiples overrides") {
        thenExpectSource(PolicySource.OVERRIDE)
        thenExpectHysteresis(StateKind.LYING, StateKind.SITTING_IN_BED, Duration.ofSeconds(8))
        thenExpectHysteresis(StateKind.STANDING, StateKind.BED_EDGE, Duration.ofSeconds(8))
        thenExpectDwell(StateKind.BED_EDGE, Duration.ofMinutes(1), Duration.ofMinutes(2))
    }.report()

    println("═══════════════════════════════════════════════════════════════")
    println("  ✅ DONE")
    println("═══════════════════════════════════════════════════════════════")
}
