package com.zelvior.salt.core.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Centralised permission checks + the intents that open the matching system settings
 * screen. Used by both the launcher and the onboarding flow so the logic lives in one place.
 */
object Permissions {

    fun hasOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun hasPostNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun hasNotificationListener(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    /** Runtime BLUETOOTH_CONNECT (API 31+); always true below that. */
    fun hasBluetoothConnect(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /** "Do Not Disturb access" — required by NotificationManager.setInterruptionFilter. */
    fun hasDndAccess(context: Context): Boolean =
        (context.getSystemService(android.app.NotificationManager::class.java))
            ?.isNotificationPolicyAccessGranted ?: false

    /** True if the Shizuku manager app is installed (the bridge is activated from there). */
    fun hasShizuku(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0) }
            .isSuccess

    fun overlayIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun writeSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun notificationListenerIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun dndAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) }

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
