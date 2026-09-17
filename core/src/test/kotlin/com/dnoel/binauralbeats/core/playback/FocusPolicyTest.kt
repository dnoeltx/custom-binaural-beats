package com.dnoel.binauralbeats.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * T036: what a session does when something else wants the audio.
 *
 * The rules come from research R4. The important one is that a transient loss is handled
 * by fading out and pausing rather than by letting the system duck: a level that drops
 * and springs back is exactly the kind of change Principle I forbids, and a phone call at
 * 3am should not leave a half volume session behind it.
 *
 * Kept as a pure decision so it can be tested without a device, leaving the Android class
 * to do nothing but translate constants.
 */
class FocusPolicyTest {

    @Test
    fun `a transient loss fades out and pauses`() {
        assertEquals(
            PlaybackAction.PAUSE_WITH_FADE,
            FocusPolicy.decide(FocusEvent.LOSS_TRANSIENT, paused = false),
        )
    }

    @Test
    fun `a duckable loss also pauses rather than ducking`() {
        assertEquals(
            PlaybackAction.PAUSE_WITH_FADE,
            FocusPolicy.decide(FocusEvent.LOSS_TRANSIENT_CAN_DUCK, paused = false),
        )
    }

    @Test
    fun `a permanent loss ends the session`() {
        assertEquals(
            PlaybackAction.END_SESSION,
            FocusPolicy.decide(FocusEvent.LOSS, paused = false),
        )
    }

    @Test
    fun `regaining focus after a pause resumes with a fade`() {
        assertEquals(
            PlaybackAction.RESUME_WITH_FADE,
            FocusPolicy.decide(FocusEvent.GAIN, paused = true),
        )
    }

    @Test
    fun `regaining focus while already playing changes nothing`() {
        assertEquals(
            PlaybackAction.NONE,
            FocusPolicy.decide(FocusEvent.GAIN, paused = false),
        )
    }

    @Test
    fun `losing focus while already paused does not resume or double pause`() {
        assertEquals(
            PlaybackAction.NONE,
            FocusPolicy.decide(FocusEvent.LOSS_TRANSIENT, paused = true),
        )
    }

    @Test
    fun `a permanent loss while paused still ends the session`() {
        // Otherwise a session paused by a phone call would sit forever claiming to run.
        assertEquals(
            PlaybackAction.END_SESSION,
            FocusPolicy.decide(FocusEvent.LOSS, paused = true),
        )
    }
}
