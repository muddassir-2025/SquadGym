package com.muddassir.squadgym.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String? = null)

@Serializable
data class ForgotPasswordRequest(val email: String)

@Serializable
data class ForgotPasswordResponse(
    val accepted: Boolean = false,
    val message: String? = null,
    val devToken: String? = null,
)

@Serializable
data class ResetPasswordRequest(
    val token: String,
    val newPassword: String,
)

@Serializable
data class AcceptInvitationRequest(
    val token: String,
    val password: String,
    val fullName: String? = null,
)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val fullName: String,
    val role: String,
    val status: String = "active",
    val phone: String? = null,
    val photoUrl: String? = null,
    val imageVersion: Int = 0,
    val permissions: List<String> = emptyList(),
    val lastLoginAt: String? = null,
) {
    val isMember: Boolean get() = role == "member"
    val isAdmin: Boolean get() = role == "admin"
    val isSuperAdmin: Boolean get() = role == "super_admin"
    val isStaff: Boolean get() = role == "admin" || role == "super_admin"

    fun hasPermission(permission: String): Boolean =
        role == "super_admin" || permissions.contains(permission)

    val initials: String
        get() = fullName.trim().split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "?" }
}

@Serializable
data class ShiftDto(
    val id: String,
    val name: String,
    val startTime: String = "",
    val endTime: String = "",
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
) {
    val label: String get() = if (startTime.isBlank()) name else "$name · $startTime–$endTime"
}

@Serializable
data class GymDto(
    val id: String,
    val name: String,
    val timezone: String = "Asia/Kolkata",
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val expiringSoonDays: Int = 7,
    val archiveAfterDays: Int = 90,
    val inGymWindowMinutes: Int = 60,
    val attendanceUndoSeconds: Int = 300,
    val renewalUndoSeconds: Int = 300,
)

@Serializable
data class SessionDto(
    val user: UserDto,
    val gym: GymDto,
    val shifts: List<ShiftDto> = emptyList(),
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresIn: Int = 900,
    val tokenType: String = "Bearer",
)

@Serializable
data class ProfileDto(
    val user: UserDto,
    val gym: GymDto,
    val shifts: List<ShiftDto> = emptyList(),
)

@Serializable
data class InvitationPreviewDto(
    val email: String = "",
    val fullName: String = "",
    val role: String = "member",
    val gymName: String = "the gym",
    val expiresAt: String? = null,
)
