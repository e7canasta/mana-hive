package com.manahive.scene.sweeper

import com.manahive.contracts.shared.seconds
import com.manahive.contracts.scene.SceneFact.SignalLost
import com.manahive.contracts.scene.StateKind
import com.manahive.scene.support.SceneTestDsl.bed
import com.manahive.scene.support.SceneTestDsl.maria
import com.manahive.scene.support.SceneTestDsl.time03_00_00
import com.manahive.scene.support.SceneTestDsl.time02_58_00
import com.manahive.scene.support.SceneTestDsl.time02_59_30
import com.manahive.scene.support.SceneTestDsl.time03_00_05
import com.manahive.scene.calibration.dsl.dwellCatalog
import com.manahive.scene.sweeper.ClockSweeperImpl
import com.manahive.scene.sweeper.DwellMarks
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * SE-12 · El Reloj detecta sensor perdido
 *
 * Patron: Domain Event (Vernon) — SignalLost es un evento que cambia la confiabilidad
 */
class ClockSweeperSignalLostSpec : BehaviorSpec({

    Given("un reloj") {
        val sweeper = ClockSweeperImpl()

        And("un gemelo con lastHeartbeat = hace 2 min") {
            val twin = (bed(3) occupiedBy maria at StateKind.LYING since time03_00_00)
                .let { it.copy(signal = it.signal.copy(lastHeartbeat = time02_58_00)) }

            And("heartbeatTimeout = 90s") {
                val catalog = dwellCatalog {
                    heartbeat { timeout = 90.seconds }
                }
                val marks = DwellMarks(emptySet())

                When("sweep a las 03:00:00") {
                    val result = sweeper.sweep(listOf(twin), time03_00_05, catalog, marks)

                    Then("emite SignalLost") {
                        result.value.facts.any { it is SignalLost } shouldBe true
                    }

                    Then("signal.lost es true") {
                        result.value.facts.any { it is SignalLost } shouldBe true
                    }
                }
            }
        }

        And("un gemelo con lastHeartbeat = hace 30s") {
            val twin = (bed(3) occupiedBy maria at StateKind.LYING since time03_00_00)
                .let { it.copy(signal = it.signal.copy(lastHeartbeat = time02_59_30)) }

            And("heartbeatTimeout = 90s") {
                val catalog = dwellCatalog {
                    heartbeat { timeout = 90.seconds }
                }
                val marks = DwellMarks(emptySet())

                When("sweep a las 03:00:00") {
                    val result = sweeper.sweep(listOf(twin), time03_00_05, catalog, marks)

                    Then("no emite SignalLost") {
                        result.value.facts.any { it is SignalLost } shouldBe false
                    }
                }
            }
        }
    }
})
