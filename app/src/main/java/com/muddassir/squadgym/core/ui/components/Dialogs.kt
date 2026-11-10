package com.muddassir.squadgym.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme
import com.muddassir.squadgym.core.util.DateText

@Composable
fun MonthPickerRow(
    month: String,
    modifier: Modifier = Modifier,
    canGoForward: Boolean = true,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            onClick = onPrevious,
        ) {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = "Previous month",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(7.dp).size(18.dp),
            )
        }
        Text(
            text = DateText.monthLabel(month),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            onClick = { if (canGoForward) onNext() },
        ) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Next month",
                tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else SquadGymTheme.colors.border,
                modifier = Modifier.padding(7.dp).size(18.dp),
            )
        }
    }
}

/** Single-choice list dialog used by filters and plan pickers. */
@Composable
fun OptionsDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedValue: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(options) { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (value == selectedValue) FontWeight.Bold else FontWeight.Normal,
                            color = if (value == selectedValue) SquadGymTheme.colors.accent
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (value == selectedValue) {
                            Text(
                                text = "Selected",
                                style = MaterialTheme.typography.labelSmall,
                                color = SquadGymTheme.colors.accent,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = SquadGymTheme.colors.accent)
            }
        },
    )
}

/** Custom range for the analytics chart (YYYY-MM-DD inputs). */
@Composable
fun DateRangeDialog(
    initialFrom: String,
    initialTo: String,
    onApply: (from: String, to: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var from by remember { mutableStateOf(initialFrom) }
    var to by remember { mutableStateOf(initialTo) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        title = { Text("Custom range", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    text = "Use YYYY-MM-DD. Ranges up to 366 days are supported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                GymTextField(value = from, onValueChange = { from = it }, label = "From", placeholder = "2026-01-01")
                Spacer(Modifier.height(10.dp))
                GymTextField(value = to, onValueChange = { to = it }, label = "To", placeholder = "2026-01-31")
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val fromDate = DateText.parse(from)
                val toDate = DateText.parse(to)
                when {
                    fromDate == null || toDate == null -> error = "Enter both dates as YYYY-MM-DD."
                    toDate.isBefore(fromDate) -> error = "The end date must be after the start date."
                    else -> onApply(from, to)
                }
            }) {
                Text("Apply", color = SquadGymTheme.colors.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
