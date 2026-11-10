package com.muddassir.squadgym.core.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * All display formatting. Server dates are gym-local `YYYY-MM-DD` strings; server
 * timestamps are UTC ISO instants rendered in the gym's timezone.
 */
object DateText {

    private val fullFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
    private val shortFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val clockFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

    fun parse(iso: String): LocalDate? = runCatching { LocalDate.parse(iso) }.getOrNull()

    fun full(iso: String): String = parse(iso)?.format(fullFormatter) ?: "—"

    fun short(iso: String): String = parse(iso)?.format(shortFormatter) ?: "—"

    fun weekdayShort(iso: String): String =
        parse(iso)?.dayOfWeek?.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) ?: ""

    fun monthLabel(month: String): String = runCatching {
        YearMonth.parse(month).format(monthFormatter)
    }.getOrElse { month }

    fun monthOf(iso: String): String = iso.take(7)

    fun shiftMonth(month: String, delta: Long): String =
        runCatching { YearMonth.parse(month).plusMonths(delta).toString() }.getOrElse { month }

    fun isFutureMonth(month: String, today: String): Boolean =
        month > monthOf(today)

    fun clock(instantIso: String?, zone: String = "UTC"): String {
        val instant = instantIso?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return "—"
        val zoneId = runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.of("UTC"))
        return instant.atZone(zoneId).format(clockFormatter)
    }

    fun dateTime(instantIso: String?, zone: String = "UTC"): String {
        val instant = instantIso?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return "—"
        val zoneId = runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.of("UTC"))
        return instant.atZone(zoneId).format(dateTimeFormatter)
    }

    fun todayIso(zone: String? = null): String {
        val zoneId = zone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        return if (zoneId != null) LocalDate.now(zoneId).toString() else LocalDate.now().toString()
    }

    /** "in 3 days" / "today" / "expired 4 days ago" / "in 2 months". */
    fun relativeDays(targetIso: String?, todayIso: String): String {
        val target = targetIso?.let { parse(it) } ?: return "—"
        val today = parse(todayIso) ?: return "—"
        val days = ChronoUnit.DAYS.between(today, target)
        return when {
            days == 0L -> "today"
            days == 1L -> "tomorrow"
            days == -1L -> "yesterday"
            days > 0 && days < 60 -> "in $days days"
            days > 0 -> "in ${days / 30} months"
            abs(days) < 60 -> "${abs(days)} days ago"
            else -> "${abs(days) / 30} months ago"
        }
    }

    /** "Expires in 5 days" / "Expired 2 days ago". */
    fun expiryLabel(endDateIso: String?, todayIso: String): String {
        val target = endDateIso?.let { parse(it) } ?: return "No membership"
        val today = parse(todayIso) ?: return "No membership"
        val days = ChronoUnit.DAYS.between(today, target)
        return when {
            days < 0 -> "Expired ${abs(days)} day${if (abs(days) == 1L) "" else "s"} ago"
            days == 0L -> "Expires today"
            days == 1L -> "Expires tomorrow"
            else -> "Expires in $days days"
        }
    }

    fun dayOfWeek(iso: String): DayOfWeek? = parse(iso)?.dayOfWeek

    fun isToday(iso: String, todayIso: String): Boolean = iso == todayIso

    fun nextDays(count: Int, todayIso: String): List<String> {
        val today = parse(todayIso) ?: return emptyList()
        return (0 until count).map { today.plusDays(it.toLong()).toString() }
    }

    fun weekdayIndex(iso: String): Int = (parse(iso)?.dayOfWeek?.value ?: 1) - 1

    fun countdown(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val minutes = safe / 60
        val remainder = safe % 60
        return if (minutes > 0) "${minutes}m ${remainder.toString().padStart(2, '0')}s" else "${remainder}s"
    }
}

object NumberText {
    fun money(cents: Int, currency: String = "INR"): String {
        if (cents <= 0) return "Free"
        val symbol = when (currency.uppercase()) {
            "INR" -> "₹"
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            else -> "${currency.uppercase()} "
        }
        val whole = cents / 100
        return "$symbol$whole"
    }

    fun percent(value: Double): String {
        val rounded = (value * 10).toLong() / 10.0
        return if (rounded % 1.0 == 0.0) "${rounded.toLong()}%" else "$rounded%"
    }

    fun plural(count: Int, singular: String, plural: String = "${singular}s"): String =
        "$count ${if (count == 1) singular else plural}"
}
