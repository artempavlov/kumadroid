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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.kumadroid.app.data.Beat
import org.kumadroid.app.data.KumaRepository

@Composable
fun MonitorEventsScreen(
    repository: KumaRepository,
    monitorId: Long,
    important: Boolean,
    onBack: () -> Unit,
) {
    val monitors by repository.monitors.collectAsStateWithLifecycle()
    val monitor = monitors.firstOrNull { it.id == monitorId }
    val scope = rememberCoroutineScope()
    var beats by remember { mutableStateOf<List<Beat>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var period by remember { mutableIntStateOf(24) }
    var offset by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }

    suspend fun load(reset: Boolean) {
        loading = true
        try {
            if (important) {
                val next = repository.getImportantBeats(monitorId, if (reset) 0 else offset, 100)
                beats = if (reset) next else (beats + next).distinctBy { it.json.toString() }
                offset = if (reset) next.size else offset + next.size
                hasMore = next.size >= 100
            } else {
                beats = repository.getMonitorBeats(monitorId, period)
            }
            error = ""
        } catch (e: Exception) {
            error = e.message ?: "Could not load heartbeats"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(monitorId, important, period) {
        offset = 0
        load(true)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 30.dp),
    ) {
        item {
            TopBar(
                if (important) "Important events" else "Heartbeats",
                subtitle = monitor?.name ?: "Monitor #$monitorId",
                onBack = onBack,
            )
        }
        if (!important) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(1 to "1h", 24 to "24h", 168 to "7d", 720 to "30d", 8760 to "1y").forEach { (hours, label) ->
                        ChipButton(label, period == hours) { period = hours }
                    }
                }
            }
        }
        if (error.isNotBlank()) {
            item { InfoBanner(error, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), KumaColors.Down) }
        }
        if (beats.isEmpty() && !loading) {
            item {
                EmptyState(
                    icon = if (important) Icons.Default.EventAvailable else Icons.Default.History,
                    title = if (important) "No important events" else "No heartbeats",
                    message = if (important) {
                        "Downtime and recovery events will appear here."
                    } else {
                        "No checks were recorded in this time range."
                    },
                )
            }
        }
        itemsIndexed(
            beats.sortedByDescending { it.timeMillis },
            key = { index, beat -> "${beat.time}|${beat.status}|${beat.msg}|$index" },
        ) { _, beat ->
            KumaCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                BeatRow(beat)
                if (beat.response.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        beat.response.take(600),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (beat.duration > 0) {
                    Text(
                        "Duration ${beat.duration}s · retries ${beat.retries}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (important && hasMore) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(
                        onClick = { scope.launch { load(false) } },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (loading) "Loading…" else "Load more") }
                }
            }
        }
    }
}
