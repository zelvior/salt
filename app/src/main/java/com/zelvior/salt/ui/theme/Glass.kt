package com.zelvior.salt.ui.theme

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glassmorphism modifier.
 *
 * On Android 12+ (API 31) we use the platform RenderEffect blur via [Modifier.blur].
 * On older versions RenderEffect is unavailable, so we gracefully fall back to no blur —
 * the translucent gradient fill + round clip already read as glass. (A true backdrop blur
 * of the underlying app is a Phase 2 enhancement.)
 */
fun Modifier.glassBlur(radius: Dp = 20.dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.blur(radius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
    } else {
        this
    }
