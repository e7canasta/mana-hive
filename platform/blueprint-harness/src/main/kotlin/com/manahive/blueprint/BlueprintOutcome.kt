package com.manahive.blueprint

/**
 * Lo que un blueprint concluyó, para que `main()` pueda terminar con un código
 * de salida y no sólo con un cartel.
 *
 * Un blueprint imprimía ❌ y salía con 0: los escenarios que documentan el
 * comportamiento acordado no podían romper nada, y `check` tampoco los corre
 * (ningún blueprint tiene source set de test). Así estuvo roto un sprint entero
 * `jose-301-sentinel-alerts`, sin abrir un solo episodio, en silencio.
 *
 * Vive acá y no dentro de un harness porque el veredicto de un blueprint no es
 * de Scene ni de Vigilancia: los dos harness reportan al mismo lugar, y un
 * blueprint que mezcle ambos tiene que sumar un total, no dos.
 *
 * Es estado mutable global, y lo es a propósito: un blueprint es un `main()` de
 * un solo hilo cuyo alcance es el proceso entero, y la alternativa —enhebrar
 * cada resultado hasta el final del main— se olvida sin hacer ruido, que es
 * exactamente lo que hay que evitar acá.
 */
public object BlueprintOutcome {
    private val failures = mutableListOf<String>()
    private var total = 0

    /** Registra los checks de un escenario. Lo llama el `report()` de cada harness. */
    public fun record(scenario: String, checks: List<Pair<String, Boolean>>) {
        total += checks.size
        checks.filterNot { it.second }.forEach { failures += "$scenario — ${it.first}" }
    }

    /** Imprime el total y termina el proceso: 0 si todo pasó, 1 si algo falló. */
    public fun summarize(): Nothing {
        println("═══════════════════════════════════════════════════════════════")
        if (failures.isEmpty()) {
            println("  ✅ DONE — $total checks, 0 fallidos")
            println("═══════════════════════════════════════════════════════════════")
            kotlin.system.exitProcess(0)
        }
        println("  ❌ FAILED — $total checks, ${failures.size} fallidos")
        failures.forEach { println("     • $it") }
        println("═══════════════════════════════════════════════════════════════")
        kotlin.system.exitProcess(1)
    }
}
