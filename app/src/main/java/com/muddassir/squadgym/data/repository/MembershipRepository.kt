package com.muddassir.squadgym.data.repository

import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.data.local.CacheStore
import com.muddassir.squadgym.data.remote.ApiCaller
import com.muddassir.squadgym.data.remote.api.DashboardApi
import com.muddassir.squadgym.data.remote.api.MemberApi
import com.muddassir.squadgym.data.remote.api.MembershipApi
import com.muddassir.squadgym.data.remote.dto.PlanDto
import com.muddassir.squadgym.data.remote.dto.PlansResponse
import com.muddassir.squadgym.data.remote.dto.RenewRequest
import com.muddassir.squadgym.data.remote.dto.RenewalPreviewDto
import com.muddassir.squadgym.data.remote.dto.RenewalResultDto
import com.muddassir.squadgym.data.remote.dto.UndoRenewalResultDto

class MembershipRepository(
    private val memberApi: MemberApi,
    private val membershipApi: MembershipApi,
    private val dashboardApi: DashboardApi,
    cache: CacheStore,
    caller: ApiCaller,
) : CachedRepository(cache, caller) {

    suspend fun plans(): ApiResult<List<PlanDto>> {
        val result = load("plans", PlansResponse.serializer()) { dashboardApi.plans() }
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

    suspend fun preview(memberId: String, planId: String): ApiResult<RenewalPreviewDto> =
        caller.call { memberApi.renewalPreview(memberId, planId) }

    suspend fun renew(memberId: String, planId: String): ApiResult<RenewalResultDto> =
        caller.call { memberApi.renew(memberId, RenewRequest(planId)) }

    suspend fun undoRenewal(renewalId: String): ApiResult<UndoRenewalResultDto> =
        caller.call { membershipApi.undoRenewal(renewalId) }
}
