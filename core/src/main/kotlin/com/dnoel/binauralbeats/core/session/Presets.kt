package com.dnoel.binauralbeats.core.session

import com.dnoel.binauralbeats.core.model.ListenerProfile
import com.dnoel.binauralbeats.core.model.PerceptualBounds
import com.dnoel.binauralbeats.core.model.ProfileSource

/**
 * The profile behind a preset choice, for a listener who starts before calibrating
 * (FR-023).
 *
 * A preset must sound like the pitch it names, so the range is built as narrow as the
 * carriers allow: enough room for `count - 1` gaps at the minimum spacing, plus the
 * margin a pair needs around its own centre, and nothing more. A wider range spreads the
 * carriers away from the chosen pitch, which is what made a 200 Hz preset play a tone
 * near 50 Hz on the first hardware run.
 */
object Presets {

    fun profileFor(
        centreHz: Double,
        tuning: SessionTuning,
        carrierCount: Int,
        nowMillis: Long,
    ): ListenerProfile {
        val requested = if (PerceptualBounds.isCarrierPerceptible(centreHz)) {
            centreHz
        } else {
            tuning.defaultPresetHz
        }

        val lowest = MIN_CARRIER_HZ
        val highest = PerceptualBounds.MAX_CARRIER_HZ - 1.0
        val centre = requested.coerceIn(lowest + MIN_HALF_WIDTH_HZ, highest - MIN_HALF_WIDTH_HZ)

        // How much room there is on the tighter side. The range is built symmetrically
        // around the chosen pitch, so the narrower side governs both.
        val availableHalf = minOf(centre - lowest, highest - centre)

        // As many carriers as fit, honestly centred, rather than three carriers and a
        // range shoved sideways. A 100 Hz preset cannot hold three carriers 100 Hz apart
        // without going below zero, so it takes two.
        //
        // The previous version clamped a fixed half width at the bottom, which moved the
        // whole band upward: "Low 100 Hz" became a range of 40 to 202 Hz and played tones
        // near 56 and 186 Hz. Found by reading the record of a real night, not by a test,
        // because no test knew what a preset was supposed to sound like.
        val fittingCount = (carrierCount downTo 1).firstOrNull { halfWidthFor(it, tuning) <= availableHalf } ?: 1
        val halfWidth = halfWidthFor(fittingCount, tuning)
            .coerceAtMost(availableHalf)
            .coerceAtLeast(MIN_HALF_WIDTH_HZ)

        return ListenerProfile(
            lowHz = centre - halfWidth,
            highHz = centre + halfWidth,
            source = ProfileSource.PRESET,
            createdAtEpochMillis = nowMillis,
        )
    }

    /** Room for the gaps between carriers, plus the margin a pair needs around its centre. */
    private fun halfWidthFor(count: Int, tuning: SessionTuning): Double =
        tuning.requiredWidthHz(count) / 2.0 + tuning.maxBeatRateHz / 2.0

    /** Keeps a profile from collapsing to zero width at the very edge of the range. */
    private const val MIN_HALF_WIDTH_HZ = 4.0

    /** Low enough to be a deep hum, high enough to be heard at sleeping volume. */
    private const val MIN_CARRIER_HZ = 40.0
}
