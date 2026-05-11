package com.ross.livemedia.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages active live notifications and determines which one should be displayed.
 */
class LiveNotificationManager {
    private val activeNotifications = ConcurrentHashMap<String, LiveNotification>()
    private val _currentNotification = MutableStateFlow<LiveNotification?>(null)
    val currentNotification: StateFlow<LiveNotification?> = _currentNotification.asStateFlow()

    fun updateNotification(notification: LiveNotification) {
        activeNotifications[notification.id] = notification
        updateTopNotification()
    }

    fun removeNotification(id: String) {
        activeNotifications.remove(id)
        updateTopNotification()
    }

    fun removeAllNotifications() {
        activeNotifications.clear()
        updateTopNotification()
    }

    fun getNotification(id: String): LiveNotification? {
        return activeNotifications[id]
    }

    private fun updateTopNotification() {
        val top = activeNotifications.values
            .sortedWith(compareByDescending<LiveNotification> { it.priority }
                .thenByDescending { it.timestamp })
            .firstOrNull()
        
        _currentNotification.value = top
    }
}
