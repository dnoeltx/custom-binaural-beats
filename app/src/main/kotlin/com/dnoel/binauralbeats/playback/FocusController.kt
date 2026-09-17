package com.dnoel.binauralbeats.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.dnoel.binauralbeats.core.playback.FocusEvent
import com.dnoel.binauralbeats.core.playback.FocusPolicy
import com.dnoel.binauralbeats.core.playback.PlaybackAction

/**
 * T036: audio focus, translated into the decisions made in :core.
 *
 * Android 15 and later only grant focus to the top app or to one running a foreground
 * service, so this is requested from inside the running service rather than before it
 * starts (research R3).
 *
 * `setWillPauseWhenDucked(true)` refuses automatic ducking: a level that dips and springs
 * back is a change the listener did not ask for, so a duckable loss is treated as a pause
 * like any other transient loss.
 *
 * TEST FIRST EXCEPTION (constitution III): the decisions live in FocusPolicy and are
 * covered by FocusPolicyTest on the JVM. What remains here is constant translation and
 * one platform call, which cannot be constructed in a unit test. Verified on the device
 * by the interruption scenario in quickstart M5.
 */
class FocusController(
    context: Context,
    private val onAction: (PlaybackAction) -> Unit,
) {
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var paused = false

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { change -> handle(change) }
        .build()

    /** True when focus was granted and playback may begin. */
    fun request(): Boolean =
        audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    fun abandon() {
        audioManager.abandonAudioFocusRequest(request)
    }

    private fun handle(change: Int) {
        val event = when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> FocusEvent.LOSS
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusEvent.LOSS_TRANSIENT
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> FocusEvent.LOSS_TRANSIENT_CAN_DUCK
            AudioManager.AUDIOFOCUS_GAIN -> FocusEvent.GAIN
            else -> return
        }

        val action = FocusPolicy.decide(event, paused)
        when (action) {
            PlaybackAction.PAUSE_WITH_FADE -> paused = true
            PlaybackAction.RESUME_WITH_FADE -> paused = false
            PlaybackAction.END_SESSION, PlaybackAction.NONE -> Unit
        }
        if (action != PlaybackAction.NONE) onAction(action)
    }
}
