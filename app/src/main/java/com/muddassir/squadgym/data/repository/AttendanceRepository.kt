package com.muddassir.squadgym.data.repository

import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.core.error.map
import com.muddassir.squadgym.data.local.CacheStore
import com.muddassir.squadgym.data.remote.ApiCaller
import com.muddassir.squadgym.data.remote.api.AttendanceApi
import com.muddassir.squadgym.data.remote.dto.ActiveSessionDto
import com.muddassir.squadgym.data.remote.dto.ActiveSessionsDto
import com.muddassir.squadgym.data.remote.dto.AttendanceDashboardDto
import com.muddassir.squadgym.data.remote.dto.CorrectionRequest
import com.muddassir.squadgym.data.remote.dto.CorrectionResponse
import com.muddassir.squadgym.data.remote.dto.MarkPresentRequest
import com.muddassir.squadgym.data.remote.dto.MarkPresentResponse
import com.muddassir.squadgym.data.remote.dto.UndoAttendanceResponse

class AttendanceRepository(
    private val api: AttendanceApi,
    cache: CacheStore,
    caller: ApiCaller,
) : CachedRepository(cache, caller) {

    data class PageFilter(
        val date: String? = null,
        val tab: String = "all",
        val search: String = "",
        val shiftId: String? = null,
        val gender: String? = null,
        val planId: String? = null,
        val page: Int = 1,
        val pageSize: Int = 30,
    ) {
        fun toQuery(): Map<String, String> = buildMap {
            put("tab", tab)
            put("page", page.toString())
            put("pageSize", pageSize.toString())
            date?.let { put("date", it) }
            if (search.isNotBlank()) put("search", search.trim())
            shiftId?.let { put("shiftId", it) }
            gender?.let { put("gender", it) }
            planId?.let { put("planId", it) }
        }

        val cacheKey: String
            get() = "attendance:${date.orEmpty()}:${tab}:${search.trim()}:${shiftId.orEmpty()}:" +
                "${gender.orEmpty()}:${planId.orEmpty()}:${page}:${pageSize}"
    }

    suspend fun dashboard(filter: PageFilter): ApiResult<AttendanceDashboardDto> =
        load(filter.cacheKey, AttendanceDashboardDto.serializer()) { api.dashboard(filter.toQuery()) }

    suspend fun mark(memberId: String, date: String? = null): ApiResult<MarkPresentResponse> =
        caller.call { api.mark(MarkPresentRequest(memberId, date)) }

    suspend fun undo(attendanceId: String): ApiResult<UndoAttendanceResponse> =
        caller.call { api.undo(attendanceId) }

    suspend fun correct(
        memberId: String,
        date: String,
        action: String,
        reason: String,
    ): ApiResult<CorrectionResponse> =
        caller.call { api.correct(CorrectionRequest(memberId, date, action, reason)) }

    suspend fun activeSessions(): ApiResult<List<ActiveSessionDto>> {
        val result = caller.call { api.activeSessions() }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(result.data.items)
            is ApiResult.Failure -> result
        }
    }

    suspend fun checkout(sessionId: String): ApiResult<Boolean> =
        caller.call { api.checkout(sessionId) }.map { true }

    suspend fun refreshOf(filter: PageFilter): ApiResult<AttendanceDashboardDto> = dashboard(filter)
}
