package com.dnoel.binauralbeats.core.audio

import com.dnoel.binauralbeats.core.session.SessionScheduler
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

/**
 * T030: one contributing source within a session (FR-014).
 *
 * A session is a set of layers summed by the renderer, each carrying its own envelope.
 * Binaural tones are the only type in this feature; generated noise and recorded ambience
 * are later specs. Adding one of those must mean writing another implementation of this
 * interface and nothing else: no change to the scheduler, to this contract, or to
 * calibration (constitution VII).
 *
 * Implementations MUST add into the buffer rather than overwrite it, MUST allocate
 * nothing per call, and MUST be deterministic for a given start frame.
 */
interface SoundLayer {

    /** Called before rendering begins, and again whenever the sample rate changes. */
    fun prepare(sampleRate: Int)

    /** Re-establishes internal state for a non-sequential jump to [startFrame]. */
    fun seekTo(startFrame: Long)

    /**
     * Adds [frames] frames of interleaved stereo into [into], starting at [startFrame],
     * scaled by [scale]. Adds; never overwrites.
     */
    fun addTo(into: FloatArray, frames: Int, startFrame: Long, scale: Double)
}

/**
 * The binaural tone layer: one pair of tones per carrier, whose left and right difference
 * produces the beat.
 *
 * Phase is integrated rather than recomputed from a clock. Computing `sin(2 pi f t)` with
 * a moving `f` puts a step in the waveform at every frequency change, which is precisely
 * the click this product exists to avoid.
 */
class BinauralToneLayer(
    private val scheduler: SessionScheduler,
    /** Parameters are refreshed this often; the tones are stationary in between. */
    private val controlBlockFrames: Int = 64,
) : SoundLayer {

    private val count = scheduler.carrierCount
    private val leftPhase = DoubleArray(count)
    private val rightPhase = DoubleArray(count)
    private val leftHz = DoubleArray(count)
    private val rightHz = DoubleArray(count)
    private var sampleRate = 0
    private var gain = 0.0

    override fun prepare(sampleRate: Int) {
        require(sampleRate > 0) { "sampleRate must be positive" }
        this.sampleRate = sampleRate
    }

    override fun seekTo(startFrame: Long) {
        val blockStart = startFrame - (startFrame % controlBlockFrames)
        refresh(blockStart)
        for (i in 0 until count) {
            leftPhase[i] = phaseAt(blockStart, leftHz[i])
            rightPhase[i] = phaseAt(blockStart, rightHz[i])
        }
        var frame = blockStart
        while (frame < startFrame) {
            step()
            frame++
        }
    }

    override fun addTo(into: FloatArray, frames: Int, startFrame: Long, scale: Double) {
        check(sampleRate > 0) { "prepare() was not called" }
        val perCarrier = scale / count

        var frame = 0
        while (frame < frames) {
            val absolute = startFrame + frame
            if (absolute % controlBlockFrames == 0L) refresh(absolute)

            var left = 0.0
            var right = 0.0
            for (i in 0 until count) {
                left += sin(leftPhase[i])
                right += sin(rightPhase[i])
            }
            step()

            val envelope = gain * perCarrier
            into[frame * 2] += (left * envelope).toFloat()
            into[frame * 2 + 1] += (right * envelope).toFloat()
            frame++
        }
    }

    private fun step() {
        for (i in 0 until count) {
            leftPhase[i] = advance(leftPhase[i], leftHz[i])
            rightPhase[i] = advance(rightPhase[i], rightHz[i])
        }
    }

    private fun advance(phase: Double, hz: Double): Double {
        val next = phase + TWO_PI * hz / sampleRate
        // Wrapping keeps the magnitude small. Without it an eight hour session reaches a
        // phase near ten million radians, where double precision starts to granulate the
        // waveform.
        return if (next >= TWO_PI) next - TWO_PI else next
    }

    private fun phaseAt(frame: Long, hz: Double): Double {
        val cycles = hz * frame / sampleRate
        return TWO_PI * (cycles - floor(cycles))
    }

    private fun refresh(frame: Long) {
        gain = scheduler.writeParametersInto(
            (frame * 1_000L / sampleRate).milliseconds,
            leftHz,
            rightHz,
        )
    }

    private companion object {
        const val TWO_PI = 2 * PI
    }
}
