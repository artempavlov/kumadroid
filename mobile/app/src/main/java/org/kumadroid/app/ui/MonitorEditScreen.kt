package org.kumadroid.app.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.kumadroid.app.data.KumaRepository
import org.kumadroid.app.data.MonitorCatalog
import org.kumadroid.app.data.MonitorTag
import org.kumadroid.app.data.MonitorCatalog.draft
import org.kumadroid.app.data.bool
import org.kumadroid.app.data.long
import org.kumadroid.app.data.numOr
import org.kumadroid.app.data.str
import org.kumadroid.app.data.stringList

private class MonitorForm(initial: JSONObject) {
    var json by mutableStateOf(initial)
        private set
    var revision by mutableIntStateOf(0)
        private set

    fun replace(next: JSONObject) {
        json = next
        revision++
    }

    fun text(key: String, fallback: String = ""): String {
        revision
        return json.str(key, fallback)
    }

    fun int(key: String, fallback: Int = 0): Int {
        revision
        return json.str(key).toIntOrNull() ?: fallback
    }

    fun bool(key: String, fallback: Boolean = false): Boolean {
        revision
        return json.bool(key, fallback)
    }

    fun set(key: String, value: Any?) {
        json.put(key, value ?: JSONObject.NULL)
        revision++
    }

    fun sasl(key: String, fallback: String = ""): String {
        revision
        return json.optJSONObject("kafkaProducerSaslOptions")?.str(key, fallback) ?: fallback
    }

    fun setSasl(key: String, value: String) {
        val sasl = json.optJSONObject("kafkaProducerSaslOptions") ?: JSONObject()
        sasl.put(key, value)
        json.put("kafkaProducerSaslOptions", sasl)
        revision++
    }

    fun csv(key: String): String {
        revision
        return json.optJSONArray(key)?.stringList()?.joinToString(", ") ?: ""
    }

    fun setCsv(key: String, value: String) {
        val arr = JSONArray()
        value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { arr.put(it) }
        json.put(key, arr)
        revision++
    }

    fun notificationEnabled(id: Long): Boolean {
        revision
        return json.optJSONObject("notificationIDList")?.bool(id.toString(), false) ?: false
    }

    fun toggleNotification(id: Long, enabled: Boolean) {
        val map = json.optJSONObject("notificationIDList") ?: JSONObject()
        map.put(id.toString(), enabled)
        json.put("notificationIDList", map)
        revision++
    }
}

@OptIn(ExperimentalLayoutApi::class)
// Connection-string hints below are examples, not embedded credentials.
@SuppressLint("AuthLeak")
@Composable
fun MonitorEditScreen(
    repository: KumaRepository,
    monitorId: Long?,
    parentId: Long?,
    cloneFromId: Long?,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val monitors by repository.monitors.collectAsStateWithLifecycle()
    val tags by repository.tags.collectAsStateWithLifecycle()
    val notifications by repository.notifications.collectAsStateWithLifecycle()
    val dockerHosts by repository.dockerHosts.collectAsStateWithLifecycle()
    val proxies by repository.proxies.collectAsStateWithLifecycle()
    val remoteBrowsers by repository.remoteBrowsers.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var form by remember { mutableStateOf<MonitorForm?>(null) }
    var loadingError by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var selectedTagIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var oldTags by remember { mutableStateOf<List<MonitorTag>>(emptyList()) }
    var gameOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var pm2Options by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(monitorId, cloneFromId) {
        try {
            val requestedId = monitorId ?: cloneFromId
            val existing = requestedId?.let { repository.getMonitor(it) }
            if (existing == null && requestedId != null) {
                error("This monitor no longer exists. It may have been deleted from another client.")
            }
            val type = existing?.str("type") ?: "http"
            val base = draft(type, existing)
            if (existing == null && parentId != null) base.put("parent", parentId)
            if (existing != null && cloneFromId != null) {
                base.put("name", "Clone of ${existing.str("name")}")
                base.remove("id")
                base.remove("includeSensitiveData")
                base.remove("maintenance")
                base.remove("childrenIDs")
                base.remove("forceInactive")
                base.remove("path")
                base.remove("pathName")
                base.remove("screenshot")
                if (type == "push") base.remove("pushToken")
            }
            if (type == "push" && base.str("pushToken").isBlank()) {
                base.put("pushToken", MonitorCatalog.newPushToken())
            }
            form = MonitorForm(base)
            val parsedTags = mutableListOf<MonitorTag>()
            val arr = existing?.optJSONArray("tags") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val tag = MonitorTag(item)
                parsedTags.add(tag)
            }
            oldTags = if (cloneFromId != null) emptyList() else parsedTags
            selectedTagIds = parsedTags.map { it.id }
        } catch (e: Exception) {
            loadingError = e.message ?: "Could not load monitor"
        }
    }

    val current = form
    if (current == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            if (loadingError.isBlank()) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(12.dp))
                Text("Loading monitor…", Modifier.align(Alignment.CenterHorizontally))
            } else {
                InfoBanner(loadingError, tone = KumaColors.Down)
            }
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Back") }
        }
        return
    }

    fun changeType(newType: String) {
        if (newType == current.text("type")) return
        val old = current.json
        val fresh = MonitorCatalog.defaultFor(newType)
        listOf(
            "name", "description", "parent", "interval", "retryInterval", "resendInterval",
            "maxretries", "retryOnlyOnStatusCodeFailure", "active", "notificationIDList",
            "upsideDown", "expiryNotification",
        ).forEach { key -> if (old.has(key)) fresh.put(key, old.get(key)) }
        if (newType == "push") fresh.put("pushToken", MonitorCatalog.newPushToken())
        current.replace(fresh)
    }

    fun save() {
        saveError = ""
        val json = current.json
        val name = json.str("name").trim()
        if (name.isBlank()) {
            saveError = "Name is required."
            return
        }
        val type = json.str("type", "http")
        if (type !in setOf("push", "manual", "group")) {
            val url = json.str("url").trim()
            val host = json.str("hostname").trim()
            val blankUrl = url.isBlank() || url == "http://" || url == "https://" ||
                url == "ws://" || url == "wss://"
            if (MonitorCatalog.usesUrl(type) && blankUrl) {
                saveError = "A URL is required."
                return
            }
            if (MonitorCatalog.usesHostname(type) && host.isBlank()) {
                saveError = "Hostname is required."
                return
            }
            if (type == "grpc-keyword" && json.str("grpcUrl").isBlank()) {
                saveError = "gRPC URL is required."
                return
            }
            if (type == "globalping" && host.isBlank() && blankUrl) {
                saveError = "Hostname or URL is required."
                return
            }
        }
        if (type == "docker" && json.str("docker_container").isBlank()) {
            saveError = "Container name is required."
            return
        }
        if (type == "pm2" && json.str("system_service_name").isBlank()) {
            saveError = "Process name is required."
            return
        }
        if (type == "snmp" && json.str("snmpVersion", "1") != "3" && json.str("radiusPassword").isBlank()) {
            saveError = "Community string is required."
            return
        }
        if (MonitorCatalog.isDatabase(type) && json.str("databaseConnectionString").isBlank()) {
            saveError = "Connection string is required."
            return
        }

        val payload = JSONObject(json.toString())
        payload.put("notificationIDList", json.optJSONObject("notificationIDList") ?: JSONObject())
        val port = json.str("port").toIntOrNull()?.takeIf { it > 0 }
            ?: MonitorCatalog.defaultPort(type, json.str("subtype"))
        payload.put("port", port)
        payload.put("interval", json.str("interval").toIntOrNull() ?: 60)
        payload.put("retryInterval", json.str("retryInterval").toIntOrNull() ?: 60)
        payload.put("resendInterval", json.str("resendInterval").toIntOrNull() ?: 0)
        payload.put("maxretries", json.str("maxretries").toIntOrNull() ?: 0)
        payload.put("timeout", json.str("timeout").toIntOrNull() ?: 48)
        payload.put("maxredirects", json.str("maxredirects").toIntOrNull() ?: 10)
        payload.put("responseMaxLength", json.str("responseMaxLength").toIntOrNull() ?: 1024)
        payload.put("screenshot_delay", json.str("screenshot_delay").toIntOrNull() ?: 0)
        payload.put("packetSize", json.str("packetSize").toIntOrNull() ?: 56)
        payload.put("ping_count", json.str("ping_count").toIntOrNull() ?: 5)
        payload.put("ping_per_request_timeout", json.str("ping_per_request_timeout").toIntOrNull() ?: 0)
        payload.put("ntpStratumThreshold", json.str("ntpStratumThreshold").toIntOrNull() ?: 5)
        payload.put("ntpTimeOffsetThreshold", json.str("ntpTimeOffsetThreshold").toIntOrNull() ?: 1000)
        payload.put("ntpRootDispersionThreshold", json.str("ntpRootDispersionThreshold").toIntOrNull() ?: 500)
        payload.put("ping_numeric", json.bool("ping_numeric"))
        payload.put("retryOnlyOnStatusCodeFailure", json.bool("retryOnlyOnStatusCodeFailure"))
        if (type == "push" && json.str("pushToken").trim().length != 32) {
            payload.put("pushToken", MonitorCatalog.newPushToken())
        }

        val accepted = json.optJSONArray("accepted_statuscodes")
        if (accepted == null || accepted.length() == 0) {
            payload.put("accepted_statuscodes", JSONArray().put("200-299"))
        }
        val conditionsText = json.str("conditionsText")
        if (conditionsText.isNotBlank()) {
            try {
                payload.put("conditions", JSONArray(conditionsText))
            } catch (e: Exception) {
                saveError = "Conditions must be a valid JSON array."
                return
            }
        }
        payload.remove("conditionsText")

        if (json.isNull("parent") || json.str("parent").isBlank() || json.str("parent") == "null") {
            payload.put("parent", JSONObject.NULL)
        } else {
            payload.put("parent", json.str("parent").toLongOrNull() ?: JSONObject.NULL)
        }
        // Picker values are stored as strings in the form, but the server stores
        // these references as numeric IDs (or null).
        listOf("docker_host", "proxyId", "remote_browser").forEach { key ->
            val value = json.str(key).trim().toLongOrNull()
            payload.put(key, value ?: JSONObject.NULL)
        }
        if (json.str("authMethod").isBlank()) payload.put("authMethod", JSONObject.NULL)

        // These are read-only/computed values from getMonitor and the web client.
        // The server maps every payload key to a column, so they must not be sent back.
        listOf(
            "tags", "path", "pathName", "childrenIDs",
            "forceInactive", "maintenance", "screenshot", "dns_last_result",
            "includeSensitiveData", "responsecheck", "humanReadableInterval",
            "globalpingdnsresolvetypeoptions", "conditionsText", "hasNotifications",
            "isParent",
        ).forEach { payload.remove(it) }

        scope.launch {
            saving = true
            try {
                val id = if (monitorId == null) {
                    repository.addMonitor(payload)
                } else {
                    payload.put("id", monitorId)
                    repository.editMonitor(payload)
                    monitorId
                }
                val newTags = selectedTagIds.mapNotNull { tagId ->
                    tags.firstOrNull { it.id == tagId }?.let { tag ->
                        MonitorTag(JSONObject().put("id", tag.id).put("tag_id", tag.id).put("name", tag.name)
                            .put("color", tag.color).put("value", oldTags.firstOrNull { it.id == tag.id }?.value ?: ""))
                    }
                }
                repository.syncMonitorTags(id, oldTags, newTags)
                repository.refreshAll()
                onSaved(id)
            } catch (e: Exception) {
                saveError = e.message ?: "Could not save monitor"
            } finally {
                saving = false
            }
        }
    }

    val type = current.text("type", "http")
    // A group cannot be moved under itself or one of its descendants.
    val unavailableParents = mutableSetOf<Long>()
    fun markDescendants(id: Long) {
        monitors.firstOrNull { it.id == id }?.childrenIds?.forEach { child ->
            if (unavailableParents.add(child)) markDescendants(child)
        }
    }
    monitorId?.let { unavailableParents.add(it); markDescendants(it) }
    val groups = monitors.filter { it.type == "group" && it.id !in unavailableParents }
    val parentValue = current.text("parent").let { if (it == "null") "" else it }
    val statusCodeText = current.csv("accepted_statuscodes").ifBlank { "200-299" }

    LaunchedEffect(type) {
        if (type == "gamedig" && gameOptions.isEmpty()) {
            gameOptions = try {
                val arr = repository.getGameList()
                (0 until arr.length()).mapNotNull { index ->
                    val item = arr.optJSONObject(index) ?: return@mapNotNull null
                    val key = item.optJSONArray("keys")?.optString(0).orEmpty()
                    if (key.isBlank()) null else key to item.str("pretty", key)
                }.sortedBy { it.second }
            } catch (_: Exception) {
                emptyList()
            }
        }
        if (type == "pm2" && pm2Options.isEmpty()) {
            pm2Options = try {
                val arr = repository.getPM2ProcessList()
                (0 until arr.length()).mapNotNull { index ->
                    val item = arr.optJSONObject(index) ?: return@mapNotNull null
                    val name = item.str("name").ifBlank { item.str("pm_id") }
                    if (name.isBlank()) null else name to name
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            TopBar(
                if (monitorId == null) {
                    if (cloneFromId != null) "Clone monitor" else "New monitor"
                } else "Edit monitor",
                subtitle = current.text("name").ifBlank { MonitorCatalog.label(type) },
                onBack = onBack,
                actions = {
                    TextButton(onClick = { save() }, enabled = !saving) {
                        Text(if (saving) "Saving…" else "Save")
                    }
                },
            )
        }
        if (saveError.isNotBlank()) {
            item { InfoBanner(saveError, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), KumaColors.Down) }
        }

        item {
            FormSection("Monitor") {
                Field("Name", current.text("name"), { current.set("name", it) }, placeholder = "My website")
                PickerField(
                    "Monitor type",
                    type,
                    MonitorCatalog.types.map { it to MonitorCatalog.label(it) },
                    ::changeType,
                )
                PickerField(
                    "Parent group",
                    parentValue,
                    listOf("" to "No group") + groups.map { it.id.toString() to it.name },
                    { current.set("parent", if (it.isBlank()) JSONObject.NULL else it) },
                )
                if (type == "push") {
                    InfoBanner(
                        "Save the monitor, then copy the push URL from its details. The URL contains a secret token.",
                    )
                }
                when {
                    MonitorCatalog.usesUrl(type) -> {
                        Field("URL", current.text("url"), { current.set("url", it) }, keyboardType = KeyboardType.Uri, placeholder = "https://example.com")
                        if (type != "websocket-upgrade") {
                            PickerField(
                                "Method", current.text("method", "GET"),
                                listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS").map { it to it },
                                { current.set("method", it) },
                            )
                        }
                    }
                    type == "globalping" -> {
                        PickerField(
                            "Subtype", current.text("subtype", "http"),
                            listOf("http", "ping", "dns").map { it to it.uppercase() },
                            {
                                current.set("subtype", it)
                                current.set(
                                    "protocol",
                                    when (it) {
                                        "ping" -> "ICMP"
                                        "dns" -> "UDP"
                                        else -> JSONObject.NULL
                                    },
                                )
                                current.set("port", MonitorCatalog.defaultPort("globalping", it))
                            },
                        )
                        if (current.text("subtype", "http") == "http") {
                            Field("URL", current.text("url"), { current.set("url", it) }, keyboardType = KeyboardType.Uri)
                        } else {
                            Field("Hostname", current.text("hostname"), { current.set("hostname", it) })
                        }
                        Field("Location", current.text("location", "world"), { current.set("location", it) }, supporting = "Globalping region, for example world, Europe, Germany.")
                        PickerField(
                            "IP family",
                            current.text("ipFamily"),
                            MonitorCatalog.ipFamilyOptions,
                            { current.set("ipFamily", if (it.isBlank()) JSONObject.NULL else it) },
                        )
                    }
                    type == "grpc-keyword" -> {
                        Field("gRPC URL", current.text("grpcUrl"), { current.set("grpcUrl", it) }, keyboardType = KeyboardType.Uri, placeholder = "host:port")
                    }
                    type == "docker" -> {
                        PickerField(
                            "Docker host", current.text("docker_host"),
                            listOf("" to "Local Docker") + dockerHosts.map { it.str("id") to it.str("name") },
                            { current.set("docker_host", it) },
                        )
                        Field("Container", current.text("docker_container"), { current.set("docker_container", it) }, placeholder = "container-name")
                    }
                    type == "system-service" -> {
                        Field("Service name", current.text("system_service_name"), { current.set("system_service_name", it) }, placeholder = "nginx.service")
                    }
                    type == "pm2" -> {
                        if (pm2Options.isNotEmpty()) {
                            val currentProcess = current.text("system_service_name")
                            val choices = pm2Options.toMutableList()
                            if (currentProcess.isNotBlank() && choices.none { it.first == currentProcess }) {
                                choices.add(0, currentProcess to currentProcess)
                            }
                            PickerField(
                                "Process name",
                                currentProcess,
                                listOf("" to "Select a process") + choices,
                                { current.set("system_service_name", it) },
                            )
                        } else {
                            Field("Process name", current.text("system_service_name"), { current.set("system_service_name", it) }, placeholder = "my-process")
                        }
                    }
                    else -> {
                        if (MonitorCatalog.usesHostname(type)) {
                            Field("Hostname", current.text("hostname"), { current.set("hostname", it) })
                        }
                        if (MonitorCatalog.usesPort(type) || type == "port") {
                            Field(
                                "Port", current.text("port"),
                                { current.set("port", it.filter(Char::isDigit)) },
                                keyboardType = KeyboardType.Number,
                            )
                        }
                        if (type == "port") {
                            PickerField(
                                "SSL/TLS",
                                current.text("smtpSecurity", "nostarttls"),
                                listOf(
                                    "nostarttls" to "None",
                                    "secure" to "SSL/TLS",
                                    "starttls" to "STARTTLS",
                                ),
                                { current.set("smtpSecurity", it) },
                            )
                            PickerField(
                                "Expected TLS alert",
                                current.text("expectedTlsAlert", "none"),
                                listOf(
                                    "none" to "None (successful connection)",
                                    "certificate_required" to "certificate_required (116)",
                                    "bad_certificate" to "bad_certificate (42)",
                                    "certificate_unknown" to "certificate_unknown (46)",
                                    "unknown_ca" to "unknown_ca (48)",
                                    "access_denied" to "access_denied (49)",
                                    "handshake_failure" to "handshake_failure (40)",
                                    "certificate_expired" to "certificate_expired (45)",
                                    "certificate_revoked" to "certificate_revoked (44)",
                                ),
                                { current.set("expectedTlsAlert", it) },
                            )
                            Text(
                                "A TLS alert counts as an expected result for this port check.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Field(
                    "Description", current.text("description"), { current.set("description", it) },
                    singleLine = false, minLines = 2, maxLines = 4,
                )
                if (type == "manual") {
                    PickerField(
                        "Manual status",
                        current.text("manual_status", "1"),
                        listOf("1" to "Up", "0" to "Down"),
                        { current.set("manual_status", it.toIntOrNull() ?: 1) },
                    )
                }
                SwitchRow("Active", current.bool("active", true), { current.set("active", it) })
            }
        }

        if (type == "http" || type == "keyword" || type == "json-query" || type == "real-browser" ||
            type == "websocket-upgrade" || type == "globalping" || type == "grpc-keyword"
        ) {
            item {
                FormSection("Request") {
                    if (type != "grpc-keyword" && type != "globalping") {
                        val method = current.text("method", "GET")
                        if (method == "POST" || method == "PUT" || method == "PATCH" || method == "DELETE") {
                            Field(
                                "Body", current.text("body"), { current.set("body", it) },
                                singleLine = false, minLines = 3, maxLines = 8,
                            )
                            PickerField(
                                "Body encoding", current.text("httpBodyEncoding", "json"),
                                listOf("json", "form", "raw").map { it to it },
                                { current.set("httpBodyEncoding", it) },
                            )
                        }
                        Field(
                            "Headers", current.text("headers"), { current.set("headers", it) },
                            singleLine = false, minLines = 2, maxLines = 5,
                            supporting = "JSON object, for example {\"Authorization\":\"Bearer …\"}",
                        )
                        PickerField(
                            "Authentication", current.text("authMethod"),
                            listOf(
                                "" to "None", "basic" to "HTTP Basic", "ntlm" to "NTLM",
                                "bearer" to "Bearer token", "oauth2-cc" to "OAuth2 client credentials",
                                "mtls" to "mTLS",
                            ),
                            { current.set("authMethod", it) },
                        )
                        when (current.text("authMethod")) {
                            "basic", "ntlm" -> {
                                Field("Username", current.text("basic_auth_user"), { current.set("basic_auth_user", it) })
                                PasswordField("Password", current.text("basic_auth_pass"), { current.set("basic_auth_pass", it) })
                                if (current.text("authMethod") == "ntlm") {
                                    Field("Domain", current.text("authDomain"), { current.set("authDomain", it) })
                                    Field("Workstation", current.text("authWorkstation"), { current.set("authWorkstation", it) })
                                }
                            }
                            "bearer" -> PasswordField("Bearer token", current.text("bearer_token"), { current.set("bearer_token", it) })
                            "oauth2-cc" -> {
                                PickerField(
                                    "Token auth method", current.text("oauth_auth_method", "client_secret_basic"),
                                    listOf("client_secret_basic", "client_secret_post").map { it to it },
                                    { current.set("oauth_auth_method", it) },
                                )
                                Field("Token URL", current.text("oauth_token_url"), { current.set("oauth_token_url", it) }, keyboardType = KeyboardType.Uri)
                                Field("Client ID", current.text("oauth_client_id"), { current.set("oauth_client_id", it) })
                                PasswordField("Client secret", current.text("oauth_client_secret"), { current.set("oauth_client_secret", it) })
                                Field("Scopes", current.text("oauth_scopes"), { current.set("oauth_scopes", it) })
                                Field("Audience", current.text("oauth_audience"), { current.set("oauth_audience", it) })
                            }
                            "mtls" -> {
                                Field("CA certificate", current.text("tlsCa"), { current.set("tlsCa", it) }, singleLine = false, minLines = 2, maxLines = 6)
                                Field("Client certificate", current.text("tlsCert"), { current.set("tlsCert", it) }, singleLine = false, minLines = 2, maxLines = 6)
                                Field("Client key", current.text("tlsKey"), { current.set("tlsKey", it) }, singleLine = false, minLines = 2, maxLines = 6)
                            }
                        }
                    }
                    if (type == "keyword") {
                        Field("Keyword", current.text("keyword"), { current.set("keyword", it) })
                        SwitchRow("Invert keyword", current.bool("invertKeyword"), { current.set("invertKeyword", it) })
                    }
                    if (type == "json-query") {
                        Field("JSON path", current.text("jsonPath", "$"), { current.set("jsonPath", it) }, placeholder = "$.data.status")
                        PickerField(
                            "Operator", current.text("jsonPathOperator", "=="),
                            listOf("==", "!=", ">", ">=", "<", "<=", "contains", "notContains", "startsWith", "endsWith")
                                .map { it to it },
                            { current.set("jsonPathOperator", it) },
                        )
                        Field("Expected value", current.text("expectedValue"), { current.set("expectedValue", it) })
                    }
                    if (type == "real-browser") {
                        Field(
                            "Screenshot delay (ms)", current.text("screenshot_delay"),
                            { current.set("screenshot_delay", it.filter(Char::isDigit)) },
                            keyboardType = KeyboardType.Number,
                        )
                        PickerField(
                            "Remote browser", current.text("remote_browser"),
                            listOf("" to "Default browser") + remoteBrowsers.map { it.str("id") to it.str("name") },
                            { current.set("remote_browser", it) },
                        )
                    }
                    if (type == "websocket-upgrade") {
                        Field("Subprotocol", current.text("wsSubprotocol"), { current.set("wsSubprotocol", it) })
                        SwitchRow(
                            "Ignore Sec-WebSocket-Accept header",
                            current.bool("wsIgnoreSecWebsocketAcceptHeader"),
                            { current.set("wsIgnoreSecWebsocketAcceptHeader", it) },
                        )
                    }
                    if (type == "globalping") {
                        Field("Protocol", current.text("protocol"), { current.set("protocol", it) }, supporting = "For example TCP, UDP or ICMP. Leave empty for automatic.")
                    }
                    if (type == "grpc-keyword") {
                        Field("Service name", current.text("grpcServiceName"), { current.set("grpcServiceName", it) })
                        Field("Method", current.text("grpcMethod"), { current.set("grpcMethod", it) })
                        SwitchRow("Enable TLS", current.bool("grpcEnableTls"), { current.set("grpcEnableTls", it) })
                        Field("Proto file", current.text("grpcProtobuf").let { if (it == "true" || it == "false") "" else it }, { current.set("grpcProtobuf", if (it.isBlank()) false else it) }, singleLine = false, minLines = 2, maxLines = 6, supporting = "Paste a .proto definition, or leave empty for server reflection.")
                        Field("Body", current.text("grpcBody"), { current.set("grpcBody", it) }, singleLine = false, minLines = 2, maxLines = 6)
                        Field("Keyword", current.text("keyword"), { current.set("keyword", it) })
                    }
                }
            }
        }

        if (type == "dns") {
            item {
                FormSection("DNS") {
                    PickerField(
                        "Record type", current.text("dns_resolve_type", "A"),
                        listOf("A", "AAAA", "CNAME", "MX", "TXT", "NS", "SOA", "PTR", "SRV", "CAA", "TLSA", "DS").map { it to it },
                        { current.set("dns_resolve_type", it) },
                    )
                    Field("Resolver server", current.text("dns_resolve_server"), { current.set("dns_resolve_server", it) }, supporting = "Leave empty to use the system resolver.")
                }
            }
        }

        if (type == "mqtt") {
            item {
                FormSection("MQTT") {
                    Field("Username", current.text("mqttUsername"), { current.set("mqttUsername", it) })
                    PasswordField("Password", current.text("mqttPassword"), { current.set("mqttPassword", it) })
                    Field("Topic", current.text("mqttTopic"), { current.set("mqttTopic", it) })
                    Field("WebSocket path", current.text("mqttWebsocketPath"), { current.set("mqttWebsocketPath", it) })
                    PickerField(
                        "Check type", current.text("mqttCheckType", "keyword"),
                        listOf("keyword", "json-query").map { it to it },
                        { current.set("mqttCheckType", it) },
                    )
                    if (current.text("mqttCheckType", "keyword") == "keyword") {
                        Field("Success message", current.text("mqttSuccessMessage"), { current.set("mqttSuccessMessage", it) })
                    } else {
                        Field(
                            "JSON query",
                            current.text("jsonPath", "$"),
                            { current.set("jsonPath", it) },
                            supporting = "JSONata expression, for example $.status.",
                        )
                        Field("Expected value", current.text("expectedValue"), { current.set("expectedValue", it) })
                    }
                }
            }
        }

        if (type == "kafka-producer") {
            item {
                FormSection("Kafka") {
                    Field("Brokers", current.csv("kafkaProducerBrokers"), { current.setCsv("kafkaProducerBrokers", it) }, supporting = "Comma-separated host:port list.")
                    Field("Topic", current.text("kafkaProducerTopic"), { current.set("kafkaProducerTopic", it) })
                    Field("Message", current.text("kafkaProducerMessage"), { current.set("kafkaProducerMessage", it) }, singleLine = false, minLines = 2, maxLines = 5)
                    SwitchRow("Use SSL", current.bool("kafkaProducerSsl"), { current.set("kafkaProducerSsl", it) })
                    SwitchRow("Allow automatic topic creation", current.bool("kafkaProducerAllowAutoTopicCreation"), { current.set("kafkaProducerAllowAutoTopicCreation", it) })
                    PickerField(
                        "SASL mechanism", current.sasl("mechanism", "None"),
                        listOf("None", "plain", "scram-sha-256", "scram-sha-512", "aws").map { it to it },
                        { current.setSasl("mechanism", it) },
                    )
                    val mechanism = current.sasl("mechanism", "None")
                    if (mechanism != "None" && mechanism != "aws") {
                        Field("SASL username", current.sasl("username"), { current.setSasl("username", it) })
                        PasswordField("SASL password", current.sasl("password"), { current.setSasl("password", it) })
                    }
                    if (mechanism == "aws") {
                        Field("Authorization identity", current.sasl("authorizationIdentity"), { current.setSasl("authorizationIdentity", it) })
                        Field("Access key ID", current.sasl("accessKeyId"), { current.setSasl("accessKeyId", it) })
                        PasswordField("Secret access key", current.sasl("secretAccessKey"), { current.setSasl("secretAccessKey", it) })
                        Field("Session token", current.sasl("sessionToken"), { current.setSasl("sessionToken", it) })
                    }
                }
            }
        }

        if (type == "rabbitmq") {
            item {
                FormSection("RabbitMQ") {
                    Field("Nodes", current.csv("rabbitmqNodes"), { current.setCsv("rabbitmqNodes", it) }, supporting = "Comma-separated URLs.")
                    Field("Username", current.text("rabbitmqUsername"), { current.set("rabbitmqUsername", it) })
                    PasswordField("Password", current.text("rabbitmqPassword"), { current.set("rabbitmqPassword", it) })
                }
            }
        }

        if (type == "smtp") {
            item {
                FormSection("SMTP") {
                    PickerField(
                        "Security", current.text("smtpSecurity", "starttls"),
                        listOf("starttls" to "Use STARTTLS", "secure" to "SMTPS", "nostarttls" to "Ignore STARTTLS"),
                        { current.set("smtpSecurity", it) },
                    )
                }
            }
        }

        if (type == "snmp") {
            item {
                FormSection("SNMP") {
                    PickerField(
                        "Version", current.text("snmpVersion", "1"),
                        listOf("1" to "v1", "2c" to "v2c", "3" to "v3"),
                        { current.set("snmpVersion", it) },
                    )
                    Field("OID", current.text("snmpOid"), { current.set("snmpOid", it) }, placeholder = "1.3.6.1.2.1.1.3.0")
                    if (current.text("snmpVersion") == "3") {
                        Field("v3 username", current.text("snmpV3Username"), { current.set("snmpV3Username", it) })
                    } else {
                        PasswordField(
                            "Community string",
                            current.text("radiusPassword"),
                            { current.set("radiusPassword", it) },
                        )
                    }
                }
            }
        }

        if (type == "sftp") {
            item {
                FormSection("SFTP") {
                    Field("Username", current.text("sshUsername"), { current.set("sshUsername", it) })
                    PickerField(
                        "Authentication", current.text("sshAuthMethod", "password"),
                        listOf("password" to "Password", "key" to "Private key"),
                        { current.set("sshAuthMethod", it) },
                    )
                    if (current.text("sshAuthMethod", "password") == "password") {
                        PasswordField("Password", current.text("sshPassword"), { current.set("sshPassword", it) })
                    } else {
                        Field("Private key", current.text("sshPrivateKey"), { current.set("sshPrivateKey", it) }, singleLine = false, minLines = 3, maxLines = 10)
                        PasswordField("Passphrase", current.text("sshPassphrase"), { current.set("sshPassphrase", it) })
                    }
                    Field("Path", current.text("sftpPath"), { current.set("sftpPath", it) }, placeholder = "/")
                }
            }
        }

        if (type == "radius") {
            item {
                FormSection("RADIUS") {
                    Field("Username", current.text("radiusUsername"), { current.set("radiusUsername", it) })
                    PasswordField("Password", current.text("radiusPassword"), { current.set("radiusPassword", it) })
                    PasswordField("Secret", current.text("radiusSecret"), { current.set("radiusSecret", it) })
                    Field("Called station ID", current.text("radiusCalledStationId"), { current.set("radiusCalledStationId", it) })
                    Field("Calling station ID", current.text("radiusCallingStationId"), { current.set("radiusCallingStationId", it) })
                }
            }
        }

        if (MonitorCatalog.isDatabase(type)) {
            item {
                FormSection("Database") {
                    Field(
                        "Connection string", current.text("databaseConnectionString"),
                        { current.set("databaseConnectionString", it) },
                        singleLine = false, minLines = 2, maxLines = 4,
                        supporting = when (type) {
                            "postgres" -> "postgres://user:pass@host:5432/db"
                            "mysql" -> "mysql://user:pass@host:3306/db"
                            "mongodb" -> "mongodb://user:pass@host:27017/db"
                            "sqlserver" -> "Server=host,1433;Database=db;User Id=user;Password=pass;"
                            "oracledb" -> "user/pass@host:1521/service"
                            "redis" -> "redis://user:pass@host:6379"
                            else -> null
                        },
                    )
                    Field("Query", current.text("databaseQuery"), { current.set("databaseQuery", it) }, singleLine = false, minLines = 2, maxLines = 4)
                }
            }
        }

        if (type == "ntp") {
            item {
                FormSection("NTP thresholds") {
                    Field("Stratum", current.text("ntpStratumThreshold"), { current.set("ntpStratumThreshold", it.filter(Char::isDigit)) }, keyboardType = KeyboardType.Number)
                    Field("Time offset (ms)", current.text("ntpTimeOffsetThreshold"), { current.set("ntpTimeOffsetThreshold", it.filter(Char::isDigit)) }, keyboardType = KeyboardType.Number)
                    Field("Root dispersion (ms)", current.text("ntpRootDispersionThreshold"), { current.set("ntpRootDispersionThreshold", it.filter(Char::isDigit)) }, keyboardType = KeyboardType.Number)
                }
            }
        }

        if (type == "gamedig") {
            item {
                FormSection("GameDig") {
                    if (gameOptions.isNotEmpty()) {
                        val currentGame = current.text("game")
                        val choices = gameOptions.toMutableList()
                        if (currentGame.isNotBlank() && choices.none { it.first == currentGame }) {
                            choices.add(0, currentGame to currentGame)
                        }
                        PickerField(
                            "Game",
                            currentGame,
                            listOf("" to "Select a game") + choices,
                            { current.set("game", it) },
                        )
                    } else {
                        Field("Game", current.text("game"), { current.set("game", it) }, placeholder = "minecraft")
                    }
                    PasswordField("GameDig token", current.text("gamedigToken"), { current.set("gamedigToken", it) })
                    SwitchRow("Given port only", current.bool("gamedigGivenPortOnly", true), { current.set("gamedigGivenPortOnly", it) })
                }
            }
        }

        if (type == "ping") {
            item {
                FormSection("Ping") {
                    Field("Packet size", current.text("packetSize"), { current.set("packetSize", it.filter(Char::isDigit)) }, keyboardType = KeyboardType.Number)
                    Field("Ping count", current.text("ping_count"), { current.set("ping_count", it.filter(Char::isDigit)) }, keyboardType = KeyboardType.Number)
                    SwitchRow("Numeric output", current.bool("ping_numeric"), { current.set("ping_numeric", it) })
                    Field("Per-request timeout (s)", current.text("ping_per_request_timeout"), { current.set("ping_per_request_timeout", it.filter(Char::isDigit)) }, keyboardType = KeyboardType.Number)
                }
            }
        }

        if (type == "http" || type == "keyword" || type == "json-query" || type == "real-browser" ||
            type == "websocket-upgrade" || type == "globalping"
        ) {
            item {
                FormSection("TLS & response") {
                    SwitchRow("Ignore TLS errors", current.bool("ignoreTls"), { current.set("ignoreTls", it) })
                    SwitchRow("Expiry notification", current.bool("expiryNotification"), { current.set("expiryNotification", it) })
                    SwitchRow("Domain expiry notification", current.bool("domainExpiryNotification", true), { current.set("domainExpiryNotification", it) })
                    Field("Max redirects", current.text("maxredirects"), { current.set("maxredirects", it.filter(Char::isDigit)) }, keyboardType = KeyboardType.Number)
                }
            }
        }

        item {
            FormSection("Advanced") {
                Field("Interval (s)", current.text("interval"), { current.set("interval", it.filter(Char::isDigit)) }, keyboardType = KeyboardType.Number)
                Field("Retry interval (s)", current.text("retryInterval"), { current.set("retryInterval", it.filter(Char::isDigit)) }, keyboardType = KeyboardType.Number)
                Field("Resend interval (s)", current.text("resendInterval"), { current.set("resendInterval", it.filter(Char::isDigit)) }, keyboardType = KeyboardType.Number)
                Field("Max retries", current.text("maxretries"), { current.set("maxretries", it.filter(Char::isDigit)) }, keyboardType = KeyboardType.Number)
                Field("Timeout (s)", current.text("timeout"), { current.set("timeout", it.filter(Char::isDigit)) }, keyboardType = KeyboardType.Number)
                SwitchRow("Retry only on status code failure", current.bool("retryOnlyOnStatusCodeFailure"), { current.set("retryOnlyOnStatusCodeFailure", it) })
                SwitchRow("Invert monitor status", current.bool("upsideDown"), { current.set("upsideDown", it) })
                if (type == "http" || type == "keyword" || type == "json-query" || type == "real-browser" || type == "websocket-upgrade") {
                    Field(
                        "Accepted status codes", statusCodeText,
                        { current.setCsv("accepted_statuscodes", it) },
                        supporting = "Comma-separated codes or ranges, for example 200-299, 301",
                    )
                    SwitchRow("Save response", current.bool("saveResponse"), { current.set("saveResponse", it) })
                    SwitchRow("Save error response", current.bool("saveErrorResponse", true), { current.set("saveErrorResponse", it) })
                    Field("Response max length", current.text("responseMaxLength"), { current.set("responseMaxLength", it.filter(Char::isDigit)) }, keyboardType = KeyboardType.Number)
                    SwitchRow("Cache bust", current.bool("cacheBust"), { current.set("cacheBust", it) })
                }
                PickerField(
                    "Proxy", current.text("proxyId"),
                    listOf("" to "No proxy") + proxies.map {
                        it.str("id") to "${it.str("protocol").uppercase()} ${it.str("host")}:${it.str("port")}".trim()
                    },
                    { current.set("proxyId", it) },
                )
                Field(
                    "Conditions", current.text("conditionsText").ifBlank {
                        current.json.optJSONArray("conditions")?.toString() ?: "[]"
                    },
                    { current.set("conditionsText", it) },
                    singleLine = false, minLines = 2, maxLines = 6,
                    supporting = "JSON array of conditions. Leave [] for none.",
                )
            }
        }

        if (notifications.isNotEmpty()) {
            item {
                FormSection("Notifications", "Choose which notifications fire for this monitor.") {
                    notifications.forEach { notification ->
                        SwitchRow(
                            notification.str("name"),
                            current.notificationEnabled(notification.long("id")),
                            { current.toggleNotification(notification.long("id"), it) },
                            subtitle = notification.str("type"),
                        )
                    }
                }
            }
        }

        if (tags.isNotEmpty()) {
            item {
                FormSection("Tags") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { tag ->
                            ChipButton(
                                tag.name,
                                selectedTagIds.contains(tag.id),
                            ) {
                                selectedTagIds = if (selectedTagIds.contains(tag.id)) {
                                    selectedTagIds - tag.id
                                } else {
                                    selectedTagIds + tag.id
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(20.dp, 16.dp)) {
                if (saveError.isNotBlank()) {
                    InfoBanner(saveError, tone = KumaColors.Down)
                    Spacer(Modifier.height(10.dp))
                }
                Button(
                    onClick = { save() },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    } else {
                        androidx.compose.material3.Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(if (monitorId == null) "Create monitor" else "Save changes")
                }
                if (monitorId != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Monitor #$monitorId",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}
