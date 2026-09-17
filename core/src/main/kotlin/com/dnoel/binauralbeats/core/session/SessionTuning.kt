package com.dnoel.binauralbeats.core.session

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Every number the engine needs that was decided by listening rather than by reasoning.
 *
 * These are **one listener's perceptual thresholds** (research.md, "Measured values").
 * Spacing, drift tolerance and fade length vary with hearing, age, earbuds and fit, so a
 * later version could measure each listener's own. That stays cheap only while nothing
 * embeds these as literals, which is why they travel as a parameter rather than living as
 * constants inside the scheduler. [MEASURED] is a default, not a definition.
 */
data class SessionTuning(

    /** M001: the shortest fade that is not noticeable starting or stopping. */
    val fadeDuration: Duration,

    /**
     * M003: below this, simultaneous carriers beat against each other and sound rough.
     * Matches the auditory critical band around a 200 Hz centre.
     */
    val minCarrierSpacingHz: Double,

    /** M005: audibility begins near 60 Hz per minute, so this sits well below it. */
    val maxDriftHzPerMinute: Double,

    /** M004: the top of the acceptable band, where the night starts. */
    val openingBeatRateHz: Double,

    /** M004: the settling band the night descends into and holds. */
    val holdBeatRateHz: Double,

    /** How long the opening descent takes. */
    val beatDescentDuration: Duration,

    /** M004: nothing at or above this was tolerable. Enforced, not merely intended. */
    val maxBeatRateHz: Double,

    /** Rate limits, so no parameter can jump even when something asks it to. */
    val maxGainChangePerSecond: Double,
    val maxBeatRateChangePerSecond: Double,

    /** The presets offered before calibration, and which one is chosen by default. */
    val presetPitchesHz: List<Double>,
    val defaultPresetIndex: Int,
) {
    init {
        require(fadeDuration > Duration.ZERO) { "fadeDuration must be positive" }
        require(minCarrierSpacingHz > 0.0) { "minCarrierSpacingHz must be positive" }
        require(maxDriftHzPerMinute > 0.0) { "maxDriftHzPerMinute must be positive" }
        require(openingBeatRateHz >= holdBeatRateHz) { "the arc descends, so opening >= hold" }
        require(openingBeatRateHz < maxBeatRateHz) { "the opening rate must stay under the maximum" }
        require(holdBeatRateHz > 0.0) { "holdBeatRateHz must be positive" }
        require(defaultPresetIndex in presetPitchesHz.indices) { "defaultPresetIndex is out of range" }
    }

    val defaultPresetHz: Double get() = presetPitchesHz[defaultPresetIndex]

    /** The width a range needs to hold [count] carriers at this spacing. */
    fun requiredWidthHz(count: Int): Double = minCarrierSpacingHz * (count - 1)

    companion object {

        /**
         * The values measured on 2026-09-16, with the app's intended listener, on the
         * earbuds he sleeps in. See research.md for the method and the reasoning, and for
         * why two of these were re-measured after the first answers landed on the edge of
         * the candidate set.
         */
        val MEASURED = SessionTuning(
            fadeDuration = 20.seconds,
            minCarrierSpacingHz = 100.0,
            maxDriftHzPerMinute = 10.0,
            // Revised 2026-09-17. The listening test put 1 to 2 Hz best and 3 "fine", and
            // this held at 1.75. Then the track the listener actually sleeps to turned out
            // to run at 3.2 Hz, constantly. A judgment made over hours of real use beats
            // a 25 second impression, and 3 Hz was acceptable in the test as well, so the
            // night now settles at 3 rather than 1.75.
            openingBeatRateHz = 3.5,
            holdBeatRateHz = 3.0,
            beatDescentDuration = 25.minutes,
            maxBeatRateHz = 4.0,
            maxGainChangePerSecond = 0.1,
            maxBeatRateChangePerSecond = 0.01,
            presetPitchesHz = listOf(100.0, 200.0, 400.0),
            defaultPresetIndex = 1,
        )
    }
}
