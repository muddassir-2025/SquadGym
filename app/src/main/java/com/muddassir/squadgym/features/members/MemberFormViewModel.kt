package com.muddassir.squadgym.features.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.features.auth.isEmailValid
import com.muddassir.squadgym.core.error.AppError
import com.muddassir.squadgym.core.util.DateText
import com.muddassir.squadgym.core.util.PickedImage
import com.muddassir.squadgym.data.remote.dto.CreateMemberRequest
import com.muddassir.squadgym.data.remote.dto.PlanDto
import com.muddassir.squadgym.data.remote.dto.ShiftDto
import com.muddassir.squadgym.data.remote.dto.UpdateMemberRequest
import com.muddassir.squadgym.data.repository.DashboardRepository
import com.muddassir.squadgym.data.repository.MemberRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemberFormUiState(
    val isEdit: Boolean = false,
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val gender: String = "male",
    val planId: String? = null,
    val startDate: String = "",
    val shiftId: String? = null,
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val initialPassword: String = "",
    val photo: PickedImage? = null,
    val existingPhotoUrl: String? = null,
    val plans: List<PlanDto> = emptyList(),
    val shifts: List<ShiftDto> = emptyList(),
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
    val savedMemberId: String? = null,
    val invitationToken: String? = null,
)

class MemberFormViewModel(
    private val memberId: String?,
    private val memberRepository: MemberRepository,
    private val dashboardRepository: DashboardRepository,
    private val shifts: List<ShiftDto>,
    private val today: String,
) : ViewModel() {

    private val _state = MutableStateFlow(
        MemberFormUiState(
            isEdit = memberId != null,
            startDate = today,
            shifts = shifts,
            shiftId = shifts.firstOrNull()?.id,
        ),
    )
    val state: StateFlow<MemberFormUiState> = _state.asStateFlow()

    init {
        loadPlans()
        if (memberId != null) loadMember(memberId)
    }

    private fun loadPlans() {
        viewModelScope.launch {
            when (val result = dashboardRepository.plans()) {
                is ApiResult.Success -> _state.update { state ->
                    state.copy(
                        plans = result.data,
                        planId = state.planId ?: result.data.firstOrNull { it.isActive }?.id,
                    )
                }

                is ApiResult.Failure -> Unit
            }
        }
    }

    private fun loadMember(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val result = memberRepository.detail(id)) {
                is ApiResult.Success -> {
                    val member = result.data.member
                    _state.update {
                        it.copy(
                            loading = false,
                            fullName = member.fullName,
                            email = member.email,
                            phone = member.phone.orEmpty(),
                            address = member.address.orEmpty(),
                            gender = member.gender ?: "male",
                            shiftId = member.shift?.id ?: it.shiftId,
                            emergencyContactName = member.emergencyContactName.orEmpty(),
                            emergencyContactPhone = member.emergencyContactPhone.orEmpty(),
                            existingPhotoUrl = member.photoUrl,
                            planId = member.membership?.planId ?: it.planId,
                            startDate = member.joinedOn.ifBlank { today },
                        )
                    }
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = it.error ?: "Couldn't load this member.")
                }
            }
        }
    }

    fun onFullName(value: String) = clearFieldError("fullName") { it.copy(fullName = value) }
    fun onEmail(value: String) = clearFieldError("email") { it.copy(email = value) }
    fun onPhone(value: String) = clearFieldError("phone") { it.copy(phone = value) }
    fun onAddress(value: String) = clearFieldError("address") { it.copy(address = value) }
    fun onGender(value: String) = _state.update { it.copy(gender = value) }
    fun onPlan(value: String) = clearFieldError("planId") { it.copy(planId = value) }
    fun onShift(value: String?) = _state.update { it.copy(shiftId = value) }
    fun onStartDate(value: String) = clearFieldError("startDate") { it.copy(startDate = value) }
    fun onEmergencyName(value: String) = _state.update { it.copy(emergencyContactName = value) }
    fun onEmergencyPhone(value: String) = _state.update { it.copy(emergencyContactPhone = value) }
    fun onPassword(value: String) = clearFieldError("initialPassword") { it.copy(initialPassword = value) }
    fun onPhoto(picked: PickedImage?) = _state.update { it.copy(photo = picked) }

    private fun clearFieldError(field: String, transform: (MemberFormUiState) -> MemberFormUiState) {
        _state.update { current ->
            transform(current).copy(
                fieldErrors = current.fieldErrors - field,
                error = null,
            )
        }
    }

    fun submit() {
        val current = _state.value
        if (current.submitting) return

        val errors = buildMap {
            if (current.fullName.trim().length < 2) put("fullName", "Enter the member's full name.")
            if (!isEmailValid(current.email)) put("email", "Enter a valid email address.")
            if (current.phone.isNotBlank() && current.phone.trim().length < 5) put("phone", "Check the phone number.")
            if (!current.isEdit && current.planId == null) put("planId", "Choose a membership plan.")
            if (!current.isEdit && current.startDate.isNotBlank() && DateText.parse(current.startDate) == null) {
                put("startDate", "Use YYYY-MM-DD.")
            }
            if (!current.isEdit && current.initialPassword.isNotBlank() && current.initialPassword.length < 8) {
                put("initialPassword", "Use at least 8 characters, or leave it blank to send an invitation.")
            }
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }

            val savedId = if (current.isEdit) {
                val request = UpdateMemberRequest(
                    fullName = current.fullName.trim(),
                    email = current.email.trim().lowercase(),
                    phone = current.phone.trim().ifBlank { null },
                    gender = current.gender,
                    address = current.address.trim().ifBlank { null },
                    shiftId = current.shiftId,
                    emergencyContactName = current.emergencyContactName.trim().ifBlank { null },
                    emergencyContactPhone = current.emergencyContactPhone.trim().ifBlank { null },
                )
                when (val result = memberRepository.update(memberId!!, request)) {
                    is ApiResult.Success -> result.data.id
                    is ApiResult.Failure -> {
                        _state.update {
                            it.copy(submitting = false, fieldErrors = fieldsOf(result.error), error = messageFor(result.error))
                        }
                        return@launch
                    }
                }
            } else {
                val request = CreateMemberRequest(
                    fullName = current.fullName.trim(),
                    email = current.email.trim().lowercase(),
                    phone = current.phone.trim().ifBlank { null },
                    gender = current.gender,
                    address = current.address.trim().ifBlank { null },
                    planId = current.planId!!,
                    startDate = current.startDate.takeIf { it.isNotBlank() },
                    shiftId = current.shiftId,
                    emergencyContactName = current.emergencyContactName.trim().ifBlank { null },
                    emergencyContactPhone = current.emergencyContactPhone.trim().ifBlank { null },
                    initialPassword = current.initialPassword.takeIf { it.isNotBlank() },
                )
                when (val result = memberRepository.create(request)) {
                    is ApiResult.Success -> {
                        val token = result.data.invitation?.token
                        _state.update { it.copy(invitationToken = token) }
                        result.data.memberId
                    }

                    is ApiResult.Failure -> {
                        _state.update {
                            it.copy(submitting = false, fieldErrors = fieldsOf(result.error), error = messageFor(result.error))
                        }
                        return@launch
                    }
                }
            }

            val photo = _state.value.photo
            if (photo != null) {
                val upload = memberRepository.uploadPhoto(savedId, photo.bytes, photo.mimeType, photo.fileName)
                if (upload is ApiResult.Failure) {
                    _state.update {
                        it.copy(
                            submitting = false,
                            error = "Member saved, but the photo upload failed. ${messageFor(upload.error)}",
                            savedMemberId = savedId,
                        )
                    }
                    return@launch
                }
            }

            _state.update { it.copy(submitting = false, savedMemberId = savedId) }
        }
    }

    private fun fieldsOf(error: AppError): Map<String, String> =
        (error as? AppError.Validation)?.fields ?: emptyMap()

    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Network -> "You're offline. The member was not saved."
        is AppError.Conflict -> error.detail
        is AppError.Validation -> error.fields.values.firstOrNull() ?: error.detail ?: "Check the fields."
        else -> error.message ?: "Something went wrong."
    }
}
