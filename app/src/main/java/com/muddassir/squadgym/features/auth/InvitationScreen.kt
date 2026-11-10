package com.muddassir.squadgym.features.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.muddassir.squadgym.core.ui.components.IconAction
import com.muddassir.squadgym.core.ui.components.LoadingState
import com.muddassir.squadgym.core.ui.components.PrimaryButton
import com.muddassir.squadgym.core.ui.components.ScreenBackground
import com.muddassir.squadgym.core.ui.components.SpacerH
import com.muddassir.squadgym.core.ui.components.Tone
import com.muddassir.squadgym.core.ui.components.ValueRow
import com.muddassir.squadgym.core.ui.theme.Spacing

@Composable
fun InvitationScreen(
    container: AppContainer,
    token: String?,
    onBackToLogin: () -> Unit,
) {
    val viewModel: InvitationViewModel = viewModel(
        key = "invite-${token.orEmpty()}",
        factory = vmFactory { InvitationViewModel(container.authRepository, token) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            IconAction(
                icon = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBackToLogin,
            )
            Spacer(Modifier.height(22.dp))

            Text(
                text = "Activate your\naccount.",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            SpacerH(10.dp)
            Text(
                text = "Your gym created this account for you. Set a password to sign in on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            when {
                state.loadingPreview -> LoadingState(message = "Checking your invitation")

                state.preview != null -> {
                    GymCard {
                        Text(
                            text = state.preview!!.gymName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        ValueRow(label = "Name", value = state.preview!!.fullName)
                        ValueRow(label = "Email", value = state.preview!!.email)
                        ValueRow(
                            label = "Access",
                            value = if (state.preview!!.role == "admin") "Administrator" else "Member",
                        )
                    }
                    SpacerH(18.dp)
                    GymTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = "Create password",
                        error = state.passwordError,
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                        leadingIcon = Icons.Filled.Lock,
                    )
                    SpacerH(12.dp)
                    GymTextField(
                        value = state.confirmPassword,
                        onValueChange = viewModel::onConfirmChange,
                        label = "Confirm password",
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    )
                    if (state.error != null) {
                        SpacerH(12.dp)
                        InfoBanner(text = state.error!!, icon = Icons.Filled.Badge, tone = Tone.Danger)
                    }
                    SpacerH(18.dp)
                    PrimaryButton(
                        text = "Activate & sign in",
                        onClick = viewModel::accept,
                        loading = state.submitting,
                        enabled = !state.submitting,
                    )
                }

                state.fatal -> ErrorState(
                    message = state.error ?: "This invitation can't be used.",
                    title = "Invitation unavailable",
                    onRetry = onBackToLogin,
                )

                else -> {
                    GymTextField(
                        value = state.manualToken,
                        onValueChange = viewModel::onManualTokenChange,
                        label = "Invitation code",
                        placeholder = "Paste the code from your email",
                        error = state.error,
                        imeAction = ImeAction.Done,
                        leadingIcon = Icons.Filled.Email,
                    )
                    SpacerH(16.dp)
                    PrimaryButton(
                        text = "Continue",
                        onClick = viewModel::submitManualToken,
                        loading = state.loadingPreview,
                    )
                }
            }
            Spacer(Modifier.height(36.dp))
        }
    }
}
