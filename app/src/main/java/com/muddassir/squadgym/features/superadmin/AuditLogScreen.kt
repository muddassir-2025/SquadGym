package com.muddassir.squadgym.features.superadmin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muddassir.squadgym.core.di.AppContainer
import com.muddassir.squadgym.core.di.vmFactory
import com.muddassir.squadgym.core.ui.components.EmptyState
import com.muddassir.squadgym.core.ui.components.ErrorState
import com.muddassir.squadgym.core.ui.components.GymCard
import com.muddassir.squadgym.core.ui.components.GymSearchField
import com.muddassir.squadgym.core.ui.components.ListSkeleton
import com.muddassir.squadgym.core.ui.components.ScreenBackground
import com.muddassir.squadgym.core.ui.components.ScreenHeader
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme
import com.muddassir.squadgym.core.util.DateText
import com.muddassir.squadgym.data.remote.dto.AuditLogDto

@Composable
fun AuditLogScreen(
    container: AppContainer,
    timezone: String,
    onBack: () -> Unit,
) {
    val viewModel: AuditLogViewModel = viewModel(
        factory = vmFactory { AuditLogViewModel(container.adminRepository) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = SquadGymTheme.colors

    ScreenBackground {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ScreenHeader(
                    title = "Activity log",
                    subtitle = "${state.meta.total} audited actions",
                    onBack = onBack,
                )
            }
            item {
                GymSearchField(
                    value = state.search,
                    onValueChange = viewModel::onSearchChange,
                    placeholder = "Search actions or staff",
                )
            }

            when {
                state.loading -> item { ListSkeleton(rows = 6) }
                state.items.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Filled.Receipt,
                        title = "No activity yet",
                        message = "Every mark, undo, renewal and admin change is recorded here.",
                    )
                }
                else -> {
                    items(state.items, key = { it.id }) { entry ->
                        AuditRow(entry, timezone)
                    }
                    if (state.meta.hasNext) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.loadingMore) {
                                    CircularProgressIndicator(
                                        color = colors.accent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp),
                                    )
                                } else {
                                    Text(
                                        text = "Load older actions",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.accent,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(onClick = viewModel::loadMore)
                                            .padding(horizontal = 14.dp, vertical = 9.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.error != null) {
                item {
                    ErrorState(message = state.error!!, onRetry = viewModel::load, title = "Couldn't load the log")
                }
            }
        }
    }
}

@Composable
private fun AuditRow(entry: AuditLogDto, timezone: String) {
    val colors = SquadGymTheme.colors
    GymCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colors.neutralTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    iconFor(entry.action),
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.actionLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(entry.actorName ?: "System")
                        entry.actorRole?.let { append(" · ${it.replace('_', ' ')}") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.metadata.entries.firstOrNull()?.let { (key, value) ->
                    Text(
                        text = "$key: ${value.toString().trim('"').take(42)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = DateText.dateTime(entry.createdAt, timezone),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private fun iconFor(action: String): ImageVector = when {
    action.startsWith("attendance.marked") -> Icons.Filled.CheckCircle
    action.contains("undo") || action.contains("reversed") -> Icons.Filled.Undo
    action.startsWith("member") -> Icons.Filled.PersonAdd
    action.startsWith("admin") -> Icons.Filled.Groups
    action.startsWith("settings") || action.startsWith("plan") -> Icons.Filled.Settings
    action.startsWith("membership") -> Icons.Filled.Edit
    else -> Icons.Filled.Receipt
}
