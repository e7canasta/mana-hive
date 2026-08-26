package com.manahive.recorder.service

import com.manahive.contracts.policy.Severity
import com.manahive.kernel.MonitorId
import com.manahive.messaging.NatsClientConfiguration
import com.manahive.recorder.Quality
import com.manahive.recorder.RecorderEngine
import com.manahive.recorder.RecordingCalibration
import com.manahive.recorder.createRecorderEngine
import com.manahive.recorder.recordingCalibration
import org.springframework.context.annotation.Bean
import java.time.Duration
import org.springframework.context.annotation.Import
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
@Import(NatsClientConfiguration::class)
class RecorderApplication {

    /**
     * Calibracion por defecto del grabador.
     *
     * RecorderNatsIngest pedia un RecorderEngine que ningun modulo declaraba,
     * asi que este servicio no arrancaba. Es el mismo default provisional que
     * usa harbor con su HarborCalibration: sirve para que el motor exista y
     * consuma del bus. La calibracion real llega por politica.
     */
    @Bean
    fun recordingCalibration(): RecordingCalibration = recordingCalibration {
        resident("default")

        rule("r-episode-critical") {
            trigger { episodeOpened(Severity.CRITICAL) }
            recordingWindow {
                before = Duration.ofSeconds(30)
                after = Duration.ofMinutes(2)
            }
            quality = Quality.FULL
            monitors = listOf(MonitorId("CAMERA_MAIN"))
        }
    }

    @Bean
    fun recorderEngine(calibration: RecordingCalibration): RecorderEngine =
        createRecorderEngine(calibration)
}

fun main(args: Array<String>) {
    runApplication<RecorderApplication>(*args)
}
