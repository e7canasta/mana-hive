package com.manahive.runtime.control

/**
 * Control command for hive — received via NATS.
 * Subject: `test.hive.v1` (test) / `hive.command.v1` (prod).
 *
 * Fowler: Command pattern — intent, not mechanism.
 */
data class HiveCommand(
    val action: String, // "reload" | "reset" | "resetFull"
    val residentId: String? = null,
    val bedId: String? = null,
    val reloadProfile: Boolean = true,
) {
    companion object {
        const val RELOAD = "reload"
        const val RESET = "reset"
        const val RESET_FULL = "resetFull"
    }
}
