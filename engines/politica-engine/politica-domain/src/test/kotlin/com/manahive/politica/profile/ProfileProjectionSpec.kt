package com.manahive.politica.profile

import com.manahive.contracts.common.Channel
import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.ResidentProfile
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.TransitionKey
import com.manahive.contracts.policy.TriggerOn
import com.manahive.contracts.scene.StateKind
import com.manahive.profile.api.ProfileExamples
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration

private val ELENA = ProfileMapper.map(ProfileExamples.ELENA)
    .shouldBeInstanceOf<ProfileMapping.Accepted>().profile

private fun proyectada(window: String = ResidentProfile.ALWAYS) =
    ProfileProjection.project(ELENA, window).value

class ProfileProjectionSpec : DescribeSpec({

    describe("el regimen normal") {

        val cal = proyectada()

        it("la permanencia en el bano es la que rige de dia") {
            cal.scene.dwellThresholds[StateKind.IN_BATHROOM] shouldBe
                DwellThreshold(warning = Duration.ofMinutes(10), exceeded = Duration.ofMinutes(15))
        }

        it("no volver a la cama es una regla aparte de llevar mucho acostado") {
            cal.scene.comeBackThresholds[StateKind.LYING] shouldBe
                DwellThreshold(warning = Duration.ofMinutes(10), exceeded = Duration.ofMinutes(20))
            cal.scene.dwellThresholds[StateKind.LYING].shouldBeNull()

            // Los dos ids tienen que diferir: el adapter de Sentinel indexa por
            // id y se quedaria con una sola de las dos reglas.
            cal.sentinel.comeBackRules.getValue(StateKind.LYING).id.value shouldBe "comeback-lying"
        }

        it("la histeresis vive en la arista, no en el estado") {
            cal.scene.hysteresis[TransitionKey(StateKind.LYING, StateKind.BED_EDGE)] shouldBe
                Duration.ofMillis(1500)
            cal.scene.hysteresis[TransitionKey(StateKind.BED_EDGE, StateKind.STANDING)] shouldBe
                Duration.ofMillis(1500)
        }

        it("un estado que solo se observa no produce regla de alerta") {
            cal.sentinel.alertRules[StateKind.STANDING].shouldBeNull()
            cal.scene.dwellThresholds[StateKind.STANDING].shouldBeNull()
        }

        it("la confianza minima viaja por estado") {
            cal.scene.confidence.minConfidence shouldBe mapOf(
                StateKind.BED_EDGE to 0.90,
                StateKind.STANDING to 0.85,
            )
        }

        it("la transicion que merece video produce su ventana de grabacion") {
            val ventana = cal.recorder.transitionWindows
                .getValue(TransitionKey(StateKind.LYING, StateKind.BED_EDGE))
            ventana.before shouldBe Duration.ofSeconds(30)
            ventana.after shouldBe Duration.ofMinutes(2)
        }
    }

    describe("el borde de la cama dispara al entrar") {

        val regla = proyectada().sentinel.alertRules.getValue(StateKind.BED_EDGE)

        it("no espera plazo: la familia de disparo es la entrada") {
            regla.triggerOn shouldBe TriggerOn.ENTRY
            regla.severity shouldBe Severity.CRITICAL
        }

        it("cierra con las dos cosas: que entre el personal y que sea seguro") {
            regla.closureCondition shouldBe ClosureCondition.STAFF_AND_SAFE
        }

        it("pide video porque el perfil lo pide, no porque sea critico") {
            // Antes esto se deducia de la severidad. Deducirlo es lo que hacia
            // que la politica de video no estuviera en la politica.
            regla.requiresNvr shouldBe true
        }

        it("no espera confirmacion: escala en el acto") {
            regla.requiresConfirmation shouldBe false
            regla.confirmationWindow.shouldBeNull()
        }
    }

    describe("no volver a la cama espera confirmacion") {

        val regla = proyectada().sentinel.comeBackRules.getValue(StateKind.LYING)

        it("la ventana de confirmacion sale del plazo de escalada del perfil") {
            regla.requiresConfirmation shouldBe true
            regla.confirmationWindow shouldBe Duration.ofMinutes(5)
        }

        it("no pide video: el perfil no lo pidio para esta regla") {
            regla.requiresNvr shouldBe false
        }
    }

    describe("a las 22:00 cambia el regimen") {

        val dia = proyectada()
        val noche = proyectada("night")

        it("se elige la regla de la ventana entera, no se mezclan campos") {
            noche.scene.dwellThresholds[StateKind.IN_BATHROOM] shouldBe
                DwellThreshold(warning = Duration.ofMinutes(5), exceeded = Duration.ofMinutes(8))

            val reglaNoche = noche.sentinel.alertRules.getValue(StateKind.IN_BATHROOM)
            reglaNoche.severity shouldBe Severity.CRITICAL
            reglaNoche.closureCondition shouldBe ClosureCondition.STAFF_OR_SAFE

            // De dia la misma regla es otra cosa entera, no el mismo plazo con
            // otro numero: cambia la gravedad y cambia como cierra.
            val reglaDia = dia.sentinel.alertRules.getValue(StateKind.IN_BATHROOM)
            reglaDia.severity shouldBe Severity.WARNING
            reglaDia.closureCondition shouldBe ClosureCondition.SAFE_ONLY
        }

        it("una regla sin version de noche sigue siendo la de 'always'") {
            noche.scene.comeBackThresholds[StateKind.LYING] shouldBe
                dia.scene.comeBackThresholds[StateKind.LYING]
        }

        it("una regla que solo existe de noche no rige de dia") {
            // La baranda baja es HIGH y solo alerta de noche. De dia no hay a
            // quien avisar en ese nivel porque no hay regla que lo produzca.
            dia.harbor.defaultChannels[Severity.HIGH].shouldBeNull()
            noche.harbor.defaultChannels[Severity.HIGH] shouldBe
                setOf(Channel.PUSH, Channel.TABLET)
        }
    }

    describe("a quien se avisa sale del perfil y no de un adapter") {

        val noche = proyectada("night")

        it("los canales se unen por nivel de gravedad") {
            noche.harbor.defaultChannels[Severity.CRITICAL] shouldBe
                setOf(Channel.PUSH, Channel.TABLET, Channel.WARD_BOARD)
        }

        it("la notificacion de la cama viaja aunque el estado de la cama no") {
            // Harbor se indexa por severidad y no por estado, asi que es la
            // unica parte del perfil que hoy cruza entera para todos los sujetos.
            noche.harbor.escalationTimeouts[Severity.HIGH] shouldBe Duration.ofMinutes(2)
        }

        it("el plazo de escalada de un nivel es el mas corto de sus reglas") {
            // CRITICAL tiene una regla que escala a los 5 minutos y otra que no
            // espera nada. Respetar los 5 incumpliria la segunda.
            noche.harbor.escalationTimeouts[Severity.CRITICAL] shouldBe Duration.ZERO
        }
    }

    describe("la huella identifica las reglas que decidieron") {

        it("la misma version y la misma ventana dan la misma huella") {
            proyectada().fingerprint shouldBe proyectada().fingerprint
        }

        it("dos ventanas del mismo perfil no comparten huella") {
            proyectada().fingerprint shouldNotBe proyectada("night").fingerprint
        }

        it("una version nueva cambia la huella aunque no cambie ningun numero") {
            // Es la correccion al defecto viejo: la huella se calculaba sobre
            // los valores resueltos, asi que cambiar una severidad o una
            // condicion de cierre daba exactamente la misma.
            val v9 = ProfileProjection.project(ELENA.copy(version = 9, supersedes = 8)).value
            v9.fingerprint shouldNotBe proyectada().fingerprint
        }
    }

    describe("lo que el perfil dice y la calibracion todavia no escucha") {

        it("se reporta con su ruta en vez de descartarse en silencio") {
            // La lista arranco en cuatro y quedo en una. La baranda y la silla
            // viajan por sceneHysteresis/sceneThresholds/sceneStateRules, el
            // cierre por closingStates, y la ventana de video por ruleWindows.
            ProfileProjection.unrepresentable(ELENA).map { it.path } shouldContainExactlyInAnyOrder
                listOf("subjects.wheelchair.aspects.presence.unknownAfter")
        }

        it("la explicacion lo nombra, para que se lea sin correr un test") {
            val explicacion = ProfileProjection.project(ELENA).explanation
            explicacion.map { it.rule } shouldContainExactlyInAnyOrder
                listOf("profile", "provenance", "window", "states", "unrepresentable")
        }

        it("el estado del personal que cierra episodios ya llega al motor") {
            // Era el mas caro de los que faltaban: sin el, un cierre
            // STAFF_AND_SAFE era una promesa sin mecanismo. Ahora el perfil dice
            // quien cierra y el motor le hace caso.
            ProfileProjection.project(ELENA).value
                .sentinel.closingStates shouldBe setOf("staff.presence.PRESENT")
            ProfileProjection.unrepresentable(ELENA)
                .none { it.path.endsWith("closesEpisodes") } shouldBe true
        }
    }
})
