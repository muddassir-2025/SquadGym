package com.muddassir.squadgym.data.remote.api

import com.muddassir.squadgym.data.remote.dto.ActiveSessionsDto
import com.muddassir.squadgym.data.remote.dto.ApiEnvelope
import com.muddassir.squadgym.data.remote.dto.AttendanceDashboardDto
import com.muddassir.squadgym.data.remote.dto.CorrectionRequest
import com.muddassir.squadgym.data.remote.dto.CorrectionResponse
import com.muddassir.squadgym.data.remote.dto.MarkPresentRequest
import com.muddassir.squadgym.data.remote.dto.MarkPresentResponse
import com.muddassir.squadgym.data.remote.dto.UndoAttendanceResponse
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.QueryMap

interface AttendanceApi {
    @GET("attendance")
    suspend fun dashboard(@QueryMap params: Map<String, String>): ApiEnvelope<AttendanceDashboardDto>

    @POST("attendance/mark")
    suspend fun mark(@Body body: MarkPresentRequest): ApiEnvelope<MarkPresentResponse>

    @POST("attendance/{id}/undo")
    suspend fun undo(@Path("id") attendanceId: String): ApiEnvelope<UndoAttendanceResponse>

    @POST("attendance/corrections")
    suspend fun correct(@Body body: CorrectionRequest): ApiEnvelope<CorrectionResponse>

    @GET("attendance/sessions/active")
    suspend fun activeSessions(): ApiEnvelope<ActiveSessionsDto>

    @POST("attendance/sessions/{id}/checkout")
    suspend fun checkout(@Path("id") sessionId: String): ApiEnvelope<JsonObject>
}
