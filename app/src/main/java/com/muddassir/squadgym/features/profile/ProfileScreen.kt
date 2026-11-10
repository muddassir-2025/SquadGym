package com.muddassir.squadgym.features.profile

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muddassir.squadgym.BuildConfig
import com.muddassir.squadgym.core.di.AppContainer
import com.muddassir.squadgym.core.ui.components.ConfirmDialog
import com.muddassir.squadgym.core.ui.components.GymCard
import com.muddassir.squadgym.core.ui.components.MemberAvatar
import com.muddassir.squadgym.core.ui.components.ScreenBackground
import com.muddassir.squadgym.core.ui.components.ScreenHeader
import com.muddassir.squadgym.core.ui.components.SecondaryButton
import com.muddassir.squadgym.core.ui.components.SectionHeader
import com.muddassir.squadgym.core.ui.components.SegmentedControl
import com.muddassir.squadgym.core.ui.components.SpacerH
import com.muddassir.squadgym.core.ui.components.StatusPill
import com.muddassir.squadgym.core.ui.components.Tone
import com.muddassir.squadgym.core.ui.components.ValueRow
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme
import com.muddassir.squadgym.data.local.SettingsStore
import com.muddassir.squadgym.data.remote.dto.GymDto
import com.muddassir.squadgym.data.remote.dto.UserDto
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    container: AppContainer,
    user: UserDto,
    gym: GymDto,
    onAdminTeam: () -> Unit = {},
    onAuditLog: () -> Unit = {},
    onGymSettings: () -> Unit = {},
) {
    val themeMode by container.settingsStore.themeMode
        .collectAsStateWithLifecycle(initialValue = SettingsStore.ThemeMode.SYSTEM)
    val scope = rememberCoroutineScope()
    var showSignOut by remember { androidx.compose.runtime.mutableStateOf(false) }
    val colors = SquadGymTheme.colors

    ScreenBackground {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ScreenHeader(title = "Profile", subtitle = "Account and preferences") }

            item {
                GymCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MemberAvatar(
                            photoUrl = user.photoUrl,
                            initials = user.initials,
                            size = 62.dp,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.fullName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = user.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(6.dp))
                            StatusPill(
                                text = when (user.role) {
                                    "super_admin" -> "Super Admin"
                                    "admin" -> "Admin"
                                    else -> "Member"
                                },
                                tone = Tone.Accent,
                                showDot = false,
                            )
                        }
                    }
                    SpacerH(12.dp)
                    HorizontalDivider(color = colors.border)
                    Spacer(Modifier.height(6.dp))
                    ValueRow(label = "Gym", value = gym.name)
                    ValueRow(label = "Timezone", value = gym.timezone)
                    ValueRow(label = "Mobile", value = user.phone ?: "—")
                }
            }

            item {
                GymCard {
                    SectionHeader(title = "Appearance", subtitle = "Match your gym's lighting")
                    SpacerH(12.dp)
                    SegmentedControl(
                        options = listOf("System", "Light", "Dark"),
                        selectedIndex = when (themeMode) {
                            SettingsStore.ThemeMode.LIGHT -> 1
                            SettingsStore.ThemeMode.DARK -> 2
                            else -> 0
                        },
                        onSelect = { index ->
                            scope.launch {
                                container.settingsStore.setThemeMode(
                                    when (index) {
                                        1 -> SettingsStore.ThemeMode.LIGHT
                                        2 -> SettingsStore.ThemeMode.DARK
                                        else -> SettingsStore.ThemeMode.SYSTEM
                                    },
                                )
                            }
                        },
                    )
                    SpacerH(10.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("System", Icons.Filled.Smartphone, themeMode == SettingsStore.ThemeMode.SYSTEM)
                        Pill("Light", Icons.Filled.LightMode, themeMode == SettingsStore.ThemeMode.LIGHT)
                        Pill("Dark", Icons.Filled.DarkMode, themeMode == SettingsStore.ThemeMode.DARK)
                    }
                }
            }

            if (user.isSuperAdmin) {
                item {
                    GymCard(padding = 0.dp) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            SectionHeader(title = "Administration", subtitle = "Super Admin tools")
                        }
                        AdminRow(Icons.Filled.Groups, "Admin team", "Create, edit and deactivate admins", onAdminTeam)
                        AdminRow(Icons.Filled.Receipt, "Activity log", "Every audited administrative action", onAuditLog)
                        AdminRow(Icons.Filled.Settings, "Gym settings", "Plans, shifts and operating days", onGymSettings)
                    }
                }
            }

            if (user.isStaff && !user.isSuperAdmin) {
                item {
                    GymCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Your permissions: ${user.permissions.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = user.permissions.joinToString(" · ") { it.replace('.', ' ') },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                GymCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SquadGym v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Manual attendance · API ${BuildConfig.API_BASE_URL}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                SecondaryButton(
                    text = "Sign out",
                    onClick = { showSignOut = true },
                    icon = Icons.Filled.Logout,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Text(
                    text = "Session tokens are stored in the Android Keystore and cleared on sign-out.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
        }
    }

    if (showSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            message = "Cached member data and images are cleared from this device.",
            confirmLabel = "Sign out",
            destructive = true,
            onConfirm = {
                showSignOut = false
                scope.launch { container.authRepository.signOut() }
            },
            onDismiss = { showSignOut = false },
        )
    }
}

@Composable
private fun AdminRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = SquadGymTheme.colors.accent,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Pill(label: String, icon: ImageVector, active: Boolean) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = if (active) SquadGymTheme.colors.accent.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (active) SquadGymTheme.colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) SquadGymTheme.colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
