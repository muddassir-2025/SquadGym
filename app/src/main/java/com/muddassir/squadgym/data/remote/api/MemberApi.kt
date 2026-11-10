package com.muddassir.squadgym.data.remote.api

import com.muddassir.squadgym.data.remote.dto.ApiEnvelope
import com.muddassir.squadgym.data.remote.dto.ArchiveMemberRequest
import com.muddassir.squadgym.data.remote.dto.ArchiveMemberResponse
import com.muddassir.squadgym.data.remote.dto.CreateMemberRequest
import com.muddassir.squadgym.data.remote.dto.CreateMemberResponse
import com.muddassir.squadgym.data.remote.dto.MemberDetailDto
import com.muddassir.squadgym.data.remote.dto.MemberListDto
import com.muddassir.squadgym.data.remote.dto.MembershipHistoryResponse
import com.muddassir.squadgym.data.remote.dto.PhotoUploadResponse
import com.muddassir.squadgym.data.remote.dto.RenewRequest
import com.muddassir.squadgym.data.remote.dto.RenewalPreviewDto
import com.muddassir.squadgym.data.remote.dto.RenewalResultDto
import com.muddassir.squadgym.data.remote.dto.UpdateMemberRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface MemberApi {
    @GET("members")
    suspend fun list(@QueryMap params: Map<String, String>): ApiEnvelope<MemberListDto>

    @GET("members/{id}")
    suspend fun detail(@Path("id") id: String): ApiEnvelope<MemberDetailDto>

    @POST("members")
    suspend fun create(@Body body: CreateMemberRequest): ApiEnvelope<CreateMemberResponse>

    @PATCH("members/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdateMemberRequest): ApiEnvelope<com.muddassir.squadgym.data.remote.dto.MemberDto>

    @POST("members/{id}/archive")
    suspend fun archive(
        @Path("id") id: String,
        @Body body: ArchiveMemberRequest = ArchiveMemberRequest(),
    ): ApiEnvelope<ArchiveMemberResponse>

    @POST("members/{id}/restore")
    suspend fun restore(@Path("id") id: String): ApiEnvelope<ArchiveMemberResponse>

    @Multipart
    @POST("members/{id}/photo")
    suspend fun uploadPhoto(
        @Path("id") id: String,
        @Part photo: MultipartBody.Part,
    ): ApiEnvelope<PhotoUploadResponse>

    @GET("members/{id}/memberships")
    suspend fun membershipHistory(@Path("id") id: String): ApiEnvelope<MembershipHistoryResponse>

    @GET("members/{id}/renewal-preview")
    suspend fun renewalPreview(
        @Path("id") id: String,
        @Query("planId") planId: String,
    ): ApiEnvelope<RenewalPreviewDto>

    @POST("members/{id}/renewals")
    suspend fun renew(
        @Path("id") id: String,
        @Body body: RenewRequest,
    ): ApiEnvelope<RenewalResultDto>
}
