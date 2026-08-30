package jose301

import com.manahive.contracts.scene.PersonState
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.scene.bdd.BddContext
import com.manahive.scene.core.DigitalTwin
import com.manahive.scene.core.SignalHealth
import java.time.Instant

// ── Constants ───────────────────────────────────────────────────────────────

val BED_4 = BedId("bed-4")
val NIGHT = NightId("night-jose-301")
val JOSE = ResidentId("jose")
val MONITOR = MonitorId("m1")
val START = Instant.parse("2024-01-15T22:00:00Z")

// ── Person States (compartidos por Main y MainFromJson) ─────────────────────

val Unknown = PersonState.Unknown(com.manahive.contracts.scene.UnknownCause.SCENE)
val Lying = PersonState.Lying
val SittingInBed = PersonState.SittingInBed

// ── Time Parser ─────────────────────────────────────────────────────────────

fun t(offset: String): Instant {
    val regex = Regex("""(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?""")
    val match = regex.matchEntire(offset) ?: error("Invalid offset: $offset")
    val hours = match.groupValues[1].toLongOrNull() ?: 0
    val minutes = match.groupValues[2].toLongOrNull() ?: 0
    val seconds = match.groupValues[3].toLongOrNull() ?: 0
    return START.plusSeconds(hours * 3600 + minutes * 60 + seconds)
}

// ── Initial Twin ────────────────────────────────────────────────────────────

fun initialTwin(at: Instant = START): DigitalTwin = DigitalTwin(
    bed = BED_4,
    night = NIGHT,
    occupant = JOSE,
    state = PersonState.Unknown(com.manahive.contracts.scene.UnknownCause.SCENE),
    stateSince = at,
    signal = SignalHealth(MONITOR, at.minusSeconds(60), false),
)

// ── BDD Context ─────────────────────────────────────────────────────────────

val jose = BddContext(
    bed = BED_4,
    monitor = MONITOR,
    start = START,
    initialTwin = ::initialTwin,
    timeParser = ::t,
)
