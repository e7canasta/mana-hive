package com.manahive.politica.profile

import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.ResidentProfile
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.SceneState
import com.manahive.profile.api.ProfileExamples
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration

private val ELENA = ProfileMapper.map(ProfileExamples.ELENA)
    .shouldBeInstanceOf<ProfileMapping.Accepted>().profile

/**
 * La baranda de Elena llega al motor.
 *
 * Es la prueba que pide SPEC-02 para la fase 4: *"barral bajo un minuto de noche
 * abre episodio en Sentinel"*. Los tres slots —`sceneHysteresis`,
 * `sceneThresholds`, `sceneStateRules`— existian desde antes y llegaban vacios,
 * porque [com.manahive.contracts.policy.PolicyCalibration] no tenia donde
 * transportarlos. La canieria estaba puesta y desconectada del tanque.
 */
class SceneFieldProjectionSpec : DescribeSpec({

    describe("la identidad del campo es la misma en las dos puntas") {

        it("el perfil dice 'bed.left' y el gemelo emite 'bed.left'") {
            // Si estas dos no coinciden hace falta una tabla de traduccion, y una
            // tabla de traduccion es donde se pierden las cosas. No hay ninguna.
            val cal = ProfileProjection.project(ELENA, "night").value
            cal.scene.sceneThresholds.keys shouldContain SceneState.BED_LEFT
            SceneState.BED_LEFT shouldBe "bed.left"
        }

        it("el aspecto se nombra, para que un sujeto pueda tener dos ejes") {
            // "wheelchair" plano no puede distinguir presencia de alcance.
            SceneState.WHEELCHAIR shouldBe "wheelchair.presence"
            ProfileProjection.project(ELENA).value
                .scene.sceneThresholds.keys shouldContain "wheelchair.presence"
        }
    }

    describe("la baranda baja, de noche") {

        val noche = ProfileProjection.project(ELENA, "night").value

        it("el debounce del flag viaja: 3 segundos") {
            // En un flag la estabilidad vive en el estado, no en la arista.
            noche.scene.sceneHysteresis["bed.left"] shouldBe Duration.ofSeconds(3)
        }

        it("el plazo viaja: alerta al minuto") {
            val umbral = noche.scene.sceneThresholds.getValue("bed.left")
            umbral.exceeded shouldBe Duration.ofMinutes(1)
            // Sin preaviso declarado, el silencioso cae a la mitad del plazo.
            umbral.warning shouldBe Duration.ofSeconds(30)
        }

        it("la regla viaja con su gravedad y su cierre") {
            val regla = noche.sentinel.sceneStateRules.getValue("bed.left")
            regla.field shouldBe "bed.left"
            regla.state shouldBe "DOWN"
            regla.severity shouldBe Severity.HIGH
            regla.closureCondition shouldBe ClosureCondition.STAFF_AND_SAFE
        }

        it("HIGH pide que alguien vaya, y por eso espera confirmacion") {
            val regla = noche.sentinel.sceneStateRules.getValue("bed.left")
            regla.severity.requiresAttendance shouldBe true
            regla.requiresConfirmation shouldBe true
            regla.confirmationWindow shouldBe Duration.ofMinutes(2)
        }

        it("la regla tiene identidad propia y no choca con las del residente") {
            noche.sentinel.sceneStateRules.getValue("bed.left").id.value shouldBe
                "alert-bed-left-down"
        }
    }

    describe("de dia la baranda no alerta") {
        it("la regla es solo de noche, asi que de dia no hay regla") {
            val dia = ProfileProjection.project(ELENA).value
            dia.sentinel.sceneStateRules["bed.left"].shouldBeNull()
            dia.scene.sceneThresholds["bed.left"].shouldBeNull()
        }

        it("pero el debounce del sensor rige igual: no es una regla clinica") {
            // El filtro de ruido no depende del horario. Confundirlos apagaria
            // el antirrebote de dia.
            ProfileProjection.project(ELENA).value
                .scene.sceneHysteresis["bed.left"] shouldBe Duration.ofSeconds(3)
        }
    }

    describe("el andador y la silla") {
        it("la silla fuera de alcance viaja con su plazo") {
            val cal = ProfileProjection.project(ELENA).value
            val umbral = cal.scene.sceneThresholds.getValue("wheelchair.presence")
            umbral.warning shouldBe Duration.ofMinutes(2)
            umbral.exceeded shouldBe Duration.ofMinutes(5)
            cal.scene.sceneHysteresis["wheelchair.presence"] shouldBe Duration.ofSeconds(5)
        }
    }

    describe("el personal se observa y no alerta") {
        it("un estado observeOnly no produce regla de campo") {
            ProfileProjection.project(ELENA).value
                .sentinel.sceneStateRules["staff.presence"].shouldBeNull()
        }
    }

    describe("lo que dejo de perderse") {
        it("la baranda y la silla ya no figuran como intransportables") {
            val paths = ProfileProjection.unrepresentable(ELENA, "night").map { it.path }
            paths shouldNotContain "subjects.bed.aspects.left.states.DOWN"
            paths shouldNotContain "subjects.wheelchair.aspects.presence.states.OUT_OF_REACH"
        }

        it("pero lo que sigue sin transporte se sigue diciendo") {
            // Queda una sola: `unknownAfter` no es un hueco de transporte sino
            // una capacidad que no existe —avisarle a mantenimiento que un
            // sensor lleva media hora mudo—. Darle un slot que nadie lee seria
            // construir otro stub como el que acabamos de sacar.
            ProfileProjection.unrepresentable(ELENA, "night").map { it.path } shouldBe
                listOf("subjects.wheelchair.aspects.presence.unknownAfter")
        }
    }

    describe("un campo con dos reglas en la misma ventana se reporta") {
        it("el transporte lleva una sola por campo, y lo dice") {
            // Invariante honesta: en vez de quedarse con una y callarse.
            val conDos = ELENA.copy(
                subjects = ELENA.subjects + ("bed" to ELENA.subjects.getValue("bed").let { bed ->
                    bed.copy(
                        aspects = bed.aspects + ("left" to bed.aspects.getValue("left").let { a ->
                            a.copy(states = a.states + ("UP" to a.states.getValue("DOWN")))
                        }),
                    )
                }),
            )
            ProfileProjection.unrepresentable(conDos, "night").map { it.path } shouldContain
                "subjects.bed.aspects.left.states"
        }
    }

    describe("la ventana implicita") {
        it("ALWAYS es el regimen normal y no hace falta declararla") {
            ResidentProfile.ALWAYS shouldBe "always"
        }
    }
})
