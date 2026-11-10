package com.muddassir.squadgym.data.remote

import com.muddassir.squadgym.core.error.AppError
import com.muddassir.squadgym.data.remote.dto.ApiErrorDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class ApiErrorEnvelope(
    val success: Boolean = false,
    val error: ApiErrorDto? = null,
)

/** Maps the server's single error shape onto typed [AppError]s. */
object ApiErrorMapper {

    fun fromHttp(status: Int, body: String?, json: Json): AppError {
        val payload = body?.takeIf { it.isNotBlank() }?.let {
            runCatching { json.decodeFromString(ApiErrorEnvelope.serializer(), it) }.getOrNull()
        }
        val dto = payload?.error
        val message = dto?.message?.takeIf { it.isNotBlank() }
        val code = dto?.code ?: defaultCode(status)

        return when {
            status == 401 && code == "AUTH_ACCOUNT_INACTIVE" -> AppError.AccountInactive
            status == 401 -> AppError.Unauthorized
            status == 403 -> AppError.Forbidden(message)
            status == 404 -> AppError.NotFound(message)
            status == 413 -> AppError.Validation(emptyMap(), message ?: "That file is too large.")
            status == 422 || code == "VALIDATION_ERROR" ->
                AppError.Validation(fieldsOf(dto), message)
            status == 409 || code in CONFLICT_CODES ->
                AppError.Conflict(code, message ?: "That action isn't possible right now.")
            status >= 500 -> AppError.Server(message)
            else -> AppError.Unknown(message)
        }
    }

    fun fieldsOf(dto: ApiErrorDto?): Map<String, String> {
        val details = dto?.details as? JsonObject ?: return emptyMap()
        return details.mapNotNull { (key, value) ->
            val text = (value as? JsonPrimitive)?.content ?: value.toString()
            key to text
        }.toMap()
    }

    private fun defaultCode(status: Int): String = when (status) {
        401 -> "AUTH_REQUIRED"
        403 -> "FORBIDDEN"
        404 -> "NOT_FOUND"
        409 -> "CONFLICT"
        422 -> "VALIDATION_ERROR"
        else -> "UNKNOWN"
    }

    private val CONFLICT_CODES = setOf(
        "ALREADY_MARKED",
        "ALREADY_UNDONE",
        "UNDO_WINDOW_EXPIRED",
        "UNDO_WINDOW_ACTIVE",
        "GYM_CLOSED",
        "MEMBER_NOT_ELIGIBLE",
        "MEMBERSHIP_NOT_ELIGIBLE",
        "RENEWAL_NOT_REVERSIBLE",
    )
}

fun ApiErrorDto.toAppError(): AppError = when (code) {
    "AUTH_REQUIRED", "AUTH_TOKEN_EXPIRED", "AUTH_TOKEN_INVALID" -> AppError.Unauthorized
    "AUTH_ACCOUNT_INACTIVE" -> AppError.AccountInactive
    "AUTH_INVALID_CREDENTIALS" -> AppError.Conflict("AUTH_INVALID_CREDENTIALS", message)
    "AUTH_ACCOUNT_INVITED" -> AppError.Conflict("AUTH_ACCOUNT_INVITED", message)
    "FORBIDDEN" -> AppError.Forbidden(message)
    "NOT_FOUND" -> AppError.NotFound(message)
    "VALIDATION_ERROR" -> AppError.Validation(ApiErrorMapper.fieldsOf(this), message)
    "INTERNAL_ERROR", "DEPENDENCY_UNAVAILABLE" -> AppError.Server(message)
    else -> if (code == "CONFLICT") AppError.Unknown(message) else AppError.Conflict(code, message)
}

private fun JsonObject.firstString(): String? =
    entries.firstOrNull()?.value?.jsonPrimitive?.content
