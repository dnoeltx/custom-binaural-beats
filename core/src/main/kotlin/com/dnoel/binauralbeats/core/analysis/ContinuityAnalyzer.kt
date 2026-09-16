package com.dnoel.binauralbeats.core.analysis

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * T014: streaming continuity analysis (constitution II).
 *
 * Ten hours of stereo audio is roughly 12 GB, so nothing here holds audio. The analyzer
 * keeps counters, the previous frame, and one block's running sum, which is what lets a
 * full night be checked in seconds on a laptop with no device.
 */

enum class ViolationKind {
    /** Two adjacent samples differ by more than the limit: a click or a cut. */
    SAMPLE_STEP,

    /**
     * The waveform's slope changed abruptly while its value did not: a corner rather
     * than a step. This is what a phase discontinuity at a zero crossing looks like,
     * and it is audible even though every individual sample step stays small. Checking
     * only the first difference would miss it.
     */
    SLOPE_STEP,

    /** Loudness moved faster than the limit: an audible swell or drop. */
    LEVEL_CHANGE,
}

data class ContinuityViolation(
    val kind: ViolationKind,
    val atFrame: Long,
    val observed: Float,
    val limit: Float,
) {
    override fun toString(): String =
        "$kind at frame $atFrame (observed $observed, limit $limit)"
}

/**
 * Limits come from measurement (M001, M002), not from taste. They are passed in so the
 * analyzer cannot quietly encode a guess.
 */
data class ContinuityLimits(
    val maxSampleDelta: Float,
    val maxSlopeChange: Float,
    val maxBlockRmsChangePerSecond: Float,
) {
    init {
        require(maxSampleDelta > 0f) { "maxSampleDelta must be positive" }
        require(maxSlopeChange > 0f) { "maxSlopeChange must be positive" }
        require(maxBlockRmsChangePerSecond > 0f) { "maxBlockRmsChangePerSecond must be positive" }
    }
}

class ContinuityAnalyzer(
    private val sampleRate: Int,
    private val channels: Int,
    private val limits: ContinuityLimits,
    /**
     * How much audio each loudness measurement covers. Half a second, not a few
     * milliseconds: several carriers playing at once interfere, and that interference is
     * real amplitude movement at the difference frequencies. A short window reads it as
     * the level lurching about, when it is simply the texture of more than one tone.
     * The window has to be long compared with those differences (100 Hz apart means
     * 10 ms) so that what is left is the loudness trend, which is the thing Principle I
     * is actually about.
     */
    private val rmsWindowFrames: Int = sampleRate / 2,
    private val maxViolationsKept: Int = 50,
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(channels >= 1) { "channels must be at least 1" }
        require(rmsWindowFrames > 0) { "rmsWindowFrames must be positive" }
    }

    var framesAnalyzed: Long = 0L
        private set

    var maxObservedSampleDelta: Float = 0f
        private set

    private val violations = mutableListOf<ContinuityViolation>()
    private var violationCount = 0

    private var previousSample = FloatArray(channels)
    private var previousDelta = FloatArray(channels)
    private var hasPrevious = false
    private var hasPreviousDelta = false

    private var windowSquareSum = 0.0
    private var windowFrames = 0
    private var previousWindowRms: Float? = null

    /**
     * Accepts [frames] frames of interleaved samples. Call repeatedly; state carries
     * across calls so a block boundary is not mistaken for a discontinuity.
     */
    fun accept(buffer: FloatArray, frames: Int) {
        require(frames * channels <= buffer.size) { "buffer too small for $frames frames" }

        for (frame in 0 until frames) {
            val base = frame * channels
            for (channel in 0 until channels) {
                val sample = buffer[base + channel]

                if (hasPrevious) {
                    val signedDelta = sample - previousSample[channel]
                    val delta = abs(signedDelta)
                    if (delta > maxObservedSampleDelta) maxObservedSampleDelta = delta
                    if (delta > limits.maxSampleDelta) {
                        record(
                            ContinuityViolation(
                                kind = ViolationKind.SAMPLE_STEP,
                                atFrame = framesAnalyzed,
                                observed = delta,
                                limit = limits.maxSampleDelta,
                            )
                        )
                    }

                    if (hasPreviousDelta) {
                        val slopeChange = abs(signedDelta - previousDelta[channel])
                        if (slopeChange > limits.maxSlopeChange) {
                            record(
                                ContinuityViolation(
                                    kind = ViolationKind.SLOPE_STEP,
                                    atFrame = framesAnalyzed,
                                    observed = slopeChange,
                                    limit = limits.maxSlopeChange,
                                )
                            )
                        }
                    }
                    previousDelta[channel] = signedDelta
                    if (channel == channels - 1) hasPreviousDelta = true
                }

                previousSample[channel] = sample
                windowSquareSum += sample.toDouble() * sample.toDouble()
            }

            hasPrevious = true
            framesAnalyzed++
            windowFrames++

            if (windowFrames == rmsWindowFrames) {
                closeWindow()
            }
        }
    }

    private fun closeWindow() {
        val rms = sqrt(windowSquareSum / (windowFrames * channels)).toFloat()
        val previous = previousWindowRms
        if (previous != null) {
            val seconds = windowFrames.toFloat() / sampleRate
            val changePerSecond = abs(rms - previous) / seconds
            if (changePerSecond > limits.maxBlockRmsChangePerSecond) {
                record(
                    ContinuityViolation(
                        kind = ViolationKind.LEVEL_CHANGE,
                        atFrame = framesAnalyzed,
                        observed = changePerSecond,
                        limit = limits.maxBlockRmsChangePerSecond,
                    )
                )
            }
        }
        previousWindowRms = rms
        windowSquareSum = 0.0
        windowFrames = 0
    }

    private fun record(violation: ContinuityViolation) {
        violationCount++
        if (violations.size < maxViolationsKept) violations += violation
    }

    /** Up to [maxViolationsKept] violations; [totalViolations] is the true count. */
    fun violations(): List<ContinuityViolation> = violations.toList()

    val totalViolations: Int get() = violationCount
}
