package com.dnoel.binauralbeats.playback

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import com.dnoel.binauralbeats.core.audio.SessionRenderer
import com.dnoel.binauralbeats.core.model.AppState
import com.dnoel.binauralbeats.core.model.EndReason
import com.dnoel.binauralbeats.core.model.ListenerProfile
import com.dnoel.binauralbeats.core.model.ProfileSource
import com.dnoel.binauralbeats.core.model.SessionRecord
import com.dnoel.binauralbeats.core.playback.PlaybackAction
import com.dnoel.binauralbeats.core.session.SessionScheduler
import com.dnoel.binauralbeats.core.session.SessionTuning
import com.dnoel.binauralbeats.notification.SessionNotification
import com.dnoel.binauralbeats.storage.DataStoreStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * T035: the foreground service that keeps a session playing with the phone locked.
 *
 * Type `mediaPlayback`, which has no time limit (research R3). The app never asks for
 * background location style permissions, never holds the screen on, and never bypasses
 * the lock screen: the phone locks normally and the notification carries the stop control
 * (FR-015, FR-016).
 *
 * TEST FIRST EXCEPTION (constitution III): a started Service with a live audio device and
 * a writer thread cannot be constructed in a JVM test. Everything with a decision in it
 * was pushed out of here: the audio into :core, the focus rules into FocusPolicy, the
 * single session guard into [isRunning]. What is left is wiring, verified on the device
 * by quickstart M2 and M3.
 */
class SessionService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stopping = AtomicBoolean(false)
    private var writer: Thread? = null
    private var sink: AudioTrackSink? = null
    private var focus: FocusController? = null
    private var paused = false
    private var startedAtMillis = 0L
    private var renderSeed = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // Always go foreground before anything else, even on a path that stops
                // immediately: a service started by a stale notification action and never
                // promoted is killed for not going foreground in time.
                goForeground()
                stopSession(EndReason.STOPPED_BY_LISTENER)
                return START_NOT_STICKY
            }

            ACTION_START -> {
                goForeground()
                // FR-013: one session at a time. A second start is ignored rather than
                // producing a second writer thread over the same audio device.
                if (isRunning) return START_NOT_STICKY
                val presetHz = intent.getDoubleExtra(EXTRA_PRESET_HZ, 0.0)
                startSession(presetHz)
                return START_NOT_STICKY
            }

            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    private fun goForeground() {
        startForeground(
            SessionNotification.NOTIFICATION_ID,
            SessionNotification.ongoing(this, paused = false),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun startSession(presetHz: Double) {
        isRunning = true
        stopping.set(false)
        startedAtMillis = System.currentTimeMillis()
        renderSeed = startedAtMillis

        scope.launch {
            val store = DataStoreStateStore.forContext(applicationContext)
            val state = store.read()
            val profile = state.profile ?: presetProfile(presetHz, state)
            val tuning = SessionTuning.MEASURED

            store.write(
                state.copy(
                    lastSession = SessionRecord(
                        startedAtEpochMillis = startedAtMillis,
                        configuration = state.settings,
                        profile = profile,
                        renderSeed = renderSeed,
                    )
                )
            )

            val scheduler = SessionScheduler(profile, state.settings, tuning, renderSeed)
            val renderer = SessionRenderer(scheduler, AudioTrackSink.SAMPLE_RATE)
            val audioSink = AudioTrackSink().also { sink = it }

            focus = FocusController(applicationContext, ::onFocusAction)
            if (focus?.request() != true) {
                Log.w(TAG, "audio focus refused; not starting")
                stopSession(EndReason.INTERRUPTED)
                return@launch
            }

            audioSink.open(AudioTrackSink.SAMPLE_RATE, AudioTrackSink.CHANNELS)
            writer = thread(name = "session-writer", priority = Thread.MAX_PRIORITY) {
                val block = FloatArray(BLOCK_FRAMES * AudioTrackSink.CHANNELS)
                var frame = 0L
                while (!stopping.get()) {
                    if (paused) {
                        Thread.sleep(PAUSE_POLL_MILLIS)
                        continue
                    }
                    renderer.render(block, BLOCK_FRAMES, frame)
                    val written = audioSink.write(block, BLOCK_FRAMES)
                    if (written <= 0) break
                    frame += written
                }
            }
        }
    }

    /** A preset choice becomes a profile centred on the chosen pitch. */
    private fun presetProfile(presetHz: Double, state: AppState): ListenerProfile {
        val tuning = SessionTuning.MEASURED
        val centre = if (presetHz > 0.0) presetHz else tuning.defaultPresetHz
        val halfWidth = tuning.minCarrierSpacingHz * state.settings.carrierCount / 2.0
        val low = (centre - halfWidth).coerceAtLeast(1.0)
        val high = (centre + halfWidth).coerceAtMost(900.0)
        return ListenerProfile(low, high, ProfileSource.PRESET, System.currentTimeMillis())
    }

    private fun onFocusAction(action: PlaybackAction) {
        when (action) {
            PlaybackAction.PAUSE_WITH_FADE -> {
                paused = true
                sink?.setVolume(0f)
                updateNotification()
            }

            PlaybackAction.RESUME_WITH_FADE -> {
                paused = false
                sink?.setVolume(1f)
                updateNotification()
            }

            PlaybackAction.END_SESSION -> stopSession(EndReason.INTERRUPTED)
            PlaybackAction.NONE -> Unit
        }
    }

    private fun updateNotification() {
        startForeground(
            SessionNotification.NOTIFICATION_ID,
            SessionNotification.ongoing(this, paused),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun stopSession(reason: EndReason) {
        if (!stopping.compareAndSet(false, true)) return
        val endedAt = System.currentTimeMillis()
        val underruns = sink?.underrunCount() ?: 0
        Log.i(TAG, "session ending: reason=$reason underruns=$underruns")

        writer?.join(WRITER_JOIN_MILLIS)
        writer = null
        sink?.close()
        sink = null
        focus?.abandon()
        focus = null
        isRunning = false

        scope.launch {
            val store = DataStoreStateStore.forContext(applicationContext)
            val state = store.read()
            val record = state.lastSession
            if (record != null && record.startedAtEpochMillis == startedAtMillis) {
                store.write(
                    state.copy(
                        lastSession = record.copy(endedAtEpochMillis = endedAt, endReason = reason)
                    )
                )
            }
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopSession(EndReason.UNKNOWN)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.dnoel.binauralbeats.START"
        const val ACTION_STOP = "com.dnoel.binauralbeats.STOP"
        const val EXTRA_PRESET_HZ = "preset_hz"

        private const val TAG = "SessionService"
        private const val BLOCK_FRAMES = 4_096
        private const val PAUSE_POLL_MILLIS = 100L
        private const val WRITER_JOIN_MILLIS = 2_000L

        /**
         * FR-013 and FR-025: exactly one session, and the app never claims to be running
         * when it is not. Process death clears this with the process itself.
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context, presetHz: Double) {
            context.startForegroundService(
                Intent(context, SessionService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_PRESET_HZ, presetHz)
            )
        }

        fun stop(context: Context) {
            context.startForegroundService(
                Intent(context, SessionService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
