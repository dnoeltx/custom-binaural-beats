package com.dnoel.binauralbeats.core.ui

import com.dnoel.binauralbeats.core.model.AppState

/** The screens this feature has. Calibration joins them with US2. */
enum class Screen { WELCOME, HOME, SESSION }

/**
 * Chooses the screen from what is stored and whether a session is playing.
 *
 * A running session always wins, including on a first run: someone opening the app while
 * it plays wants the stop control, not an introduction.
 */
object ScreenRouter {

    fun screenFor(state: AppState, sessionRunning: Boolean): Screen = when {
        sessionRunning -> Screen.SESSION
        state.profile == null && state.lastSession == null -> Screen.WELCOME
        else -> Screen.HOME
    }
}
