package com.muddassir.squadgym.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Envelope shared by every endpoint: `{ success, data, error }`. */
@Serializable
data class ApiEnvelope<T>(
    val success: Boolean = false,
    val data: T? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class ApiErrorDto(
    val code: String = "UNKNOWN",
    val message: String = "",
    val details: JsonElement? = null,
    val requestId: String? = null,
)

@Serializable
data class PaginationMeta(
    val page: Int = 1,
    val pageSize: Int = 20,
    val total: Int = 0,
    val totalPages: Int = 1,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
)

@Serializable
data class SimpleActionResponse(
    val ok: Boolean = true,
    val message: String? = null,
    @SerialName("devToken") val devToken: String? = null,
)
