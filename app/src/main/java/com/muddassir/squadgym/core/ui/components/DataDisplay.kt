package com.muddassir.squadgym.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.muddassir.squadgym.core.ui.theme.Spacing
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme
import com.muddassir.squadgym.data.remote.dto.ComparisonDto

enum class Tone { Success, Warning, Danger, Neutral, Accent }

@Composable
fun toneColors(tone: Tone): Pair<Color, Color> {
    val colors = SquadGymTheme.colors
    return when (tone) {
        Tone.Success -> colors.success to colors.successTint
        Tone.Warning -> colors.warning to colors.warningTint
        Tone.Danger -> colors.danger to colors.dangerTint
        Tone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant to colors.neutralTint
        Tone.Accent -> (colors.accentInk to colors.accent)
    }
}

@Composable
fun StatusPill(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    showDot: Boolean = true,
) {
    val (foreground, background) = toneColors(tone)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (tone == Tone.Accent) SquadGymTheme.colors.accent else background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showDot && tone != Tone.Accent) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(foreground),
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (tone == Tone.Accent) SquadGymTheme.colors.accentInk else foreground,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun MemberAvatar(
    photoUrl: String?,
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    highlight: Boolean = false,
) {
    val colors = SquadGymTheme.colors
    val ring = if (highlight) colors.accent else colors.border
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, ring, CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.accent,
            )
        }
    }
}

@Composable
fun StatTile(
    label: String,
    value: Int,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    comparison: ComparisonDto? = null,
    highlight: Boolean = false,
) {
    val colors = SquadGymTheme.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (highlight) colors.accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (highlight) colors.accent.copy(alpha = 0.35f) else colors.border,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (highlight) colors.accent.copy(alpha = 0.18f) else colors.neutralTint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (highlight) colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            if (comparison != null) {
                ComparisonIndicator(comparison)
            } else {
                Text(
                    text = if (highlight) "live now" else "no history yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ComparisonIndicator(comparison: ComparisonDto, modifier: Modifier = Modifier) {
    val colors = SquadGymTheme.colors
    val tint = when {
        comparison.difference > 0 -> colors.success
        comparison.difference < 0 -> colors.danger
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when {
        comparison.difference > 0 -> Icons.Filled.ArrowUpward
        comparison.difference < 0 -> Icons.Filled.ArrowDownward
        else -> Icons.Filled.Remove
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            text = comparison.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    emphasize: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Medium,
            color = valueColor,
            maxLines = 1,
        )
    }
}

@Composable
fun RankBadge(rank: Int, modifier: Modifier = Modifier) {
    val colors = SquadGymTheme.colors
    if (rank in 1..3) {
        val tint = when (rank) {
            1 -> colors.warning
            2 -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> Color(0xFFC98A4B)
        }
        Box(
            modifier = modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = "Rank $rank", tint = tint, modifier = Modifier.size(18.dp))
        }
    } else {
        Box(
            modifier = modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(SquadGymTheme.colors.neutralTint),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Floating confirmation with an Undo action, used after marks and renewals. */
@Composable
fun UndoBanner(
    message: String,
    secondsRemaining: Int,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SquadGymTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.accent.copy(alpha = 0.35f)),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (secondsRemaining > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Undo available for ${formatCountdown(secondsRemaining)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "UNDO",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onUndo)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

fun formatCountdown(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val rest = safe % 60
    return if (minutes > 0) "${minutes}m ${rest.toString().padStart(2, '0')}s" else "${rest}s"
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), label = "progress")
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = SquadGymTheme.colors.accent,
        trackColor = SquadGymTheme.colors.neutralTint,
        drawStopIndicator = {},
    )
}

@Composable
fun InfoBanner(
    text: String,
    icon: ImageVector,
    tone: Tone = Tone.Warning,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val (foreground, background) = toneColors(tone)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = foreground,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = foreground,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onAction)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
