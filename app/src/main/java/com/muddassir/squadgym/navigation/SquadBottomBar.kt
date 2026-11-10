package com.muddassir.squadgym.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme

data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun SquadBottomBar(
    currentRoute: String?,
    isMember: Boolean,
    onNavigate: (String) -> Unit,
) {
    val destinations = if (isMember) {
        listOf(
            BottomDestination(Routes.MEMBER_HOME, "Home", Icons.Filled.Home),
            BottomDestination(Routes.LEADERBOARD, "Ranking", Icons.Filled.EmojiEvents),
            BottomDestination(Routes.PROFILE, "Profile", Icons.Filled.Person),
        )
    } else {
        listOf(
            BottomDestination(Routes.DASHBOARD, "Dashboard", Icons.Filled.Home),
            BottomDestination(Routes.ATTENDANCE, "Attendance", Icons.Filled.CheckCircle),
            BottomDestination(Routes.MEMBERS, "Members", Icons.Filled.Groups),
            BottomDestination(Routes.LEADERBOARD, "Ranking", Icons.Filled.EmojiEvents),
            BottomDestination(Routes.PROFILE, "Profile", Icons.Filled.Person),
        )
    }

    val colors = SquadGymTheme.colors
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                val selected = currentRoute == destination.route
                val alpha by animateFloatAsState(if (selected) 1f else 0.62f, label = "tab alpha")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onNavigate(destination.route) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 34.dp, height = 26.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) colors.accent.copy(alpha = 0.16f)
                                else androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            destination.icon,
                            contentDescription = destination.label,
                            tint = if (selected) colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp).alpha(alpha),
                        )
                    }
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(alpha),
                    )
                }
            }
        }
    }
}
