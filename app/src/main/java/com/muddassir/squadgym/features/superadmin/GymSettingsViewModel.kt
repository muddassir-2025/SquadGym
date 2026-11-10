package com.muddassir.squadgym.features.superadmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.core.error.AppError
import com.muddassir.squadgym.data.remote.dto.GymConfigurationDto
import com.muddassir.squadgym.data.remote.dto.GymSettingsPatch
import com.muddassir.squadgym.data.remote.dto.OperatingDayUpdateDto
import com.muddassir.squadgym.data.remote.dto.PlanRequest
import com.muddassir.squadgym.data.remote.dto.ShiftRequest
import com.muddassir.squadgym.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GymSettingsUiState(
    val loading: Boolean = true,
    val config: GymConfigurationDto? = null,
    val error: String? = null,
    val message: String? = null,
    val saving: Boolean = false,
    val name: String = "",
    val timezone: String = "",
    val expiringSoonDays: String = "7",
    val archiveAfterDays: String = "90",
    val inGymWindowMinutes: String = "60",
    val attendanceUndoSeconds: String = "300",
    val renewalUndoSeconds: String = "300",
)

class GymSettingsViewModel(private val repository: AdminRepository) : ViewModel() {

    private val _state = MutableStateFlow(GymSettingsUiState())
    val state: StateFlow<GymSettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.config == null, error = null) }
            when (val result = repository.gymConfiguration()) {
                is ApiResult.Success -> {
                    val gym = result.data.gym
                    _state.update {
                        it.copy(
                            loading = false,
                            config = result.data,
                            name = gym.name,
                            timezone = gym.timezone,
                            expiringSoonDays = gym.expiringSoonDays.toString(),
                            archiveAfterDays = gym.archiveAfterDays.toString(),
                            inGymWindowMinutes = gym.inGymWindowMinutes.toString(),
                            attendanceUndoSeconds = gym.attendanceUndoSeconds.toString(),
                            renewalUndoSeconds = gym.renewalUndoSeconds.toString(),
                        )
                    }
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = messageFor(result.error))
                }
            }
        }
    }

    fun onName(value: String) = _state.update { it.copy(name = value) }
    fun onTimezone(value: String) = _state.update { it.copy(timezone = value) }
    fun onExpiringDays(value: String) = _state.update { it.copy(expiringSoonDays = value.filter(Char::isDigit)) }
    fun onArchiveDays(value: String) = _state.update { it.copy(archiveAfterDays = value.filter(Char::isDigit)) }
    fun onWindowMinutes(value: String) = _state.update { it.copy(inGymWindowMinutes = value.filter(Char::isDigit)) }
    fun onAttendanceUndo(value: String) = _state.update { it.copy(attendanceUndoSeconds = value.filter(Char::isDigit)) }
    fun onRenewalUndo(value: String) = _state.update { it.copy(renewalUndoSeconds = value.filter(Char::isDigit)) }

    fun saveSettings() {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(saving = true, message = null) }
            val patch = GymSettingsPatch(
                name = current.name.takeIf { it.isNotBlank() },
                timezone = current.timezone.takeIf { it.isNotBlank() },
                expiringSoonDays = current.expiringSoonDays.toIntOrNull(),
                archiveAfterDays = current.archiveAfterDays.toIntOrNull(),
                inGymWindowMinutes = current.inGymWindowMinutes.toIntOrNull(),
                attendanceUndoSeconds = current.attendanceUndoSeconds.toIntOrNull(),
                renewalUndoSeconds = current.renewalUndoSeconds.toIntOrNull(),
            )
            when (val result = repository.updateSettings(patch)) {
                is ApiResult.Success -> _state.update {
                    it.copy(saving = false, message = "Settings saved.")
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(saving = false, message = messageFor(result.error))
                }
            }
            load()
        }
    }

    fun toggleDay(dayOfWeek: Int, isOpen: Boolean) {
        val config = _state.value.config ?: return
        val updated = config.operatingDays.map { day ->
            OperatingDayUpdateDto(
                dayOfWeek = day.dayOfWeek,
                isOpen = if (day.dayOfWeek == dayOfWeek) isOpen else day.isOpen,
                opensAt = day.opensAt,
                closesAt = day.closesAt,
            )
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            when (val result = repository.updateOperatingDays(updated)) {
                is ApiResult.Success -> _state.update {
                    it.copy(saving = false, config = result.data, message = "Operating days updated.")
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(saving = false, message = messageFor(result.error))
                }
            }
        }
    }

    fun createShift(name: String, start: String, end: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            when (val result = repository.createShift(ShiftRequest(name, start, end))) {
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false, message = "Shift added.") }
                    load()
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(saving = false, message = messageFor(result.error))
                }
            }
        }
    }

    fun toggleShift(id: String, isActive: Boolean) {
        viewModelScope.launch {
            when (val result = repository.updateShift(
                id,
                com.muddassir.squadgym.data.remote.dto.ShiftPatchRequest(isActive = isActive),
            )) {
                is ApiResult.Success -> {
                    _state.update { it.copy(message = "Shift updated.") }
                    load()
                }

                is ApiResult.Failure -> _state.update { it.copy(message = messageFor(result.error)) }
            }
        }
    }

    fun createPlan(name: String, days: String, priceCents: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            when (
                val result = repository.createPlan(
                    PlanRequest(
                        name = name,
                        durationDays = days.toIntOrNull() ?: 30,
                        priceCents = priceCents.toIntOrNull() ?: 0,
                    ),
                )
            ) {
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false, message = "Plan added.") }
                    load()
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(saving = false, message = messageFor(result.error))
                }
            }
        }
    }

    fun togglePlan(id: String, isActive: Boolean) {
        viewModelScope.launch {
            when (
                val result = repository.updatePlan(
                    id,
                    com.muddassir.squadgym.data.remote.dto.PlanPatchRequest(isActive = isActive),
                )
            ) {
                is ApiResult.Success -> {
                    _state.update { it.copy(message = "Plan updated.") }
                    load()
                }

                is ApiResult.Failure -> _state.update { it.copy(message = messageFor(result.error)) }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Network -> "You're offline."
        is AppError.Validation -> error.fields.values.firstOrNull() ?: error.detail ?: "Check the values."
        else -> error.message ?: "Something went wrong."
    }
}
