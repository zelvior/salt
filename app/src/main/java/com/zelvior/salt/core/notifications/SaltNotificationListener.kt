package com.zelvior.salt.core.notifications

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Salt's notification listener — feeds the shade's notification feed and (later) the AI
 * digest. The service itself performs NO state holding; everything is pushed into
 * [NotificationRepository], which enforces the privacy contract (sanitizer before state).
 *
 * Filtering policy (noise reduction, per spec):
 *  - own notifications, ongoing (media / persistent) and group summaries are skipped —
 *    media sessions are surfaced by [com.zelvior.salt.core.media.MediaControllerManager] instead;
 *  - channels below IMPORTANCE_DEFAULT (silent / low) are skipped;
 *  - notifications without any title/text are skipped.
 */
class SaltNotificationListener : NotificationListenerService() {

    private val repository: NotificationRepository
        get() = (applicationContext as com.zelvior.salt.SaltApplication)
            .container.notificationRepository

    override fun onListenerConnected() {
        super.onListenerConnected()
        repository.attach(this)
        // Seed the feed with what is already on the bar.
        repository.replaceAll(activeNotifications.orEmpty().mapNotNull { it.toCandidate() })
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val candidate = sbn?.toCandidate() ?: return
        repository.upsert(candidate)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { repository.remove(it.key) }
    }

    override fun onListenerDisconnected() {
        repository.detach()
        super.onListenerDisconnected()
    }

    /**
     * Noise filter + raw extraction. Returns null for anything that should never reach
     * the feed. Text is passed through RAW; sanitization happens inside the repository.
     */
    private fun StatusBarNotification.toCandidate(): RawNotification? {
        if (packageName == this@SaltNotificationListener.packageName) return null
        if (isOngoing) return null // includes media + persistent system notifications
        val notif = notification ?: return null
        if (notif.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (isLowImportance(notif)) return null

        val extras = notif.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras
            ?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?.takeUnless { it.isBlank() }
            ?: extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null

        return RawNotification(
            key = key,
            packageName = packageName,
            appLabel = appLabel(packageName),
            title = title,
            text = text,
            timestamp = postTime
        )
    }

    /** Silent / low-importance channels (e.g. download finished) are noise. */
    private fun isLowImportance(notif: Notification): Boolean = runCatching {
        val channelId = notif.channelId
        if (channelId == null) return false
        val channel = getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(channelId)
        channel != null && channel.importance < NotificationManager.IMPORTANCE_DEFAULT
    }.getOrDefault(false)

    private fun appLabel(packageName: String): String = runCatching {
        packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
    }.getOrDefault(packageName)
}
