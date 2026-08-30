package com.manahive.runtime

import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.messaging.NatsObjectMapper
import com.manahive.profile.api.ResidentProfileDto
import org.slf4j.LoggerFactory
import java.io.File

/**
 * El arranque en frio, leyendo perfiles del disco.
 *
 * El contrato define `GET /api/profiles?active=true` contra el sistema de
 * registro, y **ese sistema todavia no existe**: lo implementa otro equipo. Esta
 * clase es el arranque en frio mientras tanto, y es lo que permite instalar el
 * sistema en una habitacion sin esperar esa dependencia.
 *
 * No es un atajo ni un mock: lee **el mismo [ResidentProfileDto]** que va a
 * llegar por HTTP, corre **la misma validacion**, y produce **la misma
 * calibracion**. Lo unico que cambia es de donde sale el JSON. Cuando el
 * endpoint exista, se reemplaza el origen y no se toca nada mas.
 *
 * Sin arranque en frio, despues de un reinicio el sistema queda ciego hasta que
 * alguien vuelva a tocar una politica a mano. En un turno noche eso es
 * inaceptable, y es la razon por la que las dos vias son obligatorias y no
 * alternativas.
 */
class ProfileSeed(
    private val calibrator: ProfileCalibrator,
    private val directory: File,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = NatsObjectMapper.mapper

    public companion object {
        /** El censo vive junto a los perfiles pero no es uno. */
        public const val CENSUS_FILE: String = "census.json"
    }

    /** Carga todos los perfiles del directorio. Devuelve cuantos quedaron vigentes. */
    fun load(): Int {
        if (!directory.isDirectory) {
            log.warn(
                "No hay directorio de perfiles en {}: el runtime arranca sin ninguno vigente",
                directory.absolutePath,
            )
            return 0
        }

        // `census.json` vive en el mismo directorio y no es un perfil: es
        // infraestructura. Sin excluirlo, cada arranque loguea un error que no
        // lo es, y un error que siempre aparece es un error que nadie mira.
        val archivos = directory.listFiles { f: File ->
            f.isFile && f.name.endsWith(".json") && f.name != CENSUS_FILE
        }
            ?.sortedBy { it.name }
            .orEmpty()

        if (archivos.isEmpty()) {
            log.warn("Directorio de perfiles {} vacio", directory.absolutePath)
            return 0
        }

        var aceptados = 0
        archivos.forEach { archivo ->
            try {
                val dto = mapper.readValue<ResidentProfileDto>(archivo.readText())
                if (calibrator.accept(dto)) aceptados++
            } catch (e: Exception) {
                // Un archivo ilegible no puede impedir que carguen los otros: un
                // residente con el JSON roto no deja sin vigilancia al resto.
                log.error("No se pudo leer el perfil {}: {}", archivo.name, e.message)
            }
        }
        log.info("Arranque en frio: {} de {} perfiles vigentes", aceptados, archivos.size)
        return aceptados
    }
}
