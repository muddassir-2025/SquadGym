package com.muddassir.squadgym.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AttendanceSummaryDto(
    val total: Int = 0,
    val present: Int = 0,
    val absent: Int = 0,
    val filtered: Boolean = false,
)

@Serializable
data class AttendanceTotalsDto(
    val total: Int = 0,
    val present: Int = 0,
    val absent: Int = 0,
)

@Serializable
data class SessionRefDto(
    val id: String = "",
    val checkedInAt: String = "",
    val expiresAt: String = "",
    val secondsRemaining: Int = 0,
    val isActive: Boolean = true,
    val checkedOutAt: String? = null,
    val status: String = "active",
)

@Serializable
data class AttendanceMemberDto(
    val memberId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String? = null,
    val photoUrl: String? = null,
    val imageVersion: Int = 0,
    val gender: String? = null,
    val shift: ShiftRefDto? = null,
    val plan: PlanRefDto? = null,
    val membershipEndDate: String? = null,
    val membershipStatus: String = "active",
    val status: String = "absent",
    val attendanceId: String? = null,
    val markedAt: String? = null,
    val canUndo: Boolean = false,
    val undoSecondsRemaining: Int = 0,
    val session: SessionRefDto? = null,
    val score: Int = 0,
) {
    val isPresent: Boolean get() = status == "present"
    val initials: String
        get() = fullName.trim().split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "?" }
}

@Serializable
data class AttendanceDashboardDto(
    val date: String = "",
    val dateLabel: String = "",
    val timezone: String = "Asia/Kolkata",
    val gymOpen: Boolean = true,
    val inGymNow: Int = 0,
    val summary: AttendanceSummaryDto = AttendanceSummaryDto(),
    val gymTotals: AttendanceTotalsDto = AttendanceTotalsDto(),
    val members: List<AttendanceMemberDto> = emptyList(),
    val meta: PaginationMeta = PaginationMeta(),
)

@Serializable
data class MarkPresentRequest(
    val memberId: String,
    val date: String? = null,
)

@Serializable
data class AttendanceRecordDto(
    val id: String = "",
    val memberId: String = "",
    val attendanceDate: String = "",
    val markedAt: String = "",
    val status: String = "active",
    val source: String = "manual",
)

@Serializable
data class MemberRefDto(
    val id: String = "",
    val fullName: String = "",
    val photoUrl: String? = null,
    val imageVersion: Int = 0,
)

@Serializable
data class UndoWindowDto(
    val canUndo: Boolean = false,
    val secondsRemaining: Int = 0,
    val expiresAt: String = "",
)

@Serializable
data class MarkPresentResponse(
    val attendance: AttendanceRecordDto = AttendanceRecordDto(),
    val member: MemberRefDto = MemberRefDto(),
    val alreadyMarked: Boolean = false,
    val undo: UndoWindowDto = UndoWindowDto(),
    val session: SessionRefDto? = null,
)

@Serializable
data class UndoAttendanceResponse(
    val attendanceId: String = "",
    val memberId: String = "",
    val attendanceDate: String = "",
    val status: String = "undone",
    val undoneAt: String = "",
)

@Serializable
data class CorrectionRequest(
    val memberId: String,
    val date: String,
    val action: String,
    val reason: String,
)

@Serializable
data class CorrectionResponse(
    val date: String = "",
    val status: String = "absent",
    val attendanceId: String? = null,
)

@Serializable
data class ActiveSessionDto(
    val id: String = "",
    val memberId: String = "",
    val fullName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val imageVersion: Int = 0,
    val shiftName: String? = null,
    val checkedInAt: String = "",
    val expiresAt: String = "",
    val secondsRemaining: Int = 0,
)

@Serializable
data class ActiveSessionsDto(
    val items: List<ActiveSessionDto> = emptyList(),
    val total: Int = 0,
)
