package com.zelvior.salt.core.system

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import android.app.NotificationManager as AndroidNotificationManager

/**
 * Concrete [SystemController].
 *
 * Standard Android APIs handle brightness, media volume, DND, flashlight and Bluetooth.
 * Wi-Fi / mobile data / airplane mode are restricted, so they are toggled through the
 * [ShizukuBridge] (which runs `svc` / `settings` shell commands with Shizuku privileges).
 * State is observed via ContentObservers and broadcast receivers and exposed as [StateFlow]s.
 */
class SystemControllerImpl(
    context: Context,
    private val bridge: ShizukuBridge
) : SystemController {

    private val appContext = context.applicationContext
    private val cr = appContext.contentResolver
    private val audioManager = appContext.getSystemService(AudioManager::class.java)!!
    private val cameraManager = appContext.getSystemService(CameraManager::class.java)!!
    private val notificationManager = appContext.getSystemService(AndroidNotificationManager::class.java)!!
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)!!
    private val bluetoothAdapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: BluetoothAdapter.getDefaultAdapter()

    private val _states = MutableStateFlow<Set<ToggleKey>>(emptySet())
    override val states: StateFlow<Set<ToggleKey>> = _states.asStateFlow()

    private val _brightness = MutableStateFlow(0)
    override val brightness: StateFlow<Int> = _brightness.asStateFlow()

    private val _volume = MutableStateFlow(0)
    override val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _shizukuReady = MutableStateFlow(false)
    override val shizukuReady: StateFlow<Boolean> = _shizukuReady.asStateFlow()

    private val _shizukuInstalled = MutableStateFlow(false)
    override val shizukuInstalled: StateFlow<Boolean> = _shizukuInstalled.asStateFlow()

    private var flashlightOn = false
    private var torchCameraId: String? = null
    private var started = false

    private val brightnessObserver = object : android.database.ContentObserver(null) {
        override fun onChange(selfChange: Boolean) = refreshBrightness()
    }
    private val dndReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = refreshDnd()
    }
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = refreshVolume()
    }
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val state = i?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) ?: return
            updateState(ToggleKey.BLUETOOTH, state == BluetoothAdapter.STATE_ON)
        }
    }
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                flashlightOn = enabled
                updateState(ToggleKey.FLASHLIGHT, enabled)
            }
        }
    }

    init {
        runCatching {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                    torchCameraId = id
                    break
                }
            }
        }
    }

    override fun start() {
        if (started) return
        started = true
        runCatching {
            cr.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, brightnessObserver
            )
        }
        runCatching {
            appContext.registerReceiver(
                dndReceiver, IntentFilter(AndroidNotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            )
        }
        runCatching {
            appContext.registerReceiver(volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        }
        runCatching {
            appContext.registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        }
        runCatching { cameraManager.registerTorchCallback(appContext.mainExecutor, torchCallback) }
        refreshAll()
    }

    override fun stop() {
        if (!started) return
        started = false
        runCatching { cr.unregisterContentObserver(brightnessObserver) }
        runCatching { appContext.unregisterReceiver(dndReceiver) }
        runCatching { appContext.unregisterReceiver(volumeReceiver) }
        runCatching { appContext.unregisterReceiver(bluetoothReceiver) }
        runCatching { cameraManager.unregisterTorchCallback(torchCallback) }
    }

    override suspend fun connectShizuku(): Boolean {
        if (!bridge.isInstalled()) {
            openShizukuStore()
            return false
        }
        val ok = bridge.ensurePermission()
        _shizukuReady.value = ok
        if (ok) refreshShizuku()
        return ok
    }

    override suspend fun toggle(key: ToggleKey) {
        when (key) {
            ToggleKey.FLASHLIGHT -> toggleFlashlight()
            ToggleKey.DO_NOT_DISTURB -> toggleDnd()
            ToggleKey.BLUETOOTH -> toggleBluetooth()
            ToggleKey.WIFI -> toggleViaShizuku(
                ToggleKey.WIFI, "svc wifi enable", "svc wifi disable"
            ) { wifiManager.isWifiEnabled }
            ToggleKey.MOBILE_DATA -> toggleViaShizuku(
                ToggleKey.MOBILE_DATA, "svc data enable", "svc data disable"
            ) { isMobileDataOn() }
            ToggleKey.AIRPLANE_MODE -> toggleAirplane()
        }
    }

    override suspend fun setBrightness(percent: Int) {
        val p = percent.coerceIn(0, 100)
        val value = (p * 255 / 100).coerceIn(0, 255)
        runCatching {
            // Disable automatic brightness so the manual value is honored.
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, value)
        }
        _brightness.value = p
    }

    override suspend fun setVolume(percent: Int) {
        val p = percent.coerceIn(0, 100)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val vol = if (max > 0) (p * max / 100f).roundToInt().coerceIn(0, max) else 0
        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0) }
        _volume.value = p
    }

    // ---------------------------------------------------------------- internals

    private fun updateState(key: ToggleKey, enabled: Boolean) {
        val set = _states.value.toMutableSet()
        if (enabled) set.add(key) else set.remove(key)
        _states.value = set
    }

    private fun refreshAll() {
        refreshBrightness()
        refreshVolume()
        refreshDnd()
        updateState(ToggleKey.FLASHLIGHT, flashlightOn)
        updateState(ToggleKey.WIFI, runCatching { wifiManager.isWifiEnabled }.getOrDefault(false))
        updateState(ToggleKey.MOBILE_DATA, isMobileDataOn())
        updateState(
            ToggleKey.AIRPLANE_MODE,
            runCatching { Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 }.getOrDefault(false)
        )
        updateState(ToggleKey.BLUETOOTH, bluetoothAdapter?.isEnabled == true)
        refreshShizuku()
    }

    private fun refreshBrightness() {
        val b = runCatching { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 0) }.getOrDefault(0)
        _brightness.value = (b * 100 / 255).coerceIn(0, 100)
    }

    private fun refreshVolume() {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val v = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        _volume.value = if (max > 0) (v * 100 / max).coerceIn(0, 100) else 0
    }

    private fun refreshDnd() {
        val filter = notificationManager.currentInterruptionFilter
        updateState(ToggleKey.DO_NOT_DISTURB, filter == AndroidNotificationManager.INTERRUPTION_FILTER_NONE)
    }

    private fun refreshShizuku() {
        _shizukuInstalled.value = bridge.isInstalled()
        _shizukuReady.value = bridge.isReady()
        if (bridge.isReady()) {
            updateState(ToggleKey.WIFI, runCatching { wifiManager.isWifiEnabled }.getOrDefault(false))
            updateState(ToggleKey.MOBILE_DATA, isMobileDataOn())
            updateState(
                ToggleKey.AIRPLANE_MODE,
                runCatching { Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 }.getOrDefault(false)
            )
        }
    }

    private fun isMobileDataOn(): Boolean {
        val info = runCatching { connectivityManager.activeNetworkInfo }.getOrNull()
        return info != null && info.type == ConnectivityManager.TYPE_MOBILE && info.isConnected
    }

    private suspend fun toggleViaShizuku(
        key: ToggleKey,
        onCmd: String,
        offCmd: String,
        isOn: () -> Boolean
    ) {
        if (!bridge.ensurePermission()) return
        val cmd = if (isOn()) offCmd else onCmd
        bridge.runShell(cmd)
            .onSuccess { updateState(key, !isOn()) }
            .onFailure { Log.w("Salt", "Shizuku toggle failed for $key", it) }
    }

    private suspend fun toggleAirplane() {
        if (!bridge.ensurePermission()) return
        val on = runCatching { Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 }.getOrDefault(false)
        val next = if (on) 0 else 1
        bridge.runShell("settings put global airplane_mode_on $next")
            .onSuccess {
                bridge.runShell("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state ${next == 1}")
                updateState(ToggleKey.AIRPLANE_MODE, next == 1)
            }
            .onFailure { Log.w("Salt", "Airplane toggle failed", it) }
    }

    private fun toggleFlashlight() {
        val id = torchCameraId ?: return
        val next = !flashlightOn
        runCatching { cameraManager.setTorchMode(id, next) }
            .onSuccess { flashlightOn = next; updateState(ToggleKey.FLASHLIGHT, next) }
            .onFailure { Log.w("Salt", "Flashlight toggle failed", it) }
    }

    private fun toggleDnd() {
        val current = notificationManager.currentInterruptionFilter
        val next = if (current == AndroidNotificationManager.INTERRUPTION_FILTER_NONE)
            AndroidNotificationManager.INTERRUPTION_FILTER_ALL
        else
            AndroidNotificationManager.INTERRUPTION_FILTER_NONE
        runCatching { notificationManager.setInterruptionFilter(next) }
            .onSuccess {
                updateState(ToggleKey.DO_NOT_DISTURB, next == AndroidNotificationManager.INTERRUPTION_FILTER_NONE)
            }
            .onFailure { Log.w("Salt", "DND toggle failed (need ACCESS_NOTIFICATION_POLICY)", it) }
    }

    private suspend fun toggleBluetooth() {
        val adapter = bluetoothAdapter ?: return
        val on = adapter.isEnabled
        // Android 13+ silently blocks BluetoothAdapter.enable()/disable() for normal apps,
        // so prefer the Shizuku shell path when the bridge is available.
        if (bridge.isReady() && bridge.ensurePermission()) {
            bridge.runShell(if (on) "svc bluetooth disable" else "svc bluetooth enable")
                .onSuccess { updateState(ToggleKey.BLUETOOTH, !on) }
                .onFailure { Log.w("Salt", "Shizuku bluetooth toggle failed", it) }
            return
        }
        // Fallback for older devices (may be a no-op on API 33+).
        runCatching { if (on) adapter.disable() else adapter.enable() }
            .onFailure { Log.w("Salt", "Bluetooth toggle failed (need BLUETOOTH_CONNECT)", it) }
    }

    private fun openShizukuStore() {
        runCatching {
            appContext.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api"))
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        }.onFailure {
            runCatching {
                appContext.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku"))
                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
            }
        }
    }
}
