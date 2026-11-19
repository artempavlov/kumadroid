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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.kumadroid.app.data.KumaRepository
import org.kumadroid.app.data.StatusPage
import org.kumadroid.app.data.bool
import org.kumadroid.app.data.str

@Composable
fun StatusPagesScreen(
    repository: KumaRepository,
    onOpen: (String) -> Unit,
    onPreview: (String) -> Unit,
    onIncidents: (String) -> Unit,
) {
    val pages by repository.statusPages.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<StatusPage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            TopBar("Status pages", subtitle = "Public pages for your monitors") {
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add status page")
                }
            }
        }
        if (error.isNotBlank()) {
            item { InfoBanner(error, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), KumaColors.Down) }
        }
        if (pages.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Public,
                    title = "No status pages yet",
                    message = "Create a public status page, choose monitors and share its link with anyone.",
                    actionLabel = "Create status page",
                    onAction = { showAdd = true },
                )
            }
        }
        items(pages, key = { it.id }) { page ->
            StatusPageCard(
                page = page,
                onOpen = { onOpen(page.slug) },
                onPreview = { onPreview(page.slug) },
                onIncidents = { onIncidents(page.slug) },
                onDelete = { deleteTarget = page },
            )
        }
    }

    if (showAdd) {
        AddStatusPageDialog(
            onDismiss = { showAdd = false },
            onAdd = { title, slug ->
                showAdd = false
                scope.launch {
                    busy = true
                    try {
                        repository.addStatusPage(title, slug)
                        error = ""
                        onOpen(slug.lowercase())
                    } catch (e: Exception) {
                        error = e.message ?: "Could not create status page"
                    } finally {
                        busy = false
                    }
                }
            },
        )
    }

    deleteTarget?.let { page ->
        ConfirmDialog(
            title = "Delete status page?",
            message = "\"${page.title}\" and its groups and incidents will be deleted. This cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                scope.launch {
                    try {
                        repository.deleteStatusPage(page.slug)
                    } catch (e: Exception) {
                        error = e.message ?: "Could not delete status page"
                    }
                }
            },
        )
    }
    if (busy) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Creating status page…") },
            text = { Text("Waiting for the server.") },
        )
    }
}

@Composable
private fun StatusPageCard(
    page: StatusPage,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
    onIncidents: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    KumaCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), onClick = onOpen) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        page.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (page.published) {
                        Text(
                            "PUBLIC",
                            style = MaterialTheme.typography.labelSmall,
                            color = KumaColors.Up,
                            modifier = Modifier
                                .padding(top = 2.dp),
                        )
                    }
                }
                Text(
                    "/${page.slug}",
                    style = MaterialTheme.typography.bodySmall,
                    color = KumaColors.Brand,
                )
                val description = page.json.str("description")
                if (description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Status page actions")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { menu = false; onOpen() },
                )
                DropdownMenuItem(
                    text = { Text("Preview") },
                    leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                    onClick = { menu = false; onPreview() },
                )
                DropdownMenuItem(
                    text = { Text("Incidents") },
                    leadingIcon = { Icon(Icons.Default.Report, contentDescription = null) },
                    onClick = { menu = false; onIncidents() },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap to edit settings and monitor groups",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddStatusPageDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var slug by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New status page") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Title", title, { title = it }, placeholder = "Service status")
                Field(
                    "Slug",
                    slug,
                    { value -> slug = value.lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' } },
                    placeholder = "service-status",
                    supporting = "Used in the public URL /status/service-status",
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(title.trim(), slug.trim()) },
                enabled = title.isNotBlank() && slug.isNotBlank(),
                shape = RoundedCornerShape(50),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
