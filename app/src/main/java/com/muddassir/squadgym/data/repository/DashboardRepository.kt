package com.muddassir.squadgym.data.repository

import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.data.local.CacheStore
import com.muddassir.squadgym.data.remote.ApiCaller
import com.muddassir.squadgym.data.remote.api.DashboardApi
import com.muddassir.squadgym.data.remote.dto.ChartDto
import com.muddassir.squadgym.data.remote.dto.DashboardDto
import com.muddassir.squadgym.data.remote.dto.OccupancyDto
import com.muddassir.squadgym.data.remote.dto.PlanDto
import com.muddassir.squadgym.data.remote.dto.PlansResponse

class DashboardRepository(
    private val api: DashboardApi,
    cache: CacheStore,
    caller: ApiCaller,
) : CachedRepository(cache, caller) {

    suspend fun dashboard(): ApiResult<DashboardDto> =
        load("dashboard:today", DashboardDto.serializer()) { api.dashboard() }

    suspend fun chart(
        metric: String = "attendance",
        range: String = "week",
        from: String? = null,
        to: String? = null,
    ): ApiResult<ChartDto> = load(
        "chart:$metric:$range:${from.orEmpty()}:${to.orEmpty()}",
        ChartDto.serializer(),
    ) { api.chart(metric, range, from, to) }

    suspend fun occupancy(date: String? = null): ApiResult<OccupancyDto> =
        load("occupancy:${date.orEmpty()}", OccupancyDto.serializer()) { api.occupancy(date) }

    suspend fun plans(): ApiResult<List<PlanDto>> {
        val result = load("plans", PlansResponse.serializer()) { api.plans() }
        return when (result) {
            is ApiResult.Success -> {
                cache.cachePlans(result.data.plans)
                ApiResult.Success(result.data.plans, result.fromCache, result.cachedAt)
            }

            is ApiResult.Failure -> {
                val cached = cache.cachedPlans()
                if (caller.shouldUseCache(result.error) && cached.isNotEmpty()) {
                    ApiResult.Success(cached, fromCache = true)
                } else {
                    result
                }
            }
        }
    }
}
