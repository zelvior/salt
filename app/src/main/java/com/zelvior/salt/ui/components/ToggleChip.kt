package com.zelvior.salt.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.zelvior.salt.ui.theme.SaltGlassBackground
import com.zelvior.salt.ui.theme.SaltPrimary
import com.zelvior.salt.ui.theme.SaltTextPrimary

/**
 * Circular glass toggle used in the control-center grid. `active` fills with the brand accent;
 * `enabled = false` dims it (e.g. a Shizuku-gated toggle before Shizuku is authorized).
 */
@Composable
fun ToggleChip(
    label: String,
    icon: ImageVector,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(60.dp).clip(CircleShape),
            color = if (active) SaltPrimary.copy(alpha = 0.9f) else SaltGlassBackground,
            shape = CircleShape,
            onClick = onClick,
            enabled = enabled
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = when {
                        !enabled -> SaltTextPrimary.copy(alpha = 0.4f)
                        active -> Color.Black
                        else -> SaltTextPrimary
                    }
                )
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) SaltTextPrimary else SaltTextPrimary.copy(alpha = 0.4f)
        )
    }
}
