package com.muddassir.squadgym.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class MembershipDto(
    val id: String = "",
    val planId: String = "",
    val planName: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val status: String = "none",
    val rowStatus: String = "active",
    val daysRemaining: Int = 0,
    val priceCents: Int = 0,
    val currency: String = "INR",
)

@Serializable
data class PlanDto(
    val id: String = "",
    val name: String = "",
    val durationDays: Int = 0,
    val priceCents: Int = 0,
    val currency: String = "INR",
    val description: String? = null,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
) {
    val durationLabel: String
        get() = when {
            durationDays % 365 == 0 && durationDays >= 365 -> "${durationDays / 365} year"
            durationDays % 30 == 0 -> "${durationDays / 30} month"
            else -> "$durationDays days"
        }
}

@Serializable
data class PlansResponse(
    val plans: List<PlanDto> = emptyList(),
)

@Serializable
data class RenewalPreviewDto(
    val memberId: String = "",
    val planId: String = "",
    val planName: String = "",
    val durationDays: Int = 0,
    val current: MembershipDto? = null,
    val newStartDate: String = "",
    val newEndDate: String = "",
    val extendedFromExisting: Boolean = false,
    val priceCents: Int = 0,
    val currency: String = "INR",
)

@Serializable
data class RenewalInfoDto(
    val id: String = "",
    val performedAt: String = "",
    val undoExpiresAt: String = "",
    val canUndo: Boolean = false,
    val secondsRemaining: Int = 0,
    val extendedFromExisting: Boolean = false,
)

@Serializable
data class RenewalResultDto(
    val memberId: String = "",
    val membership: MembershipDto = MembershipDto(),
    val previousMembership: MembershipDto? = null,
    val renewal: RenewalInfoDto = RenewalInfoDto(),
)

@Serializable
data class UndoRenewalResultDto(
    val memberId: String = "",
    val membership: MembershipDto = MembershipDto(),
    val renewalId: String = "",
    val reversedAt: String = "",
)

@Serializable
data class RenewRequest(val planId: String)
