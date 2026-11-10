package com.muddassir.squadgym.features.members

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.muddassir.squadgym.core.ui.components.EmptyHint
import com.muddassir.squadgym.core.ui.components.ErrorState
import com.muddassir.squadgym.core.ui.components.GymCard
import com.muddassir.squadgym.core.ui.components.GymTextField
import com.muddassir.squadgym.core.ui.components.InfoBanner
import com.muddassir.squadgym.core.ui.components.LoadingState
import com.muddassir.squadgym.core.ui.components.MemberAvatar
import com.muddassir.squadgym.core.ui.components.PrimaryButton
import com.muddassir.squadgym.core.ui.components.ProgressBar
import com.muddassir.squadgym.core.ui.components.ScreenBackground
import com.muddassir.squadgym.core.ui.components.ScreenHeader
import com.muddassir.squadgym.core.ui.components.SecondaryButton
import com.muddassir.squadgym.core.ui.components.SectionHeader
import com.muddassir.squadgym.core.ui.components.SpacerH
import com.muddassir.squadgym.core.ui.components.StatusPill
import com.muddassir.squadgym.core.ui.components.Tone
import com.muddassir.squadgym.core.ui.components.UndoBanner
import com.muddassir.squadgym.core.ui.components.ValueRow
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme
import com.muddassir.squadgym.core.util.DateText
import com.muddassir.squadgym.core.util.NumberText
import com.muddassir.squadgym.data.remote.dto.MemberDto
import com.muddassir.squadgym.data.remote.dto.MembershipDto
import com.muddassir.squadgym.data.remote.dto.PlanDto

@Composable
fun MemberDetailScreen(
    container: AppContainer,
    memberId: String,
    timezone: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val viewModel: MemberDetailViewModel = viewModel(
        key = "member-$memberId",
        factory = vmFactory {
            MemberDetailViewModel(
                memberId = memberId,
                memberRepository = container.memberRepository,
                membershipRepository = container.membershipRepository,
                attendanceRepository = container.attendanceRepository,
                dashboardRepository = container.dashboardRepository,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail = state.detail
    var showRenewDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var archiveReason by remember { mutableStateOf("") }

    ScreenBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    ScreenHeader(
                        title = detail?.member?.fullName ?: "Member",
                        subtitle = detail?.member?.membership?.planName ?: "Profile",
                        onBack = onBack,
                        actions = {
                            Surface(
                                modifier = Modifier.size(38.dp).clickable { onEdit(memberId) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "Edit member",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                    )
                }

                when {
                    state.loading && detail == null -> item { LoadingState(message = "Loading member") }
                    detail == null -> item {
                        ErrorState(message = state.error ?: "Couldn't load this member.", onRetry = viewModel::load)
                    }

                    else -> {
                        item { ProfileCard(member = detail.member, today = detail.member.joinedOn) }
                        item {
                            MembershipCard(
                                member = detail.member,
                                membership = detail.member.membership?.let {
                                    MembershipDto(
                                        id = it.id,
                                        planId = it.planId,
                                        planName = it.planName ?: "",
                                        startDate = it.startDate,
                                        endDate = it.endDate,
                                        status = it.status,
                                        daysRemaining = it.daysRemaining,
                                    )
                                },
                                onRenew = { showRenewDialog = true },
                            )
                        }
                        item { StatsCard(state = state, timezone = timezone) }
                        item {
                            GymCard {
                                SectionHeader(
                                    title = "Recent attendance",
                                    subtitle = "Last 60 days · ${detail.attendance.size} visits",
                                )
                                SpacerH(8.dp)
                                if (detail.attendance.isEmpty()) {
                                    EmptyHint("No visits recorded yet.")
                                } else {
                                    detail.attendance.take(8).forEach { row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = SquadGymTheme.colors.success,
                                                modifier = Modifier.size(15.dp),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = DateText.full(row.date),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                text = DateText.clock(row.markedAt, timezone),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            GymCard {
                                SectionHeader(title = "Membership history", subtitle = "${detail.membershipHistory.size} terms")
                                SpacerH(8.dp)
                                detail.membershipHistory.forEach { term ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = term.planName,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = "${DateText.short(term.startDate)} – ${DateText.short(term.endDate)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        StatusPill(
                                            text = term.rowStatus.replaceFirstChar { it.uppercase() },
                                            tone = when (term.rowStatus) {
                                                "active" -> Tone.Success
                                                "reversed" -> Tone.Danger
                                                else -> Tone.Neutral
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            if (detail.member.isArchived) {
                                SecondaryButton(
                                    text = "Restore member",
                                    onClick = viewModel::restore,
                                    icon = Icons.Filled.Restore,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                SecondaryButton(
                                    text = "Archive member",
                                    onClick = { showArchiveDialog = true },
                                    icon = Icons.Filled.Archive,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
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

            state.undo?.let { undo ->
                UndoBanner(
                    message = "Renewal saved for ${undo.memberName}",
                    secondsRemaining = undo.secondsRemaining,
                    onUndo = viewModel::undoRenewal,
                    onDismiss = viewModel::dismissUndo,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }

    if (showRenewDialog) {
        RenewalDialog(
            plans = state.plans,
            preview = state.renewalPreview,
            previewLoading = state.previewLoading,
            renewing = state.renewing,
            currentEndDate = detail?.member?.membership?.endDate,
            onSelectPlan = viewModel::previewRenewal,
            onConfirm = { planId -> viewModel.renew(planId) },
            onDismiss = {
                showRenewDialog = false
                viewModel.clearPreview()
            },
        )
    }

    if (showArchiveDialog) {
        ArchiveDialog(
            memberName = detail?.member?.fullName ?: "this member",
            reason = archiveReason,
            onReasonChange = { archiveReason = it },
            working = state.working,
            onConfirm = {
                viewModel.archive(archiveReason.ifBlank { null })
                showArchiveDialog = false
                archiveReason = ""
            },
            onDismiss = { showArchiveDialog = false },
        )
    }
}

@Composable
private fun ProfileCard(member: MemberDto, today: String) {
    GymCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(photoUrl = member.photoUrl, initials = member.initials, size = 58.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.fullName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = member.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(
                        text = member.membershipStatus.replaceFirstChar { it.uppercase() },
                        tone = when (member.membershipStatus) {
                            "active" -> Tone.Success
                            "expiring" -> Tone.Warning
                            "expired" -> Tone.Danger
                            else -> Tone.Neutral
                        },
                    )
                    member.shift?.let { StatusPill(text = it.name, tone = Tone.Neutral, showDot = false) }
                }
            }
        }
        SpacerH(12.dp)
        ValueRow(label = "Mobile", value = member.phone ?: "—")
        ValueRow(label = "Gender", value = member.gender?.replaceFirstChar { it.uppercase() } ?: "—")
        ValueRow(label = "Joined", value = DateText.full(member.joinedOn))
        ValueRow(label = "Emergency contact", value = member.emergencyContactName ?: "—")
        if (!member.address.isNullOrBlank()) {
            ValueRow(label = "Address", value = member.address)
        }
    }
}

@Composable
private fun MembershipCard(member: MemberDto, membership: MembershipDto?, onRenew: () -> Unit) {
    val colors = SquadGymTheme.colors
    GymCard {
        SectionHeader(
            title = "Membership",
            subtitle = member.membership?.planName ?: "No active term",
            trailing = {
                StatusPill(
                    text = member.membershipStatus.replaceFirstChar { it.uppercase() },
                    tone = when (member.membershipStatus) {
                        "active" -> Tone.Success
                        "expiring" -> Tone.Warning
                        "expired" -> Tone.Danger
                        else -> Tone.Neutral
                    },
                )
            },
        )
        SpacerH(10.dp)
        if (membership == null) {
            EmptyHint("This member has no membership term yet.")
        } else {
            ValueRow(label = "Start date", value = DateText.full(membership.startDate))
            ValueRow(label = "Expires", value = DateText.full(membership.endDate))
            ValueRow(
                label = "Days remaining",
                value = if (membership.daysRemaining >= 0) "${membership.daysRemaining} days" else "Expired",
                valueColor = if (membership.daysRemaining < 0) colors.danger else colors.success,
                emphasize = true,
            )
            SpacerH(8.dp)
            val totalDays = runCatching {
                java.time.temporal.ChronoUnit.DAYS.between(
                    java.time.LocalDate.parse(membership.startDate),
                    java.time.LocalDate.parse(membership.endDate),
                ).toFloat() + 1f
            }.getOrDefault(30f)
            val progress = (membership.daysRemaining / totalDays).coerceIn(0f, 1f)
            ProgressBar(progress = progress)
        }
        SpacerH(14.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clickable(onClick = onRenew),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Renew membership",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.surface,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsCard(state: MemberDetailUiState, timezone: String) {
    val extras = state.detail?.extras
    GymCard {
        SectionHeader(title = "Performance", subtitle = "One point per gym day")
        SpacerH(10.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("This month", extras?.monthlyScore ?: 0, Icons.Filled.CalendarMonth, Modifier.weight(1f))
            MiniStat("This week", extras?.weeklyScore ?: 0, Icons.Filled.Timeline, Modifier.weight(1f))
            MiniStat("Lifetime", extras?.lifetimeScore ?: 0, Icons.Filled.EmojiEvents, Modifier.weight(1f))
        }
        SpacerH(10.dp)
        ValueRow(
            label = "Last visit",
            value = extras?.lastAttendanceAt?.let { DateText.dateTime(it, timezone) } ?: "Never",
        )
        if (extras?.inGymNowUntil != null) {
            ValueRow(label = "In gym until", value = DateText.clock(extras.inGymNowUntil, timezone))
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: Int,
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
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RenewalDialog(
    plans: List<PlanDto>,
    preview: com.muddassir.squadgym.data.remote.dto.RenewalPreviewDto?,
    previewLoading: Boolean,
    renewing: Boolean,
    currentEndDate: String?,
    onSelectPlan: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = if (preview == null) "Renew membership" else preview.planName,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                if (preview == null) {
                    Text(
                        text = "Choose a plan. Renewing before expiry keeps the remaining days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (previewLoading) {
                        LoadingState(message = "Calculating dates")
                    }
                    plans.filter { it.isActive }.forEach { plan ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectPlan(plan.id) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(plan.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${plan.durationDays} days · ${plan.durationLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = NumberText.money(plan.priceCents, plan.currency),
                                style = MaterialTheme.typography.labelMedium,
                                color = SquadGymTheme.colors.accent,
                            )
                        }
                    }
                } else {
                    ValueRow(label = "Current expiry", value = currentEndDate?.let { DateText.full(it) } ?: "—")
                    ValueRow(label = "New start", value = DateText.full(preview.newStartDate))
                    ValueRow(label = "New expiry", value = DateText.full(preview.newEndDate), emphasize = true)
                    ValueRow(
                        label = "Duration",
                        value = NumberText.plural(preview.durationDays, "day"),
                    )
                    if (preview.extendedFromExisting) {
                        Spacer(Modifier.height(8.dp))
                        InfoBanner(
                            text = "Remaining days are preserved — the new term starts the day after the current one ends.",
                            icon = Icons.Filled.CheckCircle,
                            tone = Tone.Success,
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        InfoBanner(
                            text = "This membership has expired, so the new term starts today.",
                            icon = Icons.Filled.CalendarMonth,
                            tone = Tone.Warning,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (preview != null) {
                TextButton(onClick = { onConfirm(preview.planId) }, enabled = !renewing) {
                    Text(
                        text = if (renewing) "Saving…" else "Confirm renewal",
                        color = SquadGymTheme.colors.accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
private fun ArchiveDialog(
    memberName: String,
    reason: String,
    onReasonChange: (String) -> Unit,
    working: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(22.dp),
        title = { Text("Archive $memberName?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    text = "Archived members are hidden from the active list but keep all attendance and membership " +
                        "history. You can restore them at any time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                GymTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = "Reason (optional)",
                    placeholder = "e.g. moved away",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !working) {
                Text("Archive", color = SquadGymTheme.colors.danger, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
