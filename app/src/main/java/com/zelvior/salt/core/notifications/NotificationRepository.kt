package com.zelvior.salt.core.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Raw, *unsanitized* carrier produced by the listener — never exposed to the UI. */
data class RawNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val timestamp: Long
)

/**
 * Sanitized notification ready for the shade UI (and, later, the AI layer).
 * Construction only happens inside [NotificationRepository.clean] — after
 * [NotificationSanitizer] has redacted OTPs / emails / phone numbers.
 */
data class CleanNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val timestamp: Long
)

/**
 * Single source of truth for the shade's notification feed.
 *
 * PRIVACY CONTRACT: every raw notification passes through [NotificationSanitizer]
 * *before* it can enter the exposed [StateFlow] — [clean] is the only constructor
 * path for [CleanNotification]. Blacklisted packages never enter state at all.
 *
 * The feed is capped at [MAX_NOTIFICATIONS] (newest first) to keep idle RAM flat.
 * Dismissal actions delegate to the attached [SaltNotificationListener]; when the
 * listener is disconnected they become no-ops instead of crashing the UI.
 */
class NotificationRepository(private val sanitizer: NotificationSanitizer) {

    private val _notifications = MutableStateFlow<List<CleanNotification>>(emptyList())

    /** Sanitized feed, newest first. */
    val notifications: StateFlow<List<CleanNotification>> = _notifications.asStateFlow()

    @Volatile
    private var listener: SaltNotificationListener? = null

    internal fun attach(service: SaltNotificationListener) {
        listener = service
    }

    internal fun detach() {
        listener = null
    }

    /** Initial snapshot from onListenerConnected — replaces the whole feed. */
    internal fun replaceAll(raws: List<RawNotification>) {
        _notifications.value = raws.mapNotNull(::clean).take(MAX_NOTIFICATIONS)
    }

    /** Insert-or-move-to-top for a freshly posted notification. */
    internal fun upsert(raw: RawNotification) {
        val cleaned = clean(raw) ?: return
        val current = _notifications.value.toMutableList()
        current.removeAll { it.key == cleaned.key }
        current.add(0, cleaned)
        _notifications.value = current.take(MAX_NOTIFICATIONS)
    }

    /** Notification was removed (by us, the app, or the system). */
    internal fun remove(key: String) {
        _notifications.value = _notifications.value.filterNot { it.key == key }
    }

    /** Dismiss one notification via the listener service. */
    fun dismissNotification(key: String) {
        runCatching { listener?.cancelNotification(key) }
    }

    /** Dismiss every notification currently in the feed. */
    fun clearAllNotifications() {
        val keys = _notifications.value.map { it.key }
        keys.forEach { key -> runCatching { listener?.cancelNotification(key) } }
        // onNotificationRemoved callbacks will drain the list; nothing to force here.
    }

    /**
     * THE privacy gate. Returns null for blacklisted packages; otherwise returns a
     * [CleanNotification] whose title/text have been redacted by the sanitizer.
     */
    private fun clean(raw: RawNotification): CleanNotification? {
        if (sanitizer.isBlacklisted(raw.packageName)) return null
        return CleanNotification(
            key = raw.key,
            packageName = raw.packageName,
            appLabel = raw.appLabel,
            title = sanitizer.sanitize(raw.title).trim(),
            text = sanitizer.sanitize(raw.text).trim(),
            timestamp = raw.timestamp
        ).takeIf { it.title.isNotBlank() || it.text.isNotBlank() }
    }

    companion object {
        const val MAX_NOTIFICATIONS = 20
    }
}
