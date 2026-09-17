package com.dnoel.binauralbeats.notification

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T037: the notification is the only way to stop a session without unlocking, so its
 * shape is worth asserting rather than eyeballing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionNotificationTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `the channel exists as soon as the app starts, not when the first session does`() {
        // Created by BinauralApp.onCreate, which Robolectric has already run. Before this,
        // the channel did not exist until after the first night, so its sound could not be
        // adjusted in system settings beforehand.
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(manager.getNotificationChannel(SessionNotification.CHANNEL_ID))
    }

    @Test
    fun `the channel is silent, because an app for sleeping must never make a sound`() {
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(SessionNotification.CHANNEL_ID)

        assertNull("the channel must have no sound (FR-018)", channel.sound)
        assertFalse("the channel must not vibrate", channel.shouldVibrate())
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test
    fun `the ongoing notification carries a stop action`() {
        val notification = SessionNotification.ongoing(context, paused = false)
        val actions = notification.actions.orEmpty()

        assertEquals(1, actions.size)
        assertEquals(context.getString(com.dnoel.binauralbeats.R.string.action_stop), actions[0].title)
        assertNotNull("the stop action needs an intent to fire", actions[0].actionIntent)
    }

    @Test
    fun `the notification is ongoing, so it cannot be swiped away mid-session`() {
        val notification = SessionNotification.ongoing(context, paused = false)
        assertTrue(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun `a paused session says so`() {
        val playing = SessionNotification.ongoing(context, paused = false)
        val paused = SessionNotification.ongoing(context, paused = true)
        assertFalse(
            "paused and playing must not read the same",
            playing.extras.getCharSequence(android.app.Notification.EXTRA_TEXT) ==
                paused.extras.getCharSequence(android.app.Notification.EXTRA_TEXT),
        )
    }
}
