package com.muddassir.squadgym.data.remote.api

import com.muddassir.squadgym.data.remote.dto.AcceptInvitationRequest
import com.muddassir.squadgym.data.remote.dto.ApiEnvelope
import com.muddassir.squadgym.data.remote.dto.ForgotPasswordRequest
import com.muddassir.squadgym.data.remote.dto.ForgotPasswordResponse
import com.muddassir.squadgym.data.remote.dto.InvitationPreviewDto
import com.muddassir.squadgym.data.remote.dto.LoginRequest
import com.muddassir.squadgym.data.remote.dto.LogoutRequest
import com.muddassir.squadgym.data.remote.dto.ProfileDto
import com.muddassir.squadgym.data.remote.dto.RefreshRequest
import com.muddassir.squadgym.data.remote.dto.ResetPasswordRequest
import com.muddassir.squadgym.data.remote.dto.SessionDto
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): ApiEnvelope<SessionDto>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): ApiEnvelope<SessionDto>

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequest): ApiEnvelope<JsonObject>

    @GET("auth/me")
    suspend fun me(): ApiEnvelope<ProfileDto>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): ApiEnvelope<ForgotPasswordResponse>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): ApiEnvelope<JsonObject>

    @GET("invitations/{token}")
    suspend fun invitationPreview(@Path("token") token: String): ApiEnvelope<InvitationPreviewDto>

    @POST("invitations/accept")
    suspend fun acceptInvitation(@Body body: AcceptInvitationRequest): ApiEnvelope<SessionDto>
}
