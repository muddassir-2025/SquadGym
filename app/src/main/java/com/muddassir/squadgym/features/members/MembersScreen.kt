package com.muddassir.squadgym.features.members

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muddassir.squadgym.core.di.AppContainer
import com.muddassir.squadgym.core.di.vmFactory
import com.muddassir.squadgym.core.ui.components.EmptyState
import com.muddassir.squadgym.core.ui.components.ErrorState
import com.muddassir.squadgym.core.ui.components.FilterChip
import com.muddassir.squadgym.core.ui.components.FilterDropdown
import com.muddassir.squadgym.core.ui.components.GymCard
import com.muddassir.squadgym.core.ui.components.GymSearchField
import com.muddassir.squadgym.core.ui.components.InfoBanner
import com.muddassir.squadgym.core.ui.components.ListSkeleton
import com.muddassir.squadgym.core.ui.components.MemberAvatar
import com.muddassir.squadgym.core.ui.components.OfflineBanner
import com.muddassir.squadgym.core.ui.components.PillTabs
import com.muddassir.squadgym.core.ui.components.ScreenBackground
import com.muddassir.squadgym.core.ui.components.ScreenHeader
import com.muddassir.squadgym.core.ui.components.SpacerH
import com.muddassir.squadgym.core.ui.components.StatusPill
import com.muddassir.squadgym.core.ui.components.Tone
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme
import com.muddassir.squadgym.core.util.DateText
import com.muddassir.squadgym.data.remote.dto.MemberDto

@Composable
fun MembersScreen(
    container: AppContainer,
    gymTimezone: String,
    onOpenMember: (String) -> Unit,
    onAddMember: () -> Unit,
) {
    val viewModel: MembersViewModel = viewModel(
        factory = vmFactory { MembersViewModel(container.memberRepository, container.dashboardRepository) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val data = state.data
    val colors = SquadGymTheme.colors
    val today = data?.context?.gymLocalDate ?: DateText.todayIso(gymTimezone)

    ScreenBackground {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScreenHeader(
                    title = "Members",
                    subtitle = data?.let { "${it.meta.total} ${tabLabel(state.tab)}" } ?: "Directory",
                    actions = {
                        Surface(
                            modifier = Modifier.size(40.dp).clickable(onClick = onAddMember),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.PersonAdd,
                                    contentDescription = "Add member",
                                    tint = MaterialTheme.colorScheme.background,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    },
                )
            }

            if (state.cached) item { OfflineBanner(cachedAt = null) }

            item {
                GymSearchField(
                    value = state.search,
                    onValueChange = viewModel::onSearchChange,
                    placeholder = "Search name, email or phone",
                )
            }

            item {
                PillTabs(
                    tabs = listOf("All", "Expiring", "Expired", "Archived"),
                    selectedIndex = when (state.tab) {
                        "expiring" -> 1
                        "expired" -> 2
                        "archived" -> 3
                        else -> 0
                    },
                    onSelect = { index ->
                        viewModel.selectTab(
                            when (index) {
                                1 -> "expiring"
                                2 -> "expired"
                                3 -> "archived"
                                else -> "all"
                            },
                        )
                    },
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterDropdown(
                        label = "Sort",
                        selectedLabel = sortLabel(state.sort),
                        options = listOf(
                            "name_asc" to "Name A–Z",
                            "score_desc" to "Highest score",
                            "score_asc" to "Lowest score",
                            "newest" to "Newest members",
                            "oldest" to "Oldest members",
                            "expiry_asc" to "Expiring soonest",
                        ),
                        onSelect = viewModel::setSort,
                    )
                    FilterDropdown(
                        label = "Gender",
                        selectedLabel = state.gender?.replaceFirstChar { it.uppercase() } ?: "Gender",
                        options = listOf("" to "All genders", "male" to "Male", "female" to "Female", "other" to "Other"),
                        onSelect = { viewModel.setGender(it.ifBlank { null }) },
                    )
                    FilterDropdown(
                        label = "Plan",
                        selectedLabel = viewModel.plans.firstOrNull { it.id == state.planId }?.name ?: "Plan",
                        options = listOf("" to "All plans") + viewModel.plans.map { it.id to it.name },
                        onSelect = { viewModel.setPlan(it.ifBlank { null }) },
                    )
                    FilterDropdown(
                        label = "Status",
                        selectedLabel = when (state.status) {
                            "active" -> "Active"
                            "expired" -> "Expired"
                            "expiring" -> "Expiring"
                            else -> "Status"
                        },
                        options = listOf(
                            "" to "Any status",
                            "active" to "Active",
                            "expiring" to "Expiring soon",
                            "expired" to "Expired",
                        ),
                        onSelect = { viewModel.setStatus(it.ifBlank { null }) },
                    )
                    if (state.filtersActive) {
                        FilterChip(label = "Clear", selected = true, onClick = viewModel::clearFilters)
                    }
                }
            }

            if (state.error != null) {
                item {
                    InfoBanner(
                        text = state.error!!,
                        icon = Icons.Filled.Groups,
                        tone = Tone.Danger,
                        actionLabel = "Dismiss",
                        onAction = viewModel::consumeMessage,
                    )
                }
            }

            when {
                state.loading && data == null -> item { ListSkeleton(rows = 6) }
                data == null -> item {
                    ErrorState(message = state.error ?: "Couldn't load members.", onRetry = viewModel::refresh)
                }

                data.members.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Filled.Groups,
                        title = when (state.tab) {
                            "expiring" -> "Nothing expiring soon"
                            "expired" -> "No expired memberships"
                            "archived" -> "No archived members"
                            else -> "No members yet"
                        },
                        message = when (state.tab) {
                            "expiring" -> "Memberships inside the ${data.context.expiringSoonDays}-day warning window appear here."
                            "expired" -> "Members whose end date has passed appear here."
                            "archived" -> "Archived members keep their history and can be restored."
                            else -> "Add your first member to get started."
                        },
                        actionLabel = if (state.tab == "all") "Add member" else null,
                        onAction = if (state.tab == "all") onAddMember else null,
                    )
                }

                else -> {
                    items(data.members, key = { it.id }) { member ->
                        MemberCard(
                            member = member,
                            today = today,
                            timezone = gymTimezone,
                            onClick = { onOpenMember(member.id) },
                        )
                    }
                    if (state.hasMore) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.loadingMore) {
                                    CircularProgressIndicator(
                                        color = colors.accent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(22.dp),
                                    )
                                } else {
                                    Text(
                                        text = "Load more",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.accent,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(onClick = viewModel::loadMore)
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberCard(
    member: MemberDto,
    today: String,
    timezone: String,
    onClick: () -> Unit,
) {
    val colors = SquadGymTheme.colors
    GymCard(padding = 16.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(
                photoUrl = member.photoUrl,
                initials = member.initials,
                size = 46.dp,
                highlight = member.membershipStatus == "expiring",
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.fullName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (member.isArchived) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill("Archived", Tone.Neutral)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = listOfNotNull(member.membership?.planName, member.shift?.name)
                        .joinToString(" · ")
                        .ifBlank { "No active plan" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = DateText.expiryLabel(member.membership?.endDate, today),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor(member.membershipStatus),
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "score ${member.score}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                StatusPill(
                    text = member.membershipStatus.replaceFirstChar { it.uppercase() },
                    tone = when (member.membershipStatus) {
                        "active" -> Tone.Success
                        "expiring" -> Tone.Warning
                        "expired" -> Tone.Danger
                        else -> Tone.Neutral
                    },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = member.joinedOn.takeIf { it.isNotBlank() }?.let { "from ${DateText.short(it)}" } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.border,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun statusColor(status: String) = when (status) {
    "expiring" -> SquadGymTheme.colors.warning
    "expired" -> SquadGymTheme.colors.danger
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun tabLabel(tab: String) = when (tab) {
    "expiring" -> "expiring soon"
    "expired" -> "expired"
    "archived" -> "archived"
    else -> "members"
}

private fun sortLabel(sort: String) = when (sort) {
    "score_desc" -> "Highest score"
    "score_asc" -> "Lowest score"
    "newest" -> "Newest"
    "oldest" -> "Oldest"
    "expiry_asc" -> "Expiring soonest"
    else -> "Name A–Z"
}
