package com.muddassir.squadgym.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme
import com.muddassir.squadgym.data.remote.dto.CalendarDayDto
import java.time.LocalDate

/**
 * Month grid: green = attended, muted red = missed an open day, grey = closed,
 * outlined = future, faint = outside the membership window.
 */
@Composable
fun AttendanceCalendar(
    days: List<CalendarDayDto>,
    modifier: Modifier = Modifier,
    onDayClick: (CalendarDayDto) -> Unit = {},
) {
    if (days.isEmpty()) return
    val colors = SquadGymTheme.colors
    val leadingBlanks = runCatching {
        LocalDate.parse(days.first().date).dayOfWeek.value - 1
    }.getOrDefault(0)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        val cells = leadingBlanks + days.size
        val rows = (cells + 6) / 7
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val cellIndex = row * 7 + column
                    val dayIndex = cellIndex - leadingBlanks
                    if (dayIndex !in days.indices) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val day = days[dayIndex]
                        CalendarCell(day = day, onClick = { onDayClick(day) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot("Attended", colors.accent)
            LegendDot("Missed", colors.danger.copy(alpha = 0.7f))
            LegendDot("Closed", colors.neutralTint)
            LegendDot("Upcoming", Color.Transparent, border = colors.border)
        }
    }
}

@Composable
private fun CalendarCell(day: CalendarDayDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SquadGymTheme.colors
    val background = when (day.status) {
        "attended" -> colors.accent
        "missed" -> colors.dangerTint
        "closed" -> colors.neutralTint
        else -> Color.Transparent
    }
    val textColor = when (day.status) {
        "attended" -> colors.accentInk
        "missed" -> colors.danger
        "future" -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    }
    val dayNumber = day.date.takeLast(2).trimStart('0').ifBlank { "0" }

    Box(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .then(
                if (day.status == "future" || day.status.startsWith("before") || day.status.startsWith("after")) {
                    Modifier.border(1.dp, colors.border.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = dayNumber,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (day.status == "attended") FontWeight.Bold else FontWeight.Medium,
            color = textColor,
        )
    }
}

@Composable
private fun LegendDot(
    label: String,
    color: Color,
    border: Color? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .height(8.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
                .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(3.dp)) else Modifier),
        )
        Spacer(Modifier.height(0.dp))
        Text(
            text = "  $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
