package com.dnoel.binauralbeats.core.model

import kotlinx.serialization.Serializable

/**
 * The persisted model (data-model.md).
 *
 * Time is represented as epoch milliseconds and as a [TimeOfDay] defined here, rather
 * than with `java.time` types. Two reasons: constitution VII requires this module to
 * stay portable, so converting it to Kotlin Multiplatform later remains a build change
 * rather than a rewrite, and a wall-clock end time is a time of day rather than an
 * instant (see the daylight saving edge case in spec.md). The Android layer converts.
 */

/** A wall-clock time of day, with no date and no time zone. */
@Serializable
data class TimeOfDay(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23) { "hour must be 0..23, was $hour" }
        require(minute in 0..59) { "minute must be 0..59, was $minute" }
    }

    override fun toString(): String = "%02d:%02d".format(hour, minute)
}

@Serializable
enum class ProfileSource { CALIBRATED, PRESET, MANUAL }

/**
 * The listener's preferred carrier range: the saved result of calibration, a preset
 * chosen on first run, or a hand edit in settings.
 */
@Serializable
data class ListenerProfile(
    val lowHz: Double,
    val highHz: Double,
    val source: ProfileSource,
    val createdAtEpochMillis: Long,
) {
    init {
        require(lowHz > 0.0) { "lowHz must be positive, was $lowHz" }
        require(lowHz < highHz) { "lowHz ($lowHz) must be below highHz ($highHz)" }
        require(PerceptualBounds.isCarrierPerceptible(lowHz)) {
            "lowHz ($lowHz) is outside the perceptible carrier bound"
        }
        require(PerceptualBounds.isCarrierPerceptible(highHz)) {
            "highHz ($highHz) is outside the perceptible carrier bound"
        }
    }

    val widthHz: Double get() = highHz - lowHz

    /**
     * Whether [count] carriers can sit inside this range while staying at least
     * [minSpacingHz] apart. The spacing comes from measurement (M003), and the count
     * from the listener's configuration, so this is a query rather than an invariant.
     */
    fun fitsCarriers(count: Int, minSpacingHz: Double): Boolean {
        require(count >= 1) { "count must be at least 1, was $count" }
        require(minSpacingHz >= 0.0) { "minSpacingHz must not be negative, was $minSpacingHz" }
        if (count == 1) return true
        return widthHz >= minSpacingHz * (count - 1)
    }
}

@Serializable
enum class BeatArc { DESCEND_THEN_HOLD, CONSTANT, DESCEND_THEN_VARY }

/** How a session finishes (FR-011). */
@Serializable
sealed interface EndBehavior {

    /** Plays until the listener stops it. The default. */
    @Serializable
    data object RunUntilStopped : EndBehavior

    /** Fades out so that silence is reached [durationMillis] after the session began. */
    @Serializable
    data class AfterDuration(val durationMillis: Long) : EndBehavior {
        init {
            require(durationMillis > 0L) { "durationMillis must be positive, was $durationMillis" }
        }
    }

    /** Fades out so that silence is reached at or before this local wall-clock time. */
    @Serializable
    data class AtClockTime(val time: TimeOfDay) : EndBehavior
}

@Serializable
data class SessionConfiguration(
    val beatArc: BeatArc = BeatArc.DESCEND_THEN_HOLD,
    val endBehavior: EndBehavior = EndBehavior.RunUntilStopped,
    val carrierCount: Int = 3,
    val volumeWarningAcknowledged: Boolean = false,
) {
    init {
        require(carrierCount in 2..3) { "carrierCount must be 2 or 3, was $carrierCount" }
    }
}

@Serializable
enum class Verdict { RELAXING, NOT_RELAXING }

/** One decision during calibration. */
@Serializable
data class ToneJudgment(
    val carrierHz: Double,
    val verdict: Verdict,
    val presentedAtEpochMillis: Long,
    val responseMillis: Long,
) {
    init {
        require(carrierHz > 0.0) { "carrierHz must be positive, was $carrierHz" }
        require(responseMillis >= 0L) { "responseMillis must not be negative, was $responseMillis" }
    }
}

@Serializable
enum class CalibrationState { IN_PROGRESS, COMPLETED, ABANDONED }

/**
 * One calibration sitting. Only an IN_PROGRESS sitting is resumable, and none of them
 * may replace the saved profile before reaching COMPLETED (FR-022).
 */
@Serializable
data class CalibrationSession(
    val id: String,
    val startedAtEpochMillis: Long,
    val beatRateHz: Double,
    val judgments: List<ToneJudgment> = emptyList(),
    val state: CalibrationState = CalibrationState.IN_PROGRESS,
    val result: ListenerProfile? = null,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(PerceptualBounds.isBeatRatePerceptible(beatRateHz)) {
            "beatRateHz ($beatRateHz) is outside the perceptible beat bound"
        }
        require(state != CalibrationState.COMPLETED || result != null) {
            "a COMPLETED calibration must carry a result"
        }
        require(state == CalibrationState.COMPLETED || result == null) {
            "only a COMPLETED calibration may carry a result"
        }
    }

    val isResumable: Boolean get() = state == CalibrationState.IN_PROGRESS
}

@Serializable
enum class EndReason {
    STOPPED_BY_LISTENER,
    COMPLETED_AS_CONFIGURED,
    OUTPUT_LOST,
    INTERRUPTED,

    /** Covers process death, so the app never claims a session is running (FR-025). */
    UNKNOWN,
}

/** The most recent session, and only that one (FR-029). */
@Serializable
data class SessionRecord(
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val endReason: EndReason? = null,
    val configuration: SessionConfiguration,
    val profile: ListenerProfile,
    val renderSeed: Long,
    val carrierSummary: List<Double> = emptyList(),
) {
    val isRunning: Boolean get() = endedAtEpochMillis == null && endReason == null
}

/** The single stored object (data-model.md). */
@Serializable
data class AppState(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val profile: ListenerProfile? = null,
    val settings: SessionConfiguration = SessionConfiguration(),
    val calibrationInProgress: CalibrationSession? = null,
    val lastSession: SessionRecord? = null,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}
