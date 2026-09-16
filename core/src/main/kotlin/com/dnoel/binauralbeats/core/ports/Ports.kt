package com.dnoel.binauralbeats.core.ports

import com.dnoel.binauralbeats.core.model.AppState
import com.dnoel.binauralbeats.core.model.TimeOfDay

/**
 * Outbound ports (contracts/core-api.md). The core declares these; the Android layer
 * implements them. Nothing here names a platform type, which is what lets a full night
 * be rendered and verified on the JVM with no device.
 */

/**
 * Somewhere audio can be written. Android implements this over AudioTrack; tests
 * implement it in memory or discard the samples entirely.
 */
interface AudioSink {

    fun open(sampleRate: Int, channels: Int)

    /**
     * Writes [frames] frames of interleaved samples from [buffer] and returns the number
     * of frames accepted. Blocking: the caller owns a dedicated writer thread.
     */
    fun write(buffer: FloatArray, frames: Int): Int

    fun close()

    /**
     * How many times the sink ran out of audio to play. Zero is the requirement over a
     * full night (research R1); this is asserted in tests, not optimized against.
     */
    fun underrunCount(): Int
}

/**
 * Persistence for the single stored object. A read that fails or finds an unknown schema
 * version MUST return defaults and MUST NOT destroy the stored bytes (research R2).
 */
interface StateStore {
    suspend fun read(): AppState
    suspend fun write(state: AppState)
}

/**
 * Time, injected so that clock-time end behavior, daylight saving and long sessions are
 * all testable without waiting.
 */
interface Clock {
    fun nowEpochMillis(): Long

    /** The listener's current local wall-clock time, which is what AtClockTime means. */
    fun localTimeNow(): TimeOfDay
}
