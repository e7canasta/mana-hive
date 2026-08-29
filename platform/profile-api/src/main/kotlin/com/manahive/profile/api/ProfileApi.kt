package com.manahive.profile.api

/**
 * La novedad: el sistema de registro nos avisa que un perfil cambio.
 *
 * Trae el perfil **entero**, nunca un delta. Si el evento se pierde, el arranque
 * en frio via [ProfileEndpoints.activeProfiles] lo recupera: por eso las dos
 * vias son obligatorias y no alternativas.
 *
 * @property at instante ISO-8601 en que el sistema de registro publico el cambio
 */
public data class ResidentProfileChanged(
    public val at: String,
    public val profile: ResidentProfileDto,
)

/**
 * Los endpoints que el sistema de registro tiene que exponer.
 *
 * Esta interfaz no se implementa de este lado: esta escrita para fijar la firma
 * que el otro equipo tiene que cumplir. El jar existe para que la compilen, en
 * vez de deducirla de un ejemplo de JSON.
 *
 * Ruta base sugerida: `/api/profiles`.
 */
public interface ProfileEndpoints {

    /**
     * `GET /api/profiles?active=true`
     *
     * Todos los perfiles vigentes. Es la consulta del **arranque en frio**: sin
     * esto, despues de un reinicio el sistema queda ciego hasta que alguien
     * vuelva a tocar una politica a mano.
     */
    public fun activeProfiles(): List<ResidentProfileDto>

    /**
     * `GET /api/profiles/{residentId}`
     *
     * El perfil vigente de un residente.
     */
    public fun current(residentId: String): ResidentProfileDto?

    /**
     * `GET /api/profiles/{residentId}/versions`
     *
     * El historial completo, de la mas nueva a la mas vieja.
     */
    public fun versions(residentId: String): List<ResidentProfileDto>

    /**
     * `GET /api/profiles/{residentId}?at={instant}`
     *
     * Que regia en un instante dado. **Es la consulta del auditor**, y es la
     * razon por la que las versiones son inmutables: si la version 7 se hubiera
     * mutado al publicar la 8, esta pregunta no tendria respuesta.
     */
    public fun asOf(residentId: String, at: String): ResidentProfileDto?

    /**
     * `PUT /api/profiles/{residentId}`
     *
     * Publicar una version nueva. Recibe el perfil **entero**; no existe un
     * endpoint para tocar un umbral suelto, a proposito.
     */
    public fun publish(residentId: String, profile: ResidentProfileDto): ResidentProfileDto
}
