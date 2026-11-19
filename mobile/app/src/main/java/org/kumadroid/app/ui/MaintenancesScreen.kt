package org.kumadroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.kumadroid.app.data.KumaRepository
import org.kumadroid.app.data.Maintenance

@Composable
fun MaintenancesScreen(
    repository: KumaRepository,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onClone: (Long) -> Unit,
) {
    val maintenances by repository.maintenances.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Maintenance?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            TopBar("Maintenance", subtitle = "Scheduled windows and recurring plans", onBack = null, actions = {
                IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "New maintenance") }
            })
        }
        if (error.isNotBlank()) {
            item { InfoBanner(error, Modifier.padding(16.dp), KumaColors.Down) }
        }
        if (maintenances.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Construction,
                    title = "No maintenance windows",
                    message = "Plan maintenance to pause notifications and show a banner on status pages.",
                    actionLabel = "Create maintenance",
                    onAction = onAdd,
                )
            }
        }
        items(maintenances, key = { it.id.toString() }) { maintenance ->
            MaintenanceCard(
                maintenance = maintenance,
                onClick = { onOpen(maintenance.id) },
                onDelete = { deleteTarget = maintenance },
                onClone = { onClone(maintenance.id) },
                onToggle = {
                    scope.launch {
                        try {
                            if (maintenance.active) repository.pauseMaintenance(maintenance.id)
                            else repository.resumeMaintenance(maintenance.id)
                        } catch (e: Exception) {
                            error = e.message ?: "Could not update maintenance"
                        }
                    }
                },
            )
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete maintenance?",
            message = "“${target.title}” will be permanently removed.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                scope.launch {
                    try {
                        repository.deleteMaintenance(target.id)
                        deleteTarget = null
                    } catch (e: Exception) {
                        error = e.message ?: "Could not delete maintenance"
                    }
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun MaintenanceCard(
    maintenance: Maintenance,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onClone: () -> Unit,
    onToggle: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val active = maintenance.active
    val strategy = maintenance.strategy
    val dates = maintenance.json.optJSONArray("dateRange")
    // Recurring windows send [null, null], and org.json renders JSON null as "null".
    fun dateAt(index: Int): String =
        dates?.optString(index).orEmpty().replace("T", " ").takeUnless { it == "null" }.orEmpty()
    val start = dateAt(0)
    val end = dateAt(1)
    KumaCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), onClick = onClick) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        maintenance.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    StatusPill(if (active) 3 else -1, if (active) "Active" else "Paused")
                }
                Text(
                    strategyLabel(strategy),
                    style = MaterialTheme.typography.labelMedium,
                    color = KumaColors.Brand,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (start.isNotBlank()) {
                    Text(
                        "$start → $end",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (maintenance.description.isNotBlank()) {
                    Text(
                        maintenance.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 2,
                    )
                }
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Actions") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { menu = false; onClick() },
                )
                DropdownMenuItem(
                    text = { Text(if (active) "Pause" else "Resume") },
                    leadingIcon = { Icon(if (active) Icons.Default.Pause else Icons.Default.PlayArrow, null) },
                    onClick = { menu = false; onToggle() },
                )
                DropdownMenuItem(
                    text = { Text("Clone") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                    onClick = { menu = false; onClone() },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = KumaColors.Down) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = KumaColors.Down) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

private fun strategyLabel(strategy: String): String = when (strategy) {
    "manual" -> "Manual · active until paused"
    "cron" -> "Cron schedule"
    "recurring-interval" -> "Recurring · interval"
    "recurring-weekday" -> "Recurring · day of week"
    "recurring-day-of-month" -> "Recurring · day of month"
    else -> "Single maintenance window"
}
