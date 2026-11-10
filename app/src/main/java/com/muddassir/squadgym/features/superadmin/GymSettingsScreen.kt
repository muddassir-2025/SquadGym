package com.muddassir.squadgym.features.superadmin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muddassir.squadgym.core.di.AppContainer
import com.muddassir.squadgym.core.di.vmFactory
import com.muddassir.squadgym.core.ui.components.ErrorState
import com.muddassir.squadgym.core.ui.components.GymCard
import com.muddassir.squadgym.core.ui.components.GymTextField
import com.muddassir.squadgym.core.ui.components.InfoBanner
import com.muddassir.squadgym.core.ui.components.ListSkeleton
import com.muddassir.squadgym.core.ui.components.PrimaryButton
import com.muddassir.squadgym.core.ui.components.ScreenBackground
import com.muddassir.squadgym.core.ui.components.ScreenHeader
import com.muddassir.squadgym.core.ui.components.SectionHeader
import com.muddassir.squadgym.core.ui.components.SpacerH
import com.muddassir.squadgym.core.ui.components.StatusPill
import com.muddassir.squadgym.core.ui.components.Tone
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme
import com.muddassir.squadgym.core.util.NumberText

@Composable
fun GymSettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: GymSettingsViewModel = viewModel(
        factory = vmFactory { GymSettingsViewModel(container.adminRepository) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showShiftDialog by remember { mutableStateOf(false) }
    var showPlanDialog by remember { mutableStateOf(false) }

    ScreenBackground {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScreenHeader(
                    title = "Gym settings",
                    subtitle = state.config?.gym?.timezone ?: "Configuration",
                    onBack = onBack,
                )
            }

            if (state.message != null) {
                item {
                    InfoBanner(
                        text = state.message!!,
                        icon = Icons.Filled.Add,
                        tone = Tone.Success,
                        actionLabel = "Dismiss",
                        onAction = viewModel::consumeMessage,
                    )
                }
            }

            if (state.loading) {
                item { ListSkeleton(rows = 6) }
            } else if (state.config == null) {
                item { ErrorState(message = state.error ?: "Couldn't load settings.", onRetry = viewModel::load) }
            } else {
                item {
                    GymCard {
                        SectionHeader(title = "Gym profile", subtitle = "Name and reporting timezone")
                        SpacerH(10.dp)
                        GymTextField(
                            value = state.name,
                            onValueChange = viewModel::onName,
                            label = "Gym name",
                            imeAction = ImeAction.Next,
                        )
                        SpacerH(10.dp)
                        GymTextField(
                            value = state.timezone,
                            onValueChange = viewModel::onTimezone,
                            label = "Timezone (IANA)",
                            placeholder = "Asia/Kolkata",
                            imeAction = ImeAction.Next,
                        )
                        SpacerH(10.dp)
                        InfoBanner(
                            text = "All attendance days, leaderboard periods and expiry dates use this timezone.",
                            icon = Icons.Filled.Add,
                            tone = Tone.Neutral,
                        )
                    }
                }

                item {
                    GymCard {
                        SectionHeader(title = "Business rules", subtitle = "Applied by the backend")
                        SpacerH(10.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GymTextField(
                                value = state.expiringSoonDays,
                                onValueChange = viewModel::onExpiringDays,
                                label = "Expiring soon (days)",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                            )
                            GymTextField(
                                value = state.archiveAfterDays,
                                onValueChange = viewModel::onArchiveDays,
                                label = "Archive after (days)",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        SpacerH(10.dp)
                        GymTextField(
                            value = state.inGymWindowMinutes,
                            onValueChange = viewModel::onWindowMinutes,
                            label = "In Gym Now window (minutes)",
                            keyboardType = KeyboardType.Number,
                        )
                        SpacerH(10.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GymTextField(
                                value = state.attendanceUndoSeconds,
                                onValueChange = viewModel::onAttendanceUndo,
                                label = "Attendance undo (s)",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                            )
                            GymTextField(
                                value = state.renewalUndoSeconds,
                                onValueChange = viewModel::onRenewalUndo,
                                label = "Renewal undo (s)",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        SpacerH(14.dp)
                        PrimaryButton(
                            text = "Save settings",
                            onClick = viewModel::saveSettings,
                            loading = state.saving,
                            enabled = !state.saving,
                        )
                    }
                }

                item {
                    GymCard {
                        SectionHeader(title = "Operating days", subtitle = "Closed days are excluded from attendance and streaks")
                        SpacerH(8.dp)
                        state.config!!.operatingDays.forEach { day ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = day.dayLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${day.opensAt}–${day.closesAt}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(10.dp))
                                Switch(
                                    checked = day.isOpen,
                                    onCheckedChange = { viewModel.toggleDay(day.dayOfWeek, it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SquadGymTheme.colors.accentInk,
                                        checkedTrackColor = SquadGymTheme.colors.accent,
                                    ),
                                )
                            }
                        }
                    }
                }

                item {
                    GymCard {
                        SectionHeader(
                            title = "Shifts",
                            subtitle = "Morning, Evening or custom labels",
                            trailing = {
                                TextButton(onClick = { showShiftDialog = true }) {
                                    Text("Add", color = SquadGymTheme.colors.accent, fontWeight = FontWeight.Bold)
                                }
                            },
                        )
                        SpacerH(4.dp)
                        state.config!!.shifts.forEach { shift ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = shift.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "${shift.startTime} – ${shift.endTime}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = shift.isActive,
                                    onCheckedChange = { viewModel.toggleShift(shift.id, it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SquadGymTheme.colors.accentInk,
                                        checkedTrackColor = SquadGymTheme.colors.accent,
                                    ),
                                )
                            }
                        }
                    }
                }

                item {
                    GymCard {
                        SectionHeader(
                            title = "Membership plans",
                            subtitle = "Durations are configured, never hardcoded",
                            trailing = {
                                TextButton(onClick = { showPlanDialog = true }) {
                                    Text("Add", color = SquadGymTheme.colors.accent, fontWeight = FontWeight.Bold)
                                }
                            },
                        )
                        SpacerH(4.dp)
                        state.config!!.plans.forEach { plan ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = plan.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "${plan.durationDays} days · ${NumberText.money(plan.priceCents, plan.currency)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                StatusPill(
                                    text = if (plan.isActive) "Active" else "Hidden",
                                    tone = if (plan.isActive) Tone.Success else Tone.Neutral,
                                )
                                Spacer(Modifier.width(6.dp))
                                Switch(
                                    checked = plan.isActive,
                                    onCheckedChange = { viewModel.togglePlan(plan.id, it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SquadGymTheme.colors.accentInk,
                                        checkedTrackColor = SquadGymTheme.colors.accent,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showShiftDialog) {
        CreateShiftDialog(
            onCreate = { name, start, end ->
                viewModel.createShift(name, start, end)
                showShiftDialog = false
            },
            onDismiss = { showShiftDialog = false },
        )
    }
    if (showPlanDialog) {
        CreatePlanDialog(
            onCreate = { name, days, price ->
                viewModel.createPlan(name, days, price)
                showPlanDialog = false
            },
            onDismiss = { showPlanDialog = false },
        )
    }
}

@Composable
private fun CreateShiftDialog(onCreate: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("06:00") }
    var end by remember { mutableStateOf("12:00") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(22.dp),
        title = { Text("New shift", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                GymTextField(value = name, onValueChange = { name = it }, label = "Shift name", placeholder = "Night")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GymTextField(
                        value = start,
                        onValueChange = { start = it },
                        label = "Start (HH:MM)",
                        modifier = Modifier.weight(1f),
                    )
                    GymTextField(
                        value = end,
                        onValueChange = { end = it },
                        label = "End (HH:MM)",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim(), start.trim(), end.trim()) }, enabled = name.isNotBlank()) {
                Text("Create", color = SquadGymTheme.colors.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
    )
}

@Composable
private fun CreatePlanDialog(onCreate: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("30") }
    var price by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(22.dp),
        title = { Text("New plan", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                GymTextField(value = name, onValueChange = { name = it }, label = "Plan name", placeholder = "Monthly")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GymTextField(
                        value = days,
                        onValueChange = { days = it.filter(Char::isDigit) },
                        label = "Duration (days)",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    GymTextField(
                        value = price,
                        onValueChange = { price = it.filter(Char::isDigit) },
                        label = "Price (cents)",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim(), days, price) }, enabled = name.isNotBlank()) {
                Text("Create", color = SquadGymTheme.colors.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
    )
}
