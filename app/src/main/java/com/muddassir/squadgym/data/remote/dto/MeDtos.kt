package com.muddassir.squadgym.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PeriodDto(
    val from: String = "",
    val to: String = "",
    val label: String = "",
    val score: Int = 0,
    val daysAttended: Int = 0,
    val openDays: Int = 0,
    val missedDays: Int = 0,
    val attendancePercent: Double = 0.0,
)

@Serializable
data class PersonalDto(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lifetimeScore: Int = 0,
    val weekly: PeriodDto = PeriodDto(),
    val monthly: PeriodDto = PeriodDto(),
    val attendancePercent: Double = 0.0,
    val daysAttended: Int = 0,
    val missedDays: Int = 0,
    val todayStatus: String = "absent",
    val inGymNow: Boolean = false,
)

@Serializable
data class RankingDto(
    val week: Int = 0,
    val month: Int = 0,
    val totalMembers: Int = 0,
)

@Serializable
data class CalendarDayDto(
    val date: String = "",
    val status: String = "future",
    val isOpen: Boolean = true,
)

@Serializable
data class MemberHomeDto(
    val gymLocalDate: String = "",
    val gymOpen: Boolean = true,
    val timezone: String = "Asia/Kolkata",
    val gymName: String = "",
    val stats: StatsDto = StatsDto(),
    val personal: PersonalDto = PersonalDto(),
    val membership: MembershipDto? = null,
    val ranking: RankingDto = RankingDto(),
    val calendar: List<CalendarDayDto> = emptyList(),
    val calendarMonth: String = "",
    val calendarMonthLabel: String = "",
)

@Serializable
data class CalendarDto(
    val month: String = "",
    val monthLabel: String = "",
    val days: List<CalendarDayDto> = emptyList(),
    val summary: PeriodDto = PeriodDto(),
)

@Serializable
data class MyAttendanceItemDto(
    val id: String = "",
    val date: String = "",
    val status: String = "active",
    val markedAt: String = "",
    val markedByName: String? = null,
)

@Serializable
data class MyAttendanceHistoryDto(
    val from: String = "",
    val to: String = "",
    val items: List<MyAttendanceItemDto> = emptyList(),
)
