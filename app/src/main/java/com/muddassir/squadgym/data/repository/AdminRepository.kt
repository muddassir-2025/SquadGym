package com.muddassir.squadgym.data.repository

import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.data.local.CacheStore
import com.muddassir.squadgym.data.remote.ApiCaller
import com.muddassir.squadgym.data.remote.api.AdminApi
import com.muddassir.squadgym.data.remote.dto.AdminDto
import com.muddassir.squadgym.data.remote.dto.AdminListResponse
import com.muddassir.squadgym.data.remote.dto.AdminStatusRequest
import com.muddassir.squadgym.data.remote.dto.AuditLogResponse
import com.muddassir.squadgym.data.remote.dto.CreateAdminRequest
import com.muddassir.squadgym.data.remote.dto.CreateAdminResponse
import com.muddassir.squadgym.data.remote.dto.GymConfigurationDto
import com.muddassir.squadgym.data.remote.dto.GymDto
import com.muddassir.squadgym.data.remote.dto.GymSettingsPatch
import com.muddassir.squadgym.data.remote.dto.OperatingDayUpdateDto
import com.muddassir.squadgym.data.remote.dto.OperatingDaysRequest
import com.muddassir.squadgym.data.remote.dto.PlanDto
import com.muddassir.squadgym.data.remote.dto.PlanPatchRequest
import com.muddassir.squadgym.data.remote.dto.PlanRequest
import com.muddassir.squadgym.data.remote.dto.ShiftDto
import com.muddassir.squadgym.data.remote.dto.ShiftPatchRequest
import com.muddassir.squadgym.data.remote.dto.ShiftRequest
import com.muddassir.squadgym.data.remote.dto.UpdateAdminRequest

class AdminRepository(
    private val api: AdminApi,
    cache: CacheStore,
    caller: ApiCaller,
) : CachedRepository(cache, caller) {

    suspend fun admins(): ApiResult<List<AdminDto>> {
        val result = load("admins", AdminListResponse.serializer()) { api.admins() }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(result.data.admins, result.fromCache, result.cachedAt)
            is ApiResult.Failure -> result
        }
    }

    suspend fun createAdmin(request: CreateAdminRequest): ApiResult<CreateAdminResponse> =
        caller.call { api.createAdmin(request) }

    suspend fun updateAdmin(adminId: String, request: UpdateAdminRequest): ApiResult<AdminDto> =
        caller.call { api.updateAdmin(adminId, request) }

    suspend fun setStatus(adminId: String, status: String): ApiResult<AdminDto> =
        caller.call { api.setAdminStatus(adminId, AdminStatusRequest(status)) }

    suspend fun auditLogs(
        page: Int = 1,
        pageSize: Int = 30,
        action: String? = null,
        from: String? = null,
        to: String? = null,
        search: String? = null,
    ): ApiResult<AuditLogResponse> {
        val params = buildMap {
            put("page", page.toString())
            put("pageSize", pageSize.toString())
            action?.let { put("action", it) }
            from?.let { put("from", it) }
            to?.let { put("to", it) }
            search?.takeIf { it.isNotBlank() }?.let { put("search", it.trim()) }
        }
        return load("audit:$params", AuditLogResponse.serializer()) { api.auditLogs(params) }
    }

    suspend fun gymConfiguration(): ApiResult<GymConfigurationDto> =
        load("gym:configuration", GymConfigurationDto.serializer()) { api.gymConfiguration() }

    suspend fun updateSettings(patch: GymSettingsPatch): ApiResult<GymDto> =
        caller.call { api.updateGymSettings(patch) }

    suspend fun updateOperatingDays(days: List<OperatingDayUpdateDto>): ApiResult<GymConfigurationDto> {
        val result = caller.call { api.updateOperatingDays(OperatingDaysRequest(days)) }
        if (result is ApiResult.Success) {
            cache.put("gym:configuration", GymConfigurationDto.serializer(), result.data)
        }
        return result
    }

    suspend fun createShift(request: ShiftRequest): ApiResult<ShiftDto> =
        caller.call { api.createShift(request) }

    suspend fun updateShift(shiftId: String, request: ShiftPatchRequest): ApiResult<ShiftDto> =
        caller.call { api.updateShift(shiftId, request) }

    suspend fun createPlan(request: PlanRequest): ApiResult<PlanDto> =
        caller.call { api.createPlan(request) }

    suspend fun updatePlan(planId: String, request: PlanPatchRequest): ApiResult<PlanDto> =
        caller.call { api.updatePlan(planId, request) }

    /** Hides a plan without touching memberships that already reference it. */
    suspend fun removePlan(planId: String): ApiResult<PlanDto> =
        caller.call { api.removePlan(planId) }
}
