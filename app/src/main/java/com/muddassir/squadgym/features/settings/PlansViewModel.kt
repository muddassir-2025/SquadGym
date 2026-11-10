package com.muddassir.squadgym.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.core.error.AppError
import com.muddassir.squadgym.data.remote.dto.PlanDto
import com.muddassir.squadgym.data.remote.dto.PlanPatchRequest
import com.muddassir.squadgym.data.remote.dto.PlanRequest
import com.muddassir.squadgym.data.repository.AdminRepository
import com.muddassir.squadgym.data.repository.DashboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlansUiState(
    val loading: Boolean = true,
    val plans: List<PlanDto> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val saving: Boolean = false,
    val cached: Boolean = false,
)

class PlansViewModel(
    private val dashboardRepository: DashboardRepository,
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlansUiState())
    val state: StateFlow<PlansUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.plans.isEmpty(), error = null) }
            when (val result = dashboardRepository.plans()) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        plans = result.data.sortedWith(compareBy({ plan -> plan.sortOrder }, { plan -> plan.name })),
                        error = null,
                        cached = result.fromCache,
                    )
                }

                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = messageFor(result.error)) }
            }
        }
    }

    fun save(planId: String?, name: String, days: Int, priceCents: Int, description: String?) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, message = null) }
            val result = if (planId == null) {
                adminRepository.createPlan(
                    PlanRequest(
                        name = name,
                        durationDays = days,
                        priceCents = priceCents,
                        description = description,
                    ),
                )
            } else {
                adminRepository.updatePlan(
                    planId,
                    PlanPatchRequest(
                        name = name,
                        durationDays = days,
                        priceCents = priceCents,
                        description = description,
                    ),
                )
            }
            when (result) {
                is ApiResult.Success -> _state.update {
                    it.copy(saving = false, message = if (planId == null) "Plan created." else "Plan updated.")
                }

                is ApiResult.Failure -> _state.update { it.copy(saving = false, message = messageFor(result.error)) }
            }
            load()
        }
    }

    fun setActive(planId: String, active: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, message = null) }
            val result = if (active) {
                adminRepository.updatePlan(planId, PlanPatchRequest(isActive = true))
            } else {
                adminRepository.removePlan(planId)
            }
            when (result) {
                is ApiResult.Success -> _state.update {
                    it.copy(saving = false, message = if (active) "Plan restored." else "Plan removed.")
                }

                is ApiResult.Failure -> _state.update { it.copy(saving = false, message = messageFor(result.error)) }
            }
            load()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Network -> "You're offline."
        is AppError.Validation -> error.fields.values.firstOrNull() ?: error.detail ?: "Check the values."
        else -> error.message ?: "Something went wrong."
    }
}
