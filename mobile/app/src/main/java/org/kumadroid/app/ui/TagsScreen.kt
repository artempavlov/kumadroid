package org.kumadroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.kumadroid.app.data.KumaRepository
import org.kumadroid.app.data.Tag

private val tagColors = listOf(
    "#4b5563", "#ef4444", "#f97316", "#eab308", "#22c55e",
    "#14b8a6", "#3b82f6", "#8b5cf6", "#ec4899", "#64748b",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsScreen(repository: KumaRepository, onBack: () -> Unit) {
    val tags by repository.tags.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Tag?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Tag?>(null) }
    var error by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            TopBar(
                "Tags",
                subtitle = "${tags.size} tag${if (tags.size == 1) "" else "s"}",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add tag")
                    }
                },
            )
        }
        if (error.isNotBlank()) {
            item { InfoBanner(error, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), KumaColors.Down) }
        }
        if (tags.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Sell,
                    title = "No tags yet",
                    message = "Tags group monitors and are shown on the dashboard.",
                    actionLabel = "Create a tag",
                    onAction = { creating = true },
                )
            }
        }
        items(tags, key = { it.id }) { tag ->
            KumaCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TagPill(tag.name, tag.color)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { editing = tag }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { deleting = tag }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        val tag = editing
        var name by remember(tag?.id, creating) { mutableStateOf(tag?.name ?: "") }
        var color by remember(tag?.id, creating) { mutableStateOf(tag?.color ?: tagColors.first()) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) { creating = false; editing = null } },
            title = { Text(if (tag == null) "New tag" else "Edit tag") },
            text = {
                Column {
                    Field("Name", name, { name = it })
                    Spacer(Modifier.height(12.dp))
                    Text("Color", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tagColors.forEach { hex ->
                            val parsed = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(KumaColors.Brand)
                            Box(
                                Modifier
                                    .width(34.dp)
                                    .height(34.dp)
                                    .padding(2.dp),
                            ) {
                                androidx.compose.material3.Surface(
                                    onClick = { color = hex },
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = parsed,
                                    border = if (color == hex) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                                    modifier = Modifier.fillMaxSize(),
                                ) {}
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Field("Custom color", color, { color = it }, placeholder = "#4b5563")
                    if (error.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = KumaColors.Down, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = ""
                            try {
                                if (tag == null) repository.addTag(name.trim(), color)
                                else repository.editTag(tag.id, name.trim(), color)
                                creating = false
                                editing = null
                            } catch (e: Exception) {
                                error = e.message ?: "Could not save tag"
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text(if (busy) "Saving…" else "Save") }
            },
            dismissButton = { TextButton(onClick = { creating = false; editing = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { tag ->
        ConfirmDialog(
            title = "Delete “${tag.name}”?",
            message = "The tag is removed from every monitor that uses it. Monitors themselves are not deleted.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { deleting = null },
        ) {
            scope.launch {
                try {
                    repository.deleteTag(tag.id)
                    deleting = null
                } catch (e: Exception) {
                    error = e.message ?: "Could not delete tag"
                    deleting = null
                }
            }
        }
    }
}
