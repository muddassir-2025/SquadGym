package com.muddassir.squadgym.features.attendance

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muddassir.squadgym.core.di.AppContainer
import com.muddassir.squadgym.core.di.vmFactory
import com.muddassir.squadgym.core.ui.components.ConfirmDialog
import com.muddassir.squadgym.core.ui.components.EmptyState
import com.muddassir.squadgym.core.ui.components.ErrorState
import com.muddassir.squadgym.core.ui.components.FilterChip
import com.muddassir.squadgym.core.ui.components.FilterDropdown
import com.muddassir.squadgym.core.ui.components.GymCard
import com.muddassir.squadgym.core.ui.components.GymSearchField
import com.muddassir.squadgym.core.ui.components.InfoBanner
import com.muddassir.squadgym.core.ui.components.ListSkeleton
import com.muddassir.squadgym.core.ui.components.MemberAvatar
import com.muddassir.squadgym.core.ui.components.OptionsDialog
import com.muddassir.squadgym.core.ui.components.OfflineBanner
import com.muddassir.squadgym.core.ui.components.PillTabs
import com.muddassir.squadgym.core.ui.components.ScreenBackground
import com.muddassir.squadgym.core.ui.components.ScreenHeader
import com.muddassir.squadgym.core.ui.components.SpacerH
import com.muddassir.squadgym.core.ui.components.StatusPill
import com.muddassir.squadgym.core.ui.components.Tone
import com.muddassir.squadgym.core.ui.components.UndoBanner
import com.muddassir.squadgym.core.ui.theme.Spacing
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme
import com.muddassir.squadgym.core.util.DateText
import com.muddassir.squadgym.data.remote.dto.AttendanceMemberDto
import com.muddassir.squadgym.data.remote.dto.GymDto
import com.muddassir.squadgym.data.remote.dto.PlanDto
import com.muddassir.squadgym.data.remote.dto.ShiftDto

@Composable
fun AttendanceScreen(
    container: AppContainer,
    gym: GymDto,
    shifts: List<ShiftDto>,
    onOpenMember: (String) -> Unit,
) {
    val viewModel: AttendanceViewModel = viewModel(
        factory = vmFactory { AttendanceViewModel(container.attendanceRepository, container.dashboardRepository, shifts) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showShiftDialog by remember { mutableStateOf(false) }
    var showGenderDialog by remember { mutableStateOf(false) }
    var showPlanDialog by remember { mutableStateOf(false) }
    var correctionTarget by remember { mutableStateOf<AttendanceMemberDto?>(null) }

    val data = state.data
    val colors = SquadGymTheme.colors

    ScreenBackground {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScreenHeader(
                    title = "Take Attendance",
                    subtitle = data?.dateLabel ?: "Today",
                    actions = {
                        StatusPill(
                            text = if (data?.gymOpen == false) "Closed" else "${data?.inGymNow ?: 0} inside",
                            tone = if (data?.gymOpen == false) Tone.Neutral else Tone.Accent,
                        )
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

            if (data != null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryChip("Total", data.summary.total, Modifier.weight(1f))
                        SummaryChip("Present", data.summary.present, Modifier.weight(1f), highlight = true)
                        SummaryChip("Absent", data.summary.absent, Modifier.weight(1f))
                    }
                    if (data.summary.filtered) {
                        SpacerH(6.dp)
                        Text(
                            text = "Counts reflect your filters · gym-wide: ${data.gymTotals.total} total, " +
                                "${data.gymTotals.present} present",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    PillTabs(
                        tabs = listOf("All", "Present", "Absent"),
                        selectedIndex = when (state.tab) {
                            "present" -> 1
                            "absent" -> 2
                            else -> 0
                        },
                        counts = listOf(data.summary.total, data.summary.present, data.summary.absent),
                        onSelect = { index ->
                            viewModel.selectTab(
                                when (index) {
                                    1 -> "present"
                                    2 -> "absent"
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
                            label = "Shift",
                            selectedLabel = shifts.firstOrNull { it.id == state.shiftId }?.name ?: "Shift",
                            options = listOf("" to "All shifts") + viewModel.shiftOptions,
                            onSelect = { viewModel.setShift(it.ifBlank { null }) },
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
                        if (state.filtersActive) {
                            FilterChip(
                                label = "Clear",
                                selected = true,
                                onClick = viewModel::clearFilters,
                            )
                        }
                    }
                }
            }

            if (state.error != null) {
                item {
                    InfoBanner(
                        text = state.error!!,
                        icon = Icons.Filled.PersonOff,
                        tone = Tone.Danger,
                        actionLabel = "Dismiss",
                        onAction = viewModel::consumeMessage,
                    )
                }
            }

            if (state.message != null) {
                item {
                    InfoBanner(
                        text = state.message!!,
                        icon = Icons.Filled.CheckCircle,
                        tone = Tone.Success,
                        actionLabel = "Dismiss",
                        onAction = viewModel::consumeMessage,
                    )
                }
            }

            when {
                state.loading && data == null -> item { ListSkeleton(rows = 6) }

                data == null -> item {
                    ErrorState(message = state.error ?: "Couldn't load attendance.", onRetry = viewModel::refresh)
                }

                data.members.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Filled.Groups,
                        title = when (state.tab) {
                            "present" -> "Nobody checked in yet"
                            "absent" -> "Everyone has arrived"
                            else -> if (state.filtersActive) "No members match" else "No eligible members"
                        },
                        message = when (state.tab) {
                            "present" -> "Mark members present from the All or Absent tab."
                            "absent" -> "All eligible members are already marked present."
                            else -> if (state.filtersActive) {
                                "Try clearing filters or a different search."
                            } else {
                                "Members need an active membership for this date."
                            }
                        },
                        actionLabel = if (state.filtersActive) "Clear filters" else null,
                        onAction = if (state.filtersActive) viewModel::clearFilters else null,
                    )
                }

                else -> {
                    items(data.members, key = { it.memberId }) { member ->
                        AttendanceMemberCard(
                            member = member,
                            timezone = gym.timezone,
                            marking = state.markingMemberId == member.memberId,
                            undoing = state.undoing,
                            onMark = { viewModel.markPresent(member) },
                            onUndo = { viewModel.undoMember(member) },
                            onCorrect = { correctionTarget = member },
                            onOpen = { onOpenMember(member.memberId) },
                        )
                    }
                    if (state.hasMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                if (state.loadingMore) {
                                    CircularProgressIndicator(
                                        color = colors.accent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(22.dp),
                                    )
                                } else {
                                    Text(
                                        text = "Load more members",
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

        val undo = state.undo
        if (undo != null) {
            UndoBanner(
                message = "${undo.memberName} marked present",
                secondsRemaining = undo.secondsRemaining,
                onUndo = viewModel::undoLastMark,
                onDismiss = viewModel::dismissUndo,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }

    if (showShiftDialog) {
        OptionsDialog(
            title = "Filter by shift",
            options = viewModel.shiftOptions,
            selectedValue = state.shiftId,
            onSelect = {
                viewModel.setShift(it)
                showShiftDialog = false
            },
            onDismiss = { showShiftDialog = false },
        )
    }
    if (showGenderDialog) {
        OptionsDialog(
            title = "Filter by gender",
            options = listOf("male" to "Male", "female" to "Female", "other" to "Other"),
            selectedValue = state.gender,
            onSelect = {
                viewModel.setGender(it)
                showGenderDialog = false
            },
            onDismiss = { showGenderDialog = false },
        )
    }
    if (showPlanDialog) {
        OptionsDialog(
            title = "Filter by plan",
            options = viewModel.plans.map { it.id to it.name },
            selectedValue = state.planId,
            onSelect = {
                viewModel.setPlan(it)
                showPlanDialog = false
            },
            onDismiss = { showPlanDialog = false },
        )
    }

    correctionTarget?.let { member ->
        CorrectionDialog(
            member = member,
            submitting = state.correcting,
            onConfirm = { reason ->
                viewModel.correctRemove(member, reason)
                correctionTarget = null
            },
            onDismiss = { correctionTarget = null },
        )
    }
}

@Composable
private fun SummaryChip(label: String, value: Int, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val colors = SquadGymTheme.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = if (highlight) colors.accent else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AttendanceMemberCard(
    member: AttendanceMemberDto,
    timezone: String,
    marking: Boolean,
    undoing: Boolean,
    onMark: () -> Unit,
    onUndo: () -> Unit,
    onCorrect: () -> Unit,
    onOpen: () -> Unit,
) {
    val colors = SquadGymTheme.colors
    GymCard(padding = 16.dp, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(
                photoUrl = member.photoUrl,
                initials = member.initials,
                size = 44.dp,
                highlight = member.isPresent,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.fullName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(member.shift?.name, member.plan?.name, "score ${member.score}")
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (member.isPresent) {
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = colors.success,
                            modifier = Modifier.size(11.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Marked ${DateText.clock(member.markedAt, timezone)}" +
                                if (member.session?.isActive == true) " · in gym" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.success,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))

            when {
                member.isPresent && member.canUndo -> {
                    Column(horizontalAlignment = Alignment.End) {
                        StatusPill("Present", Tone.Success)
                        val remaining = (member.undoSecondsRemaining).coerceAtLeast(0)
                        Text(
                            text = if (undoing) "Undoing…" else "Undo ${DateText.countdown(remaining)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !undoing, onClick = onUndo)
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }

                member.isPresent -> {
                    Column(horizontalAlignment = Alignment.End) {
                        StatusPill("Present", Tone.Success)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = androidx.compose.ui.graphics.Color.Transparent,
                            onClick = onCorrect,
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "Correct attendance",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(4.dp).size(16.dp),
                            )
                        }
                    }
                }

                else -> {
                    Surface(
                        modifier = Modifier
                            .height(34.dp)
                            .clickable(enabled = !marking, onClick = onMark),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (marking) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.surface,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp),
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Mark present",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.surface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CorrectionDialog(
    member: AttendanceMemberDto,
    submitting: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
        title = { Text("Correct attendance", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    text = "The 5-minute undo window has closed. Removing ${member.fullName}'s check-in is an " +
                        "audited correction and is kept in the admin log.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                com.muddassir.squadgym.core.ui.components.GymTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = "Reason",
                    placeholder = "e.g. marked by mistake",
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = reason.trim().length >= 3 && !submitting,
                onClick = { onConfirm(reason.trim()) },
            ) {
                Text(
                    text = if (submitting) "Saving…" else "Remove check-in",
                    color = SquadGymTheme.colors.danger,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
