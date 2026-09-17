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
        val centre = if (PerceptualBounds.isCarrierPerceptible(centreHz)) {
            centreHz
        } else {
            tuning.defaultPresetHz
        }

        val halfWidth = tuning.requiredWidthHz(carrierCount) / 2.0 + tuning.maxBeatRateHz / 2.0
        val low = (centre - halfWidth).coerceAtLeast(MIN_CARRIER_HZ)
        val high = (centre + halfWidth).coerceAtMost(PerceptualBounds.MAX_CARRIER_HZ - 1.0)

        return ListenerProfile(
            lowHz = low,
            highHz = high,
            source = ProfileSource.PRESET,
            createdAtEpochMillis = nowMillis,
        )
    }

    /** Low enough to be a deep hum, high enough to be heard at sleeping volume. */
    private const val MIN_CARRIER_HZ = 40.0
}
