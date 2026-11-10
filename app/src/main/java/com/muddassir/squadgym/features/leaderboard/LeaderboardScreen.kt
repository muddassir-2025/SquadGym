package com.muddassir.squadgym.features.leaderboard

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muddassir.squadgym.core.di.AppContainer
import com.muddassir.squadgym.core.di.vmFactory
import com.muddassir.squadgym.core.ui.components.EmptyState
import com.muddassir.squadgym.core.ui.components.ErrorState
import com.muddassir.squadgym.core.ui.components.GymCard
import com.muddassir.squadgym.core.ui.components.ListSkeleton
import com.muddassir.squadgym.core.ui.components.MemberAvatar
import com.muddassir.squadgym.core.ui.components.OfflineBanner
import com.muddassir.squadgym.core.ui.components.PillTabs
import com.muddassir.squadgym.core.ui.components.RankBadge
import com.muddassir.squadgym.core.ui.components.ScreenBackground
import com.muddassir.squadgym.core.ui.components.ScreenHeader
import com.muddassir.squadgym.core.ui.components.SpacerH
import com.muddassir.squadgym.core.ui.components.StatusPill
import com.muddassir.squadgym.core.ui.components.Tone
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme
import com.muddassir.squadgym.data.remote.dto.LeaderboardDto
import com.muddassir.squadgym.data.remote.dto.LeaderboardEntryDto

@Composable
fun LeaderboardScreen(container: AppContainer) {
    val viewModel: LeaderboardViewModel = viewModel(
        factory = vmFactory { LeaderboardViewModel(container.leaderboardRepository) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val data = state.data

    ScreenBackground {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                ScreenHeader(
                    title = "Leaderboard",
                    subtitle = data?.periodLabel?.takeIf { it.isNotBlank() } ?: "Attendance rankings",
                    actions = {
                        Icon(
                            Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                )
            }

            if (state.cached) item { OfflineBanner(cachedAt = null) }

            item {
                PillTabs(
                    tabs = listOf("This week", "This month"),
                    selectedIndex = if (state.period == "month") 1 else 0,
                    onSelect = { index -> viewModel.selectPeriod(if (index == 1) "month" else "week") },
                )
            }

            if (data?.me != null) {
                item {
                    MyRankCard(
                        rank = data.me!!.rank,
                        score = data.me!!.score,
                        totalMembers = data.me!!.totalMembers,
                        streak = data.me!!.currentStreak,
                    )
                }
            }

            when {
                state.loading && data == null -> item { ListSkeleton(rows = 7) }
                data == null -> item {
                    ErrorState(message = state.error ?: "Couldn't load the leaderboard.", onRetry = viewModel::load)
                }
                data.entries.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Filled.EmojiEvents,
                        title = "No scores yet",
                        message = "One point is awarded for every gym day a member attends.",
                    )
                }
                else -> {
                    item {
                        val podium = data.entries.take(3)
                        if (podium.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                podium.getOrNull(1)?.let { entry ->
                                    PodiumCard(entry, height = 118.dp, modifier = Modifier.weight(1f))
                                }
                                podium.getOrNull(0)?.let { entry ->
                                    PodiumCard(entry, height = 144.dp, modifier = Modifier.weight(1f))
                                }
                                podium.getOrNull(2)?.let { entry ->
                                    PodiumCard(entry, height = 104.dp, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    items(data.entries.drop(3), key = { it.memberId }) { entry ->
                        LeaderboardRow(entry, data)
                    }

                    item {
                        Text(
                            text = "One point per distinct attendance day · ties broken by name · periods use the gym's timezone",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumCard(entry: LeaderboardEntryDto, height: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val colors = SquadGymTheme.colors
    val tint = if (entry.rank == 1) colors.accent else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier.height(height),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                MemberAvatar(photoUrl = entry.photoUrl, initials = entry.fullName.take(2).uppercase(), size = 44.dp)
                Icon(
                    Icons.Filled.MilitaryTech,
                    contentDescription = "Rank ${entry.rank}",
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = entry.fullName.substringBefore(' '),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${entry.score} pts",
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntryDto, data: LeaderboardDto) {
    val colors = SquadGymTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (entry.isMe) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RankBadge(entry.rank)
            Spacer(Modifier.width(10.dp))
            MemberAvatar(photoUrl = entry.photoUrl, initials = entry.fullName.take(2).uppercase(), size = 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (entry.isMe) "${entry.fullName} (you)" else entry.fullName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = colors.warning,
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "${entry.currentStreak} day streak",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = entry.score.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.accent,
                )
                Text(
                    text = "points",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MyRankCard(rank: Int, score: Int, totalMembers: Int, streak: Int) {
    val colors = SquadGymTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your rank",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "#$rank of $totalMembers",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    StatusPill(text = "$score pts", tone = Tone.Accent, showDot = false)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$streak day streak",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
