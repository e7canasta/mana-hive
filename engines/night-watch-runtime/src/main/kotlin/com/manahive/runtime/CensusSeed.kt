package com.manahive.runtime

import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.messaging.NatsObjectMapper
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Donde esta alojado cada residente vigilado.
 *
 * Es **infraestructura, no clinica**, y por eso vive en un archivo aparte del
 * perfil: el sistema de registro sabe que a Elena hay que avisarle a los cinco
 * minutos, y no tiene por que saber en que camara la vemos. Meter la cama en
 * [com.manahive.profile.api.ResidentProfileDto] hubiera ensuciado un contrato
 * publicado con un detalle de nuestro despliegue.
 *
 * La observacion llega por **cama** (`perception.observation.v1.<bed>`) y la
 * politica por **residente**. Este archivo es el unico lugar donde se cruzan.
 */
public data class CensusEntryDto(
    val resident: String,
    val bed: String,
    val night: String,
    val monitor: String,
)

/**
 * Carga el censo desde disco, junto a los perfiles.
 *
 * Reemplaza a un censo cableado en el arranque con dos residentes de ejemplo.
 * Eso alcanzaba para un blueprint y no para una habitacion real: instalar el
 * sistema requeria recompilar.
 */
class CensusSeed(
    private val census: Census,
    private val file: File,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = NatsObjectMapper.mapper

    /** Devuelve cuantas camas quedaron registradas. */
    fun load(): Int {
        if (!file.isFile) {
            log.warn(
                "No hay censo en {}: ningun residente queda vigilado hasta que llegue uno por el bus",
                file.absolutePath,
            )
            return 0
        }
        return try {
            val placements = mapper.readValue<List<CensusEntryDto>>(file.readText())
            placements.forEach {
                census.register(
                    BedId(it.bed), ResidentId(it.resident), NightId(it.night), MonitorId(it.monitor),
                )
                log.info("Censo: {} en cama {} ({})", it.resident, it.bed, it.monitor)
            }
            placements.size
        } catch (e: Exception) {
            // Sin censo no se puede vincular una observacion con un residente.
            // Es un error de instalacion y tiene que gritar, no degradarse.
            log.error("Censo ilegible en {}: {}", file.absolutePath, e.message)
            0
        }
    }
}
