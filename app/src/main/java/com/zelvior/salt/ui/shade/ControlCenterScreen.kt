package com.zelvior.salt.ui.shade

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zelvior.salt.R
import com.zelvior.salt.core.overlay.OverlayInteractor
import com.zelvior.salt.core.system.SystemController
import com.zelvior.salt.core.system.ToggleKey
import com.zelvior.salt.ui.components.ToggleChip
import com.zelvior.salt.ui.theme.SaltGlassBackground
import com.zelvior.salt.ui.theme.SaltPrimary
import com.zelvior.salt.ui.theme.SaltTextPrimary
import com.zelvior.salt.ui.theme.glassBlur
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Root of the overlay control center. The outer Box fills the (top-portion) panel container;
 * the [AnimatedVisibility] slides the glass panel down from the top edge when [OverlayInteractor.isOpen]
 * becomes true and back up when closed.
 */
@Composable
fun ControlCenterScreen(
    interactor: OverlayInteractor,
    controller: SystemController,
    onClose: () -> Unit
) {
    val isOpen by interactor.isOpen.collectAsStateWithLifecycle()
    val states by controller.states.collectAsStateWithLifecycle()
    val brightness by controller.brightness.collectAsStateWithLifecycle()
    val volume by controller.volume.collectAsStateWithLifecycle()
    val shizukuReady by controller.shizukuReady.collectAsStateWithLifecycle()
    val shizukuInstalled by controller.shizukuInstalled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            GlassPanel(
                states = states,
                brightness = brightness,
                volume = volume,
                shizukuReady = shizukuReady,
                shizukuInstalled = shizukuInstalled,
                onToggle = { scope.launch { controller.toggle(it) } },
                onBrightness = { scope.launch { controller.setBrightness(it) } },
                onVolume = { scope.launch { controller.setVolume(it) } },
                onConnectShizuku = { scope.launch { controller.connectShizuku() } },
                onClose = onClose
            )
        }
    }
}

@Composable
private fun GlassPanel(
    states: Set<ToggleKey>,
    brightness: Int,
    volume: Int,
    shizukuReady: Boolean,
    shizukuInstalled: Boolean,
    onToggle: (ToggleKey) -> Unit,
    onBrightness: (Int) -> Unit,
    onVolume: (Int) -> Unit,
    onConnectShizuku: () -> Unit,
    onClose: () -> Unit
) {
    val needsShizuku = setOf(ToggleKey.WIFI, ToggleKey.MOBILE_DATA, ToggleKey.AIRPLANE_MODE)

    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(12.dp)
    ) {
        // Glass backdrop: soft translucent gradient, hardware-blurred on API 31+ (no-op fallback).
        Box(
            Modifier
                .matchParentSize()
                .glassBlur(20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f))
                    )
                )
        )

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.salt_logo),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Salt", style = MaterialTheme.typography.titleMedium, color = SaltTextPrimary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Control Center",
                    style = MaterialTheme.typography.bodySmall,
                    color = SaltTextPrimary.copy(alpha = 0.6f)
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = SaltTextPrimary)
                }
            }

            Spacer(Modifier.height(8.dp))
            SliderRow(Icons.Default.BrightnessMedium, brightness, onBrightness)
            SliderRow(Icons.Default.VolumeUp, volume, onVolume)

            // Shizuku gating banner for restricted toggles.
            if (!shizukuReady) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (shizukuInstalled) "Authorize Shizuku to toggle Wi-Fi / data / airplane"
                        else "Install Shizuku for Wi-Fi / data / airplane toggles",
                        style = MaterialTheme.typography.bodySmall,
                        color = SaltTextPrimary.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = onConnectShizuku) {
                        Text(if (shizukuInstalled) "Connect" else "Get Shizuku")
                    }
                }
            }

            // Toggle grid (2 rows of 3).
            val toggles = listOf(
                ToggleKey.WIFI to Icons.Default.Wifi,
                ToggleKey.BLUETOOTH to Icons.Default.Bluetooth,
                ToggleKey.MOBILE_DATA to Icons.Default.SignalCellularAlt,
                ToggleKey.AIRPLANE_MODE to Icons.Default.AirplanemodeActive,
                ToggleKey.DO_NOT_DISTURB to Icons.Default.DoNotDisturb,
                ToggleKey.FLASHLIGHT to Icons.Default.FlashlightOn
            )
            toggles.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { (key, icon) ->
                        val needShiz = key in needsShizuku
                        val enabled = !needShiz || shizukuReady
                        ToggleChip(
                            label = key.displayName(),
                            icon = icon,
                            active = key in states,
                            enabled = enabled,
                            onClick = {
                                if (needShiz && !shizukuReady) onConnectShizuku() else onToggle(key)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(icon: ImageVector, value: Int, onValueChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = SaltTextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Slider(
            value = value / 100f,
            onValueChange = { onValueChange((it * 100).roundToInt()) },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = SaltPrimary,
                activeTrackColor = SaltPrimary,
                inactiveTrackColor = SaltGlassBackground
            )
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "$value%",
            style = MaterialTheme.typography.labelSmall,
            color = SaltTextPrimary,
            modifier = Modifier.width(36.dp)
        )
    }
}

private fun ToggleKey.displayName(): String = when (this) {
    ToggleKey.WIFI -> "Wi-Fi"
    ToggleKey.BLUETOOTH -> "BT"
    ToggleKey.MOBILE_DATA -> "Data"
    ToggleKey.AIRPLANE_MODE -> "Airplane"
    ToggleKey.DO_NOT_DISTURB -> "DND"
    ToggleKey.FLASHLIGHT -> "Torch"
}
