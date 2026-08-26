package jose301recording

import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.PersonState
import com.manahive.recorder.*
import com.manahive.recorder.bdd.RecorderContext
import com.manahive.recorder.bdd.scenario
import com.manahive.recorder.testdata.fullCalibration
import java.time.Instant

val BED_4 = com.manahive.kernel.BedId("bed-4")
val JOSE = com.manahive.kernel.ResidentId("jose")
val CAM_MAIN = com.manahive.kernel.MonitorId("CAMERA_MAIN")
val CAM_CORRIDOR = com.manahive.kernel.MonitorId("CAMERA_CORRIDOR")
val START = Instant.parse("2024-01-15T22:00:00Z")

fun recorderCtx(cal: RecordingCalibration) = RecorderContext(
    bed = BED_4,
    resident = JOSE,
    calibration = cal,
)

val configBasica = com.manahive.recorder.testdata.testCalibration("jose")
val configCompleta = fullCalibration("jose")
