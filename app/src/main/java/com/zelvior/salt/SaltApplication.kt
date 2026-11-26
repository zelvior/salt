package com.zelvior.salt

import android.app.Application
import com.zelvior.salt.core.di.AppContainer

/**
 * Application entry point. Owns the manual DI container so singletons (overlay controller,
 * and — in later phases — system controller, AI client, plugin registry) live for the
 * process lifetime.
 */
class SaltApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
