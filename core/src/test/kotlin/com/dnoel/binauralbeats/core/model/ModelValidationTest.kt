package com.dnoel.binauralbeats.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * T009: validation rules quoted from data-model.md.
 *
 * ListenerProfile: `lowHz > 0`, `lowHz < highHz`, both within the perceptible carrier
 * bound (FR-002), and the range wide enough to hold `carrierCount` carriers at the
 * minimum spacing (FR-004). The last rule depends on a measured spacing value (M003)
 * and a configured carrier count, so it is a query rather than a constructor invariant.
 */
class ModelValidationTest {

    // --- ListenerProfile ---

    @Test
    fun `a valid profile is accepted`() {
        val profile = ListenerProfile(
            lowHz = 150.0,
            highHz = 260.0,
            source = ProfileSource.CALIBRATED,
            createdAtEpochMillis = 1_000L,
        )
        assertEquals(150.0, profile.lowHz)
        assertEquals(110.0, profile.widthHz)
    }

    @Test
    fun `a profile with a non-positive low bound is rejected`() {
        assertThrows<IllegalArgumentException> { profile(lowHz = 0.0, highHz = 260.0) }
        assertThrows<IllegalArgumentException> { profile(lowHz = -10.0, highHz = 260.0) }
    }

    @Test
    fun `a profile whose low bound is not below its high bound is rejected`() {
        assertThrows<IllegalArgumentException> { profile(lowHz = 260.0, highHz = 260.0) }
        assertThrows<IllegalArgumentException> { profile(lowHz = 300.0, highHz = 260.0) }
    }

    @Test
    fun `a profile outside the perceptible carrier bound is rejected`() {
        assertThrows<IllegalArgumentException> {
            profile(lowHz = 900.0, highHz = PerceptualBounds.MAX_CARRIER_HZ + 1.0)
        }
    }

    @Test
    fun `a profile reports whether it can hold the requested carriers at a spacing`() {
        val profile = profile(lowHz = 200.0, highHz = 260.0) // 60 Hz wide

        // Three carriers need two gaps, so 2 x 25 Hz = 50 Hz fits inside 60 Hz.
        assertTrue(profile.fitsCarriers(count = 3, minSpacingHz = 25.0))
        // 2 x 40 Hz = 80 Hz does not.
        assertFalse(profile.fitsCarriers(count = 3, minSpacingHz = 40.0))
        // Two carriers need only one gap.
        assertTrue(profile.fitsCarriers(count = 2, minSpacingHz = 40.0))
    }

    @Test
    fun `a single carrier always fits`() {
        assertTrue(profile(lowHz = 200.0, highHz = 201.0).fitsCarriers(count = 1, minSpacingHz = 50.0))
    }

    @Test
    fun `fitsCarriers rejects a nonsensical request`() {
        val profile = profile(lowHz = 200.0, highHz = 260.0)
        assertThrows<IllegalArgumentException> { profile.fitsCarriers(count = 0, minSpacingHz = 10.0) }
        assertThrows<IllegalArgumentException> { profile.fitsCarriers(count = 3, minSpacingHz = -1.0) }
    }

    // --- SessionConfiguration ---

    @Test
    fun `configuration defaults match the specification`() {
        val config = SessionConfiguration()
        assertEquals(BeatArc.DESCEND_THEN_HOLD, config.beatArc)
        assertEquals(EndBehavior.RunUntilStopped, config.endBehavior)
        assertEquals(3, config.carrierCount)
        assertFalse(config.volumeWarningAcknowledged)
    }

    @Test
    fun `a carrier count outside two or three is rejected`() {
        assertThrows<IllegalArgumentException> { SessionConfiguration(carrierCount = 1) }
        assertThrows<IllegalArgumentException> { SessionConfiguration(carrierCount = 4) }
    }

    @Test
    fun `an end behavior with a non-positive duration is rejected`() {
        assertThrows<IllegalArgumentException> { EndBehavior.AfterDuration(durationMillis = 0L) }
        assertThrows<IllegalArgumentException> { EndBehavior.AfterDuration(durationMillis = -1L) }
    }

    // --- TimeOfDay, defined here rather than borrowed from the platform ---

    @Test
    fun `a time of day accepts every valid wall clock value`() {
        assertEquals(0, TimeOfDay(0, 0).hour)
        assertEquals(59, TimeOfDay(23, 59).minute)
    }

    @Test
    fun `a time of day rejects impossible values`() {
        assertThrows<IllegalArgumentException> { TimeOfDay(24, 0) }
        assertThrows<IllegalArgumentException> { TimeOfDay(-1, 0) }
        assertThrows<IllegalArgumentException> { TimeOfDay(12, 60) }
    }

    // --- AppState ---

    @Test
    fun `a fresh app state has no profile, no calibration and no last session`() {
        val state = AppState()
        assertEquals(AppState.CURRENT_SCHEMA_VERSION, state.schemaVersion)
        assertEquals(null, state.profile)
        assertEquals(null, state.calibrationInProgress)
        assertEquals(null, state.lastSession)
        assertEquals(SessionConfiguration(), state.settings)
    }

    private fun profile(lowHz: Double, highHz: Double) = ListenerProfile(
        lowHz = lowHz,
        highHz = highHz,
        source = ProfileSource.CALIBRATED,
        createdAtEpochMillis = 0L,
    )
}
