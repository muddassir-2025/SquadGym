package com.muddassir.squadgym.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AdminDto(
    val id: String = "",
    val email: String = "",
    val fullName: String = "",
    val phone: String? = null,
    val role: String = "admin",
    val status: String = "active",
    val permissions: List<String> = emptyList(),
    val photoUrl: String? = null,
    val imageVersion: Int = 0,
    val lastLoginAt: String? = null,
    val createdAt: String = "",
) {
    val initials: String
        get() = fullName.trim().split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "?" }
}

@Serializable
data class AdminListResponse(
    val admins: List<AdminDto> = emptyList(),
)

@Serializable
data class CreateAdminRequest(
    val fullName: String,
    val email: String,
    val phone: String? = null,
    val permissions: List<String>? = null,
    val initialPassword: String? = null,
)

@Serializable
data class CreateAdminResponse(
    val admin: AdminDto = AdminDto(),
    val invitation: InvitationResultDto? = null,
)

@Serializable
data class UpdateAdminRequest(
    val fullName: String? = null,
    val phone: String? = null,
    val permissions: List<String>? = null,
)

@Serializable
data class AdminStatusRequest(val status: String)

@Serializable
data class OperatingDayDto(
    val dayOfWeek: Int = 1,
    val dayLabel: String = "",
    val isOpen: Boolean = true,
    val opensAt: String = "05:00",
    val closesAt: String = "23:00",
)

@Serializable
data class OperatingDayUpdateDto(
    val dayOfWeek: Int,
    val isOpen: Boolean,
    val opensAt: String? = null,
    val closesAt: String? = null,
)

@Serializable
data class OperatingDaysRequest(val days: List<OperatingDayUpdateDto>)

@Serializable
data class GymConfigurationDto(
    val gym: GymDto = GymDto(id = "", name = ""),
    val operatingDays: List<OperatingDayDto> = emptyList(),
    val shifts: List<ShiftDto> = emptyList(),
    val plans: List<PlanDto> = emptyList(),
    val permissionsCatalog: List<String> = emptyList(),
)

@Serializable
data class GymSettingsPatch(
    val name: String? = null,
    val timezone: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val expiringSoonDays: Int? = null,
    val archiveAfterDays: Int? = null,
    val inGymWindowMinutes: Int? = null,
    val attendanceUndoSeconds: Int? = null,
    val renewalUndoSeconds: Int? = null,
)

@Serializable
data class ShiftRequest(
    val name: String,
    val startTime: String,
    val endTime: String,
    val sortOrder: Int? = null,
)

@Serializable
data class ShiftPatchRequest(
    val name: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val sortOrder: Int? = null,
    val isActive: Boolean? = null,
)

@Serializable
data class PlanRequest(
    val name: String,
    val durationDays: Int,
    val priceCents: Int? = null,
    val currency: String? = null,
    val description: String? = null,
    val sortOrder: Int? = null,
)

@Serializable
data class PlanPatchRequest(
    val name: String? = null,
    val durationDays: Int? = null,
    val priceCents: Int? = null,
    val currency: String? = null,
    val description: String? = null,
    val isActive: Boolean? = null,
    val sortOrder: Int? = null,
)

@Serializable
data class AuditLogDto(
    val id: String = "",
    val actorUserId: String? = null,
    val actorName: String? = null,
    val actorRole: String? = null,
    val action: String = "",
    val entityType: String? = null,
    val entityId: String? = null,
    val metadata: JsonObject = JsonObject(emptyMap()),
    val createdAt: String = "",
) {
    val actionLabel: String
        get() = action.substringAfter('.').replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Serializable
data class AuditLogResponse(
    val items: List<AuditLogDto> = emptyList(),
    val meta: PaginationMeta = PaginationMeta(),
)
