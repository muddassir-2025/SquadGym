package com.muddassir.squadgym.data.remote.api

import com.muddassir.squadgym.data.remote.dto.ApiEnvelope
import com.muddassir.squadgym.data.remote.dto.ChartDto
import com.muddassir.squadgym.data.remote.dto.DashboardDto
import com.muddassir.squadgym.data.remote.dto.LeaderboardDto
import com.muddassir.squadgym.data.remote.dto.OccupancyDto
import com.muddassir.squadgym.data.remote.dto.PlansResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface DashboardApi {
    @GET("dashboard/today")
    suspend fun dashboard(): ApiEnvelope<DashboardDto>

    @GET("dashboard/chart")
    suspend fun chart(
        @Query("metric") metric: String,
        @Query("range") range: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): ApiEnvelope<ChartDto>

    @GET("dashboard/occupancy")
    suspend fun occupancy(@Query("date") date: String? = null): ApiEnvelope<OccupancyDto>

    @GET("leaderboard")
    suspend fun leaderboard(
        @Query("period") period: String,
        @Query("anchor") anchor: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50,
    ): ApiEnvelope<LeaderboardDto>

    @GET("plans")
    suspend fun plans(): ApiEnvelope<PlansResponse>
}
