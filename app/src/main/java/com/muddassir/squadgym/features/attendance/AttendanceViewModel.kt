package com.muddassir.squadgym.features.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.core.error.AppError
import com.muddassir.squadgym.data.remote.dto.AttendanceDashboardDto
import com.muddassir.squadgym.data.remote.dto.AttendanceMemberDto
import com.muddassir.squadgym.data.remote.dto.PlanDto
import com.muddassir.squadgym.data.remote.dto.ShiftDto
import com.muddassir.squadgym.data.repository.AttendanceRepository
import com.muddassir.squadgym.data.repository.DashboardRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UndoBannerState(
    val attendanceId: String,
    val memberId: String,
    val memberName: String,
    val secondsRemaining: Int,
    val expiresAtMillis: Long,
)

data class AttendanceUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val data: AttendanceDashboardDto? = null,
    val tab: String = "all",
    val search: String = "",
    val shiftId: String? = null,
    val gender: String? = null,
    val planId: String? = null,
    val cached: Boolean = false,
    val error: String? = null,
    val markingMemberId: String? = null,
    val undoing: Boolean = false,
    val undo: UndoBannerState? = null,
    val message: String? = null,
    val correcting: Boolean = false,
) {
    val page: Int get() = data?.meta?.page ?: 1
    val hasMore: Boolean get() = data?.meta?.hasNext == true
    val filtersActive: Boolean
        get() = search.isNotBlank() || shiftId != null || gender != null || planId != null
}

class AttendanceViewModel(
    private val attendanceRepository: AttendanceRepository,
    private val dashboardRepository: DashboardRepository,
    private val shifts: List<ShiftDto>,
) : ViewModel() {

    private val _state = MutableStateFlow(AttendanceUiState())
    val state: StateFlow<AttendanceUiState> = _state.asStateFlow()

    var plans: List<PlanDto> = emptyList()
        private set

    private var searchJob: Job? = null
    private var undoJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            when (val result = dashboardRepository.plans()) {
                is ApiResult.Success -> plans = result.data
                is ApiResult.Failure -> plans = emptyList()
            }
        }
    }

    val shiftOptions: List<Pair<String, String>>
        get() = shifts.map { it.id to it.name }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.data == null, error = null) }
            val result = attendanceRepository.dashboard(filter(page = 1))
            when (result) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        data = result.data,
                        cached = result.fromCache,
                        error = null,
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = messageFor(result.error))
                }
            }
        }
    }

    fun selectTab(tab: String) {
        if (_state.value.tab == tab) return
        _state.update { it.copy(tab = tab, loading = true) }
        refresh()
    }

    fun onSearchChange(value: String) {
        _state.update { it.copy(search = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(320)
            _state.update { it.copy(loading = true) }
            refresh()
        }
    }

    fun setShift(shiftId: String?) {
        _state.update { it.copy(shiftId = shiftId, loading = true) }
        refresh()
    }

    fun setGender(gender: String?) {
        _state.update { it.copy(gender = gender, loading = true) }
        refresh()
    }

    fun setPlan(planId: String?) {
        _state.update { it.copy(planId = planId, loading = true) }
        refresh()
    }

    fun clearFilters() {
        _state.update {
            it.copy(search = "", shiftId = null, gender = null, planId = null, loading = true)
        }
        refresh()
    }

    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.loadingMore || current.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            val nextPage = current.page + 1
            when (val result = attendanceRepository.dashboard(filter(page = nextPage))) {
                is ApiResult.Success -> _state.update { state ->
                    val merged = state.data?.copy(
                        members = state.data.members + result.data.members,
                        meta = result.data.meta,
                        summary = result.data.summary,
                        gymTotals = result.data.gymTotals,
                        inGymNow = result.data.inGymNow,
                    )
                    state.copy(data = merged, loadingMore = false)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loadingMore = false, message = messageFor(result.error))
                }
            }
        }
    }

    fun markPresent(member: AttendanceMemberDto) {
        if (_state.value.markingMemberId != null) return
        viewModelScope.launch {
            _state.update { it.copy(markingMemberId = member.memberId, error = null) }
            when (val result = attendanceRepository.mark(member.memberId, _state.value.data?.date)) {
                is ApiResult.Success -> {
                    val response = result.data
                    _state.update {
                        it.copy(
                            markingMemberId = null,
                            message = if (response.alreadyMarked) {
                                "${member.fullName} was already marked present today."
                            } else {
                                "${member.fullName} marked present."
                            },
                            undo = if (response.undo.canUndo) {
                                UndoBannerState(
                                    attendanceId = response.attendance.id,
                                    memberId = member.memberId,
                                    memberName = member.fullName,
                                    secondsRemaining = response.undo.secondsRemaining,
                                    expiresAtMillis = System.currentTimeMillis() +
                                        response.undo.secondsRemaining * 1000L,
                                )
                            } else {
                                it.undo
                            },
                        )
                    }
                    startUndoTicker()
                    refresh()
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(
                        markingMemberId = null,
                        // Never pretend an unconfirmed action succeeded.
                        error = when (result.error) {
                            is AppError.Network -> "Not confirmed by the server. Check your connection and try again."
                            else -> messageFor(result.error)
                        },
                    )
                }
            }
        }
    }

    fun undoLastMark() {
        val undo = _state.value.undo ?: return
        if (_state.value.undoing) return
        viewModelScope.launch {
            _state.update { it.copy(undoing = true) }
            when (val result = attendanceRepository.undo(undo.attendanceId)) {
                is ApiResult.Success -> {
                    undoJob?.cancel()
                    _state.update {
                        it.copy(
                            undoing = false,
                            undo = null,
                            message = "${undo.memberName}'s check-in was undone.",
                            error = null,
                        )
                    }
                    refresh()
                }

                is ApiResult.Failure -> {
                    undoJob?.cancel()
                    _state.update {
                        it.copy(
                            undoing = false,
                            undo = null,
                            error = when (result.error) {
                                is AppError.Conflict -> result.error.detail
                                else -> messageFor(result.error)
                            },
                        )
                    }
                    refresh()
                }
            }
        }
    }

    fun undoMember(member: AttendanceMemberDto) {
        val attendanceId = member.attendanceId ?: return
        if (!member.canUndo || _state.value.undoing) return
        viewModelScope.launch {
            _state.update { it.copy(undoing = true, undo = null) }
            undoJob?.cancel()
            when (val result = attendanceRepository.undo(attendanceId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(undoing = false, message = "${member.fullName}'s check-in was undone.") }
                    refresh()
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(
                        undoing = false,
                        error = (result.error as? AppError.Conflict)?.detail ?: messageFor(result.error),
                    )
                }
            }
        }
    }

    fun correctRemove(member: AttendanceMemberDto, reason: String) {
        val date = _state.value.data?.date ?: return
        viewModelScope.launch {
            _state.update { it.copy(correcting = true) }
            when (val result = attendanceRepository.correct(member.memberId, date, "remove", reason)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(correcting = false, message = "Correction saved and logged for ${member.fullName}.")
                    }
                    refresh()
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(
                        correcting = false,
                        error = (result.error as? AppError.Conflict)?.detail ?: messageFor(result.error),
                    )
                }
            }
        }
    }

    fun dismissUndo() {
        undoJob?.cancel()
        _state.update { it.copy(undo = null) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null, error = null) }

    private fun startUndoTicker() {
        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val current = _state.value.undo ?: return@launch
                val remaining = ((current.expiresAtMillis - System.currentTimeMillis()) / 1000).toInt()
                if (remaining <= 0) {
                    _state.update { it.copy(undo = null, message = "Undo window closed. Use a correction if needed.") }
                    return@launch
                }
                _state.update { it.copy(undo = current.copy(secondsRemaining = remaining)) }
            }
        }
    }

    private fun filter(page: Int) = AttendanceRepository.PageFilter(
        date = _state.value.data?.date,
        tab = _state.value.tab,
        search = _state.value.search,
        shiftId = _state.value.shiftId,
        gender = _state.value.gender,
        planId = _state.value.planId,
        page = page,
    )

    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Network -> "You're offline. Showing saved data."
        is AppError.Conflict -> error.detail
        is AppError.Validation -> error.detail ?: "Check the details and try again."
        else -> error.message ?: "Something went wrong."
    }
}
