package com.manahive.harbor

import com.manahive.contracts.policy.Severity
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * El escalon que faltaba entre "te aviso" y "es una emergencia".
 *
 * Sin HIGH, todo el trabajo nocturno real de un geriatrico —la baranda que quedo
 * baja, el andador fuera de alcance, el bano que se estira— o se subestimaba
 * como aviso o se inflaba a critico. Un sistema que grita siempre deja de
 * escucharse, y uno que susurra siempre no llega a tiempo.
 */
class SeverityHighSpec : DescribeSpec({

    describe("los cuatro niveles contestan dos preguntas") {

        it("quien se entera: el orden es estricto") {
            Severity.INFO.rank shouldBe 0
            Severity.WARNING.rank shouldBe 1
            Severity.HIGH.rank shouldBe 2
            Severity.CRITICAL.rank shouldBe 3
        }

        it("hay que ir: solo desde HIGH para arriba") {
            Severity.INFO.requiresAttendance shouldBe false
            Severity.WARNING.requiresAttendance shouldBe false
            Severity.HIGH.requiresAttendance shouldBe true
            Severity.CRITICAL.requiresAttendance shouldBe true
        }
    }

    describe("HIGH se ubica entre aviso y emergencia, no al lado") {

        it("espera menos que un aviso, porque alguien tiene que ir") {
            val alto = ventana(Severity.HIGH)!!
            val aviso = ventana(Severity.WARNING)!!
            (alto < aviso) shouldBe true
        }

        it("pero no es cero, porque el turno puede estar ocupado") {
            ventana(Severity.HIGH)!!.isZero shouldBe false
            ventana(Severity.CRITICAL)!!.isZero shouldBe true
        }
    }

    describe("la composicion de episodios usa este orden") {

        it("un evento menor entra al episodio abierto sin volver a notificar") {
            (Severity.WARNING.rank < Severity.CRITICAL.rank) shouldBe true
        }

        it("un evento mayor eleva el episodio") {
            (Severity.CRITICAL.rank > Severity.HIGH.rank) shouldBe true
        }
    }
})

private fun ventana(s: Severity): java.time.Duration? = when (s) {
    Severity.INFO -> null
    Severity.WARNING -> java.time.Duration.ofMinutes(5)
    Severity.HIGH -> java.time.Duration.ofMinutes(2)
    Severity.CRITICAL -> java.time.Duration.ZERO
}
