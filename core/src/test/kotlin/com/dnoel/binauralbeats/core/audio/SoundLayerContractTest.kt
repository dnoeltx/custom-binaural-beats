package com.dnoel.binauralbeats.core.audio

import com.dnoel.binauralbeats.core.model.ListenerProfile
import com.dnoel.binauralbeats.core.model.ProfileSource
import com.dnoel.binauralbeats.core.model.SessionConfiguration
import com.dnoel.binauralbeats.core.session.SessionScheduler
import com.dnoel.binauralbeats.core.session.SessionTuning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * T030: the promise in FR-014 and constitution VII is that a new kind of sound can be
 * added later without touching the scheduler, the mixer, or calibration.
 *
 * A promise like that is worth nothing until something takes it up, so this test adds a
 * second layer type. If adding one ever requires editing [SessionRenderer], this test
 * will not compile, which is the point.
 */
class SoundLayerContractTest {

    private val sampleRate = 8_000
    private val profile = ListenerProfile(150.0, 400.0, ProfileSource.CALIBRATED, 0L)

    /** A stand-in for the noise layer that a later spec will add. */
    private class SteadyLayer(private val level: Float) : SoundLayer {
        var prepared = false
            private set
        var seeks = 0
            private set

        override fun prepare(sampleRate: Int) {
            prepared = true
        }

        override fun seekTo(startFrame: Long) {
            seeks++
        }

        override fun addTo(into: FloatArray, frames: Int, startFrame: Long, scale: Double) {
            for (i in 0 until frames * 2) into[i] += (level * scale).toFloat()
        }
    }

    @Test
    fun `a new layer type mixes in without any change to the renderer`() {
        val extra = SteadyLayer(0.5f)
        val renderer = SessionRenderer(listOf(toneLayer(), extra), sampleRate)

        val buffer = FloatArray(2 * 256)
        renderer.render(buffer, 256, startFrame = 100L * sampleRate)

        assertTrue(extra.prepared, "the renderer must prepare every layer")
        assertTrue(buffer.any { it != 0.0f }, "nothing was rendered")
    }

    @Test
    fun `layers are summed and normalized, so adding one does not make the session louder`() {
        val tonesOnly = SessionRenderer(listOf(toneLayer()), sampleRate)
        val withExtra = SessionRenderer(listOf(toneLayer(), SteadyLayer(0.0f)), sampleRate)

        val a = FloatArray(2 * 512)
        val b = FloatArray(2 * 512)
        tonesOnly.render(a, 512, startFrame = 300L * sampleRate)
        withExtra.render(b, 512, startFrame = 300L * sampleRate)

        // The silent second layer halves the tone contribution rather than adding to it.
        for (i in a.indices) {
            assertEquals(a[i] / 2.0f, b[i], 1e-6f)
        }
    }

    @Test
    fun `every layer is told about a jump, so none of them drifts out of step`() {
        val extra = SteadyLayer(0.2f)
        val renderer = SessionRenderer(listOf(toneLayer(), extra), sampleRate)
        val buffer = FloatArray(2 * 128)

        renderer.render(buffer, 128, startFrame = 0L)
        val seeksAfterStart = extra.seeks
        renderer.render(buffer, 128, startFrame = 128L)   // sequential: no seek
        assertEquals(seeksAfterStart, extra.seeks)

        renderer.render(buffer, 128, startFrame = 9_000L) // a jump: every layer told
        assertEquals(seeksAfterStart + 1, extra.seeks)
    }

    @Test
    fun `a layer only ever adds, so it cannot silence another`() {
        val loud = SteadyLayer(1.0f)
        val renderer = SessionRenderer(listOf(loud, toneLayer()), sampleRate)
        val buffer = FloatArray(2 * 256)
        renderer.render(buffer, 256, startFrame = 600L * sampleRate)

        assertTrue(buffer.all { abs(it) > 0.0f }, "a layer appears to have overwritten the buffer")
    }

    private fun toneLayer() = BinauralToneLayer(
        SessionScheduler(profile, SessionConfiguration(), SessionTuning.MEASURED, seed = 3L)
    )
}
