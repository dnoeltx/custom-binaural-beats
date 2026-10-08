package com.dnoel.binauralbeats.core.calibration

import com.dnoel.binauralbeats.core.model.ListenerProfile
import com.dnoel.binauralbeats.core.model.PerceptualBounds
import com.dnoel.binauralbeats.core.model.ProfileSource
import com.dnoel.binauralbeats.core.model.ToneJudgment
import com.dnoel.binauralbeats.core.model.Verdict
import com.dnoel.binauralbeats.core.session.SessionTuning
import kotlin.math.ln
import kotlin.math.exp

/**
 * How a sitting is laid out. Tone durations and the inactivity timeout belong to M007 and
 * are decided by listening, not here.
 */
data class CalibrationSettings(
    val lowestHz: Double,
    val highestHz: Double,
    val coarseToneCount: Int,
    val refinementTonesPerEdge: Int,
    val maxJudgments: Int,
    /** Fixed for the whole sitting, so pitch is the only variable (FR-021a). */
    val beatRateHz: Double,
) {
    init {
        require(lowestHz > 0.0 && lowestHz < highestHz) { "the searched range must be positive and ordered" }
        require(PerceptualBounds.isCarrierPerceptible(highestHz)) { "the range must stay perceptible" }
        require(coarseToneCount >= 3) { "a coarse pass needs at least three tones" }
        require(refinementTonesPerEdge >= 0) { "refinement count cannot be negative" }
        require(maxJudgments >= coarseToneCount) { "the budget must at least cover the coarse pass" }
        require(PerceptualBounds.isBeatRatePerceptible(beatRateHz)) { "beat rate must be perceptible" }
    }

    companion object {
        val DEFAULT = CalibrationSettings(
            // Wide enough to contain any plausible preference, narrow enough not to waste
            // tones on pitches nobody sleeps to.
            lowestHz = 60.0,
            highestHz = 600.0,
            // Eleven, not nine. With a tenth of answers wrong, each extra coarse tone is
            // evidence against a mis-tap, and eleven plus four refinement tones still sits
            // inside both the budget and the twelve-minute target in SC-004.
            coarseToneCount = 11,
            refinementTonesPerEdge = 2,
            maxJudgments = 20,
            beatRateHz = 3.0,
        )
    }
}

/**
 * Everything the search knows. No hidden state: an interrupted sitting resumes by
 * rebuilding this from storage (FR-022).
 */
data class CalibrationSearchState(
    val settings: CalibrationSettings,
    val judgments: List<ToneJudgment> = emptyList(),
)

/** What a finished sitting produced, including why it produced nothing. */
sealed interface CalibrationOutcome {
    data class Found(val profile: ListenerProfile, val widened: Boolean) : CalibrationOutcome

    /** Nothing was liked. Offer a retry or a preset rather than inventing a band. */
    data object NothingAccepted : CalibrationOutcome

    /** Everything was liked, which is what tapping through without listening looks like. */
    data object EverythingAccepted : CalibrationOutcome
}

/**
 * Sample and fit (research R9, plan "Calibration search").
 *
 * A coarse pass spreads tones across the range, a refinement pass sharpens the two edges
 * it suggested, and the band is fitted across every judgment from both. Preference over
 * pitch is a band with two edges rather than a monotonic threshold, which is why a
 * staircase was rejected, and a fit degrades gently when a tap is wrong.
 *
 * Tones are spaced geometrically rather than evenly. Pitch is heard in ratios: the step
 * from 60 to 120 Hz is the same musical distance as 300 to 600, so even spacing would
 * crowd the low end with near-identical tones and leave the high end coarse.
 */
object CalibrationSearch {

    fun start(settings: CalibrationSettings): CalibrationSearchState =
        CalibrationSearchState(settings)

    fun accept(state: CalibrationSearchState, judgment: ToneJudgment): CalibrationSearchState =
        state.copy(judgments = state.judgments + judgment)

    /** The next tone to present, or null when the sitting is done (FR-020). */
    fun next(state: CalibrationSearchState): Double? {
        val settings = state.settings
        if (state.judgments.size >= settings.maxJudgments) return null

        val coarse = coarseTones(settings)
        if (state.judgments.size < coarse.size) return coarse[state.judgments.size]

        val refinement = refinementTones(state, coarse)
        val index = state.judgments.size - coarse.size
        return refinement.getOrNull(index)
    }

    /** How far through the planned sitting this state is, for a quiet progress hint. */
    fun progress(state: CalibrationSearchState): Double {
        val coarse = coarseTones(state.settings)
        val planned = coarse.size + refinementTones(state, coarse).size
        if (planned == 0) return 1.0
        return (state.judgments.size.toDouble() / planned).coerceIn(0.0, 1.0)
    }

    fun result(
        state: CalibrationSearchState,
        tuning: SessionTuning,
        nowMillis: Long,
    ): ListenerProfile? = (outcome(state, tuning, nowMillis) as? CalibrationOutcome.Found)?.profile

    fun outcome(
        state: CalibrationSearchState,
        tuning: SessionTuning,
        nowMillis: Long,
    ): CalibrationOutcome {
        val judgments = state.judgments
        val accepted = judgments.count { it.verdict == Verdict.RELAXING }
        if (accepted == 0) return CalibrationOutcome.NothingAccepted
        if (accepted == judgments.size) return CalibrationOutcome.EverythingAccepted

        val band = fitBand(judgments) ?: return CalibrationOutcome.NothingAccepted
        val required = tuning.requiredWidthHz(2) + tuning.maxBeatRateHz
        val widened = band.width < required

        val (low, high) = if (widened) widen(band, required) else band.low to band.high

        return CalibrationOutcome.Found(
            profile = ListenerProfile(
                lowHz = low,
                highHz = high,
                source = ProfileSource.CALIBRATED,
                createdAtEpochMillis = nowMillis,
            ),
            widened = widened,
        )
    }

    // --- the plan ---

    private fun coarseTones(settings: CalibrationSettings): List<Double> =
        geometricSpread(settings.lowestHz, settings.highestHz, settings.coarseToneCount)

    /**
     * Tones placed between the outermost liked tone and its disliked neighbour, on each
     * side. That interval is where the true edge lies, and nowhere else is worth spending
     * the remaining budget on.
     */
    private fun refinementTones(
        state: CalibrationSearchState,
        coarse: List<Double>,
    ): List<Double> {
        val settings = state.settings
        if (settings.refinementTonesPerEdge == 0) return emptyList()

        val coarseJudgments = state.judgments.take(coarse.size)
        if (coarseJudgments.size < coarse.size) return emptyList()

        val likedIndices = coarseJudgments.withIndex()
            .filter { it.value.verdict == Verdict.RELAXING }
            .map { it.index }
        if (likedIndices.isEmpty()) return emptyList()

        val lowIndex = likedIndices.first()
        val highIndex = likedIndices.last()

        val belowEdge = if (lowIndex == 0) settings.lowestHz else coarse[lowIndex - 1]
        val aboveEdge = if (highIndex == coarse.lastIndex) settings.highestHz else coarse[highIndex + 1]

        return between(belowEdge, coarse[lowIndex], settings.refinementTonesPerEdge) +
            between(coarse[highIndex], aboveEdge, settings.refinementTonesPerEdge)
    }

    /** Interior points, geometrically spaced, excluding both ends. */
    private fun between(lowHz: Double, highHz: Double, count: Int): List<Double> {
        if (count <= 0 || highHz <= lowHz) return emptyList()
        return (1..count).map { step ->
            val fraction = step.toDouble() / (count + 1)
            exp(ln(lowHz) + fraction * (ln(highHz) - ln(lowHz)))
        }
    }

    private fun geometricSpread(lowHz: Double, highHz: Double, count: Int): List<Double> =
        (0 until count).map { index ->
            when (index) {
                // Pinned rather than computed: exp(ln(60)) comes back as 59.999999999999986,
                // which is outside the range the search promised to stay inside.
                0 -> lowHz
                count - 1 -> highHz
                else -> {
                    val fraction = index.toDouble() / (count - 1)
                    exp(ln(lowHz) + fraction * (ln(highHz) - ln(lowHz)))
                }
            }
        }

    // --- the fit ---

    private data class Band(val low: Double, val high: Double, val likedCount: Int) {
        val width: Double get() = high - low
    }

    /**
     * The band is the best run of liked tones, in pitch order, tolerating a single
     * disliked tone inside it.
     *
     * Tolerating one absorbs a mis-tap in the middle of the good region, which would
     * otherwise split the answer in two. Requiring a run means an isolated stray
     * acceptance far from the rest loses to the real cluster instead of stretching the
     * band out to meet it.
     *
     * "Best" means the most liked tones, and only then the widest. Scoring by width alone
     * let two adjacent stray acceptances at the bottom of the range beat a real preference
     * whose tones had been unluckily mis-rejected: with a tenth of answers wrong, that
     * happened on one simulated listener in eight.
     */
    private fun fitBand(judgments: List<ToneJudgment>): Band? {
        val ordered = judgments.sortedBy { it.carrierHz }

        // Each tone votes: liked +1, disliked -1. The band is the stretch of pitch with
        // the strongest net vote, found by a maximum-sum scan.
        //
        // This replaced a run-based fit that scored candidate bands by length and width.
        // Runs only look at neighbours, so two lucky stray acceptances could outscore a
        // real preference whose tones had been unluckily mis-rejected, and that happened
        // on roughly one simulated listener in eight at a tenth of answers wrong. Voting
        // weighs every tone in the stretch, so strays have to outnumber the truth rather
        // than merely sit next to each other.
        var bestStart = -1
        var bestEnd = -1
        var bestScore = 0.0

        var runStart = 0
        var runScore = 0.0
        for (index in ordered.indices) {
            val vote = if (ordered[index].verdict == Verdict.RELAXING) 1.0 else DISLIKE_WEIGHT
            if (runScore <= 0.0) {
                runStart = index
                runScore = vote
            } else {
                runScore += vote
            }
            if (runScore > bestScore) {
                bestScore = runScore
                bestStart = runStart
                bestEnd = index
            }
        }
        if (bestStart < 0) return null

        // Trim to the liked tones at each end: a disliked tone should never define an edge.
        val window = ordered.subList(bestStart, bestEnd + 1).filter { it.verdict == Verdict.RELAXING }
        if (window.isEmpty()) return null

        return Band(
            low = window.first().carrierHz,
            high = window.last().carrierHz,
            likedCount = window.size,
        )
    }

    /**
     * A tone disliked inside a candidate band counts double against it.
     *
     * The asymmetry is deliberate, and it is the difference between a right and a wrong
     * answer on a real simulated sitting. Preference over pitch is one continuous region,
     * so a rejection inside a candidate band is direct evidence the band reaches too far,
     * while an acceptance outside it may be a mis-tap. At equal weights, one seeded
     * listener mis-accepted two adjacent tones near the top of the range and the fit
     * bridged a single rejection to swallow them, stretching a true 140 to 260 Hz
     * preference out to 476 Hz.
     *
     * The cost: a single mis-rejection in the middle of a genuinely liked region now
     * splits the band rather than being absorbed, and the result is the narrower half,
     * widened back to a usable range. That is the safer failure of the two, because it
     * lands inside what the listener actually liked rather than outside it.
     */
    private const val DISLIKE_WEIGHT = -2.0

    /** Grows a band to [required] width around its own center, staying perceptible. */
    private fun widen(band: Band, required: Double): Pair<Double, Double> {
        val center = (band.low + band.high) / 2
        val half = required / 2
        val lowest = 40.0
        val highest = PerceptualBounds.MAX_CARRIER_HZ - 1.0

        var low = center - half
        var high = center + half
        if (low < lowest) {
            high += lowest - low
            low = lowest
        }
        if (high > highest) {
            low -= high - highest
            high = highest
        }
        return low.coerceAtLeast(lowest) to high.coerceAtMost(highest)
    }
}
