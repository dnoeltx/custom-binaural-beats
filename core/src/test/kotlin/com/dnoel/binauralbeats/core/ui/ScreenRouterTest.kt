package com.dnoel.binauralbeats.core.ui

import com.dnoel.binauralbeats.core.model.AppState
import com.dnoel.binauralbeats.core.model.EndReason
import com.dnoel.binauralbeats.core.model.ListenerProfile
import com.dnoel.binauralbeats.core.model.ProfileSource
import com.dnoel.binauralbeats.core.model.SessionConfiguration
import com.dnoel.binauralbeats.core.model.SessionRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Which screen the listener sees, decided here rather than inside Compose so the rule can
 * be read and tested on its own.
 *
 * The welcome screen exists for someone who has never used the app (FR-023). Anyone who
 * has run a session or saved a profile goes straight to the home screen, because being
 * introduced to an app you already use is its own small annoyance.
 */
class ScreenRouterTest {

    private val profile = ListenerProfile(150.0, 400.0, ProfileSource.CALIBRATED, 0L)

    private fun endedSession() = SessionRecord(
        startedAtEpochMillis = 1L,
        endedAtEpochMillis = 2L,
        endReason = EndReason.STOPPED_BY_LISTENER,
        configuration = SessionConfiguration(),
        profile = profile,
        renderSeed = 1L,
    )

    @Test
    fun `a brand new install sees the welcome screen`() {
        assertEquals(Screen.WELCOME, ScreenRouter.screenFor(AppState(), sessionRunning = false))
    }

    @Test
    fun `someone who has run a session before goes straight home`() {
        val state = AppState(lastSession = endedSession())
        assertEquals(Screen.HOME, ScreenRouter.screenFor(state, sessionRunning = false))
    }

    @Test
    fun `someone with a saved profile goes straight home`() {
        val state = AppState(profile = profile)
        assertEquals(Screen.HOME, ScreenRouter.screenFor(state, sessionRunning = false))
    }

    @Test
    fun `a running session takes over whatever else would have been shown`() {
        // Including on a first run: opening the app mid-session must show the session,
        // not an introduction.
        assertEquals(Screen.SESSION, ScreenRouter.screenFor(AppState(), sessionRunning = true))
        assertEquals(
            Screen.SESSION,
            ScreenRouter.screenFor(AppState(profile = profile), sessionRunning = true),
        )
    }

    @Test
    fun `the welcome screen does not come back after the first session ends`() {
        val afterFirstNight = AppState(lastSession = endedSession())
        assertEquals(Screen.HOME, ScreenRouter.screenFor(afterFirstNight, sessionRunning = false))
    }
}
