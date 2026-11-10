package com.muddassir.squadgym.features.members

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muddassir.squadgym.core.di.AppContainer
import com.muddassir.squadgym.core.di.vmFactory
import com.muddassir.squadgym.core.ui.components.FilterDropdown
import com.muddassir.squadgym.core.ui.components.GymCard
import com.muddassir.squadgym.core.ui.components.GymTextField
import com.muddassir.squadgym.core.ui.components.InfoBanner
import com.muddassir.squadgym.core.ui.components.LoadingState
import com.muddassir.squadgym.core.ui.components.MemberAvatar
import com.muddassir.squadgym.core.ui.components.OptionsDialog
import com.muddassir.squadgym.core.ui.components.PrimaryButton
import com.muddassir.squadgym.core.ui.components.ScreenBackground
import com.muddassir.squadgym.core.ui.components.ScreenHeader
import com.muddassir.squadgym.core.ui.components.SectionHeader
import com.muddassir.squadgym.core.ui.components.SpacerH
import com.muddassir.squadgym.core.ui.components.Tone
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme
import com.muddassir.squadgym.core.util.DateText
import com.muddassir.squadgym.core.util.ImageReader
import com.muddassir.squadgym.data.remote.dto.ShiftDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MemberFormScreen(
    container: AppContainer,
    memberId: String?,
    shifts: List<ShiftDto>,
    timezone: String,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val today = DateText.todayIso(timezone)
    val viewModel: MemberFormViewModel = viewModel(
        key = "member-form-${memberId ?: "new"}",
        factory = vmFactory {
            MemberFormViewModel(
                memberId = memberId,
                memberRepository = container.memberRepository,
                dashboardRepository = container.dashboardRepository,
                shifts = shifts,
                today = today,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPhotoOptions by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val picked = withContext(Dispatchers.IO) { ImageReader.read(context, uri) }
                if (picked != null) viewModel.onPhoto(picked)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            scope.launch {
                val picked = withContext(Dispatchers.IO) { ImageReader.read(context, uri) }
                if (picked != null) viewModel.onPhoto(picked)
            }
        }
    }

    fun takePhoto() {
        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(directory, "member_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    LaunchedEffect(state.savedMemberId) {
        state.savedMemberId?.let(onSaved)
    }

    ScreenBackground {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                ScreenHeader(
                    title = if (state.isEdit) "Edit member" else "New member",
                    subtitle = if (state.isEdit) "Update profile details" else "Create profile, plan and login",
                    onBack = onBack,
                )
            }

            if (state.loading) item { LoadingState(message = "Loading member") }

            item {
                GymCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            MemberAvatar(
                                photoUrl = state.existingPhotoUrl,
                                initials = state.fullName.trim().split(" ")
                                    .filter { it.isNotBlank() }
                                    .take(2)
                                    .joinToString("") { it.first().uppercaseChar().toString() }
                                    .ifBlank { "?" },
                                size = 68.dp,
                                highlight = state.photo != null,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Profile photo",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (state.photo != null) "New photo selected" else "Optional · JPEG, PNG or WebP",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PhotoChip("Gallery", Icons.Filled.PhotoLibrary) {
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                }
                                PhotoChip("Camera", Icons.Filled.PhotoCamera) { takePhoto() }
                                if (state.photo != null) {
                                    PhotoChip("Remove", Icons.Filled.AddAPhoto) { viewModel.onPhoto(null) }
                                }
                            }
                        }
                    }
                }
            }

            item {
                GymCard {
                    SectionHeader(title = "Personal details")
                    SpacerH(10.dp)
                    GymTextField(
                        value = state.fullName,
                        onValueChange = viewModel::onFullName,
                        label = "Full name",
                        placeholder = "e.g. Aarav Sharma",
                        error = state.fieldErrors["fullName"],
                        imeAction = ImeAction.Next,
                        leadingIcon = Icons.Filled.Face,
                    )
                    SpacerH(10.dp)
                    GymTextField(
                        value = state.email,
                        onValueChange = viewModel::onEmail,
                        label = "Email address",
                        placeholder = "member@gym.com",
                        error = state.fieldErrors["email"],
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        leadingIcon = Icons.Filled.Email,
                        enabled = !state.isEdit,
                    )
                    SpacerH(10.dp)
                    GymTextField(
                        value = state.phone,
                        onValueChange = viewModel::onPhone,
                        label = "Mobile number",
                        placeholder = "+91 98765 43210",
                        error = state.fieldErrors["phone"],
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next,
                        leadingIcon = Icons.Filled.Phone,
                    )
                    SpacerH(10.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gender",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            FilterDropdown(
                                label = "Gender",
                                selectedLabel = state.gender.replaceFirstChar { it.uppercase() },
                                options = listOf("male" to "Male", "female" to "Female", "other" to "Other"),
                                onSelect = viewModel::onGender,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Shift",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            FilterDropdown(
                                label = "Shift",
                                selectedLabel = state.shifts.firstOrNull { it.id == state.shiftId }?.name ?: "No shift",
                                options = listOf("" to "No shift") + state.shifts.map { it.id to it.name },
                                onSelect = { viewModel.onShift(it.ifBlank { null }) },
                            )
                        }
                    }
                    SpacerH(10.dp)
                    GymTextField(
                        value = state.address,
                        onValueChange = viewModel::onAddress,
                        label = "Address",
                        placeholder = "Street, city",
                        leadingIcon = Icons.Filled.Place,
                        singleLine = false,
                        imeAction = ImeAction.Next,
                    )
                    SpacerH(10.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GymTextField(
                            value = state.emergencyContactName,
                            onValueChange = viewModel::onEmergencyName,
                            label = "Emergency contact",
                            modifier = Modifier.weight(1f),
                        )
                        GymTextField(
                            value = state.emergencyContactPhone,
                            onValueChange = viewModel::onEmergencyPhone,
                            label = "Contact number",
                            keyboardType = KeyboardType.Phone,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (!state.isEdit) {
                item {
                    GymCard {
                        SectionHeader(title = "Membership", subtitle = "Plan and start date")
                        SpacerH(10.dp)
                        Text(
                            text = "Plan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        FilterDropdown(
                            label = "Plan",
                            selectedLabel = state.plans.firstOrNull { it.id == state.planId }?.name ?: "Choose a plan",
                            options = state.plans.filter { it.isActive }.map { it.id to it.name },
                            onSelect = viewModel::onPlan,
                        )
                        if (state.fieldErrors["planId"] != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = state.fieldErrors["planId"]!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        SpacerH(10.dp)
                        val plan = state.plans.firstOrNull { it.id == state.planId }
                        GymTextField(
                            value = state.startDate,
                            onValueChange = viewModel::onStartDate,
                            label = "Start date (YYYY-MM-DD)",
                            error = state.fieldErrors["startDate"],
                            leadingIcon = Icons.Filled.DateRange,
                            keyboardType = KeyboardType.Number,
                        )
                        if (plan != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${plan.durationDays} days · ends " +
                                    DateText.full(
                                        runCatching {
                                            java.time.LocalDate.parse(state.startDate)
                                                .plusDays(plan.durationDays.toLong() - 1)
                                                .toString()
                                        }.getOrElse { state.startDate },
                                    ),
                                style = MaterialTheme.typography.labelSmall,
                                color = SquadGymTheme.colors.accent,
                            )
                        }
                    }
                }

                item {
                    GymCard {
                        SectionHeader(
                            title = "Account access",
                            subtitle = "Email login for the member app",
                        )
                        SpacerH(10.dp)
                        GymTextField(
                            value = state.initialPassword,
                            onValueChange = viewModel::onPassword,
                            label = "Temporary password (optional)",
                            placeholder = "Leave blank to send an invitation",
                            error = state.fieldErrors["initialPassword"],
                            isPassword = true,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                            leadingIcon = Icons.Filled.Password,
                        )
                        Spacer(Modifier.height(8.dp))
                        InfoBanner(
                            text = "If you leave the password blank, an invitation is created and the member sets their own password.",
                            icon = Icons.Filled.Groups,
                            tone = Tone.Neutral,
                        )
                    }
                }
            }

            if (state.invitationToken != null) {
                item {
                    InfoBanner(
                        text = "Invitation code (email not sent in dev): ${state.invitationToken}",
                        icon = Icons.Filled.CheckCircle,
                        tone = Tone.Success,
                    )
                }
            }

            if (state.error != null) {
                item {
                    InfoBanner(
                        text = state.error!!,
                        icon = Icons.Filled.Groups,
                        tone = Tone.Danger,
                    )
                }
            }

            item {
                PrimaryButton(
                    text = if (state.isEdit) "Save changes" else "Create member",
                    onClick = viewModel::submit,
                    loading = state.submitting,
                    enabled = !state.submitting && !state.loading,
                    icon = Icons.Filled.CheckCircle,
                )
            }
        }
    }

    if (showPhotoOptions) {
        OptionsDialog(
            title = "Profile photo",
            options = listOf("gallery" to "Choose from gallery", "camera" to "Take a photo"),
            selectedValue = null,
            onSelect = { value ->
                showPhotoOptions = false
                if (value == "gallery") {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } else {
                    takePhoto()
                }
            },
            onDismiss = { showPhotoOptions = false },
        )
    }
}

@Composable
private fun PhotoChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
