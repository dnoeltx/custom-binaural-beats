package com.dnoel.binauralbeats.core.audio

import com.dnoel.binauralbeats.core.model.EndBehavior
import com.dnoel.binauralbeats.core.model.ListenerProfile
import com.dnoel.binauralbeats.core.model.ProfileSource
import com.dnoel.binauralbeats.core.model.SessionConfiguration
import com.dnoel.binauralbeats.core.session.SessionScheduler
import com.dnoel.binauralbeats.core.session.SessionTuning
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * T024 to T026: the renderer that turns scheduler parameters into samples.
 *
 * This is the single source of audio for both playback and verification (research R5).
 * A test that rendered through a different code path than the phone does would prove
 * nothing about the phone.
 */
class SessionRendererTest {

    private val tuning = SessionTuning.MEASURED
    private val profile = ListenerProfile(150.0, 400.0, ProfileSource.CALIBRATED, 0L)
    private val sampleRate = 44_100

    // --- T024: fades ---

    @Test
    fun `a session begins at exact silence`() {
        val buffer = FloatArray(2 * 64)
        renderer().render(buffer, frames = 64, startFrame = 0L)
        assertEquals(0.0f, buffer[0], 1e-7f)
        assertEquals(0.0f, buffer[1], 1e-7f)
    }

    @Test
    fun `a session reaches full level only after the measured fade`() {
        val renderer = renderer()
        val halfway = peakOverSecond(renderer, atSecond = tuning.fadeDuration.inWholeSeconds / 2)
        val after = peakOverSecond(renderer, atSecond = tuning.fadeDuration.inWholeSeconds + 30)

        assertTrue(halfway < after * 0.9, "level at half fade ($halfway) was not below full ($after)")
        assertTrue(halfway > 0.05f, "level at half fade was inaudible")
    }

    @Test
    fun `a session with a duration ends at exact silence`() {
        val duration = 30.minutes
        val renderer = renderer(
            SessionConfiguration(endBehavior = EndBehavior.AfterDuration(duration.inWholeMilliseconds))
        )
        val lastFrame = duration.inWholeSeconds * sampleRate
        val buffer = FloatArray(2 * 2)
        renderer.render(buffer, frames = 2, startFrame = lastFrame)
        assertEquals(0.0f, buffer[0], 1e-6f)
        assertEquals(0.0f, buffer[1], 1e-6f)
    }

    // --- T025: determinism ---

    @Test
    fun `the same seed renders byte identical audio`() {
        val a = FloatArray(2 * 4_096)
        val b = FloatArray(2 * 4_096)
        renderer(seed = 123L).render(a, 4_096, startFrame = 60L * sampleRate)
        renderer(seed = 123L).render(b, 4_096, startFrame = 60L * sampleRate)
        assertArrayEquals(a, b, 0.0f)
    }

    @Test
    fun `a different seed renders different audio`() {
        val a = FloatArray(2 * 4_096)
        val b = FloatArray(2 * 4_096)
        renderer(seed = 1L).render(a, 4_096, startFrame = 3_600L * sampleRate)
        renderer(seed = 2L).render(b, 4_096, startFrame = 3_600L * sampleRate)
        assertFalse(a.contentEquals(b), "two seeds produced identical audio")
    }

    @Test
    fun `rendering from a given frame does not depend on what was rendered before`() {
        // Playback renders sequentially; verification may jump. They must agree, or a test
        // would be checking audio the listener never hears.
        val sequential = FloatArray(2 * 1_024)
        val renderer = renderer()
        renderer.render(FloatArray(2 * 1_024), 1_024, startFrame = 0L)
        renderer.render(sequential, 1_024, startFrame = 1_024L)

        val jumped = FloatArray(2 * 1_024)
        renderer().render(jumped, 1_024, startFrame = 1_024L)

        // Not bit identical, and cannot be: sequential rendering integrates phase step by
        // step while a seek reconstructs it, so the two differ by accumulated floating
        // point error. Measured at about 4e-10 here. The tolerance is far below anything
        // audible (a 16 bit sample step is 3e-5) while still failing loudly if a seek ever
        // landed on genuinely different audio.
        assertArrayEquals(sequential, jumped, 1e-5f)
    }

    // --- T026: allocation ---

    @Test
    fun `rendering allocates nothing once warmed`() {
        // Ten hours of playback with an allocation per block is a garbage collection pause
        // waiting to happen, and a pause is an audible gap (research R1).
        val renderer = renderer()
        val buffer = FloatArray(2 * 2_048)
        repeat(50) { renderer.render(buffer, 2_048, startFrame = it.toLong() * 2_048) }

        val runtime = Runtime.getRuntime()
        System.gc()
        Thread.sleep(50)
        val before = runtime.totalMemory() - runtime.freeMemory()
        repeat(500) { renderer.render(buffer, 2_048, startFrame = 100_000L + it * 2_048) }
        val after = runtime.totalMemory() - runtime.freeMemory()

        val grewBytes = after - before
        assertTrue(
            grewBytes < 512 * 1024,
            "500 blocks grew the heap by $grewBytes bytes, so rendering is allocating",
        )
    }

    // --- T028: every tone stays in the listener's range ---

    @Test
    fun `the rendered signal stays within full scale`() {
        val renderer = renderer()
        val buffer = FloatArray(2 * 4_096)
        var at = 0L
        while (at < 3_600L * sampleRate) {
            renderer.render(buffer, 4_096, startFrame = at)
            for (sample in buffer) {
                assertTrue(abs(sample) <= 1.0f, "sample $sample exceeded full scale at frame $at")
            }
            at += 60L * sampleRate
        }
    }

    @Test
    fun `left and right differ, which is what makes it binaural`() {
        val renderer = renderer()
        val buffer = FloatArray(2 * 4_096)
        renderer.render(buffer, 4_096, startFrame = 600L * sampleRate)

        var differences = 0
        for (frame in 0 until 4_096) {
            if (abs(buffer[frame * 2] - buffer[frame * 2 + 1]) > 1e-6f) differences++
        }
        assertTrue(differences > 3_000, "channels were nearly identical: only $differences frames differed")
    }

    private fun renderer(
        configuration: SessionConfiguration = SessionConfiguration(),
        seed: Long = 42L,
    ) = SessionRenderer(
        scheduler = SessionScheduler(profile, configuration, tuning, seed),
        sampleRate = sampleRate,
    )

    private fun peakOverSecond(renderer: SessionRenderer, atSecond: Long): Float {
        val buffer = FloatArray(2 * sampleRate)
        renderer.render(buffer, sampleRate, startFrame = atSecond * sampleRate)
        return buffer.maxOf { abs(it) }
    }
}
