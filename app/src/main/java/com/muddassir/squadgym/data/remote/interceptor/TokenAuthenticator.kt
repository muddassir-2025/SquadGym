package com.muddassir.squadgym.data.remote.interceptor

import com.muddassir.squadgym.data.local.SecureTokenStore
import com.muddassir.squadgym.data.remote.api.AuthApi
import com.muddassir.squadgym.data.remote.dto.RefreshRequest
import com.muddassir.squadgym.data.remote.dto.SessionDto
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Silent session refresh.
 *
 * The access token is short-lived (15 min); when a request comes back 401 the
 * refresh token is exchanged for a new pair and the request is retried once.
 * Concurrent 401s are serialised: the first thread refreshes, the rest reuse the
 * new access token.
 */
class TokenAuthenticator(
    private val tokenStore: SecureTokenStore,
    private val refreshApiProvider: () -> AuthApi,
    private val onSessionRefreshed: (SessionDto) -> Unit,
    private val onRefreshFailed: () -> Unit,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        // Only an authenticated request can be refreshed. A 401 on sign-in or an
        // invitation preview must never clear an unrelated session.
        val originalAuth = response.request.header("Authorization") ?: return null

        val requestToken = originalAuth.removePrefix("Bearer ")
        val currentToken = tokenStore.accessToken

        // Another thread already refreshed while this request was in flight.
        if (!currentToken.isNullOrBlank() && currentToken != requestToken) {
            return response.request.newBuilder()
                .header("Authorization", "Bearer $currentToken")
                .build()
        }

        val refreshToken = tokenStore.refreshToken
        if (refreshToken.isNullOrBlank()) {
            onRefreshFailed()
            return null
        }

        synchronized(lock) {
            val latest = tokenStore.accessToken
            if (!latest.isNullOrBlank() && latest != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $latest")
                    .build()
            }

            val session = runBlocking {
                runCatching { refreshApiProvider().refresh(RefreshRequest(refreshToken)).data }.getOrNull()
            }

            if (session == null) {
                tokenStore.clearSession()
                onRefreshFailed()
                return null
            }

            onSessionRefreshed(session)
            return response.request.newBuilder()
                .header("Authorization", "Bearer ${session.accessToken}")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count += 1
            prior = prior.priorResponse
        }
        return count
    }
}
