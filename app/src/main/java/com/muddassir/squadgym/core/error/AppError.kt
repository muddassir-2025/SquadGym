package com.muddassir.squadgym.core.error

/**
 * Every failure the UI can show, normalised from transport, HTTP and business
 * errors. `code` is the server's machine-readable error code when available, so
 * screens can react (e.g. offer Undo again when the window expired).
 */
sealed class AppError(open val code: String, message: String) : Exception(message) {

    /** No connection / DNS / timeout. */
    data class Network(val detail: String? = null) :
        AppError("NETWORK", "You're offline. Check your connection and try again.")

    data object Unauthorized : AppError("AUTH_REQUIRED", "Your session expired. Please sign in again.")

    data object AccountInactive : AppError("AUTH_ACCOUNT_INACTIVE", "This account has been deactivated.")

    data class Forbidden(val detail: String?) :
        AppError("FORBIDDEN", detail?.takeIf { it.isNotBlank() } ?: "You don't have permission for that.")

    data class NotFound(val detail: String?) :
        AppError("NOT_FOUND", detail?.takeIf { it.isNotBlank() } ?: "That record no longer exists.")

    data class Validation(val fields: Map<String, String>, val detail: String?) :
        AppError("VALIDATION_ERROR", detail?.takeIf { it.isNotBlank() } ?: "Some fields need attention.") {
        fun field(name: String): String? = fields[name] ?: fields.entries.firstOrNull { it.key.endsWith(name) }?.value
    }

    /** Business conflict: already marked, undo expired, gym closed … */
    data class Conflict(override val code: String, val detail: String) :
        AppError(code, detail)

    data class Server(val detail: String?) :
        AppError("INTERNAL_ERROR", "Something went wrong on our side. Please try again.")

    data class Unknown(val detail: String?) :
        AppError("UNKNOWN", detail?.takeIf { it.isNotBlank() } ?: "Something went wrong.")

    val isUndoWindowExpired: Boolean
        get() = code == "UNDO_WINDOW_EXPIRED"

    val isAlreadyMarked: Boolean
        get() = code == "ALREADY_MARKED"

    val isAlreadyUndone: Boolean
        get() = code == "ALREADY_UNDONE"

    val isGymClosed: Boolean
        get() = code == "GYM_CLOSED"

    val isNotEligible: Boolean
        get() = code == "MEMBER_NOT_ELIGIBLE"

    val requiresReauthentication: Boolean
        get() = this is Unauthorized || this is AccountInactive
}

/** A successful value plus whether it came from the local cache. */
sealed interface ApiResult<out T> {
    data class Success<T>(
        val data: T,
        val fromCache: Boolean = false,
        val cachedAt: Long? = null,
    ) : ApiResult<T>

    data class Failure(val error: AppError) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data), fromCache, cachedAt)
    is ApiResult.Failure -> this
}

fun <T> ApiResult<T>.dataOrNull(): T? = (this as? ApiResult.Success)?.data

fun <T> ApiResult<T>.errorOrNull(): AppError? = (this as? ApiResult.Failure)?.error

inline fun <T> ApiResult<T>.onSuccess(block: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) block(data)
    return this
}

inline fun <T> ApiResult<T>.onFailure(block: (AppError) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) block(error)
    return this
}
