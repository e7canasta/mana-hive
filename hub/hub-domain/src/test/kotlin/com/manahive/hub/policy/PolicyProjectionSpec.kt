package com.manahive.hub.policy

import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.PolicyMode
import com.manahive.contracts.policy.PolicyOverride
import com.manahive.contracts.policy.WatchLevel
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import com.manahive.kernel.StaffId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/**
 * ADR-001 · El hub proyecta, no resuelve.
 *
 * Lo que se prueba acá no es el cálculo — eso es de Política — sino que la
 * **procedencia sobreviva**: quién cambió qué, cuándo y por qué. Es lo que hace
 * contestable la pregunta del inspector.
 */
class PolicyProjectionSpec : BehaviorSpec({

    val jose = ResidentId("jose")
    val laNoche = Instant.parse("2026-08-22T02:00:00Z")
    val laTarde = Instant.parse("2026-08-22T15:00:00Z")

    fun capas(
        level: WatchLevel = WatchLevel.FALL_RISK,
        adjustments: List<ManualAdjustment> = emptyList(),
        windows: List<TimeWindow> = emptyList(),
    ) = PolicyLayers(
        level = level,
        template = LevelTemplate(id = level.label, level = level),
        adjustments = adjustments,
        windows = windows,
    )

    fun ajuste(
        id: String,
        state: StateKind,
        minutos: Long,
        actor: String = "dr-garcia",
        reason: String = "post-operatorio",
    ) = ManualAdjustment(
        id = id,
        state = state,
        threshold = DwellThreshold(
            warning = Duration.ofMinutes(minutos).dividedBy(2),
            exceeded = Duration.ofMinutes(minutos),
        ),
        actor = StaffId(actor),
        at = laNoche,
        reason = reason,
    )

    Given("un residente con nivel y plantilla, sin ajustes") {
        val proyectado = capas().toAlarmProfile(jose, laNoche)

        Then("el perfil apunta al catálogo del nivel") {
            proyectado.value.catalogVersion.value shouldBe WatchLevel.FALL_RISK.label
            proyectado.value.templateId!!.value shouldBe "fall-risk"
        }

        Then("es PRESET — nadie ajustó nada a mano") {
            proyectado.value.mode shouldBe PolicyMode.PRESET
        }

        Then("la explicación nombra las dos capas que aportaron") {
            proyectado.explanation.map { it.rule } shouldBe listOf("watch-level", "template")
        }
    }

    Given("un ajuste manual firmado por el director") {
        val proyectado = capas(
            adjustments = listOf(ajuste("adj-1", StateKind.SITTING_IN_BED, 15)),
        ).toAlarmProfile(jose, laNoche)

        Then("el umbral llega como override de dwell") {
            val override = proyectado.value.overrides.values
                .filterIsInstance<PolicyOverride.DwellOverride>()
                .single { it.state == StateKind.SITTING_IN_BED }
            override.value.exceeded shouldBe Duration.ofMinutes(15)
        }

        Then("pasa a CUSTOM") {
            proyectado.value.mode shouldBe PolicyMode.CUSTOM
        }

        Then("la explicación nombra al actor y el motivo — la pregunta del inspector") {
            val paso = proyectado.explanation.single { it.rule == "manual-adjustment" }
            paso.observed shouldContain "dr-garcia"
            paso.observed shouldContain "post-operatorio"
        }
    }

    Given("una ventana nocturna que cruza medianoche") {
        val ventana = TimeWindow(
            id = "noche",
            from = LocalTime.of(22, 0),
            to = LocalTime.of(7, 0),
            adjustments = listOf(
                StateAdjustment(
                    state = StateKind.ABSENT,
                    threshold = DwellThreshold(Duration.ofMinutes(2), Duration.ofMinutes(5)),
                ),
            ),
        )

        When("son las 02:00 — dentro de la ventana") {
            val proyectado = capas(windows = listOf(ventana)).toAlarmProfile(jose, laNoche)

            Then("la ventana aporta su umbral") {
                proyectado.value.overrides.values
                    .filterIsInstance<PolicyOverride.DwellOverride>()
                    .map { it.state } shouldContain StateKind.ABSENT
            }

            Then("la explicación dice que estaba vigente") {
                proyectado.explanation.single { it.rule == "time-window" }
                    .conclusion shouldContain "wins"
            }
        }

        When("son las 15:00 — fuera de la ventana") {
            val proyectado = capas(windows = listOf(ventana)).toAlarmProfile(jose, laTarde)

            Then("no aporta nada") {
                proyectado.value.overrides.shouldBe(emptyMap())
            }

            Then("pero queda registrado que se evaluó y no aplicó") {
                proyectado.explanation.single { it.rule == "time-window" }
                    .conclusion shouldContain "did not contribute"
            }
        }
    }

    Given("dos capas que tocan el mismo estado con umbrales distintos") {
        val ventana = TimeWindow(
            id = "noche",
            from = LocalTime.of(22, 0),
            to = LocalTime.of(7, 0),
            adjustments = listOf(
                // La ventana avisa MÁS TARDE que el ajuste manual.
                StateAdjustment(
                    state = StateKind.SITTING_IN_BED,
                    threshold = DwellThreshold(Duration.ofMinutes(15), Duration.ofMinutes(30)),
                ),
            ),
        )
        val proyectado = capas(
            adjustments = listOf(ajuste("adj-1", StateKind.SITTING_IN_BED, 10)),
            windows = listOf(ventana),
        ).toAlarmProfile(jose, laNoche)

        Then("gana la más protectora, aunque venga de la capa de menor precedencia") {
            val override = proyectado.value.overrides.values
                .filterIsInstance<PolicyOverride.DwellOverride>()
                .single { it.state == StateKind.SITTING_IN_BED }
            override.value.exceeded shouldBe Duration.ofMinutes(10)
        }

        Then("la explicación dice que se conservó la anterior") {
            proyectado.explanation.single { it.rule == "time-window" }
                .conclusion shouldContain "more protective"
        }
    }

    Given("un ajuste sin motivo") {
        Then("se rechaza al construirse — no seis meses después") {
            val error = shouldThrow<IllegalArgumentException> {
                ajuste("adj-sin-motivo", StateKind.STANDING, 5, reason = "  ")
            }
            error.message!! shouldContain "no reason"
        }
    }
})
