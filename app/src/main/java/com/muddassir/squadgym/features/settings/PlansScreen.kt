package com.muddassir.squadgym.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import com.muddassir.squadgym.core.ui.components.ConfirmDialog
import com.muddassir.squadgym.core.ui.components.EmptyState
import com.muddassir.squadgym.core.ui.components.ErrorState
import com.muddassir.squadgym.core.ui.components.GymCard
import com.muddassir.squadgym.core.ui.components.GymTextField
import com.muddassir.squadgym.core.ui.components.InfoBanner
import com.muddassir.squadgym.core.ui.components.ListSkeleton
import com.muddassir.squadgym.core.ui.components.OfflineBanner
import com.muddassir.squadgym.core.ui.components.PrimaryButton
import com.muddassir.squadgym.core.ui.components.ScreenBackground
import com.muddassir.squadgym.core.ui.components.ScreenHeader
import com.muddassir.squadgym.core.ui.components.StatusPill
import com.muddassir.squadgym.core.ui.components.Tone
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme
import com.muddassir.squadgym.core.util.NumberText
import com.muddassir.squadgym.data.remote.dto.PlanDto
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PlansScreen(
    container: AppContainer,
    canManage: Boolean,
    onBack: () -> Unit,
) {
    val viewModel: PlansViewModel = viewModel(
        factory = vmFactory { PlansViewModel(container.dashboardRepository, container.adminRepository) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf<PlanEditorTarget?>(null) }
    var removing by remember { mutableStateOf<PlanDto?>(null) }

    ScreenBackground {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScreenHeader(
                    title = "Subscription plans",
                    subtitle = if (canManage) "What members can sign up for" else "Read only",
                    onBack = onBack,
                )
            }

            if (state.cached) {
                item { OfflineBanner(cachedAt = null) }
            }

            if (state.message != null) {
                item {
                    InfoBanner(
                        text = state.message!!,
                        icon = Icons.Filled.Info,
                        tone = Tone.Success,
                        actionLabel = "Dismiss",
                        onAction = viewModel::consumeMessage,
                    )
                }
            }

            when {
                state.loading -> item { ListSkeleton(rows = 3) }

                state.error != null && state.plans.isEmpty() -> item {
                    ErrorState(message = state.error ?: "Couldn't load plans.", onRetry = viewModel::load)
                }

                else -> {
                    item {
                        InfoBanner(
                            text = if (canManage) {
                                "Removing a plan hides it from new memberships. Members already on it keep their dates."
                            } else {
                                "Only staff with membership permissions can change plans."
                            },
                            icon = Icons.Filled.Info,
                            tone = Tone.Neutral,
                        )
                    }

                    if (state.plans.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Filled.CardMembership,
                                title = "No plans yet",
                                message = "Add a plan before selling or renewing a membership.",
                            )
                        }
                    } else {
                        items(state.plans, key = { it.id }) { plan ->
                            PlanRow(
                                plan = plan,
                                canManage = canManage,
                                busy = state.saving,
                                onEdit = { editor = PlanEditorTarget(plan) },
                                onToggle = { active -> viewModel.setActive(plan.id, active) },
                                onRemove = { removing = plan },
                            )
                        }
                    }

                    if (canManage) {
                        item {
                            PrimaryButton(
                                text = "New plan",
                                icon = Icons.Filled.Add,
                                onClick = { editor = PlanEditorTarget(null) },
                            )
                        }
                    }
                }
            }
        }
    }

    editor?.let { target ->
        PlanEditorDialog(
            initial = target.plan,
            saving = state.saving,
            onSave = { name, daysText, priceText, description ->
                val days = daysText.toIntOrNull()
                val rupees = priceText.toDoubleOrNull() ?: 0.0
                if (days != null) {
                    viewModel.save(
                        planId = target.plan?.id,
                        name = name,
                        days = days,
                        priceCents = (rupees * 100).roundToInt(),
                        description = description.ifBlank { null },
                    )
                    editor = null
                }
            },
            onDismiss = { editor = null },
        )
    }

    removing?.let { plan ->
        ConfirmDialog(
            title = "Remove ${plan.name}?",
            message = "It will no longer be offered. Members already on this plan keep their dates, and you can restore it later.",
            confirmLabel = "Remove",
            destructive = true,
            loading = state.saving,
            onConfirm = {
                viewModel.setActive(plan.id, false)
                removing = null
            },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun PlanRow(
    plan: PlanDto,
    canManage: Boolean,
    busy: Boolean,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = SquadGymTheme.colors
    GymCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${plan.durationLabel} · ${NumberText.money(plan.priceCents, plan.currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(
                text = if (plan.isActive) "Active" else "Hidden",
                tone = if (plan.isActive) Tone.Success else Tone.Neutral,
            )
        }

        plan.description?.takeIf { it.isNotBlank() }?.let { note ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (canManage) {
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onEdit, enabled = !busy) {
                    Text("Edit", color = colors.accent, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = { if (plan.isActive) onRemove() else onToggle(true) },
                    enabled = !busy,
                ) {
                    Text(
                        text = if (plan.isActive) "Remove" else "Restore",
                        color = if (plan.isActive) colors.danger else colors.accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanEditorDialog(
    initial: PlanDto?,
    saving: Boolean,
    onSave: (name: String, days: String, price: String, description: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var days by remember(initial?.id) { mutableStateOf((initial?.durationDays ?: 30).toString()) }
    var price by remember(initial?.id) { mutableStateOf(initial?.let { priceText(it.priceCents) } ?: "0") }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }

    val duration = days.toIntOrNull()
    val valid = name.trim().length >= 2 &&
        duration != null && duration in 1..3650 &&
        price.toDoubleOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = if (initial == null) "New plan" else "Edit plan",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                GymTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Plan name",
                    placeholder = "Monthly",
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GymTextField(
                        value = days,
                        onValueChange = { days = it.filter(Char::isDigit).take(4) },
                        label = "Duration (days)",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    GymTextField(
                        value = price,
                        onValueChange = { value -> price = value.filter { it.isDigit() || it == '.' }.take(8) },
                        label = "Price (₹)",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                GymTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Short note (optional)",
                    placeholder = "What it includes",
                    singleLine = false,
                    minLines = 2,
                    imeAction = ImeAction.Done,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), days, price, description.trim()) },
                enabled = valid && !saving,
            ) {
                Text(
                    text = if (saving) "Saving…" else "Save",
                    color = SquadGymTheme.colors.accent,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

private data class PlanEditorTarget(val plan: PlanDto?)

private fun priceText(cents: Int): String =
    if (cents % 100 == 0) (cents / 100).toString() else String.format(Locale.US, "%.2f", cents / 100.0)
