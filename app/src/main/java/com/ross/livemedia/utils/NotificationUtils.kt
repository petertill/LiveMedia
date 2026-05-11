package com.ross.livemedia.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

fun buildBaseBigTextStyle() = NotificationCompat.BigTextStyle()

fun <T> createAction(
    icon: Int,
    title: String,
    action: String,
    requestCode: Int,
    packageContext: Context,
    cls: Class<T>,
): NotificationCompat.Action {
    val intent = Intent(packageContext, cls).setAction(action)
    val pendingIntent = PendingIntent.getService(
        packageContext,
        requestCode,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    return NotificationCompat.Action(icon, title, pendingIntent)
}
