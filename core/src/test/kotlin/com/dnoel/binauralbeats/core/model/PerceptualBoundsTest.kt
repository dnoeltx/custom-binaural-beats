package com.dnoel.binauralbeats.core.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T008b: the perceptible bounds for a binaural beat (FR-002).
 *
 * Sources for the values are recorded in research.md: a binaural beat is perceived
 * only when each carrier is below roughly 1000 Hz and the difference between the ears
 * is below roughly 30 Hz. Above either bound the listener hears something else, so
 * producing audio there would not be a binaural beat at all.
 *
 * The matching assertion that the scheduler can never leave these bounds arrives with
 * T021, once a scheduler exists.
 */
class PerceptualBoundsTest {

    @Test
    fun `a carrier within the bound is accepted`() {
        assertTrue(PerceptualBounds.isCarrierPerceptible(400.0))
        assertTrue(PerceptualBounds.isCarrierPerceptible(120.0))
    }

    @Test
    fun `a carrier at or above the upper bound is rejected`() {
        assertFalse(PerceptualBounds.isCarrierPerceptible(PerceptualBounds.MAX_CARRIER_HZ))
        assertFalse(PerceptualBounds.isCarrierPerceptible(1500.0))
    }

    @Test
    fun `a non-positive carrier is rejected`() {
        assertFalse(PerceptualBounds.isCarrierPerceptible(0.0))
        assertFalse(PerceptualBounds.isCarrierPerceptible(-100.0))
    }

    @Test
    fun `a beat rate within the bound is accepted`() {
        assertTrue(PerceptualBounds.isBeatRatePerceptible(2.0))
        assertTrue(PerceptualBounds.isBeatRatePerceptible(12.0))
    }

    @Test
    fun `a beat rate at or above the upper bound is rejected`() {
        assertFalse(PerceptualBounds.isBeatRatePerceptible(PerceptualBounds.MAX_BEAT_RATE_HZ))
        assertFalse(PerceptualBounds.isBeatRatePerceptible(40.0))
    }

    @Test
    fun `a pair is valid only when both carriers and their difference are perceptible`() {
        assertTrue(PerceptualBounds.isPairPerceptible(leftHz = 200.0, rightHz = 203.0))
        assertFalse(PerceptualBounds.isPairPerceptible(leftHz = 200.0, rightHz = 260.0))
        assertFalse(PerceptualBounds.isPairPerceptible(leftHz = 1200.0, rightHz = 1203.0))
    }

    @Test
    fun `identical carriers produce no beat and are rejected`() {
        assertFalse(PerceptualBounds.isPairPerceptible(leftHz = 200.0, rightHz = 200.0))
    }
}
