package com.dnoel.binauralbeats.core.playback

import com.dnoel.binauralbeats.core.model.AppState
import com.dnoel.binauralbeats.core.model.SessionEnding

/**
 * FR-029a: a bounded record of how recent sessions ended.
 *
 * Added after a real failure. A session stopped about two hours into an overnight run and
 * nothing survived to say why: the most-recent-session record had been overwritten by a
 * later test, and the system log had rotated. The single-session rule in FR-029 is right
 * for the product and wrong for finding a fault that happens once a fortnight.
 *
 * Append only, capped, and never shown in the interface. It answers "what happened last
 * night" and refuses to become a sleep diary.
 */
object SessionDiagnostics {

    const val MAX_ENTRIES = 20

    /** Appends [ending], dropping the oldest entry once the cap is reached. */
    fun record(state: AppState, ending: SessionEnding): AppState {
        val appended = state.recentEndings + ending
        return state.copy(recentEndings = appended.takeLast(MAX_ENTRIES))
    }

    /**
     * One line per ending, newest first, for reading over adb or pasting into a pull
     * request after an overnight run.
     */
    fun summarize(state: AppState): String =
        state.recentEndings.asReversed().joinToString("\n") { ending ->
            val minutes = ending.durationMillis / 60_000.0
            "started=${ending.startedAtEpochMillis} " +
                "ran=${formatMinutes(minutes)} " +
                "reason=${ending.endReason} " +
                "underruns=${ending.underrunCount}"
        }

    private fun formatMinutes(minutes: Double): String = when {
        minutes >= 60.0 -> "${(minutes / 60.0).roundTo1()}h"
        else -> "${minutes.roundTo1()}m"
    }

    private fun Double.roundTo1(): Double = kotlin.math.round(this * 10.0) / 10.0
}
