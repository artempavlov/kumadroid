package org.kumadroid.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import org.kumadroid.app.data.Beat
import org.kumadroid.app.data.KumaRepository
import org.kumadroid.app.data.KumaStatus
import org.kumadroid.app.data.bool
import org.kumadroid.app.data.long
import org.kumadroid.app.data.num
import org.kumadroid.app.data.str

@Composable
fun PublicStatusPageScreen(
    repository: KumaRepository,
    slug: String,
    onBack: () -> Unit,
    onIncidents: () -> Unit,
) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var heartbeats by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun load(first: Boolean) {
        if (first) loading = true else refreshing = true
        try {
            data = repository.fetchStatusPageData(slug)
            heartbeats = repository.fetchStatusPageHeartbeats(slug)
            error = ""
        } catch (e: Exception) {
            error = e.message ?: "Could not load status page"
        } finally {
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(slug) {
        load(true)
        while (true) {
            delay(30_000)
            load(false)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Status page", subtitle = "/$slug", onBack = onBack) {
            if (refreshing) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = KumaColors.Brand)
            } else {
                IconButton(onClick = { /* reloaded automatically */ }) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
            }
            IconButton(onClick = onIncidents) { Icon(Icons.Default.Report, contentDescription = "Incidents") }
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KumaColors.Brand)
            }
            return@Column
        }
        val page = data
        if (page == null) {
            EmptyState(
                icon = Icons.Default.Report,
                title = "Status page unavailable",
                message = error.ifBlank { "Check the status page slug and server connection." },
                actionLabel = "Retry",
                onAction = { /* LaunchedEffect owns refresh */ },
            )
            return@Column
        }
        val config = page.optJSONObject("config") ?: JSONObject()
        val groups = page.optJSONArray("publicGroupList") ?: JSONArray()
        val incidents = page.optJSONArray("incidents") ?: JSONArray()
        val maintenance = page.optJSONArray("maintenanceList") ?: JSONArray()
        val hb = heartbeats ?: JSONObject()

        val monitorBeats = remember(hb) {
            mutableMapOf<Long, List<Beat>>()
        }
        fun beatsFor(id: Long): List<Beat> {
            monitorBeats[id]?.let { return it }
            val array = hb.optJSONArray(id.toString()) ?: return emptyList()
            val list = (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::Beat) }
            monitorBeats[id] = list
            return list
        }

        val allStatuses = mutableListOf<Int>()
        for (i in 0 until groups.length()) {
            val group = groups.optJSONObject(i) ?: continue
            val monitors = group.optJSONArray("monitorList") ?: continue
            for (j in 0 until monitors.length()) {
                val monitor = monitors.optJSONObject(j) ?: continue
                allStatuses += beatsFor(monitor.long("id")).lastOrNull()?.status ?: -1
            }
        }
        val overall = when {
            allStatuses.isEmpty() -> -1
            allStatuses.any { it == KumaStatus.DOWN || it == KumaStatus.PENDING } -> KumaStatus.DOWN
            allStatuses.any { it == KumaStatus.MAINTENANCE } -> KumaStatus.MAINTENANCE
            else -> KumaStatus.UP
        }
        val overallLabel = when (overall) {
            KumaStatus.UP -> "All systems operational"
            KumaStatus.DOWN -> "Some systems are experiencing issues"
            KumaStatus.MAINTENANCE -> "Under maintenance"
            else -> "No services yet"
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(KumaColors.Brand),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                config.str("title", "K").take(1).uppercase(),
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color(0xFF07240F),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                config.str("title", "Status"),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            val desc = config.str("description")
                            if (desc.isNotBlank()) {
                                Text(
                                    desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(statusColor(overall).copy(alpha = 0.14f))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(overall, 13)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            overallLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = statusColor(overall),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Auto-refresh 30s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (incidents.length() > 0) {
                item {
                    SectionTitle("Active incidents")
                    for (index in 0 until incidents.length()) {
                        val incident = incidents.optJSONObject(index) ?: continue
                        IncidentCard(incident)
                    }
                }
            }

            if (maintenance.length() > 0) {
                item {
                    SectionTitle("Maintenance")
                    for (index in 0 until maintenance.length()) {
                        val item = maintenance.optJSONObject(index) ?: continue
                        MaintenanceNotice(item)
                    }
                }
            }

            if (groups.length() == 0) {
                item {
                    EmptyState(
                        icon = Icons.Default.Build,
                        title = "No monitors on this page",
                        message = "The owner has not added any monitor groups yet.",
                    )
                }
            }

            for (gIndex in 0 until groups.length()) {
                val group = groups.optJSONObject(gIndex) ?: continue
                item(key = "public-group-${group.optLong("id", gIndex.toLong())}") {
                    KumaCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(
                            group.str("name", "Services"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val monitorArray = group.optJSONArray("monitorList") ?: JSONArray()
                        for (mIndex in 0 until monitorArray.length()) {
                            val monitor = monitorArray.optJSONObject(mIndex) ?: continue
                            val id = monitor.long("id")
                            val beats = beatsFor(id)
                            val status = beats.lastOrNull()?.status ?: -1
                            val uptime = hb.optJSONObject("uptimeList")?.optDouble("${id}_24", -1.0)?.let { if (it >= 0) it * 100.0 else it } ?: -1.0
                            Column(Modifier.padding(vertical = 5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusDot(status, 10)
                                    Spacer(Modifier.width(9.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            monitor.str("name", "Monitor #$id"),
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (monitor.bool("sendUrl") && monitor.str("url").isNotBlank()) {
                                            Text(
                                                monitor.str("url"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Text(
                                        if (uptime >= 0) formatPercent(uptime) else "—",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (uptime >= 99.0) KumaColors.Up else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (beats.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    HeartbeatBars(beats, count = 36, height = 20)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (config.bool("showPoweredBy", true)) {
                        Text(
                            "Powered by Uptime Kuma",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val footer = config.str("footerText")
                    if (footer.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            footer,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IncidentCard(incident: JSONObject) {
    val style = incident.str("style", "warning")
    val color = when (style) {
        "danger" -> KumaColors.Down
        "info" -> KumaColors.Pending
        "primary" -> KumaColors.Brand
        "light", "dark" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> KumaColors.Pending
    }
    KumaCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(
                incident.str("title"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (incident.bool("pin")) {
                Text("PINNED", style = MaterialTheme.typography.labelSmall, color = KumaColors.Brand)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            incident.str("content"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val created = incident.str("createdDate")
        if (created.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(created.replace("T", " ").take(16), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MaintenanceNotice(item: JSONObject) {
    KumaCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Build, contentDescription = null, tint = KumaColors.Maintenance, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                item.str("title", "Scheduled maintenance"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                item.str("status").replace("-", " "),
                style = MaterialTheme.typography.labelSmall,
                color = KumaColors.Maintenance,
            )
        }
        val description = item.str("description")
        if (description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val dates = item.optJSONArray("dateRange")
        if (dates != null && dates.length() >= 2) {
            val start = dates.optString(0)
            val end = dates.optString(1)
            if (start.isNotBlank() || end.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${start.replace("T", " ").take(16)} → ${end.replace("T", " ").take(16)}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
