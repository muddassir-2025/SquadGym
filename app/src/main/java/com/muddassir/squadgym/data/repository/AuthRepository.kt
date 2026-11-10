package com.muddassir.squadgym.data.repository

import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.data.local.CacheStore
import com.muddassir.squadgym.data.local.SecureTokenStore
import com.muddassir.squadgym.data.remote.ApiCaller
import com.muddassir.squadgym.data.remote.api.AuthApi
import com.muddassir.squadgym.data.remote.dto.AcceptInvitationRequest
import com.muddassir.squadgym.data.remote.dto.ForgotPasswordRequest
import com.muddassir.squadgym.data.remote.dto.ForgotPasswordResponse
import com.muddassir.squadgym.data.remote.dto.InvitationPreviewDto
import com.muddassir.squadgym.data.remote.dto.LoginRequest
import com.muddassir.squadgym.data.remote.dto.LogoutRequest
import com.muddassir.squadgym.data.remote.dto.ProfileDto
import com.muddassir.squadgym.data.remote.dto.ResetPasswordRequest
import com.muddassir.squadgym.data.remote.dto.SessionDto
import com.muddassir.squadgym.data.session.SessionManager
import kotlinx.serialization.json.JsonObject

class AuthRepository(
    private val authApi: AuthApi,
    private val caller: ApiCaller,
    private val sessionManager: SessionManager,
    private val tokenStore: SecureTokenStore,
    private val cache: CacheStore,
) {
    suspend fun login(email: String, password: String): ApiResult<SessionDto> {
        val result = caller.call { authApi.login(LoginRequest(email.trim(), password)) }
        if (result is ApiResult.Success) sessionManager.onSignedIn(result.data)
        return result
    }

    suspend fun acceptInvitation(
        token: String,
        password: String,
        fullName: String? = null,
    ): ApiResult<SessionDto> {
        val result = caller.call { authApi.acceptInvitation(AcceptInvitationRequest(token, password, fullName)) }
        if (result is ApiResult.Success) sessionManager.onSignedIn(result.data)
        return result
    }

    suspend fun invitationPreview(token: String): ApiResult<InvitationPreviewDto> =
        caller.call { authApi.invitationPreview(token) }

    /** Confirms the stored session and refreshes the cached profile. */
    suspend fun refreshProfile(): ApiResult<ProfileDto> {
        val result = caller.call { authApi.me() }
        if (result is ApiResult.Success) sessionManager.onProfileLoaded(result.data)
        return result
    }

    suspend fun forgotPassword(email: String): ApiResult<ForgotPasswordResponse> =
        caller.call { authApi.forgotPassword(ForgotPasswordRequest(email.trim())) }

    suspend fun resetPassword(token: String, newPassword: String): ApiResult<JsonObject> =
        caller.call { authApi.resetPassword(ResetPasswordRequest(token.trim().uppercase(), newPassword)) }

    /** Best-effort server-side revocation; local state is cleared regardless. */
    suspend fun signOut() {
        val refresh = tokenStore.refreshToken
        if (refresh != null) {
            runCatching { authApi.logout(LogoutRequest(refresh)) }
        }
        cache.clearAll()
        sessionManager.signedOut()
    }

    fun restoreCachedSession(): Boolean = sessionManager.restoreFromCache()
}
