package com.dnoel.binauralbeats.core.calibration

import com.dnoel.binauralbeats.core.model.ToneJudgment
import com.dnoel.binauralbeats.core.model.Verdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T047: an interrupted sitting resumes exactly (FR-022).
 *
 * Calibration takes about twelve minutes, so being interrupted is ordinary rather than
 * exceptional. The search therefore keeps no hidden state: everything it needs is the
 * settings and the judgments already given, both of which are stored. Resuming is simply
 * asking the same question of the same data.
 */
class CalibrationResumeTest {

    private val settings = CalibrationSettings.DEFAULT

    private fun judge(hz: Double, verdict: Verdict) = ToneJudgment(hz, verdict, 0L, 800L)

    @Test
    fun `the next tone depends only on the judgments so far`() {
        var original = CalibrationSearch.start(settings)
        repeat(4) {
            val tone = CalibrationSearch.next(original)!!
            original = CalibrationSearch.accept(original, judge(tone, Verdict.RELAXING))
        }

        // What an app would reconstruct from storage: the same settings, the same list.
        val rebuilt = CalibrationSearchState(settings = settings, judgments = original.judgments)

        assertEquals(CalibrationSearch.next(original), CalibrationSearch.next(rebuilt))
    }

    @Test
    fun `resuming continues rather than starting over`() {
        var state = CalibrationSearch.start(settings)
        val presented = mutableListOf<Double>()
        repeat(5) {
            val tone = CalibrationSearch.next(state)!!
            presented += tone
            state = CalibrationSearch.accept(state, judge(tone, Verdict.NOT_RELAXING))
        }

        val resumed = CalibrationSearchState(settings, state.judgments)
        val nextTone = CalibrationSearch.next(resumed)

        assertTrue(nextTone !in presented, "resuming repeated a tone already judged")
        assertEquals(5, resumed.judgments.size)
    }

    @Test
    fun `a sitting that ran out of tones stays finished after a resume`() {
        var state = CalibrationSearch.start(settings)
        while (true) {
            val tone = CalibrationSearch.next(state) ?: break
            state = CalibrationSearch.accept(state, judge(tone, Verdict.NOT_RELAXING))
        }

        val resumed = CalibrationSearchState(settings, state.judgments)
        assertEquals(null, CalibrationSearch.next(resumed))
    }

    @Test
    fun `progress can be reported without knowing the search's internals`() {
        // The screen needs a quiet progress indication (FR-019) and should not have to
        // reimplement the plan to draw one.
        var state = CalibrationSearch.start(settings)
        assertEquals(0.0, CalibrationSearch.progress(state), 0.001)

        // A listener with an actual preference, so there are edges left to refine. Someone
        // who likes every tone has nothing to refine and is legitimately finished after
        // the coarse pass.
        repeat(settings.coarseToneCount) {
            val tone = CalibrationSearch.next(state)!!
            val verdict = if (tone in 140.0..300.0) Verdict.RELAXING else Verdict.NOT_RELAXING
            state = CalibrationSearch.accept(state, judge(tone, verdict))
        }

        val progress = CalibrationSearch.progress(state)
        assertTrue(progress > 0.0 && progress < 1.0, "progress after the coarse pass was $progress")
    }
}
