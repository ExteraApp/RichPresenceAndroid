package xyz.extera.rpc.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import xyz.extera.rpc.data.Credentials
import xyz.extera.rpc.data.CredentialsStore
import xyz.extera.rpc.data.MatrixApi
import xyz.extera.rpc.data.SettingsStore
import xyz.extera.rpc.data.UploadHelper
import java.io.File
import java.io.FileOutputStream

/**
 * Represents the current music playback state extracted from a media notification.
 */
data class MusicState(
    val packageName: String,
    val title: String,
    val artist: String,
    val album: String,
    val isPlaying: Boolean,
    val coverArtBitmap: Bitmap? = null
)

/**
 * NotificationListenerService that listens for audio player (MediaStyle) notifications
 * from user-selected apps when the music feature is enabled.
 *
 * Extracts track name, artist, album and play/pause state via MediaController.
 */
class MusicNotificationListenerService : NotificationListenerService() {
    companion object {
        private const val TAG = "MusicNLS"

        /** The latest known music state, or null if nothing is playing / service is idle. */
        @Volatile
        var currentMusicState: MusicState? = null
            private set

        /** Optional listener for state changes. */
        @Volatile
        var onMusicStateChanged: ((MusicState?) -> Unit)? = null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        if (!SettingsStore.isMusicEnabled(applicationContext)) return
        if (!isMediaNotification(sbn)) return
        if (!isAllowedApp(sbn.packageName)) return

        val state = extractMusicState(sbn)
        if (state != null) {
            Log.d(TAG, "Now ${if (state.isPlaying) "playing" else "paused"}: " +
                    "${state.artist} - ${state.title} [${state.album}] (${sbn.packageName})")
            currentMusicState = state
            onMusicStateChanged?.invoke(state)

            // Send or delete Rich Presence based on playback state
            val credentials = CredentialsStore.getCredentials(applicationContext)
            if (credentials != null) {
                if (state.isPlaying) {
                    sendRpc(credentials, state)
                } else {
                    deleteRpc(credentials)
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        if (!isMediaNotification(sbn)) return
        if (!isAllowedApp(sbn.packageName)) return

        // Only clear if the removed notification belongs to the currently tracked app
        if (currentMusicState?.packageName == sbn.packageName) {
            Log.d(TAG, "Media notification removed from ${sbn.packageName}, clearing state")
            currentMusicState = null
            onMusicStateChanged?.invoke(null)

            val credentials = CredentialsStore.getCredentials(applicationContext)
            if (credentials != null) {
                deleteRpc(credentials)
            }
        }
    }

    /**
     * Extract music state from a media notification using its MediaSession token.
     * Falls back to notification extras if the MediaController doesn't provide metadata.
     */
    private fun extractMusicState(sbn: StatusBarNotification): MusicState? {
        val extras = sbn.notification.extras
        val token = extras.getParcelable<MediaSession.Token>("android.mediaSession")

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var isPlaying = false
        var coverArtBitmap: Bitmap? = null

        if (token != null) {
            try {
                val controller = MediaController(applicationContext, token)

                // Playback state
                val playbackState = controller.playbackState
                isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

                // Metadata
                val metadata = controller.metadata
                if (metadata != null) {
                    title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
                    // Try to get album art bitmap
                    coverArtBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                        ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read MediaController", e)
            }
        }

        // Fallback to notification extras if metadata was unavailable
        if (title.isNullOrBlank()) {
            title = extras.getCharSequence("android.title")?.toString()
        }
        if (artist.isNullOrBlank()) {
            artist = extras.getCharSequence("android.text")?.toString()
        }

        // Fallback: try notification large icon
        if (coverArtBitmap == null) {
            try {
                val largeIcon = sbn.notification.getLargeIcon()
                if (largeIcon != null) {
                    val drawable = largeIcon.loadDrawable(applicationContext)
                    if (drawable != null) {
                        val w = drawable.intrinsicWidth.coerceAtLeast(1)
                        val h = drawable.intrinsicHeight.coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        drawable.setBounds(0, 0, w, h)
                        drawable.draw(canvas)
                        coverArtBitmap = bmp
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to extract large icon as cover art", e)
            }
        }

        if (title.isNullOrBlank()) return null

        return MusicState(
            packageName = sbn.packageName,
            title = title,
            artist = artist ?: "",
            album = album ?: "",
            isPlaying = isPlaying,
            coverArtBitmap = coverArtBitmap
        )
    }

    /**
     * Returns true if the notification uses MediaStyle (i.e. is an audio player notification).
     */
    private fun isMediaNotification(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras
        val token = extras.getParcelable<MediaSession.Token>("android.mediaSession")
        if (token != null) return true

        val category = sbn.notification.category
        return category == "transport"
    }

    /**
     * Returns true if the notification's package is in the user's allowed-apps list.
     */
    private fun isAllowedApp(packageName: String): Boolean {
        val allowed = SettingsStore.getAllowedApps(applicationContext)
        if (allowed.isEmpty()) return false
        return allowed.contains(packageName)
    }

    // Helper to send RPC (with album cover upload)
    private fun sendRpc(credentials: Credentials, state: MusicState) {
        GlobalScope.launch(Dispatchers.IO) {
            // Upload cover art if available and broadcast is enabled
            var coverArtMxc: String? = null
            val shouldBroadcastCover = SettingsStore.isBroadcastAlbumCover(applicationContext)
            if (shouldBroadcastCover && state.coverArtBitmap != null) {
                try {
                    val tempFile = File(applicationContext.cacheDir, "cover_art.png")
                    FileOutputStream(tempFile).use { out ->
                        state.coverArtBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    val uploadResult = UploadHelper.upload(
                        context = applicationContext,
                        credentials = credentials,
                        file = tempFile,
                        mimeType = "image/png",
                        fileName = "cover.png"
                    )
                    coverArtMxc = uploadResult.getOrNull()
                    if (uploadResult.isFailure) {
                        Log.w(TAG, "Failed to upload cover art", uploadResult.exceptionOrNull())
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to save/upload cover art", e)
                }
            }

            // Determine player name if broadcasting is enabled
            val playerName = if (SettingsStore.isBroadcastAppName(applicationContext)) {
                try {
                    val pm = applicationContext.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(state.packageName, 0)).toString()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get app label for ${state.packageName}", e)
                    null
                }
            } else {
                null
            }
            val rpcMedia = MatrixApi.RpcMedia(
                artist = state.artist,
                album = state.album,
                track = state.title,
                coverArt = coverArtMxc,
                player = playerName
            )
            val result = MatrixApi.setRpcMedia(
                homeserverUrl = credentials.homeserverUrl,
                accessToken = credentials.accessToken,
                userId = credentials.userId,
                media = rpcMedia
            )
            if (result.isSuccess) {
                Log.d(TAG, "Rich Presence RPC sent successfully")
            } else {
                Log.w(TAG, "Failed to send Rich Presence RPC", result.exceptionOrNull())
            }
        }
    }

    // Helper to delete RPC
    private fun deleteRpc(credentials: Credentials) {
        GlobalScope.launch(Dispatchers.IO) {
            val delResult = MatrixApi.deleteRichPresence(
                homeserverUrl = credentials.homeserverUrl,
                accessToken = credentials.accessToken,
                userId = credentials.userId
            )
            if (delResult.isSuccess) {
                Log.d(TAG, "Rich Presence deleted successfully")
            } else {
                Log.w(TAG, "Failed to delete Rich Presence", delResult.exceptionOrNull())
            }
        }
    }
}

