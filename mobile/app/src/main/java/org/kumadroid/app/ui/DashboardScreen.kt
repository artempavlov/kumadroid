package org.kumadroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.kumadroid.app.data.Beat
import org.kumadroid.app.data.KumaRepository
import org.kumadroid.app.data.KumaStatus
import org.kumadroid.app.data.Monitor
import org.kumadroid.app.data.bool
import org.kumadroid.app.data.num
import org.kumadroid.app.data.str

private enum class DashFilter(val label: String) {
    All("All"), Up("Up"), Down("Down"), Paused("Paused")
}

internal data class DashboardCounts(val up: Int, val down: Int, val paused: Int)

/** Counts every check, including monitors nested inside group folders. */
internal fun dashboardCounts(monitors: List<Monitor>, heartbeats: Map<Long, List<Beat>>): DashboardCounts {
    val checks = monitors.filter { it.type != "group" }
    return DashboardCounts(
        up = checks.count { statusForMonitor(it, heartbeats[it.id]) == KumaStatus.UP },
        down = checks.count { statusForMonitor(it, heartbeats[it.id]) == KumaStatus.DOWN },
        paused = checks.count { statusForMonitor(it, heartbeats[it.id]) == -1 },
    )
}

@Composable
fun DashboardScreen(
    repository: KumaRepository,
    onOpenMonitor: (Long) -> Unit,
    onAddMonitor: () -> Unit,
    onEditMonitor: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTags: () -> Unit,
) {
    val monitors by repository.monitors.collectAsStateWithLifecycle()
    val heartbeats by repository.heartbeats.collectAsStateWithLifecycle()
    val serverInfo by repository.serverInfo.collectAsStateWithLifecycle()
    val barStyle by repository.heartbeatBarStyle.collectAsStateWithLifecycle()
    val elapsedStyle by repository.elapsedTimeStyle.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(DashFilter.All) }
    var error by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }

    val roots = monitors.filter { it.parent == null }
    val children = monitors.filter { it.parent != null }.groupBy { it.parent }
    val needle = query.trim().lowercase()
    fun matchesQuery(monitor: Monitor): Boolean = needle.isEmpty() ||
        monitor.name.lowercase().contains(needle) ||
        monitor.displayTarget().lowercase().contains(needle) ||
        monitor.type.lowercase().contains(needle)
    fun matchesFilter(monitor: Monitor): Boolean {
        val status = statusForMonitor(monitor, heartbeats[monitor.id])
        return when (filter) {
            DashFilter.All -> true
            DashFilter.Up -> status == KumaStatus.UP
            DashFilter.Down -> status == KumaStatus.DOWN || status == KumaStatus.PENDING
            DashFilter.Paused -> status == -1
        }
    }
    // A group is visible when at least one of its children matches. If the
    // group name itself matches the search, keep all of its children visible.
    val shownChildren = children.mapValues { (groupId, groupChildren) ->
        val groupMatchesQuery = needle.isEmpty() || monitors.firstOrNull { it.id == groupId }?.let {
            it.name.lowercase().contains(needle) || it.type.lowercase().contains(needle)
        } == true
        groupChildren.filter { (groupMatchesQuery || matchesQuery(it)) && matchesFilter(it) }
    }
    val visibleRoots = roots.filter { monitor ->
        if (monitor.type == "group") {
            val shown = shownChildren[monitor.id].orEmpty()
            shown.isNotEmpty() || (filter == DashFilter.All && needle.isEmpty() && children[monitor.id].isNullOrEmpty())
        } else {
            matchesQuery(monitor) && matchesFilter(monitor)
        }
    }
    // Group children are still checks, even though they are rendered inside a folder.
    val checks = monitors.filter { it.type != "group" }
    val counts = dashboardCounts(monitors, heartbeats)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
    ) {
        item {
            PageTitle("Monitors", "${checks.size} ${if (checks.size == 1) "check" else "checks"} · ${serverInfo.version.ifBlank { "Uptime Kuma" }}") {
                IconButton(onClick = {
                    refreshing = true
                    scope.launch {
                        try {
                            repository.refreshAll()
                        } catch (e: Exception) {
                            error = e.message ?: "Refresh failed"
                        } finally {
                            refreshing = false
                        }
                    }
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onAddMonitor) {
                    Icon(Icons.Default.Add, contentDescription = "Add monitor", tint = KumaColors.Brand)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryPill("Up", counts.up, KumaColors.Up, Modifier.weight(1f))
                SummaryPill("Down", counts.down, KumaColors.Down, Modifier.weight(1f))
                SummaryPill("Paused", counts.paused, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                SearchField(query, { query = it })
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashFilter.entries.forEach { f ->
                        ChipButton(f.label, filter == f) { filter = f }
                    }
                }
            }
        }
        if (error.isNotBlank()) {
            item {
                InfoBanner(error, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), KumaColors.Down)
            }
        }
        if (visibleRoots.isEmpty()) {
            item {
                EmptyState(
                    icon = if (monitors.isEmpty()) Icons.Default.CloudOff else Icons.Default.SearchOff,
                    title = if (monitors.isEmpty()) "No monitors yet" else "Nothing matches",
                    message = if (monitors.isEmpty()) {
                        "Create your first check and it will show up here with live heartbeats."
                    } else {
                        "Try a different search or status filter."
                    },
                    actionLabel = if (monitors.isEmpty()) "Add monitor" else null,
                    onAction = if (monitors.isEmpty()) onAddMonitor else null,
                )
            }
        }
        items(visibleRoots, key = { it.id }) { monitor ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                if (monitor.type == "group") {
                    GroupCard(
                        monitor = monitor,
                        children = shownChildren[monitor.id].orEmpty(),
                        heartbeats = heartbeats,
                        showBars = barStyle != "none",
                        onOpen = onOpenMonitor,
                        onEdit = onEditMonitor,
                    )
                } else {
                    MonitorCard(
                        monitor = monitor,
                        beats = heartbeats[monitor.id].orEmpty(),
                        barStyle = barStyle,
                        elapsedStyle = elapsedStyle,
                        onOpen = { onOpenMonitor(monitor.id) },
                        onEdit = { onEditMonitor(monitor.id) },
                        onTogglePause = {
                            scope.launch {
                                try {
                                    if (monitor.active) repository.pauseMonitor(monitor.id) else repository.resumeMonitor(monitor.id)
                                } catch (e: Exception) {
                                    error = e.message ?: "Action failed"
                                }
                            }
                        },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SummaryPill(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(color.copy(alpha = 0.09f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(7.dp).background(color, RoundedCornerShape(50)))
        Text(count.toString(), style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonitorCard(
    monitor: Monitor,
    beats: List<Beat>,
    barStyle: String,
    elapsedStyle: String,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onTogglePause: () -> Unit,
) {
    val status = statusForMonitor(monitor, beats)
    val last = beats.lastOrNull()
    KumaCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (status == -1) KumaStatus.PENDING else status, 11)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    monitor.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (monitor.displayTarget().isNotBlank()) {
                    Text(
                        monitor.displayTarget(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            MonitorMenu(monitor, onEdit, onTogglePause)
        }
        if (monitor.tags.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                monitor.tags.take(3).forEach { tag -> TagPill(tag.name, tag.color) }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (barStyle == "normal") {
            HeartbeatBars(beats, count = 42, height = 32)
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (!monitor.active) "Paused" else statusLabel(status),
                style = MaterialTheme.typography.labelMedium,
                color = if (!monitor.active) MaterialTheme.colorScheme.onSurfaceVariant else statusColor(status),
            )
            if (elapsedStyle != "none") {
                if (elapsedStyle == "with-line") {
                    DotDivider()
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    last?.let { beatAgo(it.timeMillis) } ?: "waiting for data",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            if (last?.ping != null) {
                Text(
                    "${last.ping!!.toInt()} ms",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    humanInterval(monitor.interval),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (barStyle == "bottom") {
            Spacer(Modifier.height(10.dp))
            HeartbeatBars(beats, count = 42, height = 22)
        }
    }
}

@Composable
private fun GroupCard(
    monitor: Monitor,
    children: List<Monitor>,
    heartbeats: Map<Long, List<Beat>>,
    showBars: Boolean,
    onOpen: (Long) -> Unit,
    onEdit: (Long) -> Unit,
) {
    KumaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                androidx.compose.material.icons.Icons.Default.Folder,
                contentDescription = null,
                tint = KumaColors.Brand,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(monitor.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${children.size} ${if (children.size == 1) "monitor" else "monitors"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onEdit(monitor.id) }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit group", modifier = Modifier.size(18.dp))
            }
        }
        if (children.isEmpty()) {
            Text(
                "This group is empty.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        children.forEach { child ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(child.id) }
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(statusForMonitor(child, heartbeats[child.id]).let { if (it == -1) KumaStatus.PENDING else it }, 9)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(child.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        child.displayTarget(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (showBars) {
                    Box(Modifier.width(88.dp)) {
                        HeartbeatBars(heartbeats[child.id].orEmpty(), count = 18, height = 20)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorMenu(monitor: Monitor, onEdit: () -> Unit, onTogglePause: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Edit monitor") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { open = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text(if (monitor.active) "Pause" else "Resume") },
                leadingIcon = {
                    Icon(if (monitor.active) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                },
                onClick = { open = false; onTogglePause() },
            )
        }
    }
}
