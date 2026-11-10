package com.muddassir.squadgym.features.superadmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.core.error.AppError
import com.muddassir.squadgym.data.remote.dto.AuditLogDto
import com.muddassir.squadgym.data.remote.dto.PaginationMeta
import com.muddassir.squadgym.data.repository.AdminRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuditLogUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val items: List<AuditLogDto> = emptyList(),
    val meta: PaginationMeta = PaginationMeta(),
    val search: String = "",
    val error: String? = null,
)

class AuditLogViewModel(private val repository: AdminRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuditLogUiState())
    val state: StateFlow<AuditLogUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.items.isEmpty(), error = null) }
            when (val result = repository.auditLogs(page = 1, search = _state.value.search)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, items = result.data.items, meta = result.data.meta)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.error.message ?: "Couldn't load the log.")
                }
            }
        }
    }

    fun onSearchChange(value: String) {
        _state.update { it.copy(search = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(320)
            load()
        }
    }

    fun loadMore() {
        val current = _state.value
        if (!current.meta.hasNext || current.loadingMore) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            when (val result = repository.auditLogs(page = current.meta.page + 1, search = current.search)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loadingMore = false,
                        items = it.items + result.data.items,
                        meta = result.data.meta,
                    )
                }

                is ApiResult.Failure -> _state.update { it.copy(loadingMore = false) }
            }
        }
    }
}
