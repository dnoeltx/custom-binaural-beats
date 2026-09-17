package com.dnoel.binauralbeats.core.playback

/** What the platform told us about audio focus, in platform-free terms. */
enum class FocusEvent {
    /** Focus is gone for good: another app took over. */
    LOSS,

    /** Something brief, such as a notification or a call. */
    LOSS_TRANSIENT,

    /** Brief, and the system would allow ducking instead of pausing. */
    LOSS_TRANSIENT_CAN_DUCK,

    /** Focus is ours again. */
    GAIN,
}

enum class PlaybackAction { PAUSE_WITH_FADE, RESUME_WITH_FADE, END_SESSION, NONE }

/**
 * T036: how a session reacts to audio focus (research R4).
 *
 * Ducking is deliberately refused. The system offers to lower the level and raise it
 * again, which is a change the listener did not ask for and would notice, so a transient
 * loss is handled the same way whether ducking was offered or not: fade out, pause, and
 * fade back in afterwards.
 */
object FocusPolicy {

    fun decide(event: FocusEvent, paused: Boolean): PlaybackAction = when (event) {
        FocusEvent.LOSS -> PlaybackAction.END_SESSION
        FocusEvent.LOSS_TRANSIENT,
        FocusEvent.LOSS_TRANSIENT_CAN_DUCK,
        -> if (paused) PlaybackAction.NONE else PlaybackAction.PAUSE_WITH_FADE
        FocusEvent.GAIN -> if (paused) PlaybackAction.RESUME_WITH_FADE else PlaybackAction.NONE
    }
}
