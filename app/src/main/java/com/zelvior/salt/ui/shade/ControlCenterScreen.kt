package com.zelvior.salt.ui.shade

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zelvior.salt.core.overlay.OverlayInteractor
import com.zelvior.salt.ui.theme.SaltGlassBackground
import com.zelvior.salt.ui.theme.SaltPrimary
import com.zelvior.salt.ui.theme.SaltTextPrimary
import com.zelvior.salt.ui.theme.glassBlur

/**
 * Root of the overlay control center. The outer Box fills the (top-portion) panel container;
 * the [AnimatedVisibility] slides the glass panel down from the top edge when [OverlayInteractor.isOpen]
 * becomes true and back up when closed.
 */
@Composable
fun ControlCenterScreen(interactor: OverlayInteractor, onClose: () -> Unit) {
    val isOpen by interactor.isOpen.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            GlassPanel(onClose = onClose)
        }
    }
}

@Composable
private fun GlassPanel(onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(12.dp)
    ) {
        // Glass backdrop: a soft translucent gradient, hardware-blurred on API 31+
        // (no-op fallback on older versions — the alpha fill still reads as glass).
        Box(
            Modifier
                .matchParentSize()
                .glassBlur(20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    )
                )
        )

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .padding(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickToggle(Icons.Default.Wifi, "Wi-Fi")
                QuickToggle(Icons.Default.Bluetooth, "BT")
                QuickToggle(Icons.Default.FlashlightOn, "Torch")
                QuickToggle(Icons.Default.DoNotDisturb, "DND")
            }

            Text(
                "Pull the edge strip down to open · Phase 2 adds live toggles, AI digest & plugins",
                style = MaterialTheme.typography.bodySmall,
                color = SaltTextPrimary.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

@Composable
private fun QuickToggle(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(56.dp).clip(CircleShape),
            color = SaltGlassBackground,
            shape = CircleShape
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = SaltPrimary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = SaltTextPrimary)
    }
}
