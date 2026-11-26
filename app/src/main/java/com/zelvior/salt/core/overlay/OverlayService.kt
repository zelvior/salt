package com.zelvior.salt.core.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.zelvior.salt.MainActivity
import com.zelvior.salt.R
import com.zelvior.salt.SaltApplication
import com.zelvior.salt.core.system.SystemController
import com.zelvior.salt.ui.shade.ControlCenterScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service that shows the Salt control-center overlay above other apps.
 *
 * Lifecycle wiring for the Compose overlay:
 *   1. A dedicated [OverlayLifecycleOwner] is created and driven CREATED -> RESUMED.
 *   2. [OverlayWindowHost] attaches a ComposeView to the WindowManager and binds the owners.
 *   3. On destroy the window is removed and the owner is torn down, releasing ViewModels.
 */
class OverlayService : Service(), OverlayInteractor {

    private val _isOpen = MutableStateFlow(false)
    override val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    companion object {
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.zelvior.salt.action.STOP_OVERLAY"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var host: OverlayWindowHost? = null
    private var controller: SystemController? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        ensureOverlay()
        // START_STICKY keeps the shade alive across transient process deaths.
        return START_STICKY
    }

    private fun ensureOverlay() {
        if (host != null) return
        val owner = OverlayLifecycleOwner().also { it.onCreate() }
        lifecycleOwner = owner
        controller = (applicationContext as SaltApplication).container.systemController
        host = OverlayWindowHost(
            context = this,
            lifecycleOwner = owner,
            interactor = this
        ) {
            ControlCenterScreen(
                interactor = this,
                controller = controller!!,
                onClose = ::close
            )
        }.also {
            it.show()
            owner.onResume()
            controller?.start()
        }
    }

    override fun open() { _isOpen.value = true }
    override fun close() { _isOpen.value = false }
    override fun toggle() { _isOpen.value = !_isOpen.value }

    override fun onDestroy() {
        controller?.stop()
        controller = null
        host?.hide()
        host = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = "salt_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(channelId) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        getString(R.string.overlay_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = getString(R.string.overlay_channel_description)
                    }
                )
            }
        }

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_notify)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .setOngoing(true)
            .build()
    }
}
