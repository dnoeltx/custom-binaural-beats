package com.dnoel.binauralbeats.core.analysis

import com.dnoel.binauralbeats.core.audio.SessionRenderer
import com.dnoel.binauralbeats.core.model.BeatArc
import com.dnoel.binauralbeats.core.model.EndBehavior
import com.dnoel.binauralbeats.core.model.ListenerProfile
import com.dnoel.binauralbeats.core.model.ProfileSource
import com.dnoel.binauralbeats.core.model.SessionConfiguration
import com.dnoel.binauralbeats.core.session.SessionScheduler
import com.dnoel.binauralbeats.core.session.SessionTuning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/**
 * T027: the test the constitution's second principle exists for.
 *
 * A full night is rendered through the same code that plays it, streamed past an
 * analyzer, and checked for anything abrupt. Ten hours of stereo at full rate is about
 * 12 GB, so nothing is ever held: the analyzer keeps counters and one frame.
 */
class FullNightContinuityTest {

    private val tuning = SessionTuning.MEASURED
    private val profile = ListenerProfile(150.0, 400.0, ProfileSource.CALIBRATED, 0L)

    @Test
    fun `ten hours of the default session contains nothing abrupt`() {
        val violations = analyseSession(SessionConfiguration(), hours = 10)
        assertEquals(emptyList<ContinuityViolation>(), violations.take(5))
    }

    @Test
    fun `each beat arc survives a full night`() {
        for (arc in BeatArc.entries) {
            val violations = analyseSession(SessionConfiguration(beatArc = arc), hours = 10)
            assertTrue(violations.isEmpty(), "$arc produced ${violations.take(3)}")
        }
    }

    @Test
    fun `a session that fades out on a timer is continuous through the ending`() {
        val eightHours = 8.hours
        val violations = analyseSession(
            SessionConfiguration(endBehavior = EndBehavior.AfterDuration(eightHours.inWholeMilliseconds)),
            hours = 9, // past the end, so the fade and the silence after it are included
        )
        assertTrue(violations.isEmpty(), "ending produced ${violations.take(3)}")
    }

    @Test
    fun `a narrow range, where carriers have least room to move, is also continuous`() {
        val narrow = ListenerProfile(180.0, 320.0, ProfileSource.CALIBRATED, 0L)
        val violations = analyseSession(SessionConfiguration(), hours = 10, profile = narrow)
        assertTrue(violations.isEmpty(), "narrow range produced ${violations.take(3)}")
    }

    /**
     * Analysis runs at a reduced sample rate. The properties under test are envelope and
     * parameter continuity, not audio bandwidth, and a click or a step survives
     * decimation. Full rate is used around the transitions, where anything abrupt would
     * actually live: see [fullRateAroundTransitions].
     */
    private fun analyseSession(
        configuration: SessionConfiguration,
        hours: Int,
        profile: ListenerProfile = this.profile,
        // 4 kHz, not 44.1 kHz. The properties under test are envelope and parameter
        // continuity, and a step or a click survives decimation. Carriers stay under
        // 1000 Hz by FR-002, so 4 kHz is still comfortably above Nyquist for them, and it
        // makes a ten hour night cost seconds rather than a minute. Anything that only
        // shows up at full rate is covered by the transition test below.
        sampleRate: Int = 4_000,
    ): List<ContinuityViolation> {
        val renderer = SessionRenderer(
            scheduler = SessionScheduler(profile, configuration, tuning, seed = 4242L),
            sampleRate = sampleRate,
        )
        val analyzer = ContinuityAnalyzer(
            sampleRate = sampleRate,
            channels = 2,
            limits = limitsFor(sampleRate, profile),
        )

        val blockFrames = 4_096
        val buffer = FloatArray(blockFrames * 2)
        val totalFrames = hours.toLong() * 3_600L * sampleRate
        var at = 0L
        while (at < totalFrames) {
            val frames = minOf(blockFrames.toLong(), totalFrames - at).toInt()
            renderer.render(buffer, frames, startFrame = at)
            analyzer.accept(buffer, frames)
            at += frames
        }
        return analyzer.violations()
    }

    @Test
    fun `full rate analysis around every transition is also clean`() {
        val violations = fullRateAroundTransitions()
        assertTrue(violations.isEmpty(), "transitions produced ${violations.take(3)}")
    }

    /** Start, the end of the fade, the end of the beat descent, and the fade out. */
    private fun fullRateAroundTransitions(): List<ContinuityViolation> {
        val sampleRate = 44_100
        val duration = 8.hours
        val configuration = SessionConfiguration(
            endBehavior = EndBehavior.AfterDuration(duration.inWholeMilliseconds)
        )
        val renderer = SessionRenderer(
            scheduler = SessionScheduler(profile, configuration, tuning, seed = 99L),
            sampleRate = sampleRate,
        )
        val found = mutableListOf<ContinuityViolation>()

        val moments = listOf(
            0L,
            tuning.fadeDuration.inWholeSeconds,
            tuning.beatDescentDuration.inWholeSeconds,
            duration.inWholeSeconds - tuning.fadeDuration.inWholeSeconds - 5,
            duration.inWholeSeconds - 2,
        )
        for (second in moments) {
            val analyzer = ContinuityAnalyzer(sampleRate, 2, limitsFor(sampleRate, profile))
            val buffer = FloatArray(4_096 * 2)
            var frame = maxOf(0L, (second - 15) * sampleRate)
            val until = (second + 30) * sampleRate
            while (frame < until) {
                renderer.render(buffer, 4_096, startFrame = frame)
                analyzer.accept(buffer, 4_096)
                frame += 4_096
            }
            found += analyzer.violations()
        }
        return found
    }

    /**
     * Limits computed rather than fixed (research M002): the legitimate sample step and
     * slope scale with the highest carrier, and the loudness limit comes from the fade.
     */
    private fun limitsFor(sampleRate: Int, profile: ListenerProfile): ContinuityLimits {
        val radiansPerSample = 2 * Math.PI * profile.highHz / sampleRate
        return ContinuityLimits(
            maxSampleDelta = (radiansPerSample * 1.5).toFloat(),
            maxSlopeChange = (radiansPerSample * radiansPerSample * 3.0).toFloat(),
            maxBlockRmsChangePerSecond = (Math.PI / (2 * tuning.fadeDuration.inWholeSeconds) * 2.0).toFloat(),
        )
    }
}
