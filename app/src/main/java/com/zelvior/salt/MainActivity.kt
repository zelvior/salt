package com.zelvior.salt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zelvior.salt.core.overlay.OverlayService
import com.zelvior.salt.core.permissions.Permissions
import com.zelvior.salt.ui.components.PermissionRow
import com.zelvior.salt.ui.onboarding.OnboardingActivity
import com.zelvior.salt.ui.theme.SaltTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // First run / recovery: without overlay permission the shade can't be shown,
        // so send the user through the onboarding checklist.
        if (!Permissions.hasOverlay(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContent {
            SaltTheme { MainScreen() }
        }
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    var overlayRunning by remember { mutableStateOf(false) }

    val postNotifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled by system settings; we re-check on resume */ }

    // Best-effort: ask for notification permission so the overlay/AI can surface info.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Salt", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Custom Control Center & Quick Settings",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PermissionRow(
            "Display over other apps",
            Permissions.hasOverlay(context)
        ) { context.startActivity(Permissions.overlayIntent(context)) }
        PermissionRow(
            "Notification access",
            Permissions.hasNotificationListener(context)
        ) { context.startActivity(Permissions.notificationListenerIntent()) }
        PermissionRow(
            "Modify system settings",
            Permissions.hasWriteSettings(context)
        ) { context.startActivity(Permissions.writeSettingsIntent(context)) }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                OverlayService.start(context)
                overlayRunning = true
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (overlayRunning) "Salt is running" else "Start Salt overlay") }

        OutlinedButton(
            onClick = {
                OverlayService.stop(context)
                overlayRunning = false
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Stop overlay") }

        TextButton(
            onClick = { context.startActivity(Intent(context, OnboardingActivity::class.java)) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open setup") }
    }
}
