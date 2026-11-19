package org.kumadroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.kumadroid.app.data.KumaRepository
import org.kumadroid.app.data.bool
import org.kumadroid.app.data.str
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
private val displayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private val maintenanceStrategies = listOf(
    "manual" to "Manual",
    "single" to "Single window",
    "cron" to "Cron expression",
    "recurring-interval" to "Recurring · interval",
    "recurring-weekday" to "Recurring · day of week",
    "recurring-day-of-month" to "Recurring · day of month",
)

private fun defaultMaintenance(): JSONObject {
    val now = LocalDateTime.now().withSecond(0).withNano(0)
    val later = now.plusHours(1)
    return JSONObject()
        .put("title", "")
        .put("description", "")
        .put("strategy", "single")
        .put("active", true)
        .put("cron", "30 3 * * *")
        .put("durationMinutes", 60)
        .put("intervalDay", 1)
        .put("dateRange", JSONArray().put(now.format(dateTimeFormat)).put(later.format(dateTimeFormat)))
        .put(
            "timeRange",
            JSONArray()
                .put(JSONObject().put("hours", 2).put("minutes", 0))
                .put(JSONObject().put("hours", 3).put("minutes", 0)),
        )
        .put("weekdays", JSONArray())
        .put("daysOfMonth", JSONArray())
        .put("timezoneOption", "SAME_AS_SERVER")
}

private fun normalizeDate(value: String?): String {
    if (value.isNullOrBlank() || value == "null") return ""
    return value.replace(" ", "T").take(16)
}

private fun arrayString(array: JSONArray?, index: Int): String? {
    val value = array?.opt(index) ?: return null
    if (value == JSONObject.NULL) return null
    return value as? String ?: value.toString()
}

private fun parseTime(value: String?): JSONObject {
    val parts = (value ?: "02:00").split(":")
    return JSONObject()
        .put("hours", parts.getOrNull(0)?.toIntOrNull() ?: 2)
        .put("minutes", parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaintenanceEditScreen(
    repository: KumaRepository,
    maintenanceId: Long?,
    cloneFromId: Long? = null,
    onBack: () -> Unit,
) {
    val monitors by repository.monitors.collectAsStateWithLifecycle()
    val statusPages by repository.statusPages.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<JSONObject?>(null) }
    var savedId by remember { mutableStateOf(maintenanceId?.takeIf { it > 0 }) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var strategy by remember { mutableStateOf("single") }
    var active by remember { mutableStateOf(true) }
    var cron by remember { mutableStateOf("30 3 * * *") }
    var duration by remember { mutableStateOf("60") }
    var intervalDay by remember { mutableStateOf("1") }
    var timezone by remember { mutableStateOf("SAME_AS_SERVER") }
    var dateStart by remember { mutableStateOf("") }
    var dateEnd by remember { mutableStateOf("") }
    var timeStart by remember { mutableStateOf("02:00") }
    var timeEnd by remember { mutableStateOf("03:00") }
    var weekdays by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var daysOfMonth by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedMonitors by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedPages by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var allPages by remember { mutableStateOf(false) }
    var showMonitorPicker by remember { mutableStateOf(false) }
    var showPagePicker by remember { mutableStateOf(false) }

    LaunchedEffect(maintenanceId, cloneFromId) {
        loading = true
        try {
            val sourceId = if (maintenanceId != null && maintenanceId > 0) maintenanceId else cloneFromId
            val isClone = maintenanceId == null || maintenanceId <= 0
            val obj = if (sourceId != null && sourceId > 0) {
                (repository.getMaintenance(sourceId) ?: defaultMaintenance()).let {
                    if (isClone) {
                        it.put("id", JSONObject.NULL)
                        it.put("title", "Clone of ${it.optString("title")}")
                    }
                    it
                }
            } else {
                defaultMaintenance()
            }
            draft = obj
            title = obj.str("title")
            description = obj.str("description")
            strategy = obj.str("strategy", "single")
            active = obj.bool("active", true)
            cron = obj.str("cron", "30 3 * * *")
            duration = obj.optInt("durationMinutes", 60).toString()
            intervalDay = obj.optInt("intervalDay", 1).toString()
            timezone = obj.str("timezoneOption", "SAME_AS_SERVER")
            val dates = obj.optJSONArray("dateRange")
            dateStart = normalizeDate(arrayString(dates, 0))
            dateEnd = normalizeDate(arrayString(dates, 1))
            val times = obj.optJSONArray("timeRange")
            timeStart = timeLabel(times?.optJSONObject(0))
            timeEnd = timeLabel(times?.optJSONObject(1))
            val wd = obj.optJSONArray("weekdays") ?: JSONArray()
            weekdays = (0 until wd.length()).mapNotNull { wd.optInt(it).takeIf { v -> v > 0 } }.toSet()
            val dm = obj.optJSONArray("daysOfMonth") ?: JSONArray()
            daysOfMonth = (0 until dm.length()).mapNotNull { i ->
                val v = dm.opt(i)
                if (v is Number) v.toInt().toString() else v?.toString()
            }.toSet()
            if (sourceId != null && sourceId > 0) {
                selectedMonitors = repository.getMaintenanceMonitorIds(sourceId).toSet()
                selectedPages = repository.getMaintenanceStatusPageIds(sourceId).toSet()
            }
            error = ""
        } catch (e: Exception) {
            error = e.message ?: "Could not load maintenance"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(statusPages, selectedPages) {
        if (statusPages.isNotEmpty()) allPages = selectedPages.size == statusPages.size
    }

    fun build(): JSONObject {
        val obj = JSONObject((draft ?: defaultMaintenance()).toString())
        if (maintenanceId == null || maintenanceId <= 0) obj.remove("id")
        obj.put("title", title.trim())
        obj.put("description", description)
        obj.put("strategy", strategy)
        obj.put("active", active)
        obj.put("timezoneOption", timezone.trim().ifBlank { "SAME_AS_SERVER" })
        obj.put("cron", cron.trim())
        obj.put("durationMinutes", duration.toIntOrNull() ?: 60)
        obj.put("intervalDay", intervalDay.toIntOrNull() ?: 1)
        obj.put(
            "dateRange",
            JSONArray().put(dateStart.ifBlank { JSONObject.NULL }).put(dateEnd.ifBlank { JSONObject.NULL }),
        )
        obj.put("timeRange", JSONArray().put(parseTime(timeStart)).put(parseTime(timeEnd)))
        obj.put("weekdays", JSONArray().apply { weekdays.sorted().forEach { put(it) } })
        obj.put("daysOfMonth", JSONArray().apply { daysOfMonth.sorted().forEach { put(it) } })
        return obj
    }

    fun save() {
        if (title.isBlank()) {
            error = "Title is required"
            return
        }
        if (strategy == "single") {
            val start = runCatching { LocalDateTime.parse(dateStart, dateTimeFormat) }.getOrNull()
            val end = runCatching { LocalDateTime.parse(dateEnd, dateTimeFormat) }.getOrNull()
            if (start == null || end == null) {
                error = "Use date format yyyy-MM-ddTHH:mm"
                return
            }
            if (!end.isAfter(start)) {
                error = "End must be after start"
                return
            }
        }
        saving = true
        scope.launch {
            try {
                val id = if (maintenanceId != null && maintenanceId > 0) maintenanceId else {
                    repository.addMaintenance(build())
                }
                if (maintenanceId != null && maintenanceId > 0) repository.editMaintenance(build())
                repository.setMaintenanceMonitors(id, selectedMonitors.toList())
                repository.setMaintenanceStatusPages(
                    id,
                    if (allPages) statusPages.map { it.id } else selectedPages.toList(),
                )
                onBack()
            } catch (e: Exception) {
                error = e.message ?: "Could not save maintenance"
            } finally {
                saving = false
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 36.dp)) {
        item {
            TopBar(
                if (maintenanceId == null || maintenanceId <= 0) {
                    if (cloneFromId != null && cloneFromId > 0) "Clone maintenance" else "New maintenance"
                } else "Edit maintenance",
                onBack = onBack,
                actions = {
                    TextButton(onClick = { save() }, enabled = !saving && !loading) {
                        Text(if (saving) "Saving…" else "Save")
                    }
                },
            )
        }
        if (loading) {
            item { Text("Loading…", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (error.isNotBlank()) InfoBanner(error, tone = KumaColors.Down)
                    FormSection("Details") {
                        Field("Title", title, { title = it })
                        Field("Description", description, { description = it }, singleLine = false)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Active", Modifier.weight(1f))
                            androidx.compose.material3.Switch(checked = active, onCheckedChange = { active = it })
                        }
                    }
                    FormSection("Schedule") {
                        PickerField("Strategy", strategy, maintenanceStrategies, { strategy = it })
                        if (strategy == "cron") {
                            Field("Cron expression", cron, { cron = it }, supporting = "Example: 30 3 * * * (every day at 03:30)")
                            Field("Duration (minutes)", duration, { duration = it.filter(Char::isDigit) }, keyboardType = KeyboardType.Number)
                        }
                        if (strategy == "recurring-interval") {
                            Field("Every N days", intervalDay, { intervalDay = it.filter(Char::isDigit) }, keyboardType = KeyboardType.Number)
                        }
                        if (strategy == "recurring-weekday") {
                            Text("Days of week", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun").forEach { (value, label) ->
                                    ChipButton(label, value in weekdays) {
                                        weekdays = if (value in weekdays) weekdays - value else weekdays + value
                                    }
                                }
                            }
                        }
                        if (strategy == "recurring-day-of-month") {
                            Text("Days of month", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                (1..31).forEach { value ->
                                    val key = value.toString()
                                    ChipButton(key, key in daysOfMonth) {
                                        daysOfMonth = if (key in daysOfMonth) daysOfMonth - key else daysOfMonth + key
                                    }
                                }
                                ChipButton("Last", "lastDay1" in daysOfMonth) {
                                    daysOfMonth = if ("lastDay1" in daysOfMonth) daysOfMonth - "lastDay1" else daysOfMonth + "lastDay1"
                                }
                            }
                        }
                        if (strategy == "recurring-interval" || strategy == "recurring-weekday" || strategy == "recurring-day-of-month") {
                            Text("Daily time window", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Field("From (HH:mm)", timeStart, { timeStart = it }, Modifier.weight(1f))
                                Field("To (HH:mm)", timeEnd, { timeEnd = it }, Modifier.weight(1f))
                            }
                        }
                        if (strategy != "manual") {
                            Text("Effective date range", style = MaterialTheme.typography.labelMedium)
                            Field("Start", dateStart, { dateStart = it }, placeholder = "2026-11-19T09:00")
                            Field("End", dateEnd, { dateEnd = it }, placeholder = "2026-11-19T10:00")
                        }
                        if (strategy == "single") {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(15 to "15m", 30 to "30m", 60 to "1h", 240 to "4h").forEach { (minutes, label) ->
                                    ChipButton(label, false) {
                                        val start = runCatching { LocalDateTime.parse(dateStart, dateTimeFormat) }.getOrNull() ?: return@ChipButton
                                        dateEnd = start.plusMinutes(minutes.toLong()).format(dateTimeFormat)
                                    }
                                }
                            }
                        }
                        Field(
                            "Timezone",
                            timezone,
                            { timezone = it },
                            supporting = "IANA name, UTC, or SAME_AS_SERVER",
                        )
                    }
                    FormSection("Scope") {
                        OutlinedButton(onClick = { showMonitorPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Monitors: ${selectedMonitors.size} selected")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("All status pages", Modifier.weight(1f))
                            androidx.compose.material3.Switch(checked = allPages, onCheckedChange = { allPages = it })
                        }
                        if (!allPages) {
                            OutlinedButton(onClick = { showPagePicker = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Status pages: ${selectedPages.size} selected")
                            }
                        }
                    }
                    Button(onClick = { save() }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                        Text(if (saving) "Saving…" else "Save maintenance")
                    }
                }
            }
        }
    }

    if (showMonitorPicker) {
        SelectionDialog(
            title = "Affected monitors",
            options = monitors.map { it.id to it.name },
            selected = selectedMonitors,
            onToggle = { id -> selectedMonitors = if (id in selectedMonitors) selectedMonitors - id else selectedMonitors + id },
            onDismiss = { showMonitorPicker = false },
        )
    }
    if (showPagePicker) {
        SelectionDialog(
            title = "Status pages",
            options = statusPages.map { it.id to it.title },
            selected = selectedPages,
            onToggle = { id -> selectedPages = if (id in selectedPages) selectedPages - id else selectedPages + id },
            onDismiss = { showPagePicker = false },
        )
    }
}

private fun timeLabel(obj: JSONObject?): String {
    if (obj == null) return "02:00"
    val h = obj.optInt("hours", 2)
    val m = obj.optInt("minutes", 0)
    return "%02d:%02d".format(h, m)
}

@Composable
private fun SelectionDialog(
    title: String,
    options: List<Pair<Long, String>>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options, key = { it.first.toString() }) { (id, name) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = id in selected, onCheckedChange = { onToggle(id) })
                        Text(name, Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Icon(Icons.Default.Check, null)
                Text("Done", Modifier.padding(start = 6.dp))
            }
        },
    )
}
