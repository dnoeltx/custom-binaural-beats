package com.dnoel.binauralbeats.core.playback

import kotlin.time.Duration

/** What to do when the audio output disappears or comes back (FR-024). */
enum class OutputAction { PAUSE_AND_WAIT, RESUME_WITH_FADE, END_SESSION, NONE }

/**
 * FR-024, kept as a pure decision so the Android side is only plumbing.
 *
 * Losing output means the earbuds came out, went out of range, or ran flat. The session
 * pauses and waits. If the same kind of output returns within the grace period it fades
 * back in and carries on from where it paused; otherwise the session ends cleanly, so
 * nothing claims to be running (FR-025).
 */
object OutputLossPolicy {

    /**
     * Never true, and stated here so it is visible rather than implied. Falling back to
     * the phone speaker would both destroy the binaural effect, which needs a separate
     * tone per ear, and wake whoever is in the room.
     */
    const val MAY_FALL_BACK_TO_ANOTHER_OUTPUT = false

    fun onOutputLost(playing: Boolean): OutputAction =
        if (playing) OutputAction.PAUSE_AND_WAIT else OutputAction.NONE

    fun onOutputReturned(awayFor: Duration, grace: Duration): OutputAction =
        if (awayFor <= grace) OutputAction.RESUME_WITH_FADE else OutputAction.END_SESSION

    fun onGraceExpired(): OutputAction = OutputAction.END_SESSION
}
