package com.muddassir.squadgym.data.remote.api

import com.muddassir.squadgym.data.remote.dto.AdminDto
import com.muddassir.squadgym.data.remote.dto.AdminListResponse
import com.muddassir.squadgym.data.remote.dto.AdminStatusRequest
import com.muddassir.squadgym.data.remote.dto.ApiEnvelope
import com.muddassir.squadgym.data.remote.dto.AuditLogResponse
import com.muddassir.squadgym.data.remote.dto.CreateAdminRequest
import com.muddassir.squadgym.data.remote.dto.CreateAdminResponse
import com.muddassir.squadgym.data.remote.dto.GymConfigurationDto
import com.muddassir.squadgym.data.remote.dto.GymDto
import com.muddassir.squadgym.data.remote.dto.GymSettingsPatch
import com.muddassir.squadgym.data.remote.dto.OperatingDaysRequest
import com.muddassir.squadgym.data.remote.dto.PlanDto
import com.muddassir.squadgym.data.remote.dto.PlanPatchRequest
import com.muddassir.squadgym.data.remote.dto.PlanRequest
import com.muddassir.squadgym.data.remote.dto.ShiftDto
import com.muddassir.squadgym.data.remote.dto.ShiftPatchRequest
import com.muddassir.squadgym.data.remote.dto.ShiftRequest
import com.muddassir.squadgym.data.remote.dto.UpdateAdminRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.QueryMap

interface AdminApi {
    @GET("superadmin/admins")
    suspend fun admins(): ApiEnvelope<AdminListResponse>

    @POST("superadmin/admins")
    suspend fun createAdmin(@Body body: CreateAdminRequest): ApiEnvelope<CreateAdminResponse>

    @PATCH("superadmin/admins/{id}")
    suspend fun updateAdmin(@Path("id") id: String, @Body body: UpdateAdminRequest): ApiEnvelope<AdminDto>

    @POST("superadmin/admins/{id}/status")
    suspend fun setAdminStatus(@Path("id") id: String, @Body body: AdminStatusRequest): ApiEnvelope<AdminDto>

    @GET("superadmin/audit-logs")
    suspend fun auditLogs(@QueryMap params: Map<String, String>): ApiEnvelope<AuditLogResponse>

    @GET("superadmin/gym")
    suspend fun gymConfiguration(): ApiEnvelope<GymConfigurationDto>

    @PATCH("superadmin/gym/settings")
    suspend fun updateGymSettings(@Body body: GymSettingsPatch): ApiEnvelope<GymDto>

    @PUT("superadmin/gym/operating-days")
    suspend fun updateOperatingDays(@Body body: OperatingDaysRequest): ApiEnvelope<GymConfigurationDto>

    @POST("superadmin/gym/shifts")
    suspend fun createShift(@Body body: ShiftRequest): ApiEnvelope<ShiftDto>

    @PATCH("superadmin/gym/shifts/{id}")
    suspend fun updateShift(@Path("id") id: String, @Body body: ShiftPatchRequest): ApiEnvelope<ShiftDto>

    @POST("plans")
    suspend fun createPlan(@Body body: PlanRequest): ApiEnvelope<PlanDto>

    @PATCH("plans/{id}")
    suspend fun updatePlan(@Path("id") id: String, @Body body: PlanPatchRequest): ApiEnvelope<PlanDto>

    @DELETE("plans/{id}")
    suspend fun removePlan(@Path("id") id: String): ApiEnvelope<PlanDto>
}
