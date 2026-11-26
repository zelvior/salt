package com.zelvior.salt.ui.onboarding

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zelvior.salt.core.overlay.OverlayService
import com.zelvior.salt.core.permissions.Permissions
import com.zelvior.salt.ui.components.PermissionRow
import com.zelvior.salt.ui.theme.SaltTheme

/**
 * Permission setup checklist: overlay, notification access, write-settings (+ Shizuku later).
 * On finish it starts the overlay service and closes itself.
 */
class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SaltTheme { OnboardingScreen(onFinish = { finish() }) }
        }
    }
}

@Composable
private fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val btLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* BLUETOOTH_CONNECT; row state re-checks */ }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Welcome to Salt", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Grant the permissions below to enable the control center.",
            style = MaterialTheme.typography.bodyMedium
        )

        PermissionRow("Display over other apps", Permissions.hasOverlay(context)) {
            context.startActivity(Permissions.overlayIntent(context))
        }
        PermissionRow("Notification access", Permissions.hasNotificationListener(context)) {
            context.startActivity(Permissions.notificationListenerIntent())
        }
        PermissionRow("Modify system settings", Permissions.hasWriteSettings(context)) {
            context.startActivity(Permissions.writeSettingsIntent(context))
        }
        PermissionRow("Bluetooth", Permissions.hasBluetoothConnect(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !Permissions.hasBluetoothConnect(context)
            ) {
                btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                context.startActivity(Permissions.appDetailsIntent(context))
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                OverlayService.start(context)
                onFinish()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Finish & start Salt") }
    }
}
