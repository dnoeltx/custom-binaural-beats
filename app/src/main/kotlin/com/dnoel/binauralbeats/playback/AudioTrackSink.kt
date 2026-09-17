package com.dnoel.binauralbeats.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.dnoel.binauralbeats.core.ports.AudioSink

/**
 * T034: the [AudioSink] implementation, over AudioTrack (research R1).
 *
 * MODE_STREAM with a deliberately large buffer and PERFORMANCE_MODE_POWER_SAVING. That
 * mode takes a lower power path with deeper internal buffers and better underrun
 * resistance, paying for it in latency, which this product does not care about: nothing
 * here responds to input in real time and a second of startup delay is invisible.
 *
 * TEST FIRST EXCEPTION (constitution III): this class is a thin adapter over a platform
 * type that cannot be constructed in a JVM test. It holds no logic, and everything it is
 * asked to play is produced and verified in :core. Verified instead by playing on the
 * physical device, and by the underrun count it reports being asserted after an overnight
 * run (quickstart M2).
 */
class AudioTrackSink(
    private val bufferMultiplier: Int = 8,
) : AudioSink {

    private var track: AudioTrack? = null

    override fun open(sampleRate: Int, channels: Int) {
        check(track == null) { "sink already open" }
        require(channels == 2) { "this sink is stereo only, was asked for $channels" }

        val channelMask = AudioFormat.CHANNEL_OUT_STEREO
        val minimumBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        check(minimumBytes > 0) { "AudioTrack rejected $sampleRate Hz stereo float" }

        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            // Large on purpose. Battery Saver throttles the CPU and has been measured to
            // raise underruns substantially, and a deeper buffer is the defence.
            .setBufferSizeInBytes(minimumBytes * bufferMultiplier)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
            .build()
            .also { it.play() }
    }

    /** Blocking, as the port requires: the caller owns a dedicated writer thread. */
    override fun write(buffer: FloatArray, frames: Int): Int {
        val current = track ?: return 0
        val written = current.write(buffer, 0, frames * 2, AudioTrack.WRITE_BLOCKING)
        return if (written < 0) 0 else written / 2
    }

    override fun close() {
        track?.let {
            it.stop()
            it.release()
        }
        track = null
    }

    /** Zero over a full night is the requirement (research R1), asserted after M2. */
    override fun underrunCount(): Int = track?.underrunCount ?: 0

    /** Applied to the whole track, so a fade can be done without re-rendering. */
    fun setVolume(volume: Float) {
        track?.setVolume(volume.coerceIn(0f, 1f))
    }

    companion object {
        /** Matches what the renderer produces, and what AudioTrack takes without resampling. */
        const val SAMPLE_RATE = 44_100
        const val CHANNELS = 2
    }
}
