package com.zelvior.salt.core.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Phase 2 (Smart Notification Shade) — stub.
 *
 * Will intercept active notifications, group them, extract text/actions, and run each
 * through [NotificationSanitizer] BEFORE anything reaches the AI layer.
 */
class SaltNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) { /* Phase 2 */ }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { /* Phase 2 */ }
}
