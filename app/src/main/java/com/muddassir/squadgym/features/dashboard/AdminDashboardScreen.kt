package com.muddassir.squadgym.features.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muddassir.squadgym.core.di.AppContainer
import com.muddassir.squadgym.core.di.vmFactory
import com.muddassir.squadgym.core.ui.components.BarChart
import com.muddassir.squadgym.core.ui.components.ComparisonIndicator
import com.muddassir.squadgym.core.ui.components.DateRangeDialog
import com.muddassir.squadgym.core.ui.components.ErrorState
import com.muddassir.squadgym.core.ui.components.FilterChip
import com.muddassir.squadgym.core.ui.components.GymCard
import com.muddassir.squadgym.core.ui.components.InfoBanner
import com.muddassir.squadgym.core.ui.components.LoadingState
import com.muddassir.squadgym.core.ui.components.OfflineBanner
import com.muddassir.squadgym.core.ui.components.PrimaryButton
import com.muddassir.squadgym.core.ui.components.ScreenBackground
import com.muddassir.squadgym.core.ui.components.SectionHeader
import com.muddassir.squadgym.core.ui.components.SegmentedControl
import com.muddassir.squadgym.core.ui.components.SpacerH
import com.muddassir.squadgym.core.ui.components.StatTile
import com.muddassir.squadgym.core.ui.components.StatusPill
import com.muddassir.squadgym.core.ui.components.Tone
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme
import com.muddassir.squadgym.core.util.DateText
import com.muddassir.squadgym.data.remote.dto.GymDto
import com.muddassir.squadgym.data.remote.dto.UserDto
import java.time.LocalTime

@Composable
fun AdminDashboardScreen(
    container: AppContainer,
    user: UserDto,
    gym: GymDto,
    onTakeAttendance: () -> Unit,
    onAddMember: () -> Unit,
    onViewMembers: () -> Unit,
    onLeaderboard: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenGymSettings: () -> Unit,
) {
    val viewModel: AdminDashboardViewModel = viewModel(
        factory = vmFactory { AdminDashboardViewModel(container.dashboardRepository, container.attendanceRepository) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showRangeDialog by remember { mutableStateOf(false) }

    ScreenBackground {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Column {
                    Text(
                        text = greeting(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = user.fullName.substringBefore(' '),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = state.dashboard?.gymLocalDateLabel ?: "Today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        StatusPill(
                            text = if (state.dashboard?.gymOpen == false) "Closed today" else "Open today",
                            tone = if (state.dashboard?.gymOpen == false) Tone.Neutral else Tone.Success,
                        )
                    }
                }
            }

            if (state.cached) {
                item { OfflineBanner(cachedAt = null) }
            }

            if (state.loading) {
                item { LoadingState(message = "Reading today's floor") }
            } else if (state.dashboard == null) {
                item {
                    ErrorState(
                        message = state.error ?: "Couldn't load the dashboard.",
                        onRetry = viewModel::refresh,
                    )
                }
            } else {
                val stats = state.dashboard!!.stats
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(
                            label = "In gym now",
                            value = stats.inGymNow.value,
                            icon = Icons.Filled.FitnessCenter,
                            comparison = stats.inGymNow.comparison,
                            highlight = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatTile(
                                label = "Members",
                                value = stats.totalMembers.value,
                                icon = Icons.Filled.Groups,
                                comparison = stats.totalMembers.comparison,
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = "Attendance today",
                                value = stats.todayAttendance.value,
                                icon = Icons.Filled.CheckCircle,
                                comparison = stats.todayAttendance.comparison,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                item {
                    GymCard {
                        SectionHeader(
                            title = "Analytics",
                            subtitle = state.chart?.rangeLabel ?: "Last 7 days",
                        )
                        SpacerH(20.dp)
                        SegmentedControl(
                            options = listOf("Attendance", "New", "Total"),
                            selectedIndex = when (state.metric) {
                                "new_members" -> 1
                                "total_members" -> 2
                                else -> 0
                            },
                            onSelect = { index ->
                                viewModel.selectMetric(
                                    when (index) {
                                        1 -> "new_members"
                                        2 -> "total_members"
                                        else -> "attendance"
                                    },
                                )
                            },
                        )
                        SpacerH(14.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(label = "Weekly", selected = state.range == "week", onClick = { viewModel.selectRange("week") })
                            FilterChip(label = "Monthly", selected = state.range == "month", onClick = { viewModel.selectRange("month") })
                            FilterChip(label = "Custom", selected = state.range == "custom", onClick = { showRangeDialog = true })
                        }
                        SpacerH(20.dp)
                        if (state.chartLoading) {
                            LoadingState(message = "Crunching numbers", modifier = Modifier.padding(vertical = 8.dp))
                        } else {
                            BarChart(
                                points = state.chart?.points.orEmpty(),
                                selectedIndex = state.selectedPoint,
                                onSelect = viewModel::selectPoint,
                            )
                        }
                        SpacerH(14.dp)
                        val chartTotal = state.chart?.total ?: 0
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$chartTotal total",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(10.dp))
                            state.chart?.comparison?.let { ComparisonIndicator(it) }
                        }
                    }
                }

                item {
                    GymCard {
                        SectionHeader(
                            title = "Take attendance",
                            subtitle = "Manual check-in for ${state.dashboard?.gymLocalDateLabel ?: "today"}",
                        )
                        SpacerH(20.dp)
                        PrimaryButton(
                            text = "Open attendance",
                            onClick = onTakeAttendance,
                            icon = Icons.Filled.CheckCircle,
                        )
                        SpacerH(16.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuickAction(
                                icon = Icons.Filled.Add,
                                label = "Add member",
                                modifier = Modifier.weight(1f),
                                onClick = onAddMember,
                            )
                            QuickAction(
                                icon = Icons.Filled.Groups,
                                label = "All members",
                                modifier = Modifier.weight(1f),
                                onClick = onViewMembers,
                            )
                            QuickAction(
                                icon = Icons.Filled.EmojiEvents,
                                label = "Leaderboard",
                                modifier = Modifier.weight(1f),
                                onClick = onLeaderboard,
                            )
                        }
                    }
                }

                item {
                    GymCard {
                        SectionHeader(
                            title = "Settings",
                            subtitle = "Plans and gym configuration",
                        )
                        SpacerH(6.dp)
                        SettingsRow(
                            icon = Icons.Filled.WorkspacePremium,
                            title = "Subscription plans",
                            subtitle = "Create, edit or remove membership plans",
                            onClick = onOpenPlans,
                        )
                        if (user.isSuperAdmin) {
                            HorizontalDivider(color = SquadGymTheme.colors.border)
                            SettingsRow(
                                icon = Icons.Filled.Tune,
                                title = "Gym preferences",
                                subtitle = "Hours, shifts and business rules",
                                onClick = onOpenGymSettings,
                            )
                        }
                    }
                }

                if (state.message != null) {
                    item {
                        InfoBanner(
                            text = state.message!!,
                            icon = Icons.Filled.Timeline,
                            tone = Tone.Success,
                            actionLabel = "Dismiss",
                            onAction = viewModel::consumeMessage,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (showRangeDialog) {
        DateRangeDialog(
            initialFrom = state.customFrom ?: state.dashboard?.gymLocalDate ?: DateText.todayIso(gym.timezone),
            initialTo = state.customTo ?: state.dashboard?.gymLocalDate ?: DateText.todayIso(gym.timezone),
            onApply = { from, to ->
                showRangeDialog = false
                viewModel.applyCustomRange(from, to)
            },
            onDismiss = { showRangeDialog = false },
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(72.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}
