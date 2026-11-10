package com.muddassir.squadgym.features.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.core.error.AppError
import com.muddassir.squadgym.data.remote.dto.MemberListDto
import com.muddassir.squadgym.data.remote.dto.PlanDto
import com.muddassir.squadgym.data.repository.DashboardRepository
import com.muddassir.squadgym.data.repository.MemberRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MembersUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val data: MemberListDto? = null,
    val tab: String = "all",
    val search: String = "",
    val sort: String = "name_asc",
    val gender: String? = null,
    val planId: String? = null,
    val shiftId: String? = null,
    val status: String? = null,
    val cached: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val filtersActive: Boolean
        get() = gender != null || planId != null || shiftId != null || status != null
    val hasMore: Boolean get() = data?.meta?.hasNext == true
    val page: Int get() = data?.meta?.page ?: 1
}

class MembersViewModel(
    private val memberRepository: MemberRepository,
    private val dashboardRepository: DashboardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MembersUiState())
    val state: StateFlow<MembersUiState> = _state.asStateFlow()

    var plans: List<PlanDto> = emptyList()
        private set

    private var searchJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            when (val result = dashboardRepository.plans()) {
                is ApiResult.Success -> plans = result.data
                is ApiResult.Failure -> plans = emptyList()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.data == null, error = null) }
            when (val result = memberRepository.list(filter(page = 1))) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, data = result.data, cached = result.fromCache, error = null)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = messageFor(result.error))
                }
            }
        }
    }

    fun selectTab(tab: String) {
        if (_state.value.tab == tab) return
        _state.update { it.copy(tab = tab, loading = true, data = it.data) }
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

    fun setSort(sort: String) {
        _state.update { it.copy(sort = sort, loading = true) }
        refresh()
    }

    fun setGender(gender: String?) = applyFilter { it.copy(gender = gender) }
    fun setPlan(planId: String?) = applyFilter { it.copy(planId = planId) }
    fun setShift(shiftId: String?) = applyFilter { it.copy(shiftId = shiftId) }
    fun setStatus(status: String?) = applyFilter { it.copy(status = status) }

    private fun applyFilter(transform: (MembersUiState) -> MembersUiState) {
        _state.update { transform(it).copy(loading = true) }
        refresh()
    }

    fun clearFilters() {
        _state.update {
            it.copy(gender = null, planId = null, shiftId = null, status = null, loading = true)
        }
        refresh()
    }

    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.loadingMore || current.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            when (val result = memberRepository.list(filter(page = current.page + 1))) {
                is ApiResult.Success -> _state.update { state ->
                    val merged = state.data?.copy(
                        members = state.data.members + result.data.members,
                        meta = result.data.meta,
                    )
                    state.copy(data = merged, loadingMore = false)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loadingMore = false, message = messageFor(result.error))
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null, error = null) }

    private fun filter(page: Int) = MemberRepository.ListFilter(
        tab = _state.value.tab,
        search = _state.value.search,
        planId = _state.value.planId,
        shiftId = _state.value.shiftId,
        gender = _state.value.gender,
        status = _state.value.status,
        sort = _state.value.sort,
        page = page,
    )

    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Network -> "You're offline. Showing saved members."
        is AppError.Conflict -> error.detail
        else -> error.message ?: "Something went wrong."
    }
}
