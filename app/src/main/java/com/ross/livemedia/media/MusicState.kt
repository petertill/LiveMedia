package com.ross.livemedia.media

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.net.Uri
import androidx.core.net.toUri
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import com.bumptech.glide.Glide
import com.ross.livemedia.notification.LiveNotification
import com.ross.livemedia.notification.MediaNotificationListenerService
import com.ross.livemedia.storage.StorageHelper
import com.ross.livemedia.utils.*
import androidx.core.app.NotificationCompat

data class MusicState(
    val title: String,
    val artist: String,
    val albumArt: Bitmap?,
    val albumArtUri: Uri?,
    val isPlaying: Boolean,
    val duration: Long,
    val position: Long,
    override val packageName: String,
    val mediaSessionActive: Boolean,
    val albumName: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val titleStartTime: Long = System.currentTimeMillis()
) : LiveNotification {

    override val id: String get() = "music_$packageName"
    override val priority: Int get() = if (isPlaying) 100 else 50
    override val isPersistent: Boolean get() = true

    fun withUpdatedTitleStartTime(lastTitle: String?, lastTitleStartTime: Long): MusicState {
        return if (title == lastTitle) {
            this.copy(titleStartTime = lastTitleStartTime)
        } else {
            this
        }
    }

    override fun buildNotification(
        context: Context,
        storageHelper: StorageHelper,
        channelId: String
    ): Notification {
        var contentIntent: PendingIntent? = null
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            contentIntent = PendingIntent.getActivity(
                context, 0,
                launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val musicAppName = context.packageManager.getAppName(packageName) as String

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(NotificationProvider.getByAppName(musicAppName).iconRes)
            .setContentTitle(title)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShortCriticalText(providePillText(
                title,
                position.toInt(),
                duration.toInt(),
                isPlaying,
                storageHelper.pillContent,
                storageHelper.isScrollEnabled,
                System.currentTimeMillis() - titleStartTime
            ))
            .setRequestPromotedOngoing(true) // Android 16 Live Activity Chip
            .setShowWhen(false)
            .setStyle(buildBaseBigTextStyle())
            .setSubText(
                combineProviderAndTimestamp(
                    musicAppName,
                    storageHelper.showMusicProvider,
                    storageHelper.showTimestamp,
                    position.toInt(),
                    duration.toInt()
                )
            )

        if (storageHelper.showAlbumArt) {
            var art: Bitmap? = albumArt
            if (art == null && albumArtUri != null) {
                try {
                    art = Glide.with(context)
                        .asBitmap()
                        .load(albumArtUri)
                        .submit(144, 144)
                        .get()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            notification.setLargeIcon(art)
        }

        if (storageHelper.showProgress) {
            notification.setProgress(
                duration.toInt(),
                position.toInt(),
                false
            )
        }

        if (storageHelper.showArtistName || storageHelper.showAlbumName) {
            notification.setContentText(
                buildArtisAlbumTitle(
                    storageHelper.showArtistName,
                    storageHelper.showAlbumName,
                    this
                )
            )
        }

        if (storageHelper.showActionButtons) {
            notification.addAction(prevMusicAction(context))
            notification.addAction(if (isPlaying) pauseMusicAction(context) else playMusicAction(context))
            notification.addAction(nextMusicAction(context))
        }

        if (contentIntent != null) {
            notification.setContentIntent(contentIntent)
        }

        return notification.build()
    }

    override fun getUpdateInterval(storageHelper: StorageHelper): Long? {
        val isTitleScrollable = title.trim().length > 7
        val shouldScroll = storageHelper.isScrollEnabled && isTitleScrollable

        val shouldRun = isPlaying || shouldScroll
        return if (shouldRun) {
            if (shouldScroll) 500L else 1000L
        } else {
            null
        }
    }

    private fun playMusicAction(context: Context) = createAction(
        android.R.drawable.ic_media_play,
        "Play",
        MediaStateManager.ACTION_PLAY_PAUSE,
        MediaStateManager.REQUEST_CODE_PLAY_PAUSE,
        context,
        MediaNotificationListenerService::class.java
    )

    private fun pauseMusicAction(context: Context) = createAction(
        android.R.drawable.ic_media_pause,
        "Pause",
        MediaStateManager.ACTION_PLAY_PAUSE,
        MediaStateManager.REQUEST_CODE_PLAY_PAUSE,
        context,
        MediaNotificationListenerService::class.java
    )

    private fun prevMusicAction(context: Context) = createAction(
        android.R.drawable.ic_media_previous,
        "Previous",
        MediaStateManager.ACTION_SKIP_TO_PREVIOUS,
        MediaStateManager.REQUEST_CODE_PREVIOUS,
        context,
        MediaNotificationListenerService::class.java
    )

    private fun nextMusicAction(context: Context) = createAction(
        android.R.drawable.ic_media_next,
        "Next",
        MediaStateManager.ACTION_SKIP_TO_NEXT,
        MediaStateManager.REQUEST_CODE_NEXT,
        context,
        MediaNotificationListenerService::class.java
    )

    constructor(
        metadata: MediaMetadata,
        playbackState: PlaybackState,
        packageName: String
    ) : this(
        title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Title",
        artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist",
        albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
        albumArtUri = (metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI))?.toUri(),
        isPlaying = playbackState.state == PlaybackState.STATE_PLAYING,
        duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L),
        position = playbackState.position.coerceAtLeast(0L),
        packageName = packageName,
        mediaSessionActive = true,
        albumName = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "Unknown Album",
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (javaClass != other?.javaClass) return false

        other as MusicState

        if (title != other.title) return false
        if (artist != other.artist) return false
        if (packageName != other.packageName) return false
        if (albumArt != other.albumArt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + packageName.hashCode()
        return result
    }

    companion object {
        const val EMPTY_ALBUM = "Unknown Album"
        const val EMPTY_ARTIST = "Unknown Artist"
    }
}