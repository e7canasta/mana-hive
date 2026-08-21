package com.manahive.scene.service

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Imperative shell of the scene engine. All it does is wire:
 *  in  <- perception.observation.v1.>   (durable JetStream consumer "scene-engine")
 *  in  <- hub.census.snapshot.v1        (occupant binding)
 *  out -> scene.fact.v1.<bed>           (facts the twin states)
 * plus the sweep tick (in-process loop, NOT a cron: dwells are derived state)
 * and the DecisionRecord push to the hub. Domain logic lives in scene-domain.
 */
@SpringBootApplication
class SceneEngineApplication

fun main(args: Array<String>) {
    runApplication<SceneEngineApplication>(*args)
}
