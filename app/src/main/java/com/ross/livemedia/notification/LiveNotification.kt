package com.ross.livemedia.notification

import android.app.Notification
import android.content.Context
import com.ross.livemedia.storage.StorageHelper

/**
 * Interface representing a live notification (Dynamic Island-like).
 */
interface LiveNotification {
    val id: String
    val priority: Int
    val packageName: String
    val timestamp: Long
    val isPersistent: Boolean

    fun buildNotification(
        context: Context,
        storageHelper: StorageHelper,
        channelId: String
    ): Notification

    fun getUpdateInterval(storageHelper: StorageHelper): Long?
}
