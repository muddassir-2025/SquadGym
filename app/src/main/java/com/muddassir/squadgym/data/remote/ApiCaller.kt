package com.muddassir.squadgym.data.remote

import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.core.error.AppError
import com.muddassir.squadgym.data.remote.dto.ApiEnvelope
import com.muddassir.squadgym.data.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * Executes a Retrofit call and normalises every outcome into [ApiResult].
 *
 * A 401 that survives the authenticator means the session is truly gone, so the
 * global session state is cleared and the UI falls back to the sign-in screen.
 */
class ApiCaller(
    private val json: Json,
    private val sessionManager: SessionManager,
) {
    suspend fun <T> call(block: suspend () -> ApiEnvelope<T>): ApiResult<T> {
        return try {
            val envelope = block()
            val data = envelope.data
            when {
                envelope.success && data != null -> ApiResult.Success(data)
                envelope.error != null -> ApiResult.Failure(envelope.error.toAppError())
                else -> ApiResult.Failure(AppError.Unknown("The server returned no data."))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (http: HttpException) {
            val error = ApiErrorMapper.fromHttp(http.code(), http.response()?.errorBody()?.string(), json)
            if (error.requiresReauthentication) sessionManager.forceSignOut()
            ApiResult.Failure(error)
        } catch (io: IOException) {
            ApiResult.Failure(AppError.Network(io.message))
        } catch (other: Exception) {
            ApiResult.Failure(AppError.Unknown(other.message))
        }
    }

    /** Only network failures are worth falling back to cache for. */
    fun shouldUseCache(error: AppError?): Boolean = error is AppError.Network

    /** Treats "already done" conflicts as success for idempotent taps. */
    fun <T> recoverConflict(result: ApiResult<T>, onConflict: (AppError.Conflict) -> ApiResult<T> = { result }): ApiResult<T> {
        val error = (result as? ApiResult.Failure)?.error
        return if (error is AppError.Conflict) onConflict(error) else result
    }
}
