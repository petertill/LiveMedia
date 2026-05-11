package com.ross.livemedia.notification.types

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.ross.livemedia.notification.LiveNotification
import com.ross.livemedia.storage.StorageHelper

data class CalendarNotification(
    val eventTitle: String,
    val startTimeMs: Long,
    override val packageName: String,
    override val timestamp: Long = System.currentTimeMillis()
) : LiveNotification {
    override val id: String = "calendar_$packageName"
    override val priority: Int = 150 // Above music, below timer/call
    override val isPersistent: Boolean = false

    override fun buildNotification(
        context: Context,
        storageHelper: StorageHelper,
        channelId: String
    ): Notification {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle("Calendar Event")
            .setContentText(eventTitle)
            .setShortCriticalText(eventTitle)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setRequestPromotedOngoing(true) // Android 16 Live Activity Chip
            .setWhen(startTimeMs)
            .setShowWhen(true)
            .build()
    }

    override fun getUpdateInterval(storageHelper: StorageHelper): Long? = null

    companion object {
        fun fromSbn(sbn: StatusBarNotification): CalendarNotification? {
            val notification = sbn.notification
            if (notification.category == Notification.CATEGORY_EVENT || 
                sbn.packageName.contains("calendar")) {
                
                val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Event"
                val startTime = notification.`when`
                return CalendarNotification(title, startTime, sbn.packageName)
            }
            return null
        }
    }
}
