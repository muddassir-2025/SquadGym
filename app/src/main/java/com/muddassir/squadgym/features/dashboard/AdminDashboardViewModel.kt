package com.muddassir.squadgym.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.core.error.AppError
import com.muddassir.squadgym.core.error.dataOrNull
import com.muddassir.squadgym.data.remote.dto.ActiveSessionDto
import com.muddassir.squadgym.data.remote.dto.ChartDto
import com.muddassir.squadgym.data.remote.dto.DashboardDto
import com.muddassir.squadgym.data.repository.AttendanceRepository
import com.muddassir.squadgym.data.repository.DashboardRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminDashboardUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val dashboard: DashboardDto? = null,
    val chart: ChartDto? = null,
    val chartLoading: Boolean = false,
    val metric: String = "attendance",
    val range: String = "week",
    val customFrom: String? = null,
    val customTo: String? = null,
    val selectedPoint: Int? = null,
    val error: String? = null,
    val cached: Boolean = false,
    val sessions: List<ActiveSessionDto> = emptyList(),
    val checkingOut: String? = null,
    val message: String? = null,
)

class AdminDashboardViewModel(
    private val dashboardRepository: DashboardRepository,
    private val attendanceRepository: AttendanceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminDashboardUiState())
    val state: StateFlow<AdminDashboardUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = it.dashboard != null, loading = it.dashboard == null, error = null) }

            val dashboardDeferred = async { dashboardRepository.dashboard() }
            val chartDeferred = async { loadChartInternal() }
            val dashboardResult = dashboardDeferred.await()
            val chartResult = chartDeferred.await()

            when (dashboardResult) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        dashboard = dashboardResult.data,
                        cached = dashboardResult.fromCache,
                        error = null,
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = messageFor(dashboardResult.error),
                    )
                }
            }

            val chart = chartResult.dataOrNull()
            if (chart != null) {
                _state.update { it.copy(chart = chart, chartLoading = false) }
            } else {
                _state.update { it.copy(chartLoading = false) }
            }

            refreshSessions()
        }
    }

    fun selectMetric(metric: String) {
        if (_state.value.metric == metric) return
        _state.update { it.copy(metric = metric, selectedPoint = null) }
        loadChart()
    }

    fun selectRange(range: String) {
        if (range == "custom") return
        _state.update { it.copy(range = range, customFrom = null, customTo = null, selectedPoint = null) }
        loadChart()
    }

    fun applyCustomRange(from: String, to: String) {
        _state.update { it.copy(range = "custom", customFrom = from, customTo = to, selectedPoint = null) }
        loadChart()
    }

    fun selectPoint(index: Int) = _state.update { it.copy(selectedPoint = index) }

    fun loadChart() {
        viewModelScope.launch {
            _state.update { it.copy(chartLoading = true) }
            val result = loadChartInternal()
            val chart = result.dataOrNull()
            _state.update {
                it.copy(chart = chart ?: it.chart, chartLoading = false)
            }
        }
    }

    private suspend fun loadChartInternal(): ApiResult<ChartDto> {
        val current = _state.value
        return dashboardRepository.chart(
            metric = current.metric,
            range = current.range,
            from = current.customFrom,
            to = current.customTo,
        )
    }

    fun refreshSessions() {
        viewModelScope.launch {
            val result = attendanceRepository.activeSessions()
            if (result is ApiResult.Success) {
                _state.update { it.copy(sessions = result.data) }
            }
        }
    }

    fun checkOut(sessionId: String, memberName: String) {
        if (_state.value.checkingOut != null) return
        viewModelScope.launch {
            _state.update { it.copy(checkingOut = sessionId) }
            when (val result = attendanceRepository.checkout(sessionId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        checkingOut = null,
                        sessions = it.sessions.filterNot { session -> session.id == sessionId },
                        message = "$memberName checked out. Attendance is untouched.",
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(checkingOut = null, message = messageFor(result.error))
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Network -> "You're offline — showing the last saved snapshot."
        else -> error.message ?: "Something went wrong."
    }
}
