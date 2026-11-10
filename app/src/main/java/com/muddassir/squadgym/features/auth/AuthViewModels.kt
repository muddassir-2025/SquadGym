package com.muddassir.squadgym.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.core.error.AppError
import com.muddassir.squadgym.data.remote.dto.InvitationPreviewDto
import com.muddassir.squadgym.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

fun isEmailValid(value: String): Boolean = EMAIL_PATTERN.matches(value.trim())

fun errorMessage(error: AppError): String = when (error) {
    is AppError.Conflict -> when (error.code) {
        "AUTH_INVALID_CREDENTIALS" -> "Email or password is incorrect."
        "AUTH_ACCOUNT_INVITED" -> "Activate your account from the invitation email first."
        else -> error.detail
    }

    is AppError.Forbidden -> error.message ?: "You don't have permission for that."
    else -> error.message ?: "Something went wrong."
}

// ── Login ───────────────────────────────────────────────────────────────────

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null, error = null) }

    fun clearError() = _state.update { it.copy(error = null) }

    fun submit() {
        val current = _state.value
        val emailError = when {
            current.email.isBlank() -> "Enter your email address."
            !isEmailValid(current.email) -> "That doesn't look like a valid email."
            else -> null
        }
        val passwordError = if (current.password.isEmpty()) "Enter your password." else null
        if (emailError != null || passwordError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        if (current.loading) return

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = authRepository.login(current.email, current.password)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false) }
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = errorMessage(result.error))
                }
            }
        }
    }
}

// ── Forgot password ────────────────────────────────────────────────────────

enum class ResetStep { REQUEST, RESET, DONE }

data class ForgotUiState(
    val step: ResetStep = ResetStep.REQUEST,
    val email: String = "",
    val code: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val emailError: String? = null,
    val codeError: String? = null,
    val passwordError: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val devCode: String? = null,
)

class ForgotPasswordViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(ForgotUiState())
    val state: StateFlow<ForgotUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, error = null) }

    fun onCodeChange(value: String) = _state.update { it.copy(code = value.uppercase(), codeError = null, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(newPassword = value, passwordError = null, error = null) }

    fun onConfirmChange(value: String) = _state.update { it.copy(confirmPassword = value, passwordError = null, error = null) }

    fun clearError() = _state.update { it.copy(error = null) }

    fun requestCode() {
        val current = _state.value
        if (!isEmailValid(current.email)) {
            _state.update { it.copy(emailError = "Enter the email on your account.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = authRepository.forgotPassword(current.email)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        step = ResetStep.RESET,
                        info = result.data.message ?: "If that email exists, a code is on its way.",
                        devCode = result.data.devToken,
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = errorMessage(result.error))
                }
            }
        }
    }

    fun resetPassword() {
        val current = _state.value
        val codeError = if (current.code.length < 4) "Enter the code from your email." else null
        val passwordError = when {
            current.newPassword.length < 8 -> "Use at least 8 characters."
            current.newPassword != current.confirmPassword -> "Passwords don't match."
            else -> null
        }
        if (codeError != null || passwordError != null) {
            _state.update { it.copy(codeError = codeError, passwordError = passwordError) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = authRepository.resetPassword(current.code, current.newPassword)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, step = ResetStep.DONE, info = "Password updated. Sign in with your new password.")
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        error = errorMessage(result.error),
                        codeError = (result.error as? AppError.Validation)?.field("token"),
                    )
                }
            }
        }
    }
}

// ── Invitation acceptance ──────────────────────────────────────────────────

data class InviteUiState(
    val manualToken: String = "",
    val token: String = "",
    val preview: InvitationPreviewDto? = null,
    val loadingPreview: Boolean = false,
    val password: String = "",
    val confirmPassword: String = "",
    val passwordError: String? = null,
    val submitting: Boolean = false,
    val error: String? = null,
    val fatal: Boolean = false,
)

class InvitationViewModel(
    private val authRepository: AuthRepository,
    initialToken: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(InviteUiState(token = initialToken.orEmpty()))
    val state: StateFlow<InviteUiState> = _state.asStateFlow()

    init {
        if (!initialToken.isNullOrBlank()) loadPreview(initialToken)
    }

    fun onManualTokenChange(value: String) = _state.update { it.copy(manualToken = value.trim(), error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null, error = null) }

    fun onConfirmChange(value: String) = _state.update { it.copy(confirmPassword = value, passwordError = null, error = null) }

    fun submitManualToken() {
        val token = _state.value.manualToken
        if (token.isBlank()) {
            _state.update { it.copy(error = "Paste the code from your invitation email.") }
            return
        }
        loadPreview(token)
    }

    private fun loadPreview(token: String) {
        viewModelScope.launch {
            _state.update { it.copy(loadingPreview = true, error = null, token = token, fatal = false) }
            when (val result = authRepository.invitationPreview(token)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loadingPreview = false, preview = result.data)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loadingPreview = false,
                        error = errorMessage(result.error),
                        fatal = result.error is AppError.NotFound || result.error is AppError.Conflict,
                    )
                }
            }
        }
    }

    fun accept() {
        val current = _state.value
        val passwordError = when {
            current.password.length < 8 -> "Use at least 8 characters."
            current.password != current.confirmPassword -> "Passwords don't match."
            else -> null
        }
        if (passwordError != null) {
            _state.update { it.copy(passwordError = passwordError) }
            return
        }
        if (current.submitting) return

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            when (val result = authRepository.acceptInvitation(current.token, current.password)) {
                is ApiResult.Success -> _state.update { it.copy(submitting = false) }
                is ApiResult.Failure -> _state.update {
                    it.copy(submitting = false, error = errorMessage(result.error))
                }
            }
        }
    }
}
