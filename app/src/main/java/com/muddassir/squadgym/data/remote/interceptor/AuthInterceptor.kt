package com.muddassir.squadgym.data.remote.interceptor

import com.muddassir.squadgym.data.local.SecureTokenStore
import okhttp3.Interceptor
import okhttp3.Response

/** Attaches the bearer token to every authenticated request. */
class AuthInterceptor(private val tokenStore: SecureTokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath

        val isPublicAuth = path.endsWith("/auth/login") || path.endsWith("/auth/refresh")
        val token = tokenStore.accessToken
        if (token.isNullOrBlank() || isPublicAuth) return chain.proceed(original)

        val authorized = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        return chain.proceed(authorized)
    }
}
