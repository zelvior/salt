package com.zelvior.salt.core.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.zelvior.salt.core.notifications.SaltNotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of the currently-active media session, exposed to the shade UI.
 * [albumArt] is pre-scaled to a small display size to keep memory flat.
 */
data class MediaState(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val albumArt: Bitmap?,
    val isPlaying: Boolean
)

/**
 * Observes the device's active media sessions (Spotify, YouTube, ...) via the platform
 * [MediaSessionManager] and exposes a single "current session" as a [StateFlow].
 *
 * Permission model: a normal app cannot hold MEDIA_CONTENT_CONTROL, but an app with an
 * *enabled NotificationListenerService* may call getActiveSessions with its own listener
 * component — which is exactly what Salt ships ([com.zelvior.salt.core.notifications.SaltNotificationListener]).
 * If listener access is revoked, calls throw SecurityException and the flow stays null
 * (the UI hides the media card gracefully).
 *
 * Passive by design: zero polling — only framework callbacks. [start]/[stop] mirror the
 * overlay lifecycle so nothing is registered while the shade service is down.
 */
class MediaControllerManager(private val context: Context) {

    private val mediaSessionManager =
        context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, SaltNotificationListener::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _media = MutableStateFlow<MediaState?>(null)

    /** Currently playing/paused session, or null when nothing is active. */
    val media: StateFlow<MediaState?> = _media.asStateFlow()

    private var activeController: MediaController? = null
    private var registered = false

    /** Per-session callback: metadata / playback state changed for the bound controller. */
    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
    }

    /** Session set changed — rebind to the most relevant (playing) controller. */
    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers -> bind(controllers) }

    /**
     * Idempotent; safe to call repeatedly. If notification-listener access is not granted
     * yet, registration fails and [registered] stays false — the next [start] retries,
     * so the user can grant access in settings without restarting the overlay service.
     */
    fun start() {
        val msm = mediaSessionManager ?: return
        if (registered) return
        runCatching {
            msm.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
            bind(msm.getActiveSessions(listenerComponent))
            registered = true
        }.onFailure {
            Log.w(TAG, "Media sessions unavailable — notification listener not enabled?", it)
            _media.value = null
        }
    }

    fun stop() {
        runCatching { mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        unbind()
        _media.value = null
    }

    // ------------------------------------------------------------- transport

    fun playPause() {
        val controller = activeController ?: return
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        controller.transportControls.let { if (playing) it.pause() else it.play() }
    }

    fun skipToNext() {
        activeController?.transportControls?.skipToNext()
    }

    fun skipToPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }

    // ------------------------------------------------------------- internals

    private fun bind(controllers: List<MediaController>?) {
        unbind()
        val pick = controllers
            ?.let { list ->
                list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                    ?: list.firstOrNull()
            }
            ?: return
        activeController = pick.also { it.registerCallback(controllerCallback, mainHandler) }
        refresh()
    }

    private fun unbind() {
        activeController?.let { runCatching { it.unregisterCallback(controllerCallback) } }
        activeController = null
    }

    private fun refresh() {
        val controller = activeController
        if (controller == null) {
            _media.value = null
            return
        }
        val metadata = controller.metadata
        _media.value = MediaState(
            packageName = controller.packageName,
            appLabel = appLabel(controller.packageName),
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            albumArt = metadata?.let { scaleDown(it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)) },
            isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        )
    }

    private fun appLabel(packageName: String): String = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0).loadLabel(context.packageManager)
            .toString()
    }.getOrDefault(packageName)

    /** Caps album art at [MAX_ART_PX] to keep the StateFlow cheap to recompose. */
    private fun scaleDown(art: Bitmap?): Bitmap? {
        if (art == null) return null
        val largest = maxOf(art.width, art.height)
        if (largest <= MAX_ART_PX) return art
        val scale = MAX_ART_PX.toFloat() / largest
        return Bitmap.createScaledBitmap(
            art,
            (art.width * scale).toInt().coerceAtLeast(1),
            (art.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    companion object {
        private const val TAG = "SaltMedia"
        private const val MAX_ART_PX = 512
    }
}
