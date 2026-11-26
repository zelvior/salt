package com.zelvior.salt.core.di

import android.app.Application
import com.zelvior.salt.core.system.ShizukuBridge
import com.zelvior.salt.core.system.SystemControllerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Minimal manual dependency container — intentionally NOT a heavy DI framework, to keep
 * Salt's footprint tiny. Construction is centralized here.
 *
 * Phase 2 modules will be wired as lazily-initialised singletons, e.g.:
 *   val systemController  by lazy { SystemController(app) }
 *   val notificationSan   by lazy { NotificationSanitizer() }
 *   val aiClient          by lazy { OpenAiCompatibleClient(...) }
 *   val pluginRegistry    by lazy { PluginRegistry(...) }
 */
class AppContainer(app: Application) {
    /** App-wide scope for background work (cancelled automatically on process death). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Optional root-free bridge to restricted system states (Wi-Fi / data / airplane). */
    val shizukuBridge by lazy { ShizukuBridge(app) }

    /** Unified hardware controller backing the control-center toggles & sliders. */
    val systemController by lazy { SystemControllerImpl(app, shizukuBridge) }
}
