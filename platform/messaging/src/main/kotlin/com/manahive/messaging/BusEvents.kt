package com.manahive.messaging

import io.nats.client.Connection

/**
 * Puente entre el hilo de NATS y el servicio, sin pasar por Spring.
 *
 * El listener de conexión se dispara en un hilo del cliente NATS **mientras el
 * contexto todavía se está armando**. Pedirle un bean ahí adentro deadlockea:
 * el listener espera al `nightWatchService`, que espera a la conexión, que es
 * justo la que está creándose. Esto son dos referencias volátiles que el
 * servicio completa cuando ya existe.
 */
public class BusEvents {
    private val onConnectedHandlers = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    private val onLostHandlers = java.util.concurrent.CopyOnWriteArrayList<(String) -> Unit>()

    /**
     * La conexión, cuando exista.
     *
     * Es nullable a propósito: el servicio arranca **antes** que el bus y tiene
     * que poder responder "todavía no" en vez de no arrancar.
     */
    @Volatile
    public var connection: Connection? = null

    public val connected: Boolean
        get() = connection?.status == Connection.Status.CONNECTED

    /**
     * Se llama al conectar y en **cada reconexión**: las suscripciones de una
     * conexión caída no reviven solas, hay que rehacerlas. Varios componentes
     * pueden engancharse — la ingesta y el egress son independientes.
     */
    public fun onConnected(handler: () -> Unit) { onConnectedHandlers += handler }

    public fun onLost(handler: (String) -> Unit) { onLostHandlers += handler }

    public fun fireConnected() { onConnectedHandlers.forEach { it() } }

    public fun fireLost(reason: String) { onLostHandlers.forEach { it(reason) } }
}
