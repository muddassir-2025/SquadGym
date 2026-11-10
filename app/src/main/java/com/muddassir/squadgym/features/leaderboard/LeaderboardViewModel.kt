package com.muddassir.squadgym.features.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.core.error.AppError
import com.muddassir.squadgym.data.remote.dto.LeaderboardDto
import com.muddassir.squadgym.data.repository.LeaderboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LeaderboardUiState(
    val loading: Boolean = true,
    val data: LeaderboardDto? = null,
    val period: String = "week",
    val cached: Boolean = false,
    val error: String? = null,
)

class LeaderboardViewModel(
    private val repository: LeaderboardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LeaderboardUiState())
    val state: StateFlow<LeaderboardUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.data == null, error = null) }
            when (val result = repository.board(_state.value.period)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, data = result.data, cached = result.fromCache)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = messageFor(result.error))
                }
            }
        }
    }

    fun selectPeriod(period: String) {
        if (_state.value.period == period) return
        _state.update { it.copy(period = period, loading = true) }
        load()
    }

    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Network -> "You're offline. Showing the last saved leaderboard."
        else -> error.message ?: "Something went wrong."
    }
}
