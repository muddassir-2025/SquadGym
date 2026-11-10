package com.muddassir.squadgym.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muddassir.squadgym.BuildConfig
import com.muddassir.squadgym.core.di.AppContainer
import com.muddassir.squadgym.core.di.vmFactory
import com.muddassir.squadgym.core.ui.components.GymTextField
import com.muddassir.squadgym.core.ui.components.InfoBanner
import com.muddassir.squadgym.core.ui.components.LinkText
import com.muddassir.squadgym.core.ui.components.PrimaryButton
import com.muddassir.squadgym.core.ui.components.ScreenBackground
import com.muddassir.squadgym.core.ui.components.SecondaryButton
import com.muddassir.squadgym.core.ui.components.SpacerH
import com.muddassir.squadgym.core.ui.components.Tone
import com.muddassir.squadgym.core.ui.theme.Spacing
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme

@Composable
fun LoginScreen(
    container: AppContainer,
    onForgotPassword: () -> Unit,
    onAcceptInvitation: () -> Unit,
) {
    val viewModel: LoginViewModel = viewModel(
        factory = vmFactory { LoginViewModel(container.authRepository) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = SquadGymTheme.colors

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(64.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        tint = colors.accentInk,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        text = "SQUADGYM",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.5.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Gym management",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(44.dp))
            Text(
                text = "Welcome\nback.",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            SpacerH(10.dp)
            Text(
                text = "Sign in to see today's floor, mark attendance and keep memberships moving.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(30.dp))

            GymTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = "Email address",
                placeholder = "you@gym.com",
                error = state.emailError,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                leadingIcon = Icons.Filled.Email,
            )
            SpacerH(14.dp)
            GymTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Password",
                placeholder = "Your password",
                error = state.passwordError,
                isPassword = true,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                LinkText(text = "Forgot password?", onClick = onForgotPassword)
            }

            if (state.error != null) {
                SpacerH(4.dp)
                InfoBanner(text = state.error!!, icon = Icons.Filled.MailOutline, tone = Tone.Danger)
            }

            SpacerH(16.dp)
            PrimaryButton(
                text = "Sign in",
                onClick = viewModel::submit,
                loading = state.loading,
                enabled = !state.loading,
            )

            Spacer(Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = colors.border)
                Text(
                    text = "  INVITATION ONLY  ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp,
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = colors.border)
            }
            Spacer(Modifier.height(18.dp))

            Text(
                text = "Members are added by gym staff. Use the invitation email to activate your account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SpacerH(10.dp)
            SecondaryButton(
                text = "I have an invitation",
                onClick = onAcceptInvitation,
                icon = Icons.Filled.MailOutline,
            )

            Spacer(Modifier.height(36.dp))

            if (BuildConfig.DEBUG) {
                InfoBanner(
                    text = "Demo: admin@squadgym.app · Admin@12345 — member: aarav@example.com · Member@12345",
                    icon = Icons.Filled.FitnessCenter,
                    tone = Tone.Neutral,
                )
                Spacer(Modifier.height(20.dp))
            }

            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}
