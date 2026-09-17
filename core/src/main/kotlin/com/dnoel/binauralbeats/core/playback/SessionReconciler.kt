package com.dnoel.binauralbeats.core.playback

import com.dnoel.binauralbeats.core.model.AppState
import com.dnoel.binauralbeats.core.model.EndReason

/**
 * T043 and FR-025: closes a session record that was left open by a process that died.
 *
 * A session ends by running its own cleanup, which records why. A killed process runs
 * nothing, so the record stays open and the app would otherwise show a session still in
 * progress. This closes it as [EndReason.UNKNOWN], which is the honest answer: we know it
 * stopped, and we do not know why.
 *
 * Deliberately conservative. A record that already carries an ending is never rewritten,
 * because overwriting a real reason with UNKNOWN would destroy the only evidence of how
 * the night actually finished.
 */
object SessionReconciler {

    fun reconcile(state: AppState, serviceRunning: Boolean, nowMillis: Long): AppState {
        val record = state.lastSession ?: return state
        if (serviceRunning || !record.isRunning) return state

        return state.copy(
            lastSession = record.copy(
                endedAtEpochMillis = nowMillis,
                endReason = EndReason.UNKNOWN,
            )
        )
    }
}
