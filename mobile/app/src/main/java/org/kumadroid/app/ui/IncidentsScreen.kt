package org.kumadroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.kumadroid.app.data.KumaRepository
import org.kumadroid.app.data.bool

private val incidentStyles = listOf(
    "info" to "Info",
    "warning" to "Warning",
    "danger" to "Danger",
    "primary" to "Primary",
    "light" to "Light",
    "dark" to "Dark",
)

private fun incidentColor(style: String): Color = when (style) {
    "info" -> KumaColors.Brand
    "danger" -> KumaColors.Down
    "primary" -> Color(0xFF8B5CF6)
    "light" -> Color(0xFF94A3B8)
    "dark" -> Color(0xFF475569)
    else -> KumaColors.Pending
}

@Composable
fun IncidentsScreen(repository: KumaRepository, slug: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var incidents by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<JSONObject?>(null) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(false) }

    suspend fun load(cursor: String? = null, append: Boolean = false) {
        loading = true
        try {
            val ack = repository.getIncidentHistory(slug, cursor)
            val rows = ack.optJSONArray("incidents") ?: JSONArray()
            val parsed = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
            incidents = (if (append) incidents + parsed else parsed)
                .distinctBy { it.optString("id").ifBlank { it.toString() } }
            nextCursor = ack.optString("nextCursor").takeIf { it.isNotBlank() }
            hasMore = ack.optBoolean("hasMore")
            error = ""
        } catch (e: Exception) {
            error = e.message ?: "Could not load incidents"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(slug) { load() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            TopBar("Incidents", subtitle = slug, onBack = onBack, actions = {
                IconButton(onClick = { editing = null; showEditor = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New incident")
                }
            })
        }
        item {
            Text(
                "Post updates about outages, maintenance and other events on this status page.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (error.isNotBlank()) {
            item { InfoBanner(error, Modifier.padding(16.dp), KumaColors.Down) }
        }
        if (incidents.isEmpty() && !loading) {
            item {
                EmptyState(
                    icon = Icons.Default.Campaign,
                    title = "No incidents yet",
                    message = "Post your first incident to inform status page visitors.",
                    actionLabel = "New incident",
                    onAction = { editing = null; showEditor = true },
                )
            }
        }
        items(incidents, key = { it.optString("id").ifBlank { it.toString() } }) { incident ->
            IncidentCard(
                incident = incident,
                onEdit = { editing = incident; showEditor = true },
                onResolve = {
                    val id = incident.optLong("id")
                    if (id <= 0) {
                        error = "This incident has no valid identifier."
                    } else scope.launch {
                        try {
                            repository.resolveIncident(slug, id)
                            load()
                        } catch (e: Exception) {
                            error = e.message ?: "Could not resolve incident"
                        }
                    }
                },
                onDelete = { confirmDelete = incident },
            )
        }
        if (hasMore && nextCursor != null) {
            item {
                OutlinedButton(
                    onClick = {
                        nextCursor?.let { cursor -> scope.launch { load(cursor, append = true) } }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text(if (loading) "Loading…" else "Load more") }
            }
        }
        if (incidents.any { it.bool("pin") }) {
            item {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                repository.unpinIncidents(slug)
                                load()
                            } catch (e: Exception) {
                                error = e.message ?: "Could not unpin incidents"
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text("Unpin all incidents") }
            }
        }
    }

    if (showEditor) {
        IncidentEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { payload ->
                scope.launch {
                    try {
                        val id = editing?.optLong("id")
                        if (id != null && id > 0) repository.editIncident(slug, id, payload)
                        else repository.postIncident(slug, payload)
                        showEditor = false
                        load()
                    } catch (e: Exception) {
                        error = e.message ?: "Could not save incident"
                    }
                }
            },
        )
    }
    confirmDelete?.let { incident ->
        ConfirmDialog(
            title = "Delete incident?",
            message = "“${incident.optString("title")}” will be removed from the status page.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                val id = incident.optLong("id")
                if (id <= 0) {
                    error = "This incident has no valid identifier."
                } else scope.launch {
                    try {
                        repository.deleteIncident(slug, id)
                        confirmDelete = null
                        load()
                    } catch (e: Exception) {
                        error = e.message ?: "Could not delete incident"
                    }
                }
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun IncidentCard(
    incident: JSONObject,
    onEdit: () -> Unit,
    onResolve: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val style = incident.optString("style", "warning")
    val color = incidentColor(style)
    val active = incident.bool("active")
    KumaCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        incident.optString("title", "Incident"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (incident.bool("pin")) {
                        Icon(Icons.Default.PushPin, null, tint = color, modifier = Modifier.height(16.dp))
                    }
                }
                Text(
                    incident.optString("content"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusPill(if (active) 2 else 1, if (active) "Active" else "Resolved")
                    Text(
                        incident.optString("createdDate").replace("T", " ").take(16),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Actions") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { menu = false; onEdit() },
                )
                if (active) {
                    DropdownMenuItem(
                        text = { Text("Resolve") },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, null) },
                        onClick = { menu = false; onResolve() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete", color = KumaColors.Down) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = KumaColors.Down) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun IncidentEditorDialog(
    initial: JSONObject?,
    onDismiss: () -> Unit,
    onSave: (JSONObject) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.optString("title").orEmpty()) }
    var content by remember { mutableStateOf(initial?.optString("content").orEmpty()) }
    var style by remember { mutableStateOf(initial?.optString("style", "warning") ?: "warning") }
    var pin by remember { mutableStateOf(initial?.bool("pin", true) ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Post incident" else "Edit incident") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                PickerField(
                    label = "Style",
                    value = style,
                    options = incidentStyles,
                    onSelect = { style = it },
                )
                // Uptime Kuma always pins newly posted incidents; the switch only
                // has an effect when an existing incident is edited.
                if (initial != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pin to top", modifier = Modifier.weight(1f))
                        Switch(checked = pin, onCheckedChange = { pin = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(JSONObject().put("title", title).put("content", content).put("style", style).put("pin", pin))
                },
                enabled = title.isNotBlank() && content.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
