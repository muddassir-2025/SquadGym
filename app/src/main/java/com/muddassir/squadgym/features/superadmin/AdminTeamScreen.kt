package com.muddassir.squadgym.features.superadmin

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muddassir.squadgym.core.di.AppContainer
import com.muddassir.squadgym.core.di.vmFactory
import com.muddassir.squadgym.core.ui.components.EmptyState
import com.muddassir.squadgym.core.ui.components.ErrorState
import com.muddassir.squadgym.core.ui.components.FilterChip
import com.muddassir.squadgym.core.ui.components.GymCard
import com.muddassir.squadgym.core.ui.components.GymTextField
import com.muddassir.squadgym.core.ui.components.InfoBanner
import com.muddassir.squadgym.core.ui.components.ListSkeleton
import com.muddassir.squadgym.core.ui.components.LoadingState
import com.muddassir.squadgym.core.ui.components.MemberAvatar
import com.muddassir.squadgym.core.ui.components.PrimaryButton
import com.muddassir.squadgym.core.ui.components.ScreenBackground
import com.muddassir.squadgym.core.ui.components.ScreenHeader
import com.muddassir.squadgym.core.ui.components.SecondaryButton
import com.muddassir.squadgym.core.ui.components.SpacerH
import com.muddassir.squadgym.core.ui.components.StatusPill
import com.muddassir.squadgym.core.ui.components.Tone
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme
import com.muddassir.squadgym.core.util.DateText
import com.muddassir.squadgym.data.remote.dto.AdminDto

private val PERMISSION_LABELS = mapOf(
    "dashboard.view" to "View dashboard",
    "members.view" to "View members",
    "members.manage" to "Add / edit members",
    "attendance.view" to "View attendance",
    "attendance.manage" to "Mark attendance",
    "attendance.correct" to "Correct attendance",
    "memberships.view" to "View memberships",
    "memberships.manage" to "Renew memberships",
    "leaderboard.view" to "View leaderboard",
)

@Composable
fun AdminTeamScreen(
    container: AppContainer,
    onAddAdmin: () -> Unit,
) {
    val viewModel: AdminTeamViewModel = viewModel(
        factory = vmFactory { AdminTeamViewModel(container.adminRepository) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AdminDto?>(null) }

    ScreenBackground {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    title = "Admin team",
                    subtitle = "${state.admins.size} staff accounts",
                    actions = {
                        Surface(
                            modifier = Modifier.size(40.dp).clickable(onClick = onAddAdmin),
                            shape = RoundedCornerShape(13.dp),
                            color = SquadGymTheme.colors.accent,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.PersonAdd,
                                    contentDescription = "Create admin",
                                    tint = SquadGymTheme.colors.accentInk,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    },
                )
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
                state.loading -> item { ListSkeleton(rows = 4) }
                state.admins.isEmpty() -> item {
                    ErrorState(message = state.error ?: "No admins found.", onRetry = viewModel::load)
                }
                else -> items(state.admins, key = { it.id }) { admin ->
                    GymCard(padding = 12.dp, onClick = { editing = admin }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MemberAvatar(photoUrl = admin.photoUrl, initials = admin.initials, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = admin.fullName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = admin.email,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (admin.lastLoginAt != null) {
                                        "Last login ${DateText.short(admin.lastLoginAt!!.take(10))}"
                                    } else {
                                        "Never signed in"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                StatusPill(
                                    text = if (admin.role == "super_admin") "Super Admin" else "Admin",
                                    tone = if (admin.role == "super_admin") Tone.Accent else Tone.Neutral,
                                    showDot = false,
                                )
                                Spacer(Modifier.height(4.dp))
                                StatusPill(
                                    text = admin.status.replaceFirstChar { it.uppercase() },
                                    tone = when (admin.status) {
                                        "active" -> Tone.Success
                                        "inactive" -> Tone.Danger
                                        else -> Tone.Warning
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { admin ->
        EditAdminDialog(
            admin = admin,
            saving = state.saving,
            onSave = { name, phone, permissions ->
                viewModel.updateAdmin(admin, name, phone, permissions)
                editing = null
            },
            onToggleStatus = {
                viewModel.setStatus(admin, if (admin.status == "active") "inactive" else "active")
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun EditAdminDialog(
    admin: AdminDto,
    saving: Boolean,
    onSave: (String, String?, List<String>) -> Unit,
    onToggleStatus: () -> Unit,
    onDismiss: () -> Unit,
) {
    var fullName by remember { mutableStateOf(admin.fullName) }
    var phone by remember { mutableStateOf(admin.phone.orEmpty()) }
    var permissions by remember { mutableStateOf(admin.permissions.toSet()) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(22.dp),
        title = { Text(admin.fullName, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                GymTextField(value = fullName, onValueChange = { fullName = it }, label = "Full name")
                Spacer(Modifier.height(10.dp))
                GymTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = "Phone",
                    keyboardType = KeyboardType.Phone,
                )
                if (admin.role != "super_admin") {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Permissions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    PERMISSION_LABELS.forEach { (key, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilterChip(
                                label = label,
                                selected = permissions.contains(key),
                                onClick = {
                                    permissions = if (permissions.contains(key)) {
                                        permissions - key
                                    } else {
                                        permissions + key
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                SecondaryButton(
                    text = if (admin.status == "active") "Deactivate account" else "Activate account",
                    onClick = onToggleStatus,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = !saving,
                onClick = { onSave(fullName.trim(), phone.trim().ifBlank { null }, permissions.toList()) },
            ) {
                Text(
                    text = if (saving) "Saving…" else "Save",
                    color = SquadGymTheme.colors.accent,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
fun AdminFormScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onCreated: () -> Unit,
) {
    val viewModel: AdminFormViewModel = viewModel(
        factory = vmFactory { AdminFormViewModel(container.adminRepository) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdId) {
        if (state.createdId != null) onCreated()
    }

    ScreenBackground {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            ScreenHeader(
                title = "New admin",
                subtitle = "Staff access to the gym console",
                onBack = onBack,
            )
            SpacerH(16.dp)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    GymCard {
                        GymTextField(
                            value = state.fullName,
                            onValueChange = viewModel::onFullName,
                            label = "Full name",
                            error = state.fieldErrors["fullName"],
                            imeAction = ImeAction.Next,
                        )
                        SpacerH(10.dp)
                        GymTextField(
                            value = state.email,
                            onValueChange = viewModel::onEmail,
                            label = "Email address",
                            error = state.fieldErrors["email"],
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        )
                        SpacerH(10.dp)
                        GymTextField(
                            value = state.phone,
                            onValueChange = viewModel::onPhone,
                            label = "Phone (optional)",
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next,
                        )
                        SpacerH(10.dp)
                        GymTextField(
                            value = state.password,
                            onValueChange = viewModel::onPassword,
                            label = "Temporary password (optional)",
                            placeholder = "Leave blank to send an invitation",
                            error = state.fieldErrors["initialPassword"],
                            isPassword = true,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        )
                        SpacerH(10.dp)
                        InfoBanner(
                            text = "New admins get the standard permissions. You can fine-tune them from the admin list.",
                            icon = Icons.Filled.AdminPanelSettings,
                            tone = Tone.Neutral,
                        )
                    }
                }
                if (state.invitationToken != null) {
                    item {
                        InfoBanner(
                            text = "Invitation code: ${state.invitationToken}",
                            icon = Icons.Filled.CheckCircle,
                            tone = Tone.Success,
                        )
                    }
                }
                if (state.error != null) {
                    item { InfoBanner(text = state.error!!, icon = Icons.Filled.AdminPanelSettings, tone = Tone.Danger) }
                }
                item {
                    PrimaryButton(
                        text = "Create admin",
                        onClick = viewModel::submit,
                        loading = state.submitting,
                        enabled = !state.submitting,
                    )
                }
            }
        }
    }
}
