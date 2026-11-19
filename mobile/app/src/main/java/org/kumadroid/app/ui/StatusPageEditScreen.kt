package org.kumadroid.app.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.kumadroid.app.data.KumaRepository
import org.kumadroid.app.data.Monitor
import org.kumadroid.app.data.bool
import org.kumadroid.app.data.long
import org.kumadroid.app.data.num
import org.kumadroid.app.data.str

private data class PageMonitorRef(val id: Long, val sendUrl: Boolean = false, val url: String = "")
private data class PageGroup(val id: Long?, val name: String, val monitors: List<PageMonitorRef>)

@Composable
fun StatusPageEditScreen(
    repository: KumaRepository,
    slug: String,
    onBack: () -> Unit,
    onPreview: () -> Unit,
    onIncidents: () -> Unit,
) {
    val monitors by repository.monitors.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }

    var title by remember { mutableStateOf("") }
    var slugValue by remember { mutableStateOf(slug) }
    var description by remember { mutableStateOf("") }
    var logo by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf("auto") }
    var autoRefresh by remember { mutableStateOf("300") }
    var showTags by remember { mutableStateOf(false) }
    var showPoweredBy by remember { mutableStateOf(true) }
    var showOnlyLastHeartbeat by remember { mutableStateOf(false) }
    var showCertificateExpiry by remember { mutableStateOf(false) }
    var footerText by remember { mutableStateOf("") }
    var customCss by remember { mutableStateOf("") }
    var rssTitle by remember { mutableStateOf("") }
    var analyticsType by remember { mutableStateOf("") }
    var analyticsId by remember { mutableStateOf("") }
    var analyticsScriptUrl by remember { mutableStateOf("") }
    var domains by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf<List<PageGroup>>(emptyList()) }

    var showAddGroup by remember { mutableStateOf(false) }
    var addMonitorsTo by remember { mutableStateOf<Int?>(null) }
    var monitorSettings by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val dataUrl = withContext(Dispatchers.IO) {
                        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        } ?: throw IllegalArgumentException("This file is not a supported image")
                        try {
                            val bytes = ByteArrayOutputStream()
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes)
                            "data:image/png;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    logo = dataUrl
                    error = ""
                    info = "Logo image selected. Save the page to upload it."
                } catch (e: Exception) {
                    error = e.message ?: "Could not read the selected image"
                }
            }
        }
    }

    fun applyConfig(config: JSONObject) {
        title = config.str("title")
        slugValue = config.str("slug", slug)
        description = config.str("description")
        logo = config.str("icon")
        theme = config.str("theme", "auto")
        autoRefresh = config.num("autoRefreshInterval", 300).toString()
        showTags = config.bool("showTags")
        showPoweredBy = config.bool("showPoweredBy", true)
        showOnlyLastHeartbeat = config.bool("showOnlyLastHeartbeat")
        showCertificateExpiry = config.bool("showCertificateExpiry")
        footerText = config.str("footerText")
        customCss = config.str("customCSS")
        rssTitle = config.str("rssTitle")
        analyticsType = config.str("analyticsType")
        analyticsId = config.str("analyticsId")
        analyticsScriptUrl = config.str("analyticsScriptUrl")
        val domainArray = config.optJSONArray("domainNameList") ?: JSONArray()
        domains = (0 until domainArray.length()).mapNotNull { domainArray.optString(it).takeIf { s -> s.isNotBlank() } }
            .joinToString("\n")
    }

    fun applyGroups(array: JSONArray?) {
        if (array == null) return
        groups = (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val monitorArray = obj.optJSONArray("monitorList") ?: JSONArray()
            val refs = (0 until monitorArray.length()).mapNotNull { mIndex ->
                val m = monitorArray.optJSONObject(mIndex) ?: return@mapNotNull null
                PageMonitorRef(
                    id = m.long("id"),
                    sendUrl = m.bool("sendUrl"),
                    url = m.str("url"),
                )
            }
            PageGroup(obj.long("id").takeIf { it > 0 }, obj.str("name"), refs)
        }
    }

    LaunchedEffect(slug) {
        loading = true
        try {
            val data = runCatching { repository.fetchStatusPageData(slug) }.getOrNull()
            val config = data?.optJSONObject("config") ?: repository.getStatusPageConfig(slug)
            applyConfig(config)
            applyGroups(data?.optJSONArray("publicGroupList"))
            error = ""
        } catch (e: Exception) {
            error = e.message ?: "Could not load status page"
        } finally {
            loading = false
        }
    }

    fun save() {
        if (saving) return
        if (title.isBlank() || slugValue.isBlank()) {
            error = "Title and slug are required"
            return
        }
        scope.launch {
            saving = true
            error = ""
            try {
                val config = JSONObject()
                    .put("slug", slugValue.trim().lowercase())
                    .put("title", title.trim())
                    .put("description", description)
                    .put("logo", logo.trim())
                    .put("theme", theme)
                    .put("autoRefreshInterval", autoRefresh.toIntOrNull() ?: 300)
                    .put("showTags", showTags)
                    .put("showPoweredBy", showPoweredBy)
                    .put("showOnlyLastHeartbeat", showOnlyLastHeartbeat)
                    .put("showCertificateExpiry", showCertificateExpiry)
                    .put("footerText", footerText)
                    .put("customCSS", customCss)
                    .put("rssTitle", rssTitle)
                    .put("analyticsType", if (analyticsType.isBlank()) JSONObject.NULL else analyticsType)
                    .put("analyticsId", analyticsId)
                    .put("analyticsScriptUrl", analyticsScriptUrl)
                    .put("domainNameList", JSONArray(domains.lines().map { it.trim() }.filter { it.isNotBlank() }))
                val groupArray = JSONArray()
                groups.forEach { group ->
                    val refs = JSONArray()
                    group.monitors.forEach { ref ->
                        refs.put(
                            JSONObject()
                                .put("id", ref.id)
                                .put("sendUrl", ref.sendUrl)
                                .put("url", ref.url),
                        )
                    }
                    groupArray.put(
                        JSONObject()
                            .put("id", group.id ?: JSONObject.NULL)
                            .put("name", group.name)
                            .put("monitorList", refs),
                    )
                }
                val saved = repository.saveStatusPage(slug, config, logo.trim(), groupArray)
                applyGroups(saved)
                repository.refreshStatusPages()
                info = "Status page saved"
                onBack()
            } catch (e: Exception) {
                error = e.message ?: "Could not save status page"
            } finally {
                saving = false
            }
        }
    }

    if (loading) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            TopBar("Status page", subtitle = slug, onBack = onBack)
            Spacer(Modifier.height(80.dp))
            CircularProgressIndicator(color = KumaColors.Brand)
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
        item {
            TopBar("Edit status page", subtitle = "/$slug", onBack = onBack) {
                IconButton(onClick = onPreview) { Icon(Icons.Default.Visibility, contentDescription = "Preview") }
                IconButton(onClick = onIncidents) { Icon(Icons.Default.Report, contentDescription = "Incidents") }
                TextButton(onClick = { save() }, enabled = !saving) {
                    Text(if (saving) "Saving…" else "Save", color = KumaColors.Brand, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (error.isNotBlank()) {
            item { InfoBanner(error, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), KumaColors.Down) }
        }
        if (info.isNotBlank()) {
            item { InfoBanner(info, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), KumaColors.Up) }
        }
        item {
            FormSection("Page", "Shown in the public header and browser tab.") {
                Field("Title", title, { title = it })
                Field("Slug", slugValue, { slugValue = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '-' || c == '_' } })
                Field("Description", description, { description = it }, singleLine = false, minLines = 2, maxLines = 5)
                if (logo.startsWith("data:image/")) {
                    Text(
                        "Logo image selected from this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Field(
                        "Logo URL",
                        logo,
                        { logo = it },
                        placeholder = "https://example.com/logo.png",
                        supporting = "Enter a public image URL or choose a PNG below.",
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { logoPicker.launch("image/*") }, shape = RoundedCornerShape(50)) {
                        Text("Choose PNG")
                    }
                    if (logo.startsWith("data:image/")) {
                        TextButton(onClick = { logo = "" }) { Text("Remove image") }
                    }
                }
                PickerField(
                    "Theme",
                    theme,
                    listOf("auto" to "Auto", "light" to "Light", "dark" to "Dark"),
                    { theme = it },
                )
                Field("Auto refresh (seconds)", autoRefresh, { autoRefresh = it.filter(Char::isDigit) })
                SwitchRow("Show monitor tags", showTags, { showTags = it })
                SwitchRow("Show only last heartbeat", showOnlyLastHeartbeat, { showOnlyLastHeartbeat = it })
                SwitchRow("Show certificate expiry", showCertificateExpiry, { showCertificateExpiry = it })
                SwitchRow("Show powered by Uptime Kuma", showPoweredBy, { showPoweredBy = it })
            }
        }
        item {
            FormSection("Footer & style") {
                Field("Footer text", footerText, { footerText = it })
                Field("RSS title", rssTitle, { rssTitle = it })
                Field("Custom CSS", customCss, { customCss = it }, singleLine = false, minLines = 4, maxLines = 10)
            }
        }
        item {
            FormSection("Custom domains") {
                Field(
                    "Domain names",
                    domains,
                    { domains = it },
                    singleLine = false,
                    minLines = 2,
                    maxLines = 6,
                    supporting = "One domain per line. Point DNS to this Kuma server.",
                )
            }
        }
        item {
            FormSection("Analytics") {
                PickerField(
                    "Analytics type",
                    analyticsType,
                    listOf(
                        "" to "Disabled",
                        "google" to "Google Analytics",
                        "umami" to "Umami",
                        "plausible" to "Plausible",
                        "matomo" to "Matomo",
                        "rybbit" to "Rybbit",
                    ),
                    { analyticsType = it },
                )
                if (analyticsType.isNotBlank()) {
                    Field("Analytics ID", analyticsId, { analyticsId = it })
                    Field("Script URL", analyticsScriptUrl, { analyticsScriptUrl = it })
                }
            }
        }
        item {
            PageTitle("Monitor groups", "Groups and their order on the public page.")
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                Button(onClick = { showAddGroup = true }, shape = RoundedCornerShape(50)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add group")
                }
            }
        }
        if (groups.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Public,
                    title = "No groups",
                    message = "Add a group, then choose which monitors it shows.",
                    actionLabel = "Add group",
                    onAction = { showAddGroup = true },
                )
            }
        }
        groups.forEachIndexed { groupIndex, group ->
            item(key = "group-${group.id ?: groupIndex}") {
                GroupCard(
                    group = group,
                    monitors = monitors,
                    onRename = { name ->
                        groups = groups.toMutableList().also { it[groupIndex] = group.copy(name = name) }
                    },
                    onAddMonitors = { addMonitorsTo = groupIndex },
                    onRemoveMonitor = { monitorIndex ->
                        groups = groups.toMutableList().also {
                            it[groupIndex] = group.copy(
                                monitors = group.monitors.toMutableList().also { list -> list.removeAt(monitorIndex) },
                            )
                        }
                    },
                    onMonitorSettings = { monitorIndex -> monitorSettings = groupIndex to monitorIndex },
                    onMoveUp = if (groupIndex > 0) {
                        {
                            groups = groups.toMutableList().also {
                                val previous = it[groupIndex - 1]
                                it[groupIndex - 1] = group
                                it[groupIndex] = previous
                            }
                        }
                    } else null,
                    onMoveDown = if (groupIndex < groups.lastIndex) {
                        {
                            groups = groups.toMutableList().also {
                                val next = it[groupIndex + 1]
                                it[groupIndex + 1] = group
                                it[groupIndex] = next
                            }
                        }
                    } else null,
                    onDelete = {
                        groups = groups.toMutableList().also { it.removeAt(groupIndex) }
                    },
                )
            }
        }
        item {
            Button(
                onClick = { save() },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(50),
            ) { Text(if (saving) "Saving…" else "Save status page") }
        }
    }

    if (showAddGroup) {
        TextInputDialog(
            title = "New group",
            label = "Group name",
            initial = "",
            confirmLabel = "Add",
            onDismiss = { showAddGroup = false },
            onConfirm = { name ->
                showAddGroup = false
                groups = groups + PageGroup(null, name, emptyList())
            },
        )
    }

    addMonitorsTo?.let { groupIndex ->
        val group = groups.getOrNull(groupIndex)
        if (group != null) {
            MonitorPickerDialog(
                title = "Add monitors to ${group.name}",
                monitors = monitors.filter { monitor -> group.monitors.none { it.id == monitor.id } },
                onDismiss = { addMonitorsTo = null },
                onConfirm = { ids ->
                    addMonitorsTo = null
                    val refs = ids.map { PageMonitorRef(it) }
                    groups = groups.toMutableList().also {
                        it[groupIndex] = group.copy(monitors = group.monitors + refs)
                    }
                },
            )
        }
    }

    monitorSettings?.let { (groupIndex, monitorIndex) ->
        val group = groups.getOrNull(groupIndex)
        val ref = group?.monitors?.getOrNull(monitorIndex)
        if (group != null && ref != null) {
            MonitorRefDialog(
                monitor = monitors.firstOrNull { it.id == ref.id },
                ref = ref,
                onDismiss = { monitorSettings = null },
                onConfirm = { updated ->
                    monitorSettings = null
                    groups = groups.toMutableList().also {
                        it[groupIndex] = group.copy(
                            monitors = group.monitors.toMutableList().also { list -> list[monitorIndex] = updated },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun GroupCard(
    group: PageGroup,
    monitors: List<Monitor>,
    onRename: (String) -> Unit,
    onAddMonitors: () -> Unit,
    onRemoveMonitor: (Int) -> Unit,
    onMonitorSettings: (Int) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    KumaCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = group.name,
                onValueChange = onRename,
                label = { Text("Group name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            )
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete group") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
            }
            IconButton(onClick = { onMoveDown?.invoke() }, enabled = onMoveDown != null) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onAddMonitors) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Monitors")
            }
        }
        if (group.monitors.isEmpty()) {
            Text(
                "No monitors in this group.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        group.monitors.forEachIndexed { index, ref ->
            val monitor = monitors.firstOrNull { it.id == ref.id }
            Divider(Modifier.padding(vertical = 3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        monitor?.name ?: "Monitor #${ref.id}",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        monitor?.type ?: "unknown",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ref.sendUrl) {
                    Text(
                        "URL",
                        style = MaterialTheme.typography.labelSmall,
                        color = KumaColors.Brand,
                    )
                }
                IconButton(onClick = { onMonitorSettings(index) }) {
                    Icon(Icons.Default.Settings, contentDescription = "Monitor display settings")
                }
                IconButton(onClick = { onRemoveMonitor(index) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove monitor")
                }
            }
        }
    }
}

@Composable
private fun MonitorPickerDialog(
    title: String,
    monitors: List<Monitor>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val visible = monitors.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) || it.type.contains(query, ignoreCase = true)
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(18.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                SearchField(query, { query = it }, placeholder = "Search monitors")
                Spacer(Modifier.height(6.dp))
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    if (visible.isEmpty()) {
                        Text(
                            "No monitors available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    visible.forEach { monitor ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selected = if (monitor.id in selected) selected - monitor.id else selected + monitor.id
                            }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = monitor.id in selected, onCheckedChange = null)
                            Column(Modifier.weight(1f)) {
                                Text(monitor.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    monitor.type,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = { onConfirm(selected.toList()) },
                        enabled = selected.isNotEmpty(),
                        shape = RoundedCornerShape(50),
                    ) { Text("Add ${selected.size}") }
                }
            }
        }
    }
}

@Composable
private fun MonitorRefDialog(
    monitor: Monitor?,
    ref: PageMonitorRef,
    onDismiss: () -> Unit,
    onConfirm: (PageMonitorRef) -> Unit,
) {
    var sendUrl by remember { mutableStateOf(ref.sendUrl) }
    var url by remember { mutableStateOf(ref.url) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(monitor?.name ?: "Monitor #${ref.id}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchRow(
                    "Show URL on public page",
                    sendUrl,
                    { sendUrl = it },
                    subtitle = "The monitor's target URL will be visible to visitors.",
                )
                if (sendUrl) {
                    Field(
                        "Custom URL",
                        url,
                        { url = it },
                        placeholder = monitor?.json?.str("url").orEmpty(),
                        supporting = "Leave empty to use the monitor's URL.",
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(ref.copy(sendUrl = sendUrl, url = url.trim())) }, shape = RoundedCornerShape(50)) {
                Text("Apply")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Field(label, value, { value = it }) },
        confirmButton = {
            Button(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank(), shape = RoundedCornerShape(50)) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
