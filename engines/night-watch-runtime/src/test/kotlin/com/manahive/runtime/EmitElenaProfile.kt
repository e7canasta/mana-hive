package com.manahive.runtime

import com.manahive.messaging.NatsObjectMapper
import com.manahive.profile.api.ProfileExamples
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Escribe el perfil canonico a disco, para que el runtime lo levante.
 *
 * Se genera desde [ProfileExamples] y no se escribe a mano a proposito: un JSON
 * copiado de un documento se desactualiza, y ese es exactamente el problema que
 * el jar del contrato vino a resolver.
 */
class EmitElenaProfile {
    @Test
    fun `escribe profiles-elena_json`() {
        // Sube al raiz del repo: el test corre con el working dir del modulo,
        // y dejar el perfil ahi crea un `profiles/` suelto adentro del modulo.
        val dir = File(System.getenv("MANAHIVE_PROFILES_OUT") ?: "../../profiles")
        dir.mkdirs()
        val json = NatsObjectMapper.mapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(ProfileExamples.ELENA)
        File(dir, "elena.json").writeText(json)
        println("PERFIL ESCRITO: ${File(dir, "elena.json").absolutePath}")
    }
}
