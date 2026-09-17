package com.dnoel.binauralbeats.core.session

import com.dnoel.binauralbeats.core.model.ProfileSource
import com.dnoel.binauralbeats.core.model.SessionConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A preset is a promise: pick 200 Hz and the session should sound like 200 Hz.
 *
 * The first version built a range of 50 to 350 Hz for a 200 Hz preset, putting carriers
 * near 52, 202 and 352: one almost inaudible, one bright, and nothing like what the
 * listener chose. It used one carrier's spacing too many. Found by listening on hardware,
 * which is why this now lives in :core with a test.
 */
class PresetsTest {

    private val tuning = SessionTuning.MEASURED

    @Test
    fun `a preset centres the carriers on the chosen pitch`() {
        val profile = Presets.profileFor(200.0, tuning, carrierCount = 3, nowMillis = 0L)
        val centres = SessionScheduler(
            profile,
            SessionConfiguration(carrierCount = 3),
            tuning,
            seed = 1L,
        ).parametersAt(kotlin.time.Duration.ZERO).pairs.map { it.centreHz }

        assertEquals(3, centres.size)
        assertEquals(200.0, centres.average(), 1.0, "carriers should sit around the chosen pitch")
        assertTrue(centres.min() > 80.0, "lowest carrier ${centres.min()} is far below the preset")
        assertTrue(centres.max() < 320.0, "highest carrier ${centres.max()} is far above the preset")
    }

    @Test
    fun `a preset range is exactly wide enough for its carriers, and no wider`() {
        val profile = Presets.profileFor(200.0, tuning, carrierCount = 3, nowMillis = 0L)
        // Two gaps at the minimum spacing, plus the margin a pair needs around its centre.
        val expected = tuning.minCarrierSpacingHz * 2 + tuning.maxBeatRateHz
        assertEquals(expected, profile.widthHz, 0.001)
    }

    @Test
    fun `a two carrier preset is narrower than a three carrier one`() {
        val two = Presets.profileFor(200.0, tuning, carrierCount = 2, nowMillis = 0L)
        val three = Presets.profileFor(200.0, tuning, carrierCount = 3, nowMillis = 0L)
        assertTrue(two.widthHz < three.widthHz)
    }

    @Test
    fun `the lowest preset stays above zero and within the perceptible bound`() {
        for (pitch in tuning.presetPitchesHz) {
            val profile = Presets.profileFor(pitch, tuning, carrierCount = 3, nowMillis = 0L)
            assertTrue(profile.lowHz > 0.0, "preset $pitch produced a low bound of ${profile.lowHz}")
            assertTrue(profile.highHz < 1000.0, "preset $pitch produced ${profile.highHz}")
        }
    }

    @Test
    fun `a preset profile is marked as a preset, not as calibrated`() {
        val profile = Presets.profileFor(200.0, tuning, carrierCount = 3, nowMillis = 99L)
        assertEquals(ProfileSource.PRESET, profile.source)
        assertEquals(99L, profile.createdAtEpochMillis)
    }

    @Test
    fun `an unreasonable request falls back to the default preset rather than failing`() {
        val zero = Presets.profileFor(0.0, tuning, carrierCount = 3, nowMillis = 0L)
        val defaulted = Presets.profileFor(tuning.defaultPresetHz, tuning, carrierCount = 3, nowMillis = 0L)
        assertEquals(defaulted, zero)
    }
}
