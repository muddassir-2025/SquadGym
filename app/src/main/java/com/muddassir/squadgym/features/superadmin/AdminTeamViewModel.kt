package com.muddassir.squadgym.features.superadmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.core.error.AppError
import com.muddassir.squadgym.data.remote.dto.AdminDto
import com.muddassir.squadgym.data.remote.dto.UpdateAdminRequest
import com.muddassir.squadgym.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminTeamUiState(
    val loading: Boolean = true,
    val admins: List<AdminDto> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val saving: Boolean = false,
)

class AdminTeamViewModel(private val repository: AdminRepository) : ViewModel() {

    private val _state = MutableStateFlow(AdminTeamUiState())
    val state: StateFlow<AdminTeamUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.admins.isEmpty(), error = null) }
            when (val result = repository.admins()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, admins = result.data) }
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = messageFor(result.error))
                }
            }
        }
    }

    fun setStatus(admin: AdminDto, status: String) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            when (val result = repository.setStatus(admin.id, status)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            saving = false,
                            admins = it.admins.map { row -> if (row.id == result.data.id) result.data else row },
                            message = "${admin.fullName} is now ${status}.",
                        )
                    }
                    load()
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(saving = false, message = messageFor(result.error))
                }
            }
        }
    }

    fun updateAdmin(admin: AdminDto, fullName: String, phone: String?, permissions: List<String>) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val request = UpdateAdminRequest(
                fullName = fullName.takeIf { it.isNotBlank() && it != admin.fullName },
                phone = phone,
                permissions = permissions.takeIf { admin.role != "super_admin" && it != admin.permissions },
            )
            if (request.fullName == null && request.phone == null && request.permissions == null) {
                _state.update { it.copy(saving = false) }
                return@launch
            }
            when (val result = repository.updateAdmin(admin.id, request)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            saving = false,
                            admins = it.admins.map { row -> if (row.id == result.data.id) result.data else row },
                            message = "Admin updated.",
                        )
                    }
                    load()
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(saving = false, message = messageFor(result.error))
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null, error = null) }

    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Network -> "You're offline."
        is AppError.Validation -> error.fields.values.firstOrNull() ?: error.detail ?: "Check the details."
        else -> error.message ?: "Something went wrong."
    }
}

data class AdminFormUiState(
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val fieldErrors: Map<String, String> = emptyMap(),
    val submitting: Boolean = false,
    val error: String? = null,
    val createdId: String? = null,
    val invitationToken: String? = null,
)

class AdminFormViewModel(private val repository: AdminRepository) : ViewModel() {

    private val _state = MutableStateFlow(AdminFormUiState())
    val state: StateFlow<AdminFormUiState> = _state.asStateFlow()

    fun onFullName(value: String) = _state.update { it.copy(fullName = value, fieldErrors = it.fieldErrors - "fullName") }
    fun onEmail(value: String) = _state.update { it.copy(email = value, fieldErrors = it.fieldErrors - "email") }
    fun onPhone(value: String) = _state.update { it.copy(phone = value) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, fieldErrors = it.fieldErrors - "initialPassword") }

    fun submit() {
        val current = _state.value
        if (current.submitting) return
        val errors = buildMap {
            if (current.fullName.trim().length < 2) put("fullName", "Enter the admin's full name.")
            if (!com.muddassir.squadgym.features.auth.isEmailValid(current.email)) {
                put("email", "Enter a valid email address.")
            }
            if (current.password.isNotBlank() && current.password.length < 8) {
                put("initialPassword", "Use 8+ characters, or leave blank to invite.")
            }
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            val result = repository.createAdmin(
                com.muddassir.squadgym.data.remote.dto.CreateAdminRequest(
                    fullName = current.fullName.trim(),
                    email = current.email.trim().lowercase(),
                    phone = current.phone.trim().ifBlank { null },
                    initialPassword = current.password.takeIf { it.isNotBlank() },
                ),
            )
            when (result) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        submitting = false,
                        createdId = result.data.admin.id,
                        invitationToken = result.data.invitation?.token,
                        fieldErrors = emptyMap(),
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(
                        submitting = false,
                        fieldErrors = (result.error as? AppError.Validation)?.fields.orEmpty(),
                        error = result.error.message ?: "Couldn't create the admin.",
                    )
                }
            }
        }
    }
}
