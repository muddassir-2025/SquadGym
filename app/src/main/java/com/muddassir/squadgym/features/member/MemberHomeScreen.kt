package com.muddassir.squadgym.features.member

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muddassir.squadgym.core.di.AppContainer
import com.muddassir.squadgym.core.di.vmFactory
import com.muddassir.squadgym.core.ui.components.AttendanceCalendar
import com.muddassir.squadgym.core.ui.components.EmptyState
import com.muddassir.squadgym.core.ui.components.ErrorState
import com.muddassir.squadgym.core.ui.components.GymCard
import com.muddassir.squadgym.core.ui.components.ListSkeleton
import com.muddassir.squadgym.core.ui.components.MonthPickerRow
import com.muddassir.squadgym.core.ui.components.OfflineBanner
import com.muddassir.squadgym.core.ui.components.ProgressBar
import com.muddassir.squadgym.core.ui.components.ScreenBackground
import com.muddassir.squadgym.core.ui.components.SectionHeader
import com.muddassir.squadgym.core.ui.components.SpacerH
import com.muddassir.squadgym.core.ui.components.StatTile
import com.muddassir.squadgym.core.ui.components.StatusPill
import com.muddassir.squadgym.core.ui.components.Tone
import com.muddassir.squadgym.core.ui.components.ValueRow
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme
import com.muddassir.squadgym.core.util.DateText
import com.muddassir.squadgym.core.util.NumberText
import com.muddassir.squadgym.data.remote.dto.MemberHomeDto
import com.muddassir.squadgym.data.remote.dto.UserDto

@Composable
fun MemberHomeScreen(
    container: AppContainer,
    user: UserDto,
    onLeaderboard: () -> Unit,
) {
    val viewModel: MemberHomeViewModel = viewModel(
        factory = vmFactory { MemberHomeViewModel(container.meRepository) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val data = state.data
    val colors = SquadGymTheme.colors

    ScreenBackground {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Welcome back,",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = user.fullName.substringBefore(' '),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = data?.gymName?.takeIf { it.isNotBlank() } ?: "Your gym",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusPill(
                        text = when (data?.personal?.todayStatus) {
                            "present" -> "Present today"
                            "closed" -> "Gym closed"
                            else -> "Not yet today"
                        },
                        tone = when (data?.personal?.todayStatus) {
                            "present" -> Tone.Success
                            "closed" -> Tone.Neutral
                            else -> Tone.Warning
                        },
                    )
                }
            }

            if (state.cached) item { OfflineBanner(cachedAt = null) }

            when {
                state.loading && data == null -> item { ListSkeleton(rows = 4) }
                data == null -> item {
                    ErrorState(message = state.error ?: "Couldn't load your dashboard.", onRetry = viewModel::load)
                }

                else -> {
                    item {
                        val membership = data.membership
                        GymCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.accent.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.FitnessCenter,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = membership?.planName?.takeIf { it.isNotBlank() } ?: "No active plan",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = membership?.let { DateText.expiryLabel(it.endDate, data.gymLocalDate) }
                                            ?: "Ask the gym for a membership",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (membership?.status) {
                                            "expired" -> colors.danger
                                            "expiring" -> colors.warning
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                                StatusPill(
                                    text = membership?.status?.replaceFirstChar { it.uppercase() } ?: "None",
                                    tone = when (membership?.status) {
                                        "active" -> Tone.Success
                                        "expiring" -> Tone.Warning
                                        "expired" -> Tone.Danger
                                        else -> Tone.Neutral
                                    },
                                )
                            }
                            if (membership != null) {
                                SpacerH(12.dp)
                                val totalDays = runCatching {
                                    java.time.temporal.ChronoUnit.DAYS.between(
                                        java.time.LocalDate.parse(membership.startDate),
                                        java.time.LocalDate.parse(membership.endDate),
                                    ).toFloat() + 1f
                                }.getOrDefault(30f)
                                ProgressBar((membership.daysRemaining / totalDays).coerceIn(0f, 1f))
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "${membership.startDate} → ${membership.endDate}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                label = "In gym now",
                                value = data.stats.inGymNow.value,
                                icon = Icons.Filled.FitnessCenter,
                                comparison = null,
                                highlight = true,
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = "Members",
                                value = data.stats.totalMembers.value,
                                icon = Icons.Filled.Groups,
                                comparison = data.stats.totalMembers.comparison,
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = "Today",
                                value = data.stats.todayAttendance.value,
                                icon = Icons.Filled.CheckCircle,
                                comparison = data.stats.todayAttendance.comparison,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    item {
                        GymCard(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SectionHeader(
                                title = "Your streak",
                                subtitle = "Gym-open days only",
                                trailing = {
                                    StatusPill(
                                        text = "${data.personal.currentStreak} days",
                                        tone = Tone.Accent,
                                        showDot = false,
                                    )
                                },
                            )
                            SpacerH(12.dp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricBox(
                                    label = "Current",
                                    value = data.personal.currentStreak.toString(),
                                    icon = Icons.Filled.LocalFireDepartment,
                                    modifier = Modifier.weight(1f),
                                )
                                MetricBox(
                                    label = "Longest",
                                    value = data.personal.longestStreak.toString(),
                                    icon = Icons.Filled.EmojiEvents,
                                    modifier = Modifier.weight(1f),
                                )
                                MetricBox(
                                    label = "This month",
                                    value = "${data.personal.monthly.daysAttended}/${data.personal.monthly.openDays}",
                                    icon = Icons.Filled.CalendarMonth,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            SpacerH(12.dp)
                            ValueRow(
                                label = "Attendance rate",
                                value = NumberText.percent(data.personal.attendancePercent),
                                emphasize = true,
                                valueColor = colors.accent,
                            )
                            ValueRow(label = "Lifetime points", value = data.personal.lifetimeScore.toString())
                            ValueRow(label = "Missed days (month)", value = data.personal.missedDays.toString())
                        }
                    }

                    item {
                        GymCard(onClick = onLeaderboard) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(colors.warning.copy(alpha = 0.3f), colors.warning.copy(alpha = 0.1f)),
                                            ),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.EmojiEvents,
                                        contentDescription = null,
                                        tint = colors.warning,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Rank #${data.ranking.month} this month",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "Weekly #${data.ranking.week} · ${data.ranking.totalMembers} members",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                StatusPill(
                                    text = "${data.personal.weekly.score} pts this week",
                                    tone = Tone.Accent,
                                    showDot = false,
                                )
                            }
                        }
                    }

                    item {
                        GymCard {
                            MonthPickerRow(
                                month = data.calendarMonth,
                                canGoForward = data.calendarMonth < data.gymLocalDate.take(7),
                                onPrevious = { viewModel.shiftMonth(-1) },
                                onNext = { viewModel.shiftMonth(1) },
                            )
                            SpacerH(12.dp)
                            if (data.calendar.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Filled.CalendarMonth,
                                    title = "Nothing to show",
                                    message = "Attendance will appear here once you check in.",
                                )
                            } else {
                                AttendanceCalendar(days = data.calendar)
                            }
                            SpacerH(10.dp)
                            ValueRow(
                                label = "Attended",
                                value = "${data.calendar.count { it.status == "attended" }} days",
                            )
                            ValueRow(
                                label = "Missed",
                                value = "${data.calendar.count { it.status == "missed" }} days",
                            )
                            ValueRow(
                                label = "Gym closed",
                                value = "${data.calendar.count { it.status == "closed" }} days",
                            )
                        }
                    }

                    item {
                        GymCard {
                            SectionHeader(title = "Period summary", subtitle = "Weekly and monthly views")
                            SpacerH(10.dp)
                            ValueRow(
                                label = data.personal.weekly.label,
                                value = "${data.personal.weekly.daysAttended}/${data.personal.weekly.openDays} days",
                            )
                            ValueRow(
                                label = "Weekly attendance",
                                value = NumberText.percent(data.personal.weekly.attendancePercent),
                                valueColor = colors.accent,
                            )
                            ValueRow(
                                label = data.personal.monthly.label,
                                value = "${data.personal.monthly.daysAttended}/${data.personal.monthly.openDays} days",
                            )
                            ValueRow(
                                label = "Monthly attendance",
                                value = NumberText.percent(data.personal.monthly.attendancePercent),
                                valueColor = colors.accent,
                            )
                            ValueRow(
                                label = "Total visits",
                                value = data.personal.lifetimeScore.toString(),
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Filled.Timeline,
                                contentDescription = null,
                                tint = colors.border,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Missed days count only when the gym was open and your membership active.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = SquadGymTheme.colors.accent,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
