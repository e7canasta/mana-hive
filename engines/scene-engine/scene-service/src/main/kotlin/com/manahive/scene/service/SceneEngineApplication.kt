package com.manahive.scene.service

import com.manahive.messaging.NatsClientConfiguration
import org.springframework.context.annotation.Import
import com.manahive.scene.calibration.SceneCalibration
import com.manahive.scene.calibration.sceneCalibration
import com.manahive.scene.interpreter.SceneInterpreter
import com.manahive.scene.interpreter.createInterpreter
import com.manahive.scene.sweeper.ClockSweeper
import com.manahive.scene.sweeper.createSweeper
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.time.Duration

/**
 * Imperative shell of the scene engine. Wires:
 *  in  <- perception.observation.v1.>   (durable JetStream consumer "scene-engine")
 *  in  <- hub.census.snapshot.v1        (occupant binding)
 *  out -> scene.fact.v1.<bed>           (facts the twin states)
 * plus the sweep tick (in-process loop, NOT a cron: dwells are derived state)
 * and the DecisionRecord push to the hub. Domain logic lives in scene-domain.
 */
@SpringBootApplication
@Import(NatsClientConfiguration::class)
class SceneEngineApplication {

    @Bean
    fun sceneCalibration(): SceneCalibration = sceneCalibration {
        heartbeatTimeout = Duration.ofSeconds(90)
    }

    @Bean
    fun sceneInterpreter(calibration: SceneCalibration): SceneInterpreter =
        createInterpreter(calibration)

    @Bean
    fun clockSweeper(): ClockSweeper = createSweeper()
}

fun main(args: Array<String>) {
    runApplication<SceneEngineApplication>(*args)
}
