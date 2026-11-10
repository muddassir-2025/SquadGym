package com.muddassir.squadgym.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muddassir.squadgym.core.di.AppContainer
import com.muddassir.squadgym.core.di.vmFactory
import com.muddassir.squadgym.core.ui.components.GymTextField
import com.muddassir.squadgym.core.ui.components.InfoBanner
import com.muddassir.squadgym.core.ui.components.IconAction
import com.muddassir.squadgym.core.ui.components.LinkText
import com.muddassir.squadgym.core.ui.components.PrimaryButton
import com.muddassir.squadgym.core.ui.components.ScreenBackground
import com.muddassir.squadgym.core.ui.components.SpacerH
import com.muddassir.squadgym.core.ui.components.Tone
import com.muddassir.squadgym.core.ui.theme.Spacing

@Composable
fun ForgotPasswordScreen(
    container: AppContainer,
    onBackToLogin: () -> Unit,
) {
    val viewModel: ForgotPasswordViewModel = viewModel(
        factory = vmFactory { ForgotPasswordViewModel(container.authRepository) },
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
            Spacer(Modifier.height(24.dp))

            Text(
                text = when (state.step) {
                    ResetStep.REQUEST -> "Reset your\npassword."
                    ResetStep.RESET -> "Enter your\nreset code."
                    ResetStep.DONE -> "You're all\nset."
                },
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            SpacerH(10.dp)
            Text(
                text = when (state.step) {
                    ResetStep.REQUEST -> "We'll email you an 8-character code. It expires in 30 minutes."
                    ResetStep.RESET -> "Check your inbox for the code, then choose a new password."
                    else -> "Sign in again with your new password."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))

            when (state.step) {
                ResetStep.REQUEST -> {
                    GymTextField(
                        value = state.email,
                        onValueChange = viewModel::onEmailChange,
                        label = "Email address",
                        placeholder = "you@gym.com",
                        error = state.emailError,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                    )
                    if (state.error != null) {
                        SpacerH(12.dp)
                        InfoBanner(text = state.error!!, icon = Icons.Filled.MarkEmailRead, tone = Tone.Danger)
                    }
                    SpacerH(18.dp)
                    PrimaryButton(
                        text = "Send reset code",
                        onClick = viewModel::requestCode,
                        loading = state.loading,
                        enabled = !state.loading,
                    )
                }

                ResetStep.RESET -> {
                    if (state.info != null) {
                        InfoBanner(text = state.info!!, icon = Icons.Filled.MarkEmailRead, tone = Tone.Success)
                        SpacerH(12.dp)
                    }
                    if (state.devCode != null) {
                        InfoBanner(
                            text = "Development code: ${state.devCode}",
                            icon = Icons.Filled.LockReset,
                            tone = Tone.Warning,
                        )
                        SpacerH(12.dp)
                    }
                    GymTextField(
                        value = state.code,
                        onValueChange = viewModel::onCodeChange,
                        label = "Reset code",
                        placeholder = "ABCD1234",
                        error = state.codeError,
                        imeAction = ImeAction.Next,
                    )
                    SpacerH(12.dp)
                    GymTextField(
                        value = state.newPassword,
                        onValueChange = viewModel::onPasswordChange,
                        label = "New password",
                        error = state.passwordError,
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    )
                    SpacerH(12.dp)
                    GymTextField(
                        value = state.confirmPassword,
                        onValueChange = viewModel::onConfirmChange,
                        label = "Confirm new password",
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    )
                    if (state.error != null) {
                        SpacerH(12.dp)
                        InfoBanner(text = state.error!!, icon = Icons.Filled.LockReset, tone = Tone.Danger)
                    }
                    SpacerH(18.dp)
                    PrimaryButton(
                        text = "Update password",
                        onClick = viewModel::resetPassword,
                        loading = state.loading,
                        enabled = !state.loading,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LinkText(text = "Use a different email", onClick = onBackToLogin)
                    }
                }

                ResetStep.DONE -> {
                    InfoBanner(
                        text = state.info ?: "Password updated.",
                        icon = Icons.Filled.CheckCircle,
                        tone = Tone.Success,
                    )
                    SpacerH(20.dp)
                    PrimaryButton(text = "Back to sign in", onClick = onBackToLogin)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
