package com.dnoel.binauralbeats.core.diagnostics

import com.dnoel.binauralbeats.core.model.EndReason
import kotlinx.serialization.Serializable

/**
 * How one session ended (FR-029a). Deliberately small: times, a reason, and the underrun
 * count. No audio, no content, nothing that leaves the device.
 */
@Serializable
data class SessionEnding(
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val endReason: EndReason?,
    val underrunCount: Int,
) {
    val durationMinutes: Long?
        get() = endedAtEpochMillis?.let { (it - startedAtEpochMillis) / 60_000L }

    /** Long enough to count as a real night rather than a test or a false start. */
    val isFullNight: Boolean get() = (durationMinutes ?: 0L) >= FULL_NIGHT_MINUTES

    val endedByListener: Boolean get() = endReason == EndReason.STOPPED_BY_LISTENER

    private companion object {
        const val FULL_NIGHT_MINUTES = 6 * 60L
    }
}

/**
 * A capped, newest-first log of recent endings.
 *
 * Twenty entries is weeks of nights, small enough to sit inside the stored state without
 * thought, and bounded so it can never become the accumulating history FR-029 rules out.
 */
object SessionDiagnostics {

    const val MAX_ENTRIES = 20

    fun record(log: List<SessionEnding>, ending: SessionEnding): List<SessionEnding> =
        (listOf(ending) + log).take(MAX_ENTRIES)
}
