package com.muddassir.squadgym.data.repository

import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.data.local.CacheStore
import com.muddassir.squadgym.data.remote.ApiCaller
import com.muddassir.squadgym.data.remote.api.DashboardApi
import com.muddassir.squadgym.data.remote.dto.LeaderboardDto

class LeaderboardRepository(
    private val api: DashboardApi,
    cache: CacheStore,
    caller: ApiCaller,
) : CachedRepository(cache, caller) {

    suspend fun board(period: String, page: Int = 1, pageSize: Int = 50): ApiResult<LeaderboardDto> =
        load("leaderboard:$period:$page:$pageSize", LeaderboardDto.serializer()) {
            api.leaderboard(period, null, page, pageSize)
        }
}
