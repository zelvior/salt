package com.zelvior.salt.ui.shade

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zelvior.salt.core.media.MediaState
import com.zelvior.salt.ui.theme.SaltGlassBackground
import com.zelvior.salt.ui.theme.SaltPrimary
import com.zelvior.salt.ui.theme.SaltTextPrimary
import com.zelvior.salt.ui.theme.SaltTextSecondary

/**
 * Compact glassmorphic media widget for the shade: album art + title/artist and
 * previous / play-pause / next transport controls. Rendered only when a media session
 * is active ([com.zelvior.salt.core.media.MediaControllerManager.media] != null).
 */
@Composable
fun MediaCard(
    state: MediaState,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SaltGlassBackground)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(state)

        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                state.title.ifBlank { "Unknown title" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = SaltTextPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                state.artist.ifBlank { state.appLabel },
                style = MaterialTheme.typography.labelSmall,
                color = SaltTextSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        TransportButton(Icons.Default.SkipPrevious, "Previous", onSkipPrevious)
        TransportButton(
            icon = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (state.isPlaying) "Pause" else "Play",
            onClick = onPlayPause,
            highlighted = true
        )
        TransportButton(Icons.Default.SkipNext, "Next", onSkipNext)
    }
}

@Composable
private fun AlbumArt(state: MediaState) {
    val art = state.albumArt
    if (art != null) {
        Image(
            bitmap = art.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
        )
    } else {
        // No embedded art — show a tasteful placeholder instead of stretching nothing.
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SaltPrimary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = SaltPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    highlighted: Boolean = false
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (highlighted) SaltPrimary else SaltTextPrimary,
            modifier = Modifier.size(if (highlighted) 28.dp else 24.dp)
        )
    }
}
