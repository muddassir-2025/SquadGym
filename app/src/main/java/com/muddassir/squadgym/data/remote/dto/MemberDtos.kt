package com.muddassir.squadgym.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ShiftRefDto(
    val id: String = "",
    val name: String = "",
)

@Serializable
data class PlanRefDto(
    val id: String = "",
    val name: String? = null,
)

@Serializable
data class MembershipSummaryDto(
    val id: String = "",
    val planId: String = "",
    val planName: String? = null,
    val startDate: String = "",
    val endDate: String = "",
    val status: String = "none",
    val daysRemaining: Int = 0,
)

@Serializable
data class MemberDto(
    val id: String,
    val fullName: String = "",
    val email: String = "",
    val phone: String? = null,
    val gender: String? = null,
    val address: String? = null,
    val joinedOn: String = "",
    val archivedAt: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val photoUrl: String? = null,
    val imageVersion: Int = 0,
    val accountStatus: String = "active",
    val shift: ShiftRefDto? = null,
    val membership: MembershipSummaryDto? = null,
    val membershipStatus: String = "none",
    val score: Int = 0,
) {
    val isArchived: Boolean get() = archivedAt != null
    val initials: String
        get() = fullName.trim().split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "?" }
}

@Serializable
data class MemberContextDto(
    val gymLocalDate: String = "",
    val expiringSoonDays: Int = 7,
    val scorePeriodStart: String = "",
    val scorePeriodEnd: String = "",
)

@Serializable
data class MemberListDto(
    val members: List<MemberDto> = emptyList(),
    val context: MemberContextDto = MemberContextDto(),
    val meta: PaginationMeta = PaginationMeta(),
)

@Serializable
data class MemberExtrasDto(
    val lifetimeScore: Int = 0,
    val monthlyScore: Int = 0,
    val weeklyScore: Int = 0,
    val lastAttendanceAt: String? = null,
    val totalAttendanceDays: Int = 0,
    val inGymNowUntil: String? = null,
    val currentStreak: Int = 0,
)

@Serializable
data class MemberAttendanceRowDto(
    val id: String = "",
    val date: String = "",
    val dateLabel: String = "",
    val status: String = "active",
    val markedAt: String = "",
    val markedByName: String? = null,
)

@Serializable
data class RenewalRowDto(
    val id: String = "",
    val performedAt: String = "",
    val newStartDate: String = "",
    val newEndDate: String = "",
    val status: String = "active",
    val undoExpiresAt: String = "",
)

@Serializable
data class MemberDetailDto(
    val member: MemberDto,
    val extras: MemberExtrasDto = MemberExtrasDto(),
    val membershipHistory: List<MembershipDto> = emptyList(),
    val attendance: List<MemberAttendanceRowDto> = emptyList(),
    val renewals: List<RenewalRowDto> = emptyList(),
)

@Serializable
data class CreateMemberRequest(
    val fullName: String,
    val email: String,
    val phone: String? = null,
    val gender: String? = null,
    val address: String? = null,
    val planId: String,
    val startDate: String? = null,
    val shiftId: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val dateOfBirth: String? = null,
    val notes: String? = null,
    val initialPassword: String? = null,
)

@Serializable
data class InvitationResultDto(
    val sent: Boolean = false,
    val token: String? = null,
)

@Serializable
data class CreateMemberResponse(
    val memberId: String = "",
    val invitation: InvitationResultDto? = null,
    val accountStatus: String = "invited",
)

@Serializable
data class UpdateMemberRequest(
    val fullName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val gender: String? = null,
    val address: String? = null,
    val shiftId: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val dateOfBirth: String? = null,
    val notes: String? = null,
)

@Serializable
data class ArchiveMemberRequest(val reason: String? = null)

@Serializable
data class ArchiveMemberResponse(
    val archived: Boolean = false,
    val memberId: String = "",
)

@Serializable
data class PhotoUploadResponse(
    val photoUrl: String? = null,
    val imageVersion: Int = 0,
)

@Serializable
data class MembershipHistoryResponse(
    val items: List<MembershipHistoryItemDto> = emptyList(),
)

@Serializable
data class MembershipHistoryItemDto(
    val id: String = "",
    val planName: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val status: String = "",
    val days: Int = 0,
    val renewalId: String? = null,
)
