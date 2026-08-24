package com.manahive.recorder.service

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Imperative shell of the recorder engine. Wires:
 *  in  <- scene.fact.v1.>            (triggers recording from scene facts)
 *  in  <- sentinel.signal.v1.>       (triggers recording from sentinel signals)
 *  out -> recorder.command.v1.<bed>  (recording commands for NVR adapter)
 *  out -> evidence.record.v1.<bed>   (evidence records for tracking)
 * Domain logic lives in recorder-domain.
 */
@SpringBootApplication
class RecorderApplication

fun main(args: Array<String>) {
    runApplication<RecorderApplication>(*args)
}
