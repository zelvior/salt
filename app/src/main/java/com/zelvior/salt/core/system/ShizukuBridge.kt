package com.zelvior.salt.core.system

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

/**
 * Thin, lifecycle-aware wrapper around the Shizuku API.
 *
 * Responsibilities:
 *  - report binder availability ([isBound]) and whether the manager app is installed,
 *  - request/await the Shizuku permission via [ensurePermission],
 *  - run adb-shell-equivalent commands with Shizuku's privileges ([runShell]),
 *    used for Wi-Fi / mobile data / airplane mode which the normal SDK cannot toggle.
 *
 * Shell execution goes through the public [IShizukuService] AIDL interface — the higher-level
 * `Shizuku.newProcess` helper is package-private/deprecated. Call [destroy] when the owning
 * component is gone.
 */
class ShizukuBridge(private val context: Context) {

    private var pending: CompletableDeferred<Boolean>? = null

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == REQUEST_CODE) {
            pending?.complete(result == PackageManager.PERMISSION_GRANTED)
            pending = null
        }
    }

    init {
        runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }
    }

    fun isInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
    }.isSuccess

    fun isBound(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean =
        runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
            .getOrDefault(false)

    fun isReady(): Boolean = isBound() && hasPermission()

    /** Requests Shizuku permission if needed; resolves true once the bridge is usable. */
    suspend fun ensurePermission(): Boolean {
        if (!isBound()) return false
        if (hasPermission()) return true
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
        return runCatching { deferred.await() }.getOrDefault(false)
    }

    /** Run a shell command with Shizuku privileges. Returns stdout or the failure. */
    suspend fun runShell(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val binder = Shizuku.getBinder()
            checkNotNull(binder) { "Shizuku binder unavailable" }
            val service = checkNotNull(IShizukuService.Stub.asInterface(binder)) {
                "Shizuku service unavailable"
            }
            val remote = service.newProcess(arrayOf("sh", "-c", command), null, null)
            try {
                coroutineScope {
                    // Drain stderr concurrently so a chatty command can't fill its pipe
                    // buffer and deadlock the stdout read below.
                    val err = async {
                        ParcelFileDescriptor.AutoCloseInputStream(remote.errorStream)
                            .bufferedReader().use { it.readText() }
                    }
                    val out = ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream)
                        .bufferedReader().use { it.readText() }
                    val code = remote.waitFor()
                    val errText = err.await()
                    if (code != 0) error("sh exited $code: $errText")
                    out
                }
            } finally {
                runCatching { remote.destroy() }
            }
        }
    }

    fun destroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
    }

    companion object {
        private const val REQUEST_CODE = 7391
    }
}
