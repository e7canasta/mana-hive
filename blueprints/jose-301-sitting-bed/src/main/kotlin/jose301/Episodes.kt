package jose301

import com.manahive.contracts.perception.ObservationKind.IN_BED
import com.manahive.contracts.perception.ObservationKind.IN_BATHROOM
import com.manahive.contracts.perception.ObservationKind.IN_ROOM
import com.manahive.contracts.perception.ObservationKind.SITTING_IN_BED
import com.manahive.contracts.perception.ObservationKind.STANDING
import com.manahive.scene.bdd.Episode
import com.manahive.scene.bdd.episode
import com.manahive.scene.testdata.bathroomJourney
import com.manahive.scene.testdata.sittingEpisode

// ── Episodes: José's night ──────────────────────────────────────────────────

val e1: Episode = jose.sittingEpisode(
    inBed = "0s",
    sitting = "1h15m",
    backInBed = "1h32m",
)

val e2: Episode = jose.bathroomJourney(
    sitting = "2h47m",
    standing = "2h48m",
    inBathroom = "2h50m",
    inRoom = "3h00m",
    backInBed = "3h02m",
)

val e3: Episode = jose.bathroomJourney(
    sitting = "4h32m",
    standing = "4h33m",
    inBathroom = "4h35m",
    inRoom = "5h00m",
    backInBed = "5h03m",
)

// e4 y e6 llevan `2s` de separación entre acostado y sentado a propósito.
// Antes las dos observaciones caían en el MISMO instante: la hysteresis de
// LYING → SITTING_IN_BED (BED_TRANSITION, 1500 ms en TransitionTable) suprimía
// la transición y el episodio no ocurría — dos de las seis noches de José eran
// silenciosamente inertes. Los 2s la superan; si BED_TRANSITION sube de 2000 ms
// vuelven a serlo, y el blueprint pasa a esperar 1 transición en vez de 3.
val e4: Episode = jose.sittingEpisode(
    inBed = "5h50m",
    sitting = "5h50m2s",
    backInBed = "5h54m",
)

val e5: Episode = jose.bathroomJourney(
    sitting = "7h20m",
    standing = "7h21m",
    inBathroom = "7h23m",
    inRoom = "7h43m",
    backInBed = "7h46m",
)

val e6: Episode = jose.sittingEpisode(
    inBed = "8h35m",
    sitting = "8h35m2s",
    backInBed = "8h38m",
)
