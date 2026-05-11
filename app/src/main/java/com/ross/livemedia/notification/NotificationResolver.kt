package com.ross.livemedia.notification

import android.service.notification.StatusBarNotification
import com.ross.livemedia.notification.types.TimerNotification
import com.ross.livemedia.notification.types.CallNotification
import com.ross.livemedia.notification.types.CalendarNotification
import android.app.Notification
import com.ross.livemedia.utils.Logger

class NotificationResolver {
    private val logger = Logger("NotificationResolver")
    
    fun resolve(sbn: StatusBarNotification): LiveNotification? {
        val packageName = sbn.packageName
        
        // 1. Check for Calls
        CallNotification.fromSbn(sbn)?.let { 
            logger.info("Resolved as CallNotification")
            return it 
        }

        // 2. Check for Timers (Google Clock)
        if (packageName == "com.google.android.deskclock") {
            logger.info("Google Clock detected, attempting timer resolution...")
            TimerNotification.fromSbn(sbn)?.let { 
                logger.info("Successfully resolved TimerNotification")
                return it 
            } ?: logger.info("Failed to resolve Google Clock notification as Timer")
        }

        // 3. Check for Calendar Events
        CalendarNotification.fromSbn(sbn)?.let {
            logger.info("Resolved as CalendarNotification")
            return it
        }
        
        // 4. Check for Media Notifications
        if (sbn.notification.category == Notification.CATEGORY_TRANSPORT || 
            sbn.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
            logger.info("Media notification detected, letting MediaStateManager handle it")
            // Returning null here because MediaStateManager (still used by ViewModel) 
            // will pick it up via its own listener and update the manager.
            // However, to ensure it "works", we should make sure MediaStateManager is active.
        }
        
        return null
    }
}
