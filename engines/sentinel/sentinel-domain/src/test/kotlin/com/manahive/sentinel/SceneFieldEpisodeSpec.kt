package com.manahive.sentinel

import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.SceneFieldRule
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.TriggerOn
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.kernel.BedId
import com.manahive.kernel.NightId
import com.manahive.kernel.RuleId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration
import java.time.Instant

private val ELENA = com.manahive.kernel.ResidentId("elena")
private val CAMA = BedId("301")
private val NOCHE = NightId("2026-08-29")
private val T0: Instant = Instant.parse("2026-08-30T03:00:00Z")

private val BARANDA = SceneFieldRule(
    id = RuleId("alert-bed-left-down"),
    field = "bed.left",
    state = "DOWN",
    severity = Severity.HIGH,
    closureCondition = ClosureCondition.STAFF_AND_SAFE,
    requiresConfirmation = true,
    confirmationWindow = Duration.ofMinutes(2),
)

private fun calibracion(
    vararg reglas: SceneFieldRule,
    cierra: Set<String> = setOf("staff.presence.PRESENT"),
    cierre: ClosureCondition = ClosureCondition.SAFE_ONLY,
) = sentinelCalibration {
    resident("elena")
    // Una regla de postura, para probar la composicion con la de campo.
    rule("alert-bed_edge", StateKind.BED_EDGE, TriggerOn.ENTRY) {
        severity = Severity.WARNING
        closureCondition = cierre
    }
    reglas.forEach { sceneRule(it) }
    cierra.forEach { closingState(it) }
}

private fun campoCambio(field: String, to: String) = SceneEvent.SceneStateChanged(
    bed = CAMA, night = NOCHE, at = T0, field = field, from = "ABSENT", to = to,
)

private fun bordeDeCama() = SceneEvent.TransitionDetected(
    bed = CAMA, night = NOCHE, at = T0,
    from = com.manahive.contracts.scene.PersonState.Lying,
    to = com.manahive.contracts.scene.PersonState.BedEdge,
)

private fun plazoVencido(field: String) = SceneEvent.SceneDwellExceeded(
    bed = CAMA, night = NOCHE, at = T0,
    field = field, threshold = Duration.ofMinutes(1), since = T0.minusSeconds(60),
)

/**
 * La baranda baja un minuto de noche abre episodio.
 *
 * Es la prueba que pide SPEC-02 para cerrar la fase 4. Hasta ahora
 * `SceneDwellExceeded` era un no-op con el comentario *"not yet judged by
 * sentinel"*, y lo era porque la regla nunca llegaba: el slot existia, su
 * accessor existia, y las tres construcciones le pasaban `emptyMap()`.
 */
class SceneFieldEpisodeSpec : DescribeSpec({

    val evaluador = { cal: SentinelCalibration -> SentinelEvaluatorImpl(cal) }

    describe("el plazo de la baranda vence y no hay episodio abierto") {

        val veredicto = evaluador(calibracion(BARANDA))
            .evaluate(plazoVencido("bed.left"), EpisodeLedger.empty(ELENA), T0)

        it("abre episodio") {
            veredicto.value.episodes.openForBed(CAMA) shouldBe
                veredicto.value.episodes.openForBed(CAMA)
            veredicto.value.episodes.openForBed(CAMA)!!.severity shouldBe Severity.HIGH
        }

        it("el sujeto del episodio es el campo, no una postura") {
            val abierto = veredicto.value.episodes.openForBed(CAMA)!!
            abierto.triggerField shouldBe "bed.left"
            // Y no se inventa un StateKind: la baranda no es una postura.
            abierto.trigger.shouldBeNull()
        }

        it("cierra con las dos cosas, como lo pidio el perfil") {
            veredicto.value.episodes.openForBed(CAMA)!!
                .closureCondition shouldBe ClosureCondition.STAFF_AND_SAFE
        }

        it("emite la señal con el campo adentro") {
            val señal = veredicto.value.signals.single()
                .shouldBeInstanceOf<SentinelSignal.EpisodeOpened>()
            señal.field shouldBe "bed.left"
            señal.trigger.shouldBeNull()
            señal.severity shouldBe Severity.HIGH
            señal.confirmationWindow shouldBe Duration.ofMinutes(2)
        }
    }

    describe("un campo sin regla se observa y no alerta") {
        it("no abre nada, y lo explica") {
            val veredicto = evaluador(calibracion(BARANDA))
                .evaluate(plazoVencido("walker.presence"), EpisodeLedger.empty(ELENA), T0)

            veredicto.value.signals.shouldBeEmpty()
            veredicto.value.episodes.openForBed(CAMA).shouldBeNull()
            veredicto.explanation.last().conclusion shouldBe
                "sin regla para este campo: se observa y no alerta"
        }
    }

    describe("la composicion la decide la severidad, no una condicion cruzada") {

        it("la baranda HIGH eleva un episodio WARNING ya abierto") {
            val cal = calibracion(BARANDA)
            val ev = evaluador(cal)

            val abierto = ev.evaluate(
                SceneEvent.TransitionDetected(
                    bed = CAMA, night = NOCHE, at = T0,
                    from = com.manahive.contracts.scene.PersonState.Lying,
                    to = com.manahive.contracts.scene.PersonState.BedEdge,
                ),
                EpisodeLedger.empty(ELENA), T0,
            ).value.episodes

            abierto.openForBed(CAMA)!!.severity shouldBe Severity.WARNING

            val elevado = ev.evaluate(plazoVencido("bed.left"), abierto, T0).value
            elevado.episodes.openForBed(CAMA)!!.severity shouldBe Severity.HIGH
            // El trigger original no se reescribe: lo abrio una postura.
            elevado.episodes.openForBed(CAMA)!!.trigger shouldBe StateKind.BED_EDGE
        }

        it("una baranda de nivel menor entra al episodio sin elevarlo") {
            val leve = BARANDA.copy(id = RuleId("alert-leve"), severity = Severity.INFO)
            val cal = calibracion(leve)
            val ev = evaluador(cal)

            val abierto = ev.evaluate(
                SceneEvent.TransitionDetected(
                    bed = CAMA, night = NOCHE, at = T0,
                    from = com.manahive.contracts.scene.PersonState.Lying,
                    to = com.manahive.contracts.scene.PersonState.BedEdge,
                ),
                EpisodeLedger.empty(ELENA), T0,
            ).value.episodes

            val despues = ev.evaluate(plazoVencido("bed.left"), abierto, T0).value
            despues.episodes.openForBed(CAMA)!!.severity shouldBe Severity.WARNING
            despues.signals.shouldBeEmpty()
        }
    }

    describe("el perfil decide quien cierra un episodio") {

        it("entra el personal y el episodio cierra, si asi lo pidio el cierre") {
            // STAFF_OR_SAFE: cualquiera de las dos alcanza. Con SAFE_ONLY el
            // personal no cierra nada, y esta bien que no lo haga.
            val ev = evaluador(calibracion(BARANDA, cierre = ClosureCondition.STAFF_OR_SAFE))
            val abierto = ev.evaluate(bordeDeCama(), EpisodeLedger.empty(ELENA), T0).value.episodes
            abierto.openForBed(CAMA) shouldNotBe null

            val despues = ev.evaluate(
                campoCambio("staff.presence", "PRESENT"), abierto, T0,
            ).value
            despues.episodes.openForBed(CAMA).shouldBeNull()
        }

        it("un estado que el perfil no marco no cierra nada") {
            // Antes cerraba porque el codigo decia que cerraba. Ahora hace falta
            // que lo diga el documento. Mismo cierre que el caso anterior: lo
            // unico que cambia es que el perfil no declara el estado.
            val ev = evaluador(
                calibracion(BARANDA, cierra = emptySet(), cierre = ClosureCondition.STAFF_OR_SAFE),
            )
            val abierto = ev.evaluate(bordeDeCama(), EpisodeLedger.empty(ELENA), T0).value.episodes

            val despues = ev.evaluate(
                campoCambio("staff.presence", "PRESENT"), abierto, T0,
            ).value
            despues.episodes.openForBed(CAMA) shouldNotBe null
        }

        it("con STAFF_AND_SAFE, que entre el personal cumple una mitad sola") {
            // Es el cierre correcto de una caida: no alcanza con que alguien
            // pase por la habitacion.
            val ev = evaluador(
                calibracion(BARANDA, cierre = ClosureCondition.STAFF_AND_SAFE),
            )
            val abierto = ev.evaluate(bordeDeCama(), EpisodeLedger.empty(ELENA), T0).value.episodes

            val despues = ev.evaluate(
                campoCambio("staff.presence", "PRESENT"), abierto, T0,
            ).value
            despues.episodes.openForBed(CAMA) shouldNotBe null
            despues.episodes.openForBed(CAMA)!!.staffPresent shouldBe true
        }

        it("con SAFE_ONLY el personal no cierra: cierra que el residente este seguro") {
            val ev = evaluador(calibracion(BARANDA, cierre = ClosureCondition.SAFE_ONLY))
            val abierto = ev.evaluate(bordeDeCama(), EpisodeLedger.empty(ELENA), T0).value.episodes

            val despues = ev.evaluate(
                campoCambio("staff.presence", "PRESENT"), abierto, T0,
            ).value
            despues.episodes.openForBed(CAMA) shouldNotBe null
        }

        it("un cambio de campo que no cierra no toca los episodios") {
            val ev = evaluador(calibracion(BARANDA))
            val abierto = ev.evaluate(bordeDeCama(), EpisodeLedger.empty(ELENA), T0).value.episodes

            val despues = ev.evaluate(campoCambio("bed.left", "DOWN"), abierto, T0).value
            despues.episodes.openForBed(CAMA) shouldNotBe null
            despues.signals.shouldBeEmpty()
        }
    }

    describe("el preaviso de un campo") {

        val preaviso = SceneEvent.SceneDwellWarning(
            bed = CAMA, night = NOCHE, at = T0,
            field = "bed.left", threshold = Duration.ofMinutes(1), since = T0.minusSeconds(30),
        )

        it("avisa que la baranda se acerca a su plazo, sin abrir episodio") {
            val v = evaluador(calibracion(BARANDA))
                .evaluate(preaviso, EpisodeLedger.empty(ELENA), T0)

            val señal = v.value.signals.single()
                .shouldBeInstanceOf<SentinelSignal.DwellPreWarning>()
            señal.field shouldBe "bed.left"
            // No es una postura, y no se inventa una.
            señal.state.shouldBeNull()
            señal.elapsed shouldBe Duration.ofSeconds(30)
            v.value.episodes.openForBed(CAMA).shouldBeNull()
        }

        it("un campo sin regla no genera preaviso de algo que no va a pasar") {
            evaluador(calibracion(BARANDA))
                .evaluate(preaviso.copy(field = "walker.presence"), EpisodeLedger.empty(ELENA), T0)
                .value.signals.shouldBeEmpty()
        }
    }
})
