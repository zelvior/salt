package com.zelvior.salt.core.system

/**
 * Phase 2 (System Toggles & Shizuku) — interface scaffold.
 *
 * Implementations will wrap standard Android APIs (brightness, media volume, DND,
 * flashlight) and, optionally, the Shizuku bridge for restricted states
 * (Wi-Fi, mobile data, Bluetooth, airplane mode) without root.
 */
interface SystemController {
    // data class ToggleState(val key: String, val enabled: Boolean, val available: Boolean)

    // suspend fun setBrightness(percent: Int)
    // suspend fun setMediaVolume(percent: Int)
    // suspend fun setDoNotDisturb(enabled: Boolean)
    // suspend fun setFlashlight(enabled: Boolean)

    // -- Shizuku-backed (optional, root-free) --
    // suspend fun toggleWifi(enabled: Boolean)
    // suspend fun toggleBluetooth(enabled: Boolean)
    // suspend fun toggleMobileData(enabled: Boolean)
    // suspend fun toggleAirplaneMode(enabled: Boolean)
}
