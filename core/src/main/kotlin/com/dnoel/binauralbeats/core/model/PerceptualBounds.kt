package com.dnoel.binauralbeats.core.model

/**
 * The limits within which a binaural beat exists at all (FR-002).
 *
 * Published work reports that a binaural beat is perceived only when each carrier is
 * below roughly 1000 Hz, best around 400 Hz, and when the difference between the two
 * ears is below roughly 30 Hz. Beyond either limit the listener hears two separate
 * tones rather than one beating tone. Sources are recorded in research.md.
 *
 * These are perceptual ceilings, not preferences. A listener's preferred range is a
 * much narrower band inside them, found by calibration.
 */
object PerceptualBounds {

    /** Carriers at or above this frequency do not produce a perceptible beat. */
    const val MAX_CARRIER_HZ: Double = 1000.0

    /** Differences at or above this rate are heard as two tones, not one beat. */
    const val MAX_BEAT_RATE_HZ: Double = 30.0

    fun isCarrierPerceptible(hz: Double): Boolean = hz > 0.0 && hz < MAX_CARRIER_HZ

    /** A beat rate must be greater than zero: identical carriers produce no beat. */
    fun isBeatRatePerceptible(hz: Double): Boolean = hz > 0.0 && hz < MAX_BEAT_RATE_HZ

    fun isPairPerceptible(leftHz: Double, rightHz: Double): Boolean =
        isCarrierPerceptible(leftHz) &&
            isCarrierPerceptible(rightHz) &&
            isBeatRatePerceptible(kotlin.math.abs(leftHz - rightHz))
}
