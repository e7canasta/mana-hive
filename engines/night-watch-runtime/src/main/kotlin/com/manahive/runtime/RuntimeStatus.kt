package com.manahive.runtime

import com.manahive.messaging.BusEvents
import io.nats.client.Connection
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * En qué situación está el servicio, para que se pueda preguntar desde afuera.
 *
 * Esto es un sistema 24/7: "arrancó" no es lo mismo que "está vigilando". Un
 * servicio que levantó pero perdió el bus no está sirviendo, y hasta ahora eso
 * no se podía distinguir desde afuera — el proceso estaba vivo y parecía sano.
 */
enum class RuntimeState {
    /** Levantando el contexto. */
    STARTING,

    /** Vivo, pero sin bus: no llegan observaciones. No se vigila a nadie. */
    WAITING_FOR_BUS,

    /** Conectado y consumiendo. */
    RUNNING,

    /** Estaba corriendo y se cayó el bus. Reintentando. */
    DEGRADED,

    /** Apagándose. */
    STOPPED,
}

/**
 * El estado vigente del servicio y cómo llegó ahí.
 *
 * [since] permite responder *"¿cuánto hace que está así?"*, que es la pregunta
 * que importa a las tres de la mañana.
 */
data class RuntimeStatus(
    val state: RuntimeState,
    val since: Instant,
    val detail: String,
) {
    val healthy: Boolean get() = state == RuntimeState.RUNNING
}

/** Registro mutable del estado, seguro entre hilos. */
class RuntimeStatusHolder {
    private val ref = AtomicReference(
        RuntimeStatus(RuntimeState.STARTING, Instant.now(), "levantando"),
    )

    val current: RuntimeStatus get() = ref.get()

    fun transition(state: RuntimeState, detail: String) {
        val previous = ref.get()
        if (previous.state == state) return
        ref.set(RuntimeStatus(state, Instant.now(), detail))
    }
}

/** Traduce el estado de la conexión NATS a nuestro vocabulario. */
fun Connection.Status.toRuntimeState(): RuntimeState = when (this) {
    Connection.Status.CONNECTED -> RuntimeState.RUNNING
    Connection.Status.RECONNECTING, Connection.Status.DISCONNECTED -> RuntimeState.DEGRADED
    Connection.Status.CLOSED -> RuntimeState.STOPPED
    else -> RuntimeState.WAITING_FOR_BUS
}

