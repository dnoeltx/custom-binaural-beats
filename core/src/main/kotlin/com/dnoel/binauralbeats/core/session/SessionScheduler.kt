package com.dnoel.binauralbeats.core.session

import com.dnoel.binauralbeats.core.model.BeatArc
import com.dnoel.binauralbeats.core.model.EndBehavior
import com.dnoel.binauralbeats.core.model.ListenerProfile
import com.dnoel.binauralbeats.core.model.SessionConfiguration
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.time.Duration

/** Two tones, one per ear. Their difference is the beat. */
data class CarrierPair(val leftHz: Double, val rightHz: Double) {
    val centreHz: Double get() = (leftHz + rightHz) / 2.0
    val beatHz: Double get() = rightHz - leftHz
}

/** What the audio should be doing at one instant. */
data class SessionParameters(
    val pairs: List<CarrierPair>,
    val beatRateHz: Double,
    val masterGain: Double,
)

/**
 * T031: turns a profile, a configuration and elapsed time into audio parameters.
 *
 * Pure and deterministic: the same inputs, including the seed, always give the same
 * night. That is what lets a ten hour session be rendered and checked in seconds, and
 * what makes a continuity failure reproducible rather than a ghost.
 *
 * Every threshold arrives in [SessionTuning] rather than being written here, because
 * those numbers are one listener's and a later version may measure each listener's own.
 */
class SessionScheduler(
    private val profile: ListenerProfile,
    private val configuration: SessionConfiguration,
    private val tuning: SessionTuning,
    private val seed: Long,
) {
    /**
     * How many carriers actually fit. Derived from the range rather than chosen by the
     * listener: three at the measured spacing need a range 200 Hz wide, and calibration
     * may return less. Spacing is a measured acoustic threshold and carrier count is a
     * preference, so spacing wins and the configured count is a maximum (research M003).
     */
    /**
     * A pair straddles its centre by half the beat rate, and SC-005 requires every tone,
     * not every centre, to lie inside the listener's range. So the usable band is inset
     * by half the largest beat rate the session can reach. Costs about 2 Hz; caught by
     * the range test, which found tones 0.26 Hz outside an edge.
     */
    private val beatMarginHz: Double = tuning.maxBeatRateHz / 2.0
    private val usableLowHz: Double = profile.lowHz + beatMarginHz
    private val usableHighHz: Double = profile.highHz - beatMarginHz
    private val usableWidthHz: Double = (usableHighHz - usableLowHz).coerceAtLeast(0.0)

    val carrierCount: Int = (configuration.carrierCount downTo 1)
        .first { usableWidthHz >= tuning.requiredWidthHz(it) }

    /** How far each carrier may wander without breaching spacing or the range edges. */
    private val driftRangeHz: Double = driftRange()

    /** Where each carrier sits when drift is at rest, spread evenly across the range. */
    private val restingCentres: List<Double> = restingCentres()

    private val plannedDuration: Duration? = when (val end = configuration.endBehavior) {
        is EndBehavior.AfterDuration -> Duration.parse("${end.durationMillis}ms")
        else -> null
    }

    /**
     * The allocation free path, used by the renderer. Writes the current frequencies into
     * the caller's arrays and returns the master gain.
     *
     * [parametersAt] allocates a list and a pair object per call, which is fine for tests
     * and unacceptable in playback: at one update per 64 frames that is about 690 objects
     * a second, all night. The allocation test caught this after the first version of the
     * renderer called [parametersAt] directly.
     */
    fun writeParametersInto(elapsed: Duration, leftHz: DoubleArray, rightHz: DoubleArray): Double {
        require(leftHz.size >= carrierCount && rightHz.size >= carrierCount) {
            "arrays must hold $carrierCount carriers"
        }
        val beatRateHz = beatRateAt(elapsed)
        for (index in 0 until carrierCount) {
            val centre = restingCentres[index] + driftAt(elapsed, index)
            leftHz[index] = centre - beatRateHz / 2.0
            rightHz[index] = centre + beatRateHz / 2.0
        }
        return gainAt(elapsed)
    }

    fun parametersAt(elapsed: Duration): SessionParameters {
        val beatRateHz = beatRateAt(elapsed)
        val pairs = restingCentres.mapIndexed { index, centre ->
            val driftedCentre = centre + driftAt(elapsed, index)
            CarrierPair(
                leftHz = driftedCentre - beatRateHz / 2.0,
                rightHz = driftedCentre + beatRateHz / 2.0,
            )
        }
        return SessionParameters(pairs, beatRateHz, gainAt(elapsed))
    }

    // --- gain ---

    private fun gainAt(elapsed: Duration): Double {
        if (elapsed <= Duration.ZERO) return 0.0
        val fadeIn = raisedCosine(elapsed.inSeconds / tuning.fadeDuration.inSeconds)

        val total = plannedDuration ?: return fadeIn
        if (elapsed >= total) return 0.0
        val remaining = (total - elapsed).inSeconds
        val fadeOut = raisedCosine(remaining / tuning.fadeDuration.inSeconds)
        return min(fadeIn, fadeOut)
    }

    /** Smooth at both ends, which is what keeps a fade from having a corner. */
    private fun raisedCosine(progress: Double): Double = when {
        progress <= 0.0 -> 0.0
        progress >= 1.0 -> 1.0
        else -> 0.5 - 0.5 * cos(PI * progress)
    }

    // --- beat rate ---

    private fun beatRateAt(elapsed: Duration): Double {
        val descended = when (configuration.beatArc) {
            BeatArc.CONSTANT -> tuning.holdBeatRateHz
            BeatArc.DESCEND_THEN_HOLD, BeatArc.DESCEND_THEN_VARY -> {
                val progress = (elapsed.inSeconds / tuning.beatDescentDuration.inSeconds)
                    .coerceIn(0.0, 1.0)
                val eased = raisedCosine(progress)
                tuning.openingBeatRateHz - eased * (tuning.openingBeatRateHz - tuning.holdBeatRateHz)
            }
        }

        val rate = if (configuration.beatArc == BeatArc.DESCEND_THEN_VARY) {
            val afterDescent = (elapsed - tuning.beatDescentDuration).inSeconds.coerceAtLeast(0.0)
            // A slow wander around the hold rate. The period is long and the excursion
            // small, so the change per second stays far below the rate limit.
            val excursion = 0.35
            descended + excursion * sin(2 * PI * afterDescent / VARY_PERIOD_SECONDS)
        } else {
            descended
        }

        // The measured ceiling is enforced here, not merely respected by the arcs above.
        return rate.coerceIn(MIN_BEAT_RATE_HZ, tuning.maxBeatRateHz - BEAT_CEILING_MARGIN_HZ)
    }

    // --- drift ---

    /**
     * Each carrier wanders on its own slow sine, seeded and phase offset so the carriers
     * do not move in lockstep. A sine bounds the excursion by construction, which is what
     * keeps drift inside the range and forces it to reverse rather than run to an edge.
     */
    private fun driftAt(elapsed: Duration, index: Int): Double {
        if (driftRangeHz <= 0.0) return 0.0
        val periodSeconds = driftPeriodSeconds(index)
        val phase = 2 * PI * (elapsed.inSeconds / periodSeconds) + phaseOffset(index)
        return driftRangeHz * sin(phase)
    }

    /**
     * The period is chosen so the fastest point of the sine stays under the measured
     * limit. A sine of amplitude A and period P peaks at 2 * pi * A / P per second.
     */
    private fun driftPeriodSeconds(index: Int): Double {
        val maxPerSecond = tuning.maxDriftHzPerMinute / 60.0
        val minimumPeriod = 2 * PI * driftRangeHz / maxPerSecond
        // Vary the period slightly per carrier so they never re-align, always slower than
        // the minimum rather than faster.
        return minimumPeriod * (1.0 + 0.17 * ((seedFor(index) % 5) + 1))
    }

    private fun phaseOffset(index: Int): Double =
        2 * PI * ((seedFor(index) % 1000) / 1000.0)

    private fun seedFor(index: Int): Long {
        // A small deterministic hash. Kept explicit rather than using Random, so the same
        // seed gives the same night on any platform and any Kotlin version.
        var h = seed * 31L + index * 2_654_435_761L
        h = h xor (h ushr 13)
        h *= 1_274_126_177L
        return (h xor (h ushr 16)) and Long.MAX_VALUE
    }

    // --- layout ---

    /**
     * Carriers sit evenly across the range, inset from each edge by the drift amplitude so
     * that wandering never crosses it. The spare width left after the minimum spacing is
     * spread between the gaps, which is what gives drift room to move in.
     */
    private fun restingCentres(): List<Double> {
        if (carrierCount == 1) return listOf((usableLowHz + usableHighHz) / 2.0)
        val spread = usableWidthHz - 2 * driftRangeHz
        val separation = spread / (carrierCount - 1)
        val first = usableLowHz + driftRangeHz
        return (0 until carrierCount).map { first + it * separation }
    }

    /**
     * Drift amplitude, derived rather than picked.
     *
     * Each carrier rides its own sine, so two neighbours can close on each other by twice
     * the amplitude, and the outermost can reach the range edge. Requiring both of those
     * to stay legal gives `amplitude <= slack / (2 * count)`, where slack is the width
     * left over once the minimum spacing is taken out.
     *
     * The first version of this used slack/4 for every count, which let three carriers
     * close to 75 Hz apart against a 100 Hz minimum. The spacing test caught it.
     */
    private fun driftRange(): Double {
        if (carrierCount <= 1) {
            // A lone carrier has the whole usable band to wander in, minus a margin.
            return max(0.0, usableWidthHz / 4.0)
        }
        val slack = usableWidthHz - tuning.requiredWidthHz(carrierCount)
        return max(0.0, slack / (2.0 * carrierCount))
    }

    private companion object {
        const val MIN_BEAT_RATE_HZ = 0.75
        const val BEAT_CEILING_MARGIN_HZ = 0.05
        const val VARY_PERIOD_SECONDS = 90.0 * 60.0
    }
}

private val Duration.inSeconds: Double get() = inWholeMilliseconds / 1000.0
