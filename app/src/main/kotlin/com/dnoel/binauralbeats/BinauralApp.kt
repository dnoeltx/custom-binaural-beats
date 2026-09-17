package com.dnoel.binauralbeats

import android.app.Application
import com.dnoel.binauralbeats.notification.SessionNotification

/**
 * Creates the notification channel at application start rather than when the first
 * session begins, so the listener can adjust or silence it in system settings before
 * ever running a session (T037).
 */
class BinauralApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionNotification.createChannel(this)
    }
}
