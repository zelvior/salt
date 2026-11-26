package com.zelvior.salt.core.di

import android.app.Application
import com.zelvior.salt.core.media.MediaControllerManager
import com.zelvior.salt.core.notifications.NotificationRepository
import com.zelvior.salt.core.notifications.NotificationSanitizer
import com.zelvior.salt.core.system.ShizukuBridge
import com.zelvior.salt.core.system.SystemControllerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Minimal manual dependency container — intentionally NOT a heavy DI framework, to keep
 * Salt's footprint tiny. Construction is centralized here; everything is lazily created
 * so an unused feature costs nothing.
 */
class AppContainer(app: Application) {
    /** App-wide scope for background work (cancelled automatically on process death). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Optional root-free bridge to restricted system states (Wi-Fi / data / airplane). */
    val shizukuBridge by lazy { ShizukuBridge(app) }

    /** Unified hardware controller backing the control-center toggles & sliders. */
    val systemController by lazy { SystemControllerImpl(app, shizukuBridge) }

    /** Active media sessions for the shade's media card (requires listener access). */
    val mediaControllerManager by lazy { MediaControllerManager(app) }

    /** Sanitized notification feed + dismiss actions (privacy gate lives here). */
    val notificationRepository by lazy { NotificationRepository(NotificationSanitizer()) }
}
