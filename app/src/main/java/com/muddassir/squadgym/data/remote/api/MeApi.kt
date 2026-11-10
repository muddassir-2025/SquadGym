package com.muddassir.squadgym.data.remote.api

import com.muddassir.squadgym.data.remote.dto.ApiEnvelope
import com.muddassir.squadgym.data.remote.dto.CalendarDto
import com.muddassir.squadgym.data.remote.dto.MemberDto
import com.muddassir.squadgym.data.remote.dto.MemberHomeDto
import com.muddassir.squadgym.data.remote.dto.MembershipDto
import com.muddassir.squadgym.data.remote.dto.MyAttendanceHistoryDto
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class MyMembershipResponse(val membership: MembershipDto? = null)

interface MeApi {
    @GET("me/dashboard")
    suspend fun dashboard(@Query("month") month: String? = null): ApiEnvelope<MemberHomeDto>

    @GET("me/calendar")
    suspend fun calendar(@Query("month") month: String): ApiEnvelope<CalendarDto>

    @GET("me/profile")
    suspend fun profile(): ApiEnvelope<MemberDto>

    @GET("me/membership")
    suspend fun membership(): ApiEnvelope<MyMembershipResponse>

    @GET("me/attendance")
    suspend fun attendance(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): ApiEnvelope<MyAttendanceHistoryDto>
}
