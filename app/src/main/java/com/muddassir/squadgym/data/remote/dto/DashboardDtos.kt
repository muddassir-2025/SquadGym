package com.muddassir.squadgym.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DashboardDto(
    val gymLocalDate: String = "",
    val gymLocalDateLabel: String = "",
    val gymOpen: Boolean = true,
    val timezone: String = "Asia/Kolkata",
    val gymName: String = "",
    val stats: StatsDto = StatsDto(),
    val generatedAt: String? = null,
)

@Serializable
data class StatsDto(
    val inGymNow: StatCardDto = StatCardDto(),
    val totalMembers: StatCardDto = StatCardDto(),
    val todayAttendance: StatCardDto = StatCardDto(),
)

@Serializable
data class StatCardDto(
    val value: Int = 0,
    val comparison: ComparisonDto? = null,
)

@Serializable
data class ComparisonDto(
    val previousValue: Int = 0,
    val difference: Int = 0,
    val percentChange: Double? = null,
    val direction: String = "flat",
) {
    val isUp: Boolean get() = direction == "up"
    val isDown: Boolean get() = direction == "down"

    val label: String
        get() {
            val sign = if (difference > 0) "+" else ""
            val percent = percentChange?.let { " · ${if (it > 0) "+" else ""}$it%" }.orEmpty()
            return "$sign$difference$percent vs yesterday"
        }
}

@Serializable
data class ChartPointDto(
    val date: String = "",
    val label: String = "",
    val weekday: String = "",
    val value: Int = 0,
    val isGymOpen: Boolean = true,
)

@Serializable
data class ChartDto(
    val metric: String = "attendance",
    val range: String = "week",
    val from: String = "",
    val to: String = "",
    val rangeLabel: String = "",
    val points: List<ChartPointDto> = emptyList(),
    val total: Int = 0,
    val previousTotal: Int? = null,
    val comparison: ComparisonDto? = null,
)

@Serializable
data class OccupancyPointDto(
    val capturedAt: String = "",
    val activeCount: Int = 0,
    val attendanceCount: Int = 0,
)

@Serializable
data class OccupancyDto(
    val date: String = "",
    val dateLabel: String = "",
    val points: List<OccupancyPointDto> = emptyList(),
)

@Serializable
data class LeaderboardEntryDto(
    val rank: Int = 0,
    val memberId: String = "",
    val fullName: String = "",
    val photoUrl: String? = null,
    val imageVersion: Int = 0,
    val score: Int = 0,
    val currentStreak: Int = 0,
    val isMe: Boolean = false,
)

@Serializable
data class LeaderboardMeDto(
    val rank: Int = 0,
    val memberId: String = "",
    val fullName: String = "",
    val photoUrl: String? = null,
    val imageVersion: Int = 0,
    val score: Int = 0,
    val currentStreak: Int = 0,
    val isMe: Boolean = true,
    val totalMembers: Int = 0,
)

@Serializable
data class LeaderboardDto(
    val period: String = "week",
    val periodStart: String = "",
    val periodEnd: String = "",
    val periodLabel: String = "",
    val timezone: String = "Asia/Kolkata",
    val entries: List<LeaderboardEntryDto> = emptyList(),
    val me: LeaderboardMeDto? = null,
    val meta: PaginationMeta = PaginationMeta(),
)
