package com.dnoel.binauralbeats.core.calibration

import com.dnoel.binauralbeats.core.model.ToneJudgment
import com.dnoel.binauralbeats.core.model.Verdict
import com.dnoel.binauralbeats.core.session.SessionTuning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T046 and T047a: the sittings that do not produce a usable answer, and the width rule.
 *
 * The spec's edge case is explicit: a listener who rejects everything and one who accepts
 * everything both yield no fabricated range, and the caller offers a retry or a preset. A
 * calibration that invents a band from bad data is worse than one that admits it failed,
 * because the listener then sleeps to something chosen by an accident.
 */
class CalibrationEdgeCaseTest {

    private val settings = CalibrationSettings.DEFAULT
    private val tuning = SessionTuning.MEASURED

    private fun sittingWhere(verdict: (Double) -> Verdict): CalibrationSearchState {
        var state = CalibrationSearch.start(settings)
        while (true) {
            val tone = CalibrationSearch.next(state) ?: return state
            state = CalibrationSearch.accept(state, ToneJudgment(tone, verdict(tone), 0L, 900L))
        }
    }

    @Test
    fun `rejecting everything yields no profile rather than an invented one`() {
        val state = sittingWhere { Verdict.NOT_RELAXING }
        assertNull(CalibrationSearch.result(state, tuning, 0L))
    }

    @Test
    fun `accepting everything yields no profile either`() {
        // Someone tapping through without listening looks exactly like this, and the whole
        // usable range is not a preference.
        val state = sittingWhere { Verdict.RELAXING }
        assertNull(CalibrationSearch.result(state, tuning, 0L))
    }

    @Test
    fun `a failed sitting says why, so the caller can offer a retry or a preset`() {
        val outcome = CalibrationSearch.outcome(sittingWhere { Verdict.NOT_RELAXING }, tuning, 0L)
        assertEquals(CalibrationOutcome.NothingAccepted, outcome)

        val everything = CalibrationSearch.outcome(sittingWhere { Verdict.RELAXING }, tuning, 0L)
        assertEquals(CalibrationOutcome.EverythingAccepted, everything)
    }

    @Test
    fun `a band too narrow for two carriers is widened, not returned as found`() {
        // FR-020a. The measured minimum spacing is 100 Hz, so a listener who likes only a narrow
        // region around 190 Hz still needs a range wide enough to hold two carriers.
        val state = sittingWhere { hz -> if (hz in 180.0..200.0) Verdict.RELAXING else Verdict.NOT_RELAXING }
        val profile = CalibrationSearch.result(state, tuning, 0L)

        assertNotNull(profile, "a narrow but real preference must still produce a profile")
        assertTrue(
            profile!!.fitsCarriers(2, tuning.minCarrierSpacingHz),
            "widened range ${profile.lowHz}..${profile.highHz} still cannot hold two carriers",
        )
    }

    @Test
    fun `widening keeps the centre the listener actually chose`() {
        val state = sittingWhere { hz -> if (hz in 180.0..200.0) Verdict.RELAXING else Verdict.NOT_RELAXING }
        val profile = CalibrationSearch.result(state, tuning, 0L)!!

        val centre = (profile.lowHz + profile.highHz) / 2
        assertTrue(kotlin.math.abs(centre - 190.0) < 30.0, "widening moved the centre to $centre")
    }

    @Test
    fun `a widened result records that it was widened`() {
        val state = sittingWhere { hz -> if (hz in 180.0..200.0) Verdict.RELAXING else Verdict.NOT_RELAXING }
        val outcome = CalibrationSearch.outcome(state, tuning, 0L)

        assertTrue(outcome is CalibrationOutcome.Found)
        assertTrue((outcome as CalibrationOutcome.Found).widened, "the widening was not recorded")
    }

    @Test
    fun `a comfortably wide result is not marked as widened`() {
        val state = sittingWhere { hz -> if (hz in 140.0..320.0) Verdict.RELAXING else Verdict.NOT_RELAXING }
        val outcome = CalibrationSearch.outcome(state, tuning, 0L)

        assertTrue(outcome is CalibrationOutcome.Found)
        assertTrue(!(outcome as CalibrationOutcome.Found).widened)
    }

    @Test
    fun `a preference narrower than the tone spacing is honestly missed, not guessed at`() {
        // A real limitation of sampling, found while building this: the coarse tones are
        // spaced tens of Hz apart, so a listener who likes only a 10 Hz sliver may never
        // be played a tone inside it. The honest outcome is "nothing accepted", which
        // sends the listener to a retry or a preset, rather than a band assembled from
        // tones they actually rejected.
        val state = sittingWhere { hz -> if (hz in 195.0..205.0) Verdict.RELAXING else Verdict.NOT_RELAXING }

        assertEquals(CalibrationOutcome.NothingAccepted, CalibrationSearch.outcome(state, tuning, 0L))
        assertNull(CalibrationSearch.result(state, tuning, 0L))
    }

    @Test
    fun `an isolated stray rejection inside a liked run does not split the band`() {
        // One mis-tap in the middle of the good region is noise, not an edge.
        var toneCount = 0
        val state = sittingWhere { hz ->
            toneCount++
            when {
                hz !in 140.0..300.0 -> Verdict.NOT_RELAXING
                toneCount == 3 -> Verdict.NOT_RELAXING
                else -> Verdict.RELAXING
            }
        }
        val profile = CalibrationSearch.result(state, tuning, 0L)

        assertNotNull(profile)
        assertTrue(profile!!.widthHz > 100.0, "a stray rejection collapsed the band to ${profile.widthHz} Hz")
    }
}
