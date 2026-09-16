package com.dnoel.binauralbeats.core.ports

import com.dnoel.binauralbeats.core.model.AppState
import com.dnoel.binauralbeats.core.model.TimeOfDay

/**
 * T012: test doubles for the outbound ports.
 *
 * [RecordingAudioSink] keeps what it was given, so a test can assert on the actual
 * samples. [CountingAudioSink] keeps only statistics, so a ten-hour session can run
 * without holding roughly 12 GB of audio in memory (research R5).
 */
class RecordingAudioSink(private val failAfterFrames: Long = Long.MAX_VALUE) : AudioSink {

    val written = mutableListOf<Float>()
    var sampleRate: Int = 0
        private set
    var channels: Int = 0
        private set
    var isOpen: Boolean = false
        private set
    var framesWritten: Long = 0L
        private set

    override fun open(sampleRate: Int, channels: Int) {
        check(!isOpen) { "sink already open" }
        this.sampleRate = sampleRate
        this.channels = channels
        isOpen = true
    }

    override fun write(buffer: FloatArray, frames: Int): Int {
        check(isOpen) { "write before open" }
        if (framesWritten >= failAfterFrames) return 0
        for (i in 0 until frames * channels) written += buffer[i]
        framesWritten += frames
        return frames
    }

    override fun close() {
        isOpen = false
    }

    override fun underrunCount(): Int = 0
}

/** Accepts everything, remembers nothing but totals. For long-running renders. */
class CountingAudioSink : AudioSink {

    var framesWritten: Long = 0L
        private set
    var channels: Int = 0
        private set
    var isOpen: Boolean = false
        private set

    override fun open(sampleRate: Int, channels: Int) {
        this.channels = channels
        isOpen = true
    }

    override fun write(buffer: FloatArray, frames: Int): Int {
        framesWritten += frames
        return frames
    }

    override fun close() {
        isOpen = false
    }

    override fun underrunCount(): Int = 0
}

/** In-memory [StateStore]. Optionally fails reads, to exercise the fallback rule. */
class FakeStateStore(
    initial: AppState = AppState(),
    private val failRead: Boolean = false,
) : StateStore {

    var stored: AppState = initial
        private set
    var writeCount: Int = 0
        private set

    override suspend fun read(): AppState {
        if (failRead) return AppState()
        return stored
    }

    override suspend fun write(state: AppState) {
        stored = state
        writeCount++
    }
}

/** A [Clock] the test moves by hand. */
class FakeClock(
    private var epochMillis: Long = 0L,
    private var localTime: TimeOfDay = TimeOfDay(22, 0),
) : Clock {

    override fun nowEpochMillis(): Long = epochMillis

    override fun localTimeNow(): TimeOfDay = localTime

    /** Advances both the instant and the wall clock, keeping them consistent. */
    fun advanceMillis(millis: Long) {
        require(millis >= 0L) { "cannot move time backwards" }
        epochMillis += millis
        val totalMinutes = (localTime.hour * 60 + localTime.minute + (millis / 60_000L)) % (24 * 60)
        localTime = TimeOfDay((totalMinutes / 60).toInt(), (totalMinutes % 60).toInt())
    }

    /**
     * Moves the wall clock without moving the instant, which is what a daylight saving
     * change or a flight across time zones looks like to the app.
     */
    fun setLocalTime(time: TimeOfDay) {
        localTime = time
    }
}
