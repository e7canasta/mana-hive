package com.manahive.hub.policy

import com.manahive.contracts.policy.AlarmProfile
import com.manahive.contracts.policy.CatalogVersion
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.MobilityAid
import com.manahive.contracts.policy.PolicyMode
import com.manahive.contracts.policy.PolicyOverride
import com.manahive.contracts.policy.RiskLevel
import com.manahive.contracts.policy.TemplateId
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.Explained
import com.manahive.kernel.ExplanationStep
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import java.time.Instant
import java.time.ZoneOffset

/**
 * Proyecta las capas almacenadas en el hub a un [AlarmProfile] que Política
 * resuelve contra el catálogo del nivel.
 *
 * Función pura: mismas capas y mismo instante → mismo perfil. Sin I/O.
 *
 * Devuelve [Explained] porque la procedencia es el producto, no un extra. Sin
 * ella, *"¿de dónde salió ese diez, y quién lo puso?"* no tiene respuesta — y
 * ésa es la pregunta que justifica que el hub exista.
 *
 * Precedencia aplicada, de menor a mayor: nivel → plantilla → ajuste manual →
 * ventana horaria. Ante dos capas que tocan el mismo estado, **gana la más
 * protectora**: el umbral más corto, porque avisar antes nunca es el riesgo.
 */
public fun PolicyLayers.toAlarmProfile(
    resident: ResidentId,
    at: Instant,
): Explained<AlarmProfile> {
    val steps = mutableListOf<ExplanationStep>()

    steps += ExplanationStep(
        rule = "watch-level",
        observed = "level ${level.label}",
        conclusion = "catalog for ${level.label} is the base",
    )
    steps += ExplanationStep(
        rule = "template",
        observed = "template ${template.id}",
        conclusion = "template applied over the level catalog",
    )

    // Cada capa aporta sus umbrales por estado; las de arriba pisan a las de
    // abajo salvo que la de abajo sea más protectora.
    val byState = mutableMapOf<StateKind, DwellThreshold>()

    adjustments.forEach { adj ->
        val winner = mostProtective(byState[adj.state], adj.threshold)
        byState[adj.state] = winner
        steps += ExplanationStep(
            rule = "manual-adjustment",
            observed = "${adj.id}: ${adj.state} → ${adj.threshold.exceeded} " +
                "by ${adj.actor.value} at ${adj.at} — ${adj.reason}",
            conclusion = if (winner == adj.threshold) {
                "manual adjustment wins for ${adj.state}"
            } else {
                "kept the more protective threshold already in force for ${adj.state}"
            },
        )
    }

    windows.forEach { window ->
        if (!window.isActiveAt(at)) {
            steps += ExplanationStep(
                rule = "time-window",
                observed = "${window.id} (${window.from}–${window.to}) not active at $at",
                conclusion = "did not contribute",
            )
            return@forEach
        }
        window.adjustments.forEach { adj ->
            val winner = mostProtective(byState[adj.state], adj.threshold)
            byState[adj.state] = winner
            steps += ExplanationStep(
                rule = "time-window",
                observed = "${window.id} active at $at: ${adj.state} → ${adj.threshold.exceeded}",
                conclusion = if (winner == adj.threshold) {
                    "time window wins for ${adj.state}"
                } else {
                    "kept the more protective threshold already in force for ${adj.state}"
                },
            )
        }
    }

    val overrides = byState.entries.associate { (state, threshold) ->
        val ruleId = RuleId("layer-${state.name.lowercase()}")
        ruleId to PolicyOverride.DwellOverride(
            ruleId = ruleId,
            state = state,
            value = threshold,
        ) as PolicyOverride
    }

    val profile = AlarmProfile(
        residentId = resident,
        // El nivel de vigilancia NO es el riesgo del residente: son ejes
        // distintos que se cruzan — ver WatchLevel. Derivar uno del otro
        // afirmaría algo clínico que nadie dijo. Estas dos dimensiones viajan
        // en el censo del residente, no en sus capas de política.
        riskLevel = RiskLevel.MEDIUM,
        mobilityAid = MobilityAid.NONE,
        autopilot = false,
        mode = if (overrides.isEmpty()) PolicyMode.PRESET else PolicyMode.CUSTOM,
        templateId = TemplateId(template.id),
        overrides = overrides,
        // La versión del catálogo es la del nivel: dos niveles distintos son
        // dos cuerpos de reglas distintos, y la huella tiene que distinguirlos.
        catalogVersion = CatalogVersion(level.label),
        validFrom = at,
    )

    return Explained(value = profile, explanation = steps)
}

/**
 * Ante dos umbrales para el mismo estado, gana el que avisa antes.
 *
 * Es la regla de desempate que ya documentaba el resolvedor retirado y se
 * conserva a propósito: en vigilancia nocturna, equivocarse hacia avisar antes
 * es recuperable; equivocarse hacia avisar después, no.
 */
internal fun mostProtective(current: DwellThreshold?, candidate: DwellThreshold): DwellThreshold =
    when {
        current == null -> candidate
        candidate.exceeded < current.exceeded -> candidate
        else -> current
    }

/** ¿Está vigente esta ventana en [at]? Contempla ventanas que cruzan medianoche. */
internal fun TimeWindow.isActiveAt(at: Instant): Boolean {
    val time = at.atZone(ZoneOffset.UTC).toLocalTime()
    return if (from <= to) {
        time >= from && time < to
    } else {
        // La ventana nocturna 22:00–07:00 cruza medianoche. Es el caso normal.
        time >= from || time < to
    }
}
