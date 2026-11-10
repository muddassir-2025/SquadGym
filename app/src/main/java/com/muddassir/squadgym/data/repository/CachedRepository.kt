package com.muddassir.squadgym.data.repository

import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.data.local.CacheStore
import com.muddassir.squadgym.data.remote.ApiCaller
import com.muddassir.squadgym.data.remote.dto.ApiEnvelope
import kotlinx.serialization.KSerializer

/**
 * Shared cache-then-network behaviour.
 *
 * Fresh data always wins and refreshes the cache. Only a *network* failure falls
 * back to cache — a 403 or validation error must never be masked by stale data.
 */
abstract class CachedRepository(
    protected val cache: CacheStore,
    protected val caller: ApiCaller,
) {
    protected suspend fun <T> load(
        key: String,
        serializer: KSerializer<T>,
        request: suspend () -> ApiEnvelope<T>,
    ): ApiResult<T> {
        val cached = cache.get(key, serializer)
        return when (val result = caller.call(request)) {
            is ApiResult.Success -> {
                cache.put(key, serializer, result.data)
                result
            }

            is ApiResult.Failure ->
                if (caller.shouldUseCache(result.error) && cached != null) {
                    ApiResult.Success(cached.value, fromCache = true, cachedAt = cached.cachedAt)
                } else {
                    result
                }
        }
    }
}
