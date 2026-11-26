@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zelvior.salt.ui.shade

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zelvior.salt.core.notifications.CleanNotification
import com.zelvior.salt.ui.theme.SaltGlassBackground
import com.zelvior.salt.ui.theme.SaltPrimary
import com.zelvior.salt.ui.theme.SaltTextPrimary
import com.zelvior.salt.ui.theme.SaltTextSecondary

/**
 * Scrollable (by the parent panel) notification feed with swipe-to-dismiss rows and a
 * "Clear all" action. Rows are keyed by notification key so swipe states reset correctly
 * when the underlying list changes.
 */
@Composable
fun NotificationFeed(
    items: List<CleanNotification>,
    onDismiss: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Notifications",
                style = MaterialTheme.typography.labelLarge,
                color = SaltTextSecondary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClearAll, enabled = items.isNotEmpty()) {
                Text("Clear all", color = if (items.isNotEmpty()) SaltPrimary else SaltTextSecondary)
            }
        }

        items.forEach { item ->
            key(item.key) {
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            onDismiss(item.key)
                            true
                        } else {
                            false
                        }
                    }
                )
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        Row(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SaltPrimary.copy(alpha = 0.20f))
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Dismiss",
                                tint = SaltPrimary
                            )
                        }
                    }
                ) {
                    NotificationRow(item)
                }
            }
            Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun NotificationRow(item: CleanNotification) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SaltGlassBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.appLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = SaltPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.padding(horizontal = 6.dp))
            Text(
                DateUtils.getRelativeTimeSpanString(item.timestamp).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = SaltTextSecondary
            )
        }
        if (item.title.isNotBlank()) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = SaltTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.text.isNotBlank()) {
            Text(
                item.text,
                style = MaterialTheme.typography.bodySmall,
                color = SaltTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
