package com.onesoul.app

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class NowPlayingInfo(
    val title: String,
    val artist: String,
    val positionSec: Int,
    val durationSec: Int,
    val playing: Boolean,
)

/** Shared, observable "what is playing on this phone" – fed by [MediaListenerService]. */
object NowPlaying {
    var info by mutableStateOf<NowPlayingInfo?>(null)

    fun hasAccess(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.contains(context.packageName) == true
}

class MediaListenerService : NotificationListenerService() {

    private var manager: MediaSessionManager? = null
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { track(it.orEmpty()) }

    override fun onListenerConnected() {
        val m = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        manager = m
        val me = ComponentName(this, MediaListenerService::class.java)
        m.addOnActiveSessionsChangedListener(sessionsListener, me)
        track(m.getActiveSessions(me))
    }

    override fun onListenerDisconnected() {
        manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        clear()
        NowPlaying.info = null
    }

    private fun clear() {
        callbacks.forEach { (c, cb) -> c.unregisterCallback(cb) }
        callbacks.clear()
    }

    private fun track(controllers: List<MediaController>) {
        clear()
        controllers.forEach { c ->
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = publish(controllers)
                override fun onPlaybackStateChanged(state: PlaybackState?) = publish(controllers)
            }
            c.registerCallback(cb)
            callbacks[c] = cb
        }
        publish(controllers)
    }

    private fun publish(controllers: List<MediaController>) {
        // Prefer a session that is actually playing, otherwise the most recent one.
        val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull() ?: return
        val md = c.metadata ?: return
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
        NowPlaying.info = NowPlayingInfo(
            title = title,
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            positionSec = ((c.playbackState?.position ?: 0L) / 1000).toInt(),
            durationSec = (md.getLong(MediaMetadata.METADATA_KEY_DURATION) / 1000).toInt(),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
        )
    }
}
