package com.dnoel.binauralbeats.core.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * T013: the analyzer that turns Principle I ("no surprises") into something a machine
 * checks (constitution II).
 *
 * It must flag audio that changes abruptly and must not flag audio that changes slowly,
 * because a sleep session is nothing but slow change. The limits are supplied rather
 * than hard-coded: they come from measurement (M001, M002).
 */
class ContinuityAnalyzerTest {

    private val sampleRate = 44_100
    private val limits = ContinuityLimits(
        maxSampleDelta = 0.05f,
        maxSlopeChange = 0.005f,
        maxBlockRmsChangePerSecond = 0.2f,
    )

    @Test
    fun `a steady tone passes`() {
        val analyzer = analyzer()
        analyzer.acceptAll(tone(seconds = 2.0, hz = 200.0, gain = 0.3f))
        assertTrue(analyzer.violations().isEmpty(), "steady tone reported ${analyzer.violations()}")
    }

    @Test
    fun `a long smooth fade passes`() {
        val analyzer = analyzer()
        analyzer.acceptAll(tone(seconds = 20.0, hz = 200.0, gain = 0.3f, fadeInSeconds = 20.0))
        assertTrue(analyzer.violations().isEmpty(), "fade reported ${analyzer.violations()}")
    }

    @Test
    fun `a slow drift in pitch passes`() {
        val analyzer = analyzer()
        // 200 Hz drifting to 204 Hz over 30 seconds: imperceptible per FR-004a.
        analyzer.acceptAll(sweep(seconds = 30.0, fromHz = 200.0, toHz = 204.0, gain = 0.3f))
        assertTrue(analyzer.violations().isEmpty(), "drift reported ${analyzer.violations()}")
    }

    @Test
    fun `a cut to silence is flagged`() {
        // Cut at a peak, not at a zero crossing. A tone that happens to end at zero can
        // be cut with no step at all, which is why the first draft of this test passed
        // for the wrong reason: startPhase = PI/2 makes the tone end at full amplitude.
        val analyzer = analyzer()
        val audio = tone(seconds = 1.0, hz = 200.0, gain = 0.3f, startPhase = PI / 2) +
            FloatArray(sampleRate * 2)
        analyzer.acceptAll(audio)
        assertTrue(analyzer.violations().any { it.kind == ViolationKind.SAMPLE_STEP })
    }

    @Test
    fun `a level jump is flagged`() {
        val analyzer = analyzer()
        analyzer.acceptAll(tone(seconds = 1.0, hz = 200.0, gain = 0.1f))
        analyzer.acceptAll(tone(seconds = 1.0, hz = 200.0, gain = 0.6f, startPhase = 0.0))
        assertFalse(analyzer.violations().isEmpty())
    }

    @Test
    fun `a phase discontinuity at constant level is flagged`() {
        // The subtle one: same frequency, same gain, but the waveform is cut mid-cycle.
        // This is exactly the click that ends a YouTube track badly, and RMS alone will
        // not see it.
        val analyzer = analyzer()
        val first = tone(seconds = 0.5, hz = 200.0, gain = 0.4f)
        val second = tone(seconds = 0.5, hz = 200.0, gain = 0.4f, startPhase = PI)
        analyzer.acceptAll(first + second)
        assertTrue(
            analyzer.violations().any { it.kind == ViolationKind.SLOPE_STEP },
            "expected a slope step, got ${analyzer.violations()}",
        )
    }

    @Test
    fun `a violation reports where it happened`() {
        val analyzer = analyzer()
        analyzer.acceptAll(
            tone(seconds = 1.0, hz = 200.0, gain = 0.3f, startPhase = PI / 2) + FloatArray(1_000)
        )
        val violation = analyzer.violations().first()
        val distanceFromCut = kotlin.math.abs(violation.atFrame - sampleRate.toLong())
        assertTrue(distanceFromCut < 200L, "violation reported at frame ${violation.atFrame}")
    }

    @Test
    fun `analysis holds no audio, so a long session costs nothing to check`() {
        val analyzer = analyzer()
        repeat(200) { analyzer.acceptAll(tone(seconds = 1.0, hz = 200.0, gain = 0.3f)) }
        // 200 seconds analyzed; the analyzer retains only counters and the last frame.
        assertEquals(200L * sampleRate, analyzer.framesAnalyzed)
    }

    private fun analyzer() = ContinuityAnalyzer(sampleRate = sampleRate, channels = 1, limits = limits)

    private fun ContinuityAnalyzer.acceptAll(samples: FloatArray) = accept(samples, samples.size)

    private fun tone(
        seconds: Double,
        hz: Double,
        gain: Float,
        fadeInSeconds: Double = 0.0,
        startPhase: Double = 0.0,
    ): FloatArray {
        val n = (seconds * sampleRate).toInt()
        return FloatArray(n) { i ->
            val t = i.toDouble() / sampleRate
            val envelope = if (fadeInSeconds > 0.0) minOf(1.0, t / fadeInSeconds) else 1.0
            (gain * envelope * sin(2 * PI * hz * t + startPhase)).toFloat()
        }
    }

    private fun sweep(seconds: Double, fromHz: Double, toHz: Double, gain: Float): FloatArray {
        val n = (seconds * sampleRate).toInt()
        var phase = 0.0
        return FloatArray(n) { i ->
            val progress = i.toDouble() / n
            val hz = fromHz + (toHz - fromHz) * progress
            phase += 2 * PI * hz / sampleRate
            (gain * sin(phase)).toFloat()
        }
    }
}
