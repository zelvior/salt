package com.zelvior.salt.core.overlay

import kotlinx.coroutines.flow.StateFlow

/**
 * Contract between the overlay UI and whatever drives it (the [OverlayService] in Phase 1).
 * Exposes the open/closed state as a [StateFlow] so Compose can animate reactively.
 */
interface OverlayInteractor {
    val isOpen: StateFlow<Boolean>
    fun open()
    fun close()
    fun toggle()
}
