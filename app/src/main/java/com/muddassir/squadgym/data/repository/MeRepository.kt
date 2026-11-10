package com.muddassir.squadgym.data.repository

import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.core.error.map
import com.muddassir.squadgym.data.local.CacheStore
import com.muddassir.squadgym.data.remote.ApiCaller
import com.muddassir.squadgym.data.remote.api.MeApi
import com.muddassir.squadgym.data.remote.dto.CalendarDto
import com.muddassir.squadgym.data.remote.dto.MemberDto
import com.muddassir.squadgym.data.remote.dto.MemberHomeDto
import com.muddassir.squadgym.data.remote.dto.MembershipDto
import com.muddassir.squadgym.data.remote.dto.MyAttendanceHistoryDto

class MeRepository(
    private val api: MeApi,
    cache: CacheStore,
    caller: ApiCaller,
) : CachedRepository(cache, caller) {

    suspend fun home(month: String? = null): ApiResult<MemberHomeDto> =
        load("me:home:${month.orEmpty()}", MemberHomeDto.serializer()) { api.dashboard(month) }

    suspend fun calendar(month: String): ApiResult<CalendarDto> =
        load("me:calendar:$month", CalendarDto.serializer()) { api.calendar(month) }

    suspend fun profile(): ApiResult<MemberDto> =
        load("me:profile", MemberDto.serializer()) { api.profile() }

    suspend fun membership(): ApiResult<MembershipDto?> =
        caller.call { api.membership() }.map { it.membership }

    suspend fun attendance(from: String? = null, to: String? = null): ApiResult<MyAttendanceHistoryDto> =
        caller.call { api.attendance(from, to) }
}
