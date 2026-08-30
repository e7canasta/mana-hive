package com.manahive.runtime

/**
 * The contract of the night-watch service.
 *
 * Composed from fine-grained interfaces:
 * - [ObservationSink] — receives observations
 * - [PolicyChangeSink] — receives policy changes
 * - [ProfileSink] — receives profile changes
 * - [TimeSink] — time control
 *
 * Plus one periodic action: [sweep].
 */
interface NightWatchServiceContract : ObservationSink, PolicyChangeSink, ProfileSink, TimeSink {
    fun sweep()
}
