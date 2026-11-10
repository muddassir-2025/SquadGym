package com.muddassir.squadgym.features.member

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.core.error.AppError
import com.muddassir.squadgym.data.remote.dto.MemberHomeDto
import com.muddassir.squadgym.data.repository.MeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemberHomeUiState(
    val loading: Boolean = true,
    val data: MemberHomeDto? = null,
    val month: String? = null,
    val cached: Boolean = false,
    val error: String? = null,
)

class MemberHomeViewModel(private val repository: MeRepository) : ViewModel() {

    private val _state = MutableStateFlow(MemberHomeUiState())
    val state: StateFlow<MemberHomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.data == null, error = null) }
            when (val result = repository.home(_state.value.month)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, data = result.data, cached = result.fromCache, month = result.data.calendarMonth)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = messageFor(result.error))
                }
            }
        }
    }

    fun shiftMonth(delta: Long) {
        val current = _state.value.data?.calendarMonth
            ?: com.muddassir.squadgym.core.util.DateText.todayIso().take(7)
        val next = com.muddassir.squadgym.core.util.DateText.shiftMonth(current, delta)
        _state.update { it.copy(month = next, loading = true) }
        load()
    }

    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Network -> "You're offline. Showing your last saved summary."
        else -> error.message ?: "Something went wrong."
    }
}
