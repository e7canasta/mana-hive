package com.manahive.simulator

/**
 * Release 1: prints the scenario bank so clinicians can review the canon.
 * Sprint 1 story S7 turns this into the scenario runner (virtual clock,
 * in-memory transport, expectations verified).
 */
fun main() {
    Scenarios.bank.forEach { scenario ->
        println("── ${scenario.name} ──")
        scenario.steps.forEach { println("   ${it.at}  $it") }
    }
}
