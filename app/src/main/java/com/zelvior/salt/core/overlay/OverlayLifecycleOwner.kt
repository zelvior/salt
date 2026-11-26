package com.zelvior.salt.core.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Bridges a [android.view.WindowManager] overlay (which lives outside any Android
 * component window) to the AndroidX lifecycle / ViewModel / SavedState system, so Compose
 * can use view-model-scoped state, SavedStateHandle and lifecycle-aware collectors inside
 * the overlay — exactly as it would inside an Activity.
 *
 * Mirrors the owner wiring that ComponentActivity performs for a normal window, but driven
 * manually by the overlay service.
 */
class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        // Restore any saved state (none for the overlay yet) before leaving INITIALIZED.
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onPause() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
