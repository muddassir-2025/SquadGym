package com.muddassir.squadgym.features.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.core.error.AppError
import com.muddassir.squadgym.data.remote.dto.MemberDetailDto
import com.muddassir.squadgym.data.remote.dto.PlanDto
import com.muddassir.squadgym.data.remote.dto.RenewalPreviewDto
import com.muddassir.squadgym.data.repository.AttendanceRepository
import com.muddassir.squadgym.data.repository.DashboardRepository
import com.muddassir.squadgym.data.repository.MemberRepository
import com.muddassir.squadgym.data.repository.MembershipRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RenewalUndoState(
    val renewalId: String,
    val memberName: String,
    val secondsRemaining: Int,
    val expiresAtMillis: Long,
)

data class MemberDetailUiState(
    val loading: Boolean = true,
    val detail: MemberDetailDto? = null,
    val plans: List<PlanDto> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val cached: Boolean = false,
    val renewalPreview: RenewalPreviewDto? = null,
    val previewLoading: Boolean = false,
    val renewing: Boolean = false,
    val undo: RenewalUndoState? = null,
    val working: Boolean = false,
    val markedPresentNow: Boolean = false,
)

class MemberDetailViewModel(
    private val memberId: String,
    private val memberRepository: MemberRepository,
    private val membershipRepository: MembershipRepository,
    private val attendanceRepository: AttendanceRepository,
    private val dashboardRepository: DashboardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MemberDetailUiState())
    val state: StateFlow<MemberDetailUiState> = _state.asStateFlow()

    private var ticker: Job? = null

    init {
        load()
        loadPlans()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.detail == null, error = null) }
            when (val result = memberRepository.detail(memberId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, detail = result.data, cached = result.fromCache)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = messageFor(result.error))
                }
            }
        }
    }

    private fun loadPlans() {
        viewModelScope.launch {
            when (val result = dashboardRepository.plans()) {
                is ApiResult.Success -> _state.update { it.copy(plans = result.data) }
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun previewRenewal(planId: String) {
        viewModelScope.launch {
            _state.update { it.copy(previewLoading = true) }
            when (val result = membershipRepository.preview(memberId, planId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(previewLoading = false, renewalPreview = result.data)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(previewLoading = false, message = messageFor(result.error))
                }
            }
        }
    }

    fun clearPreview() = _state.update { it.copy(renewalPreview = null) }

    fun renew(planId: String) {
        if (_state.value.renewing) return
        viewModelScope.launch {
            _state.update { it.copy(renewing = true) }
            when (val result = membershipRepository.renew(memberId, planId)) {
                is ApiResult.Success -> {
                    val response = result.data
                    _state.update {
                        it.copy(
                            renewing = false,
                            renewalPreview = null,
                            message = "Membership renewed until ${response.membership.endDate}.",
                            undo = if (response.renewal.canUndo) {
                                RenewalUndoState(
                                    renewalId = response.renewal.id,
                                    memberName = it.detail?.member?.fullName ?: "Member",
                                    secondsRemaining = response.renewal.secondsRemaining,
                                    expiresAtMillis = System.currentTimeMillis() +
                                        response.renewal.secondsRemaining * 1000L,
                                )
                            } else {
                                it.undo
                            },
                        )
                    }
                    startTicker()
                    load()
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(renewing = false, message = messageFor(result.error))
                }
            }
        }
    }

    fun undoRenewal() {
        val undo = _state.value.undo ?: return
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            when (val result = membershipRepository.undoRenewal(undo.renewalId)) {
                is ApiResult.Success -> {
                    ticker?.cancel()
                    _state.update {
                        it.copy(
                            working = false,
                            undo = null,
                            message = "Renewal undone. Previous membership restored.",
                        )
                    }
                    load()
                }

                is ApiResult.Failure -> {
                    ticker?.cancel()
                    _state.update {
                        it.copy(
                            working = false,
                            undo = null,
                            message = (result.error as? AppError.Conflict)?.detail ?: messageFor(result.error),
                        )
                    }
                }
            }
        }
    }

    fun dismissUndo() {
        ticker?.cancel()
        _state.update { it.copy(undo = null) }
    }

    fun archive(reason: String?) {
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            when (val result = memberRepository.archive(memberId, reason)) {
                is ApiResult.Success -> _state.update {
                    it.copy(working = false, message = "Member archived. History is preserved.")
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(working = false, message = messageFor(result.error))
                }
            }
            load()
        }
    }

    fun restore() {
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            when (val result = memberRepository.restore(memberId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(working = false, message = "Member restored.")
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(working = false, message = messageFor(result.error))
                }
            }
            load()
        }
    }

    fun markPresentToday() {
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            when (val result = attendanceRepository.mark(memberId, null)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        working = false,
                        markedPresentNow = true,
                        message = if (result.data.alreadyMarked) "Already present today." else "Marked present today.",
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(working = false, message = messageFor(result.error))
                }
            }
            load()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null, error = null) }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                delay(1000)
                val current = _state.value.undo ?: return@launch
                val remaining = ((current.expiresAtMillis - System.currentTimeMillis()) / 1000).toInt()
                if (remaining <= 0) {
                    _state.update { it.copy(undo = null) }
                    return@launch
                }
                _state.update { it.copy(undo = current.copy(secondsRemaining = remaining)) }
            }
        }
    }

    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Network -> "You're offline. Showing saved data."
        is AppError.Conflict -> error.detail
        is AppError.Validation -> error.fields.values.firstOrNull() ?: error.detail ?: "Check the details."
        else -> error.message ?: "Something went wrong."
    }
}
