package com.zelvior.salt.core.system

import kotlinx.coroutines.flow.StateFlow

/** Binary switches exposed by the control center. */
enum class ToggleKey {
    WIFI, BLUETOOTH, MOBILE_DATA, AIRPLANE_MODE, DO_NOT_DISTURB, FLASHLIGHT
}

/**
 * Unified hardware-control surface for the shade.
 *
 * Brightness / media volume are continuous (0..100). The binary toggles are reflected in
 * [states]. Wi-Fi / mobile data / airplane mode require the optional Shizuku bridge
 * (standard SDK cannot toggle them without root); [shizukuReady] gates those.
 */
interface SystemController {
    /** Currently-enabled binary toggles. */
    val states: StateFlow<Set<ToggleKey>>

    /** Screen brightness, 0..100 (percent). */
    val brightness: StateFlow<Int>

    /** Media volume, 0..100 (percent). */
    val volume: StateFlow<Int>

    /** Shizuku bridge bound + authorized (needed for Wi-Fi / data / airplane). */
    val shizukuReady: StateFlow<Boolean>

    /** Shizuku manager app installed on device. */
    val shizukuInstalled: StateFlow<Boolean>

    /** Toggle a binary switch (suspends while the action completes). */
    suspend fun toggle(key: ToggleKey)

    /** Set screen brightness (0..100). Requires WRITE_SETTINGS. */
    suspend fun setBrightness(percent: Int)

    /** Set media volume (0..100). */
    suspend fun setVolume(percent: Int)

    /** Authorize Shizuku (or open its store page if missing); returns true once ready. */
    suspend fun connectShizuku(): Boolean

    /** Begin observing system state. Call when the overlay becomes visible. */
    fun start()

    /** Stop observing and release listeners. */
    fun stop()
}
