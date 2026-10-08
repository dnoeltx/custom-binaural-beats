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

    @Test
    fun `every preset is centred on the pitch it names`() {
        // Found on hardware 2026-10-08. The low preset produced a range of 40 to 202 Hz,
        // centred at 121 rather than 100, because the range was built as the centre plus
        // and minus a fixed half width and then clamped at the bottom. The clamp moved the
        // whole band upward, so "Low 100 Hz" played tones near 56 and 186 Hz.
        for (pitch in tuning.presetPitchesHz) {
            val profile = Presets.profileFor(pitch, tuning, carrierCount = 3, nowMillis = 0L)
            val centre = (profile.lowHz + profile.highHz) / 2
            assertEquals(pitch, centre, 1.0, "preset $pitch is centred at $centre")
        }
    }

    @Test
    fun `a preset near the bottom of the range uses fewer carriers rather than skewing`() {
        // 100 Hz cannot hold three carriers 100 Hz apart without going below zero, so it
        // takes two and stays honest about where it is centred.
        val profile = Presets.profileFor(100.0, tuning, carrierCount = 3, nowMillis = 0L)
        val scheduler = SessionScheduler(
            profile,
            SessionConfiguration(carrierCount = 3),
            tuning,
            seed = 1L,
        )

        assertEquals(2, scheduler.carrierCount)
        val centres = scheduler.parametersAt(kotlin.time.Duration.ZERO).pairs.map { it.centreHz }
        assertTrue(centres.min() > 40.0, "lowest carrier ${centres.min()} is still near the floor")
        assertEquals(100.0, centres.average(), 6.0, "carriers average ${centres.average()}")
    }

    @Test
    fun `every tone a preset produces stays near its named pitch`() {
        for (pitch in tuning.presetPitchesHz) {
            val profile = Presets.profileFor(pitch, tuning, carrierCount = 3, nowMillis = 0L)
            val range = SessionScheduler(
                profile,
                SessionConfiguration(),
                tuning,
                seed = 3L,
            ).toneRangeOver(kotlin.time.Duration.parse("2h"))

            assertTrue(
                range.start > pitch / 2.5 && range.endInclusive < pitch * 2.5,
                "preset $pitch played ${range.start} to ${range.endInclusive}",
            )
        }
    }
}
