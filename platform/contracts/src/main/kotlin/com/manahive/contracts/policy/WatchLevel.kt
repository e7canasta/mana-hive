package com.manahive.contracts.policy

/**
 * El nivel de vigilancia de un residente: lo único que el director elige.
 *
 * Es la salida del árbol de decisión de `docs/DECISION-TREE.md`, y selecciona
 * el catálogo que fija los tiempos. El director no teclea minutos — elige un
 * nivel, y lo cambia cuando el residente cambia.
 *
 * NO confundir con [RiskLevel]. Son ejes distintos y se cruzan:
 * - [RiskLevel] describe al residente — qué tan frágil es. Es una entrada.
 * - [WatchLevel] describe la decisión clínica — qué hacemos al respecto.
 *
 * Una residente de riesgo alto puede estar en STANDARD porque la familia
 * rechazó el monitoreo; una de riesgo bajo puede estar en CRITICAL por
 * post-operatorio. Mapear uno sobre el otro destruye esa distinción.
 *
 * El orden del enum es el de protección creciente: se usa para el desempate
 * "gana la capa más protectora".
 */
public enum class WatchLevel(public val label: String) {
    /** Nivel 0 — sólo observar, sin alertas. */
    STANDARD("standard"),

    /** Nivel 1 — si se despierta de noche, avisar. */
    NIGHT_WANDERING("night-wandering"),

    /** Nivel 2 — si se mueve, avisar rápido. */
    FALL_RISK("fall-risk"),

    /** Nivel 3 — alerta inmediata ante cualquier movimiento. */
    CRITICAL("critical"),

    ;

    public companion object {
        /** Parsea desde la etiqueta usada en TOML y en los perfiles. */
        public fun fromLabel(label: String): WatchLevel? =
            entries.firstOrNull { it.label == label }
    }
}
