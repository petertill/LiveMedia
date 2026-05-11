package com.ross.livemedia.notification.types

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.ross.livemedia.notification.LiveNotification
import com.ross.livemedia.storage.StorageHelper
import com.ross.livemedia.utils.formatTime

data class TimerNotification(
    val title: String,
    val endTimeMs: Long,
    override val packageName: String,
    override val timestamp: Long = System.currentTimeMillis()
) : LiveNotification {
    override val id: String = "timer_$packageName"
    override val priority: Int = 200 // Higher than music
    override val isPersistent: Boolean = false

    override fun buildNotification(
        context: Context,
        storageHelper: StorageHelper,
        channelId: String
    ): Notification {
        val remainingMs = (endTimeMs - System.currentTimeMillis()).coerceAtLeast(0L)
        
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("Timer ending in ${formatTime(remainingMs.toInt())}")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShortCriticalText(formatTime(remainingMs.toInt()))
            .setRequestPromotedOngoing(true) // Android 16 Live Activity Chip
            .setWhen(endTimeMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .build()
    }

    override fun getUpdateInterval(storageHelper: StorageHelper): Long? {
        val remainingMs = endTimeMs - System.currentTimeMillis()
        return if (remainingMs > 0) 1000L else null
    }

    companion object {
        fun fromSbn(sbn: StatusBarNotification): TimerNotification? {
            val notification = sbn.notification
            val extras = notification.extras
            
            val isGoogleClock = sbn.packageName == "com.google.android.deskclock"
            if (!isGoogleClock) return null

            val whenMs = notification.`when`
            val currentTime = System.currentTimeMillis()
            
            // If it's Google Clock, we'll be more aggressive.
            // Any notification with a non-zero 'when' that is NOT a completed timer.
            if (whenMs > 0) {
                // To support the countdown, we need a future time.
                // If Google Clock is sending the START time in 'when', we'll try to guess 
                // if it's a timer by the lack of title/text (as seen in logs).
                val hasNoContent = extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrBlank() &&
                                 extras.getCharSequence(Notification.EXTRA_TEXT).isNullOrBlank()
                
                if (hasNoContent) {
                    // Since we don't know the end time, let's look at the 'when'.
                    // If 'when' is very recent, it might be the start time of a timer.
                    // If we can't find the duration, we'll just show it's active.
                    return TimerNotification(
                        title = "Timer",
                        endTimeMs = if (whenMs > currentTime) whenMs else currentTime + 30000, 
                        packageName = sbn.packageName
                    )
                }
            }

            return null
        }
    }
}
