package com.ross.livemedia.notification

import android.app.Application
import android.app.Notification
import android.service.notification.StatusBarNotification
import com.ross.livemedia.lockscreen.LockScreenManager
import com.ross.livemedia.media.MediaStateManager
import com.ross.livemedia.media.MusicState
import com.ross.livemedia.qs.QSStateProvider
import com.ross.livemedia.storage.StorageHelper
import com.ross.livemedia.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.lifecycle.AndroidViewModel

private const val CHANNEL_ID = "MediaLiveUpdateChannel"

class NotificationViewModel(
    application: Application,
    private val onShowNotification: (Notification) -> Unit,
    private val onCancelNotification: () -> Unit
) : AndroidViewModel(application) {
    private val logger = Logger("NotificationViewModel")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val storageHelper = StorageHelper(application)
    private val liveNotificationManager = LiveNotificationManager()
    private val notificationResolver = NotificationResolver()
    private lateinit var mediaStateManager: MediaStateManager
    private lateinit var lockScreenManager: LockScreenManager
    private lateinit var notificationUpdateScheduler: NotificationUpdateScheduler

    private var isQsOpen = false
    private var isNotificationDismissed = false

    fun init() {
        logger.info("init")
        val context = getApplication<Application>()

        lockScreenManager = LockScreenManager(
            context,
            deviceLocked = {
                logger.info("Device Locked. Clear notification")
                onCancelNotification()
            },
            deviceUnlocked = {
                logger.info("Device unlocked. Show notification")
                liveNotificationManager.currentNotification.value?.let {
                    updateNotification(it)
                }
            })

        mediaStateManager = MediaStateManager(
            context,
            notificationManager = liveNotificationManager,
            noActiveMedia = {
                logger.info("No audio. Disable notification")
                // Avoid using mediaStateManager here directly if possible during init,
                // but this callback is likely triggered by maybeUpdateMediaController in MediaStateManager's init.
                // We can use a local reference or check if it's initialized if it's called later.
                if (::mediaStateManager.isInitialized) {
                    mediaStateManager.getUpdatedMusicState()?.id?.let { id ->
                        liveNotificationManager.removeNotification(id)
                    }
                }
            })

        notificationUpdateScheduler =
            NotificationUpdateScheduler {
                val current = liveNotificationManager.currentNotification.value
                if (current != null) {
                    updateNotification(current)
                    current.getUpdateInterval(storageHelper)
                } else {
                    null
                }
            }

        scope.launch {
            liveNotificationManager.currentNotification.collectLatest { notification ->
                if (notification != null) {
                    updateNotification(notification)
                    notificationUpdateScheduler.scheduleUpdate()
                } else {
                    onCancelNotification()
                }
            }
        }

        scope.launch {
            QSStateProvider.isQsOpen.collectLatest { isOpen ->
                val wasOpen = isQsOpen
                isQsOpen = isOpen
                if (isOpen) {
                    if (storageHelper.hideNotificationOnQsOpen) {
                        onCancelNotification()
                    }
                } else if (wasOpen) {
                    liveNotificationManager.currentNotification.value?.let {
                        updateNotification(it)
                    }
                }
            }
        }
    }

    fun cleanup() {
        scope.cancel()
    }

    fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // 1. Resolve generic notifications (Timers, Calls, etc)
        notificationResolver.resolve(sbn)?.let { liveNotification ->
            logger.info("Resolved live notification from: ${sbn.packageName}")
            liveNotificationManager.updateNotification(liveNotification)
            return
        }

        // 2. Handle Media notifications via MediaStateManager
        // We always call this to let MediaStateManager check for media session updates
        mediaStateManager.maybeUpdateMediaController()
    }

    fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        
        // Remove based on ID pattern
        val timerId = "timer_${sbn.packageName}"
        if (liveNotificationManager.getNotification(timerId) != null) {
            liveNotificationManager.removeNotification(timerId)
        }
    }

    fun onTransportControlAction(action: String?) {
        mediaStateManager.handleTransportControl(action)
    }

    fun onNotificationDismissed() {
        isNotificationDismissed = true
        logger.info("Notification dismissed by user")
    }

    private fun updateNotification(notification: LiveNotification) {
        if (notification is MusicState && notification.isPlaying) {
            isNotificationDismissed = false
        }

        if (isNotificationDismissed) {
            logger.info("Notification is dismissed")
            return
        }

        if (!lockScreenManager.isScreenUnlocked() || (isQsOpen && storageHelper.hideNotificationOnQsOpen) || !storageHelper.isAppEnabled(notification.packageName)) {
            onCancelNotification()
            return
        }

        scope.launch(Dispatchers.IO) {
            val builtNotification = notification.buildNotification(getApplication(), storageHelper, CHANNEL_ID)
            onShowNotification(builtNotification)
        }
    }
}
