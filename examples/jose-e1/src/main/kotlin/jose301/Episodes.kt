package jose301

import com.manahive.scene.bdd.Episode
import com.manahive.scene.bdd.episode
import com.manahive.scene.testdata.sittingEpisode

// ── Episode E1: José se sienta 17 minutos ───────────────────────────────

val e1: Episode = jose.sittingEpisode(
    inBed = "0s",
    sitting = "1h15m",
    backInBed = "1h32m",
)
