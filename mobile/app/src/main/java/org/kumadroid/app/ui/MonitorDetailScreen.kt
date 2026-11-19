package org.kumadroid.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.kumadroid.app.data.Beat
import org.kumadroid.app.data.KumaRepository
import org.kumadroid.app.data.KumaStatus
import org.kumadroid.app.data.Monitor
import org.kumadroid.app.data.MonitorCatalog
import org.kumadroid.app.data.bool
import org.kumadroid.app.data.dbl
import org.kumadroid.app.data.long
import org.kumadroid.app.data.num
import org.kumadroid.app.data.str

private data class ChartPeriod(val label: String, val hours: Int)

@Composable
fun MonitorDetailScreen(
    repository: KumaRepository,
    monitorId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onEvents: () -> Unit,
    onImportant: () -> Unit,
    onClone: () -> Unit,
    onDeleted: () -> Unit,
) {
    val monitors by repository.monitors.collectAsStateWithLifecycle()
    val liveBeats by repository.heartbeats.collectAsStateWithLifecycle()
    val important by repository.importantBeats.collectAsStateWithLifecycle()
    val uptime by repository.uptime.collectAsStateWithLifecycle()
    val avgPing by repository.avgPing.collectAsStateWithLifecycle()
    val certInfo by repository.certInfo.collectAsStateWithLifecycle()
    val domainInfo by repository.domainInfo.collectAsStateWithLifecycle()
    val barStyle by repository.heartbeatBarStyle.collectAsStateWithLifecycle()
    val elapsedStyle by repository.elapsedTimeStyle.collectAsStateWithLifecycle()
    val monitor = monitors.firstOrNull { it.id == monitorId }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var history by remember { mutableStateOf<List<Beat>>(emptyList()) }
    var chart by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var historyHours by remember { mutableIntStateOf(24) }
    var chartHours by remember { mutableIntStateOf(24) }
    var loadingHistory by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var screenshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var screenshotError by remember { mutableStateOf("") }
    var screenshotReload by remember { mutableIntStateOf(0) }
    var pushLanguage by remember { mutableStateOf("javascript-fetch") }
    var pushCode by remember { mutableStateOf("") }
    var pushCodeError by remember { mutableStateOf("") }
    val screenshotPath = monitor?.json?.str("screenshot").orEmpty()

    val pushUrl = if (monitor?.type == "push") {
        val token = monitor.json.str("pushToken")
        if (token.isBlank()) "" else {
            repository.serverUrl.trimEnd('/') + "/api/push/" + token + "?status=up&msg=OK&ping="
        }
    } else ""

    LaunchedEffect(pushUrl, pushLanguage) {
        if (pushUrl.isBlank()) return@LaunchedEffect
        pushCode = ""
        pushCodeError = ""
        try {
            pushCode = repository.getPushExample(pushLanguage)
                .replace("60", monitor?.interval?.toString() ?: "60")
                .replace("https://example.com/api/push/key?status=up&msg=OK&ping=", pushUrl)
        } catch (e: Exception) {
            pushCodeError = e.message ?: "Could not load example"
        }
    }

    LaunchedEffect(monitorId, screenshotPath, screenshotReload) {
        if (screenshotPath.isBlank()) {
            screenshot = null
            screenshotError = ""
            return@LaunchedEffect
        }
        try {
            val bytes = repository.fetchImage(screenshotPath)
            screenshot = withContext(Dispatchers.IO) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
            screenshotError = ""
        } catch (e: Exception) {
            screenshot = null
            screenshotError = e.message ?: "Could not load screenshot"
        }
    }

    val chartPeriods = listOf(
        ChartPeriod("1h", 1), ChartPeriod("3h", 3), ChartPeriod("6h", 6),
        ChartPeriod("24h", 24), ChartPeriod("7d", 168), ChartPeriod("30d", 720), ChartPeriod("1y", 8760),
    )

    LaunchedEffect(monitorId, historyHours) {
        loadingHistory = true
        try {
            history = repository.getMonitorBeats(monitorId, historyHours)
        } catch (e: Exception) {
            error = e.message ?: "Could not load heartbeats"
        } finally {
            loadingHistory = false
        }
    }
    LaunchedEffect(monitorId, chartHours) {
        try {
            chart = repository.getChartData(monitorId, chartHours)
        } catch (e: Exception) {
            error = e.message ?: "Could not load chart"
        }
    }

    if (monitor == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            TopBar("Monitor", onBack = onBack)
            EmptyState(Icons.Default.Delete, "Monitor not found", "It may have been deleted from another client.")
        }
        return
    }

    val beats = (history + liveBeats[monitorId].orEmpty())
        .distinctBy { "${it.time}|${it.status}|${it.msg}" }
        .sortedBy { it.timeMillis }
    val status = statusForMonitor(monitor, beats)
    val last = beats.lastOrNull()
    val uptime24 = uptime["${monitorId}_24"]
    val uptime30 = uptime["${monitorId}_720"]
    val uptime1y = uptime["${monitorId}_1y"]
    val importantForMonitor = important[monitorId].orEmpty()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            TopBar(
                title = monitor.name,
                subtitle = MonitorCatalog.label(monitor.type),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (monitor.active) "Pause monitor" else "Resume monitor") },
                                leadingIcon = { Icon(if (monitor.active) Icons.Default.Pause else Icons.Default.PlayArrow, null) },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        try {
                                            if (monitor.active) repository.pauseMonitor(monitor.id) else repository.resumeMonitor(monitor.id)
                                            repository.refreshAll()
                                        } catch (e: Exception) {
                                            error = e.message ?: "Action failed"
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Clone monitor") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                onClick = { menuOpen = false; onClone() },
                            )
                            DropdownMenuItem(
                                text = { Text("Clear events") },
                                leadingIcon = { Icon(Icons.Default.Event, null) },
                                onClick = { menuOpen = false; confirmClear = "events" },
                            )
                            DropdownMenuItem(
                                text = { Text("Clear heartbeats") },
                                leadingIcon = { Icon(Icons.Default.Event, null) },
                                onClick = { menuOpen = false; confirmClear = "heartbeats" },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete monitor", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (error.isNotBlank()) {
                    InfoBanner(error, Modifier.padding(bottom = 8.dp), KumaColors.Down)
                }
                KumaCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(if (status == -1) KumaStatus.PENDING else status, 13)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(if (status == -1) KumaStatus.PENDING else status, if (!monitor.active) "Paused" else statusLabel(status))
                                if (monitor.json.bool("maintenance")) {
                                    Spacer(Modifier.width(6.dp))
                                    StatusPill(KumaStatus.MAINTENANCE, "Maintenance")
                                }
                            }
                            if (monitor.displayTarget().isNotBlank()) {
                                Spacer(Modifier.height(7.dp))
                                Text(
                                    monitor.displayTarget(),
                                    style = MaterialTheme.typography.bodySmall.merge(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable {
                                        clipboard.setText(AnnotatedString(monitor.displayTarget()))
                                    },
                                )
                            }
                        }
                    }
                    if (monitor.description.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(monitor.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (monitor.tags.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            monitor.tags.forEach { tag -> TagPill(tag.name, tag.color) }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (barStyle != "none") {
                        HeartbeatBars(beats, count = 50, height = 36)
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (elapsedStyle != "none") {
                            if (last == null) {
                                Text(
                                    "No heartbeat yet",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    "Last check",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (elapsedStyle == "with-line") {
                                    DotDivider()
                                } else {
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    beatAgo(last.timeMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onEvents) { Text("All heartbeats") }
                    }
                }
            }
        }
        if (pushUrl.isNotBlank()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    FormSection("Push URL", "Call this URL from a script to record a heartbeat. The token is secret.") {
                        Text(
                            pushUrl,
                            style = MaterialTheme.typography.bodySmall.merge(fontFamily = FontFamily.Monospace),
                        )
                        TextButton(onClick = { clipboard.setText(AnnotatedString(pushUrl)) }) {
                            Text("Copy push URL")
                        }
                        PickerField(
                            "Code example",
                            pushLanguage,
                            listOf(
                                "javascript-fetch" to "JavaScript (fetch)",
                                "typescript-fetch" to "TypeScript (fetch)",
                                "python" to "Python",
                                "go" to "Go",
                                "java" to "Java",
                                "csharp" to "C#",
                                "php" to "PHP",
                                "bash-curl" to "Bash (curl)",
                                "powershell" to "PowerShell",
                                "docker" to "Docker",
                            ),
                            { pushLanguage = it },
                        )
                        when {
                            pushCodeError.isNotBlank() -> InfoBanner(pushCodeError, tone = KumaColors.Down)
                            pushCode.isNotBlank() -> {
                                Text(
                                    pushCode,
                                    style = MaterialTheme.typography.bodySmall.merge(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 260.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                        .verticalScroll(rememberScrollState()),
                                )
                                TextButton(onClick = { clipboard.setText(AnnotatedString(pushCode)) }) {
                                    Text("Copy code")
                                }
                            }
                            else -> Text(
                                "Loading example…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (monitor.type == "real-browser") {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    FormSection("Latest screenshot") {
                        when {
                            screenshot != null -> Image(
                                bitmap = screenshot!!,
                                contentDescription = "Latest browser screenshot",
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.FillWidth,
                            )
                            screenshotError.isNotBlank() -> InfoBanner(screenshotError, tone = KumaColors.Down)
                            else -> Text(
                                "No screenshot has been captured yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { screenshotReload++ }) { Text("Reload screenshot") }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                KumaCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UptimeRing(uptime24, size = 92, label = "24 hours")
                        UptimeRing(uptime30, size = 92, label = "30 days")
                        UptimeRing(uptime1y, size = 92, label = "1 year")
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("Avg ping", avgPing[monitorId]?.let { "${it.toInt()} ms" } ?: "—", Modifier.weight(1f), Icons.Default.Speed)
                        MetricTile("Interval", humanInterval(monitor.interval), Modifier.weight(1f), Icons.Default.Timer)
                        MetricTile("Retries", monitor.json.num("maxretries").toString(), Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                KumaCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Response time", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            when {
                                chart.isEmpty() -> ""
                                chartHours % 24 == 0 && chartHours >= 168 -> "${chartHours / 24}d"
                                chartHours >= 24 -> "${chartHours / 24}d"
                                else -> "${chartHours}h"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chartPeriods.forEach { p ->
                            ChipButton(p.label, chartHours == p.hours) { chartHours = p.hours }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    PingChart(chart, 190)
                    val avg = chart.mapNotNull { it.dbl("avgPing") }.takeIf { it.isNotEmpty() }?.average()
                    val min = chart.mapNotNull { it.dbl("minPing") }.minOrNull()
                    val max = chart.mapNotNull { it.dbl("maxPing") }.maxOrNull()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("Avg", avg?.let { "${it.toInt()} ms" } ?: "—", Modifier.weight(1f))
                        MetricTile("Min", min?.let { "${it.toInt()} ms" } ?: "—", Modifier.weight(1f))
                        MetricTile("Max", max?.let { "${it.toInt()} ms" } ?: "—", Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                KumaCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent heartbeats", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onEvents) { Text("View all") }
                    }
                    if (loadingHistory && beats.isEmpty()) {
                        Text("Loading…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    beats.takeLast(8).reversed().forEach { beat ->
                        BeatRow(beat)
                    }
                    if (beats.isEmpty()) {
                        Text(
                            "No heartbeats recorded in this period.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                KumaCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Important events", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onImportant) { Text("View all") }
                    }
                    if (importantForMonitor.isEmpty()) {
                        Text(
                            "No down or recovery events yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    importantForMonitor.take(4).forEach { beat -> BeatRow(beat) }
                }
            }
        }
        val cert = certInfo[monitorId]
        if (cert != null) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    FormSection("TLS certificate") {
                        val subject = cert.optJSONObject("subject")?.str("CN").orEmpty()
                        val issuer = cert.optJSONObject("issuer")?.str("CN").orEmpty()
                        InfoRow("Subject", subject)
                        InfoRow("Issuer", issuer)
                        InfoRow("Valid from", cert.str("valid_from"))
                        InfoRow("Valid to", cert.str("valid_to"))
                        val days = cert.opt("daysRemaining")
                        if (days != null) InfoRow("Days remaining", days.toString())
                    }
                }
            }
        }
        val domain = domainInfo[monitorId]
        if (domain != null) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    FormSection("Domain expiry") {
                        InfoRow("Days remaining", domain.opt("daysRemaining")?.toString() ?: "—")
                        InfoRow("Expires on", domain.opt("expiresOn")?.toString() ?: "—")
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                KumaCard {
                    InfoRow("Type", MonitorCatalog.label(monitor.type))
                    InfoRow("Parent", monitors.firstOrNull { it.id == monitor.parent }?.name ?: "—")
                    InfoRow("Heartbeat interval", humanInterval(monitor.interval))
                    InfoRow("Retry interval", humanInterval(monitor.json.num("retryInterval", 60)))
                    InfoRow("Max retries", monitor.json.num("maxretries").toString())
                    InfoRow("Timeout", "${monitor.json.num("timeout", 48)}s")
                    InfoRow("Upside down", if (monitor.json.bool("upsideDown")) "Yes" else "No")
                    InfoRow("Created", monitor.json.str("createdDate"))
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${monitor.name}?",
            message = "All heartbeats, events and settings for this monitor will be removed. This cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                scope.launch {
                    try {
                        repository.deleteMonitor(monitor.id, true)
                        onDeleted()
                    } catch (e: Exception) {
                        error = e.message ?: "Delete failed"
                    }
                }
            },
        )
    }
    confirmClear?.let { what ->
        ConfirmDialog(
            title = if (what == "events") "Clear events?" else "Clear heartbeats?",
            message = if (what == "events") {
                "Important events for this monitor will be deleted."
            } else {
                "Heartbeats and statistics for this monitor will be deleted. Uptime history will be reset."
            },
            confirmLabel = "Clear",
            destructive = true,
            onDismiss = { confirmClear = null },
            onConfirm = {
                scope.launch {
                    try {
                        if (what == "events") repository.clearEvents(monitor.id) else repository.clearHeartbeats(monitor.id)
                        repository.refreshAll()
                        history = emptyList()
                    } catch (e: Exception) {
                        error = e.message ?: "Action failed"
                    }
                }
            },
        )
    }
}

@Composable
fun BeatRow(beat: Beat) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(beat.status, 8)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    statusLabel(beat.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor(beat.status),
                )
                if (beat.important) {
                    Spacer(Modifier.width(6.dp))
                    Text("important", style = MaterialTheme.typography.labelSmall, color = KumaColors.Pending)
                }
            }
            if (beat.msg.isNotBlank()) {
                Text(
                    beat.msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(beat.timeLabel(), style = MaterialTheme.typography.labelSmall)
            if (beat.ping != null) {
                Text("${beat.ping!!.toInt()} ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
