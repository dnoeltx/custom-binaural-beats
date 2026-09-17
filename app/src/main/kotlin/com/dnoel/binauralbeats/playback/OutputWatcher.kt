package com.dnoel.binauralbeats.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dnoel.binauralbeats.core.playback.OutputAction
import com.dnoel.binauralbeats.core.playback.OutputKind
import com.dnoel.binauralbeats.core.playback.OutputLossPolicy
import com.dnoel.binauralbeats.core.playback.OutputSuitability
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * FR-024: notices the earbuds going away and coming back.
 *
 * Two signals, because neither alone is enough. `ACTION_AUDIO_BECOMING_NOISY` is what the
 * system broadcasts when a headset is unplugged or a Bluetooth device disconnects, and is
 * what media apps are expected to treat as "pause". Device callbacks then tell us when a
 * suitable output returns, which the broadcast never does.
 *
 * The decisions live in [OutputLossPolicy] and are tested on the JVM.
 *
 * TEST FIRST EXCEPTION (constitution III): registering system receivers and device
 * callbacks cannot be exercised in a unit test, and this class holds no rules of its own.
 * Verified on the device by quickstart M4: take a bud out, put it back inside the grace
 * period, then repeat and wait past it.
 */
class OutputWatcher(
    private val context: Context,
    private val grace: Duration,
    private val onAction: (OutputAction) -> Unit,
) {
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private var lostAtMillis: Long? = null

    private val becomingNoisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            onLost()
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) {
            if (lostAtMillis == null) return
            if (added.orEmpty().none { it.isSuitableForSession }) return
            onReturned()
        }

        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) {
            if (removed.orEmpty().any { it.isSuitableForSession } && !anySuitableOutputConnected()) {
                onLost()
            }
        }
    }

    fun start() {
        context.registerReceiver(
            becomingNoisy,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            Context.RECEIVER_NOT_EXPORTED,
        )
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        runCatching { context.unregisterReceiver(becomingNoisy) }
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        lostAtMillis = null
    }

    /** True when something the session can legitimately play through is connected. */
    fun anySuitableOutputConnected(): Boolean =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.isSuitableForSession }

    private fun onLost() {
        if (lostAtMillis != null) return
        lostAtMillis = System.currentTimeMillis()
        Log.i(TAG, "output lost; waiting up to $grace")
        onAction(OutputLossPolicy.onOutputLost(playing = true))

        handler.postDelayed(
            {
                if (lostAtMillis != null) {
                    Log.i(TAG, "output did not return within $grace; ending session")
                    onAction(OutputLossPolicy.onGraceExpired())
                }
            },
            grace.inWholeMilliseconds,
        )
    }

    private fun onReturned() {
        val lostAt = lostAtMillis ?: return
        lostAtMillis = null
        handler.removeCallbacksAndMessages(null)
        val awayFor = (System.currentTimeMillis() - lostAt).milliseconds
        Log.i(TAG, "output returned after $awayFor")
        onAction(OutputLossPolicy.onOutputReturned(awayFor, grace))
    }

    private companion object {
        const val TAG = "OutputWatcher"

        /**
         * Translation only. The rule about what may be played through lives in
         * [OutputSuitability] in :core, where it is tested; this maps Android's constants
         * onto it and nothing more.
         */
        val AudioDeviceInfo.outputKind: OutputKind
            get() = when (type) {
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> OutputKind.WIRED_HEADPHONES
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> OutputKind.WIRED_HEADSET
                AudioDeviceInfo.TYPE_USB_HEADSET -> OutputKind.USB_HEADSET
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> OutputKind.BLUETOOTH_A2DP
                AudioDeviceInfo.TYPE_BLE_HEADSET -> OutputKind.BLE_HEADSET
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> OutputKind.BLUETOOTH_SPEAKER
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputKind.BUILTIN_SPEAKER
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> OutputKind.BUILTIN_EARPIECE
                else -> OutputKind.OTHER
            }

        val AudioDeviceInfo.isSuitableForSession: Boolean
            get() = OutputSuitability.isSuitable(outputKind)
    }
}
