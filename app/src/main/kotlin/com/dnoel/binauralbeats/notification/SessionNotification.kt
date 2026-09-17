package com.dnoel.binauralbeats.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dnoel.binauralbeats.MainActivity
import com.dnoel.binauralbeats.R
import com.dnoel.binauralbeats.playback.SessionService

/**
 * T037: the ongoing notification, which is how a session is stopped without unlocking
 * (FR-016).
 *
 * The channel is created when the application starts rather than when the first session
 * does. Otherwise it does not exist until after the first night, and the listener cannot
 * adjust or silence it in system settings beforehand. That was a real complaint in the
 * previous project.
 *
 * The channel is deliberately silent and low importance: this notification exists to
 * carry a button, not to announce anything. An app for sleeping must never make a sound
 * of its own (FR-018).
 */
object SessionNotification {

    const val CHANNEL_ID = "session"
    const val NOTIFICATION_ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_session_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_session_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun ongoing(context: Context, paused: Boolean): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            context,
            1,
            Intent(context, SessionService::class.java).setAction(SessionService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val text = if (paused) {
            context.getString(R.string.session_paused)
        } else {
            context.getString(R.string.session_playing)
        }

        return Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.action_stop),
                    stop,
                ).build()
            )
            .build()
    }
}
