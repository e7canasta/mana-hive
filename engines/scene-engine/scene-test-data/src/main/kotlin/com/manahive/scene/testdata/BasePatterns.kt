package com.manahive.scene.testdata

import com.manahive.contracts.perception.ObservationKind
import com.manahive.scene.bdd.BddContext
import com.manahive.scene.bdd.Episode
import com.manahive.scene.bdd.EpisodeBuilder
import com.manahive.scene.ObservedAt

// ── Helpers ──────────────────────────────────────────────────────────────────

fun BddContext.obs(kind: ObservationKind, at: String, confidence: Double): ObservedAt {
    val instant = timeParser(at)
    return ObservedAt(
        com.manahive.contracts.perception.Observation(
            sourceEventId = "pattern-${instant.toEpochMilli()}",
            monitor = monitor,
            bed = bed,
            kind = kind,
            confidence = confidence,
            observedAt = instant,
        ),
        instant,
    )
}

// ── Base Patterns: known-good observation sequences ──────────────────────────
//
// These are the "design system" blocks for scene testing.
// Each pattern represents a real-world behavior we've validated.

/**
 * Full bathroom journey: SITTING → STANDING → IN_BATHROOM → IN_ROOM → IN_BED.
 */
fun BddContext.bathroomJourney(
    sitting: String,
    standing: String,
    inBathroom: String,
    inRoom: String,
    backInBed: String,
): Episode = Episode(listOf(
    obs(ObservationKind.SITTING_IN_BED, sitting, 0.91),
    obs(ObservationKind.STANDING, standing, 0.88),
    obs(ObservationKind.IN_BATHROOM, inBathroom, 0.85),
    obs(ObservationKind.IN_ROOM, inRoom, 0.87),
    obs(ObservationKind.IN_BED, backInBed, 0.93),
))

/**
 * Simple sitting episode: IN_BED → SITTING → IN_BED.
 */
fun BddContext.sittingEpisode(
    inBed: String,
    sitting: String,
    backInBed: String,
): Episode = Episode(listOf(
    obs(ObservationKind.IN_BED, inBed, 0.95),
    obs(ObservationKind.SITTING_IN_BED, sitting, 0.92),
    obs(ObservationKind.IN_BED, backInBed, 0.94),
))
