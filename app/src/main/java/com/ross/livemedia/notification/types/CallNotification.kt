package com.ross.livemedia.notification.types

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.ross.livemedia.media.NotificationProvider
import com.ross.livemedia.notification.LiveNotification
import com.ross.livemedia.storage.StorageHelper

data class CallNotification(
    val callerName: String,
    override val packageName: String,
    override val timestamp: Long = System.currentTimeMillis()
) : LiveNotification {
    override val id: String = "call_$packageName"
    override val priority: Int = 300 // Highest priority
    override val isPersistent: Boolean = true

    override fun buildNotification(
        context: Context,
        storageHelper: StorageHelper,
        channelId: String
    ): Notification {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(NotificationProvider.getByAppName("Phone").iconRes)
            .setContentTitle("Incoming Call")
            .setContentText(callerName)
            .setShortCriticalText(callerName)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setRequestPromotedOngoing(true) // Android 16 Live Activity Chip
            .build()
    }

    override fun getUpdateInterval(storageHelper: StorageHelper): Long? = null

    companion object {
        fun fromSbn(sbn: StatusBarNotification): CallNotification? {
            val notification = sbn.notification
            if (notification.category == Notification.CATEGORY_CALL || 
                sbn.packageName.contains("telecom") || 
                sbn.packageName.contains("dialer")) {
                
                val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Unknown Caller"
                return CallNotification(title, sbn.packageName)
            }
            return null
        }
    }
}
