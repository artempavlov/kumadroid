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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.kumadroid.app.data.KumaRepository
import org.kumadroid.app.data.bool
import org.kumadroid.app.data.long
import org.kumadroid.app.data.str
import org.kumadroid.app.data.stringList

object SubSettingsScreen {
    enum class Kind { ApiKeys, DockerHosts, Proxies, RemoteBrowsers, General, Security, Data, About }

    @Composable
    operator fun invoke(repository: KumaRepository, kind: Kind, onBack: () -> Unit) {
        when (kind) {
            Kind.ApiKeys -> ApiKeysScreen(repository, onBack)
            Kind.DockerHosts -> DockerHostsScreen(repository, onBack)
            Kind.Proxies -> ProxiesScreen(repository, onBack)
            Kind.RemoteBrowsers -> RemoteBrowsersScreen(repository, onBack)
            Kind.General -> GeneralSettingsScreen(repository, onBack)
            Kind.Security -> SecuritySettingsScreen(repository, onBack)
            Kind.Data -> DataSettingsScreen(repository, onBack)
            Kind.About -> AboutScreen(repository, onBack)
        }
    }
}

@Composable
private fun ApiKeysScreen(repository: KumaRepository, onBack: () -> Unit) {
    val keys by repository.apiKeys.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<JSONObject?>(null) }
    var clearKey by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            TopBar("API keys", subtitle = "${keys.size} key${if (keys.size == 1) "" else "s"}", onBack = onBack, actions = {
                IconButton(onClick = { adding = true }) { Icon(Icons.Default.Add, contentDescription = "Add API key") }
            })
        }
        if (error.isNotBlank()) item { InfoBanner(error, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), KumaColors.Down) }
        if (keys.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Api,
                    title = "No API keys",
                    message = "API keys authenticate badging and push endpoints. Uptime Kuma does not show existing key values.",
                    actionLabel = "Create API key",
                    onAction = { adding = true },
                )
            }
        }
        items(keys) { key ->
            val id = key.long("id")
            val active = key.bool("active")
            KumaCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(key.str("name", "API key #$id"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            buildString {
                                append(if (active) "Active" else "Disabled")
                                val expires = key.str("expires")
                                if (expires.isNotBlank()) append(" · expires $expires")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = active,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                try {
                                    repository.setApiKeyActive(id, enabled)
                                } catch (e: Exception) {
                                    error = e.message ?: "Could not update key"
                                }
                            }
                        },
                    )
                    IconButton(onClick = { deleting = key }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var expires by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) adding = false },
            title = { Text("New API key") },
            text = {
                Column {
                    Field("Name", name, { name = it })
                    Spacer(Modifier.height(10.dp))
                    Field("Expiry date", expires, { expires = it }, supporting = "Optional. Format YYYY-MM-DD; empty means no expiry.")
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                clearKey = repository.addApiKey(name.trim(), expires.trim())
                                adding = false
                            } catch (e: Exception) {
                                error = e.message ?: "Could not create key"
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text(if (busy) "Creating…" else "Create") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }

    clearKey?.let { value ->
        AlertDialog(
            onDismissRequest = { clearKey = null },
            title = { Text("Copy your API key") },
            text = {
                Column {
                    Text("This value is shown only once.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text(value, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    clipboard.setText(AnnotatedString(value))
                    clearKey = null
                }) { Text("Copy and close") }
            },
        )
    }

    deleting?.let { key ->
        ConfirmDialog(
            title = "Delete API key?",
            message = "Applications using “${key.str("name", "this key")}” lose access immediately.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { deleting = null },
        ) {
            scope.launch {
                try {
                    repository.deleteApiKey(key.long("id"))
                    deleting = null
                } catch (e: Exception) {
                    error = e.message ?: "Could not delete key"
                    deleting = null
                }
            }
        }
    }
}

@Composable
private fun DockerHostsScreen(repository: KumaRepository, onBack: () -> Unit) {
    val hosts by repository.dockerHosts.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            TopBar("Docker hosts", subtitle = "${hosts.size} host${if (hosts.size == 1) "" else "s"}", onBack = onBack, actions = {
                IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, contentDescription = "Add host") }
            })
        }
        if (error.isNotBlank()) item { InfoBanner(error, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), KumaColors.Down) }
        if (toast.isNotBlank()) item { InfoBanner(toast, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), KumaColors.Up) }
        if (hosts.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Dns,
                    title = "No Docker hosts",
                    message = "Add a Docker daemon to monitor containers on another machine.",
                    actionLabel = "Add Docker host",
                    onAction = { creating = true },
                )
            }
        }
        items(hosts) { host ->
            KumaCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(host.str("name", "Docker host"), style = MaterialTheme.typography.titleMedium)
                        Text(host.str("dockerDaemon"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(host.str("dockerType"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { editing = host }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { deleting = host }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallActionButton("Test", onClick = {
                        scope.launch {
                            toast = ""
                            error = ""
                            try {
                                toast = repository.testDockerHost(host)
                            } catch (e: Exception) {
                                error = e.message ?: "Connection failed"
                            }
                        }
                    })
                }
            }
        }
    }

    if (creating || editing != null) {
        val target = editing
        var name by remember(target?.toString(), creating) { mutableStateOf(target?.str("name") ?: "") }
        var type by remember(target?.toString(), creating) { mutableStateOf(target?.str("dockerType") ?: "socket") }
        var daemon by remember(target?.toString(), creating) {
            mutableStateOf(target?.str("dockerDaemon") ?: "/var/run/docker.sock")
        }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) { creating = false; editing = null } },
            title = { Text(if (target == null) "New Docker host" else "Edit Docker host") },
            text = {
                Column {
                    Field("Name", name, { name = it })
                    Spacer(Modifier.height(10.dp))
                    PickerField("Connection type", type, listOf("socket" to "Unix socket", "tcp" to "TCP"), { type = it })
                    Spacer(Modifier.height(10.dp))
                    Field("Docker daemon", daemon, { daemon = it }, supporting = "For example /var/run/docker.sock or tcp://host:2375")
                    if (error.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = KumaColors.Down, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank() && daemon.isNotBlank() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = ""
                            try {
                                val body = JSONObject().put("name", name.trim()).put("dockerType", type).put("dockerDaemon", daemon.trim())
                                repository.saveDockerHost(body, target?.long("id"))
                                creating = false
                                editing = null
                            } catch (e: Exception) {
                                error = e.message ?: "Could not save host"
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

    deleting?.let { host ->
        ConfirmDialog("Delete Docker host?", "Monitors using this host stop working.", "Delete", destructive = true, onDismiss = { deleting = null }) {
            scope.launch {
                try {
                    repository.deleteDockerHost(host.long("id"))
                    deleting = null
                } catch (e: Exception) {
                    error = e.message ?: "Could not delete host"
                    deleting = null
                }
            }
        }
    }
}

@Composable
private fun ProxiesScreen(repository: KumaRepository, onBack: () -> Unit) {
    val proxies by repository.proxies.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            TopBar("Proxies", subtitle = "${proxies.size} prox${if (proxies.size == 1) "y" else "ies"}", onBack = onBack, actions = {
                IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, contentDescription = "Add proxy") }
            })
        }
        if (error.isNotBlank()) item { InfoBanner(error, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), KumaColors.Down) }
        if (proxies.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Language,
                    title = "No proxies",
                    message = "Monitors can send requests through an HTTP or SOCKS proxy.",
                    actionLabel = "Add proxy",
                    onAction = { creating = true },
                )
            }
        }
        items(proxies) { proxy ->
            KumaCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${proxy.str("protocol").uppercase()} proxy", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${proxy.str("host")}:${proxy.str("port")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            buildString {
                                append(if (proxy.bool("active")) "Active" else "Inactive")
                                if (proxy.bool("default")) append(" · default")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editing = proxy }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { deleting = proxy }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (creating || editing != null) {
        val target = editing
        var protocol by remember(target?.toString(), creating) { mutableStateOf(target?.str("protocol") ?: "http") }
        var host by remember(target?.toString(), creating) { mutableStateOf(target?.str("host") ?: "") }
        var port by remember(target?.toString(), creating) { mutableStateOf(target?.str("port") ?: "") }
        var auth by remember(target?.toString(), creating) { mutableStateOf(target?.bool("auth") ?: false) }
        var username by remember(target?.toString(), creating) { mutableStateOf(target?.str("username") ?: "") }
        var password by remember(target?.toString(), creating) { mutableStateOf(target?.str("password") ?: "") }
        var active by remember(target?.toString(), creating) { mutableStateOf(target?.bool("active") ?: true) }
        var isDefault by remember(target?.toString(), creating) { mutableStateOf(target?.bool("default") ?: false) }
        var applyExisting by remember(target?.toString(), creating) { mutableStateOf(target?.bool("applyExisting") ?: false) }
        var busy by remember { mutableStateOf(false) }
        val portNumber = port.toIntOrNull()
        val portValid = portNumber != null && portNumber in 1..65535
        AlertDialog(
            onDismissRequest = { if (!busy) { creating = false; editing = null } },
            title = { Text(if (target == null) "New proxy" else "Edit proxy") },
            text = {
                Column {
                    PickerField(
                        "Protocol", protocol,
                        listOf("http", "https", "socks", "socks5", "socks5h", "socks4").map { it to it.uppercase() },
                        { protocol = it },
                    )
                    Field("Host", host, { host = it })
                    Field(
                        "Port", port, { port = it.filter(Char::isDigit) },
                        supporting = if (port.isNotBlank() && !portValid) "Enter a port from 1 to 65535." else null,
                        keyboardType = KeyboardType.Number,
                    )
                    SwitchRow("Authentication", auth, { auth = it })
                    if (auth) {
                        Field("Username", username, { username = it })
                        PasswordField("Password", password, { password = it })
                    }
                    SwitchRow("Active", active, { active = it })
                    SwitchRow("Default proxy", isDefault, { isDefault = it })
                    SwitchRow("Apply to existing monitors", applyExisting, { applyExisting = it })
                    if (error.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = KumaColors.Down, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = host.isNotBlank() && portValid && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = ""
                            try {
                                val body = JSONObject()
                                    .put("protocol", protocol)
                                    .put("host", host.trim())
                                    .put("port", portNumber ?: 0)
                                    .put("auth", auth)
                                    .put("username", username)
                                    .put("password", password)
                                    .put("active", active)
                                    .put("default", isDefault)
                                    .put("applyExisting", applyExisting)
                                repository.saveProxy(body, target?.long("id"))
                                creating = false
                                editing = null
                            } catch (e: Exception) {
                                error = e.message ?: "Could not save proxy"
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

    deleting?.let { proxy ->
        ConfirmDialog("Delete proxy?", "Monitors using this proxy stop working.", "Delete", destructive = true, onDismiss = { deleting = null }) {
            scope.launch {
                try {
                    repository.deleteProxy(proxy.long("id"))
                    deleting = null
                } catch (e: Exception) {
                    error = e.message ?: "Could not delete proxy"
                    deleting = null
                }
            }
        }
    }
}

@Composable
private fun RemoteBrowsersScreen(repository: KumaRepository, onBack: () -> Unit) {
    val browsers by repository.remoteBrowsers.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            TopBar("Remote browsers", subtitle = "${browsers.size} browser${if (browsers.size == 1) "" else "s"}", onBack = onBack, actions = {
                IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, contentDescription = "Add browser") }
            })
        }
        if (error.isNotBlank()) item { InfoBanner(error, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), KumaColors.Down) }
        if (toast.isNotBlank()) item { InfoBanner(toast, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), KumaColors.Up) }
        if (browsers.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.TravelExplore,
                    title = "No remote browsers",
                    message = "Real-browser monitors can use a remote Chrome instance.",
                    actionLabel = "Add remote browser",
                    onAction = { creating = true },
                )
            }
        }
        items(browsers) { browser ->
            KumaCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(browser.str("name", "Browser"), style = MaterialTheme.typography.titleMedium)
                        Text(browser.str("url"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { editing = browser }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { deleting = browser }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
                }
                SmallActionButton("Test", onClick = {
                    scope.launch {
                        toast = ""
                        error = ""
                        try {
                            toast = repository.testRemoteBrowser(browser)
                        } catch (e: Exception) {
                            error = e.message ?: "Connection failed"
                        }
                    }
                })
            }
        }
    }

    if (creating || editing != null) {
        val target = editing
        var name by remember(target?.toString(), creating) { mutableStateOf(target?.str("name") ?: "") }
        var url by remember(target?.toString(), creating) { mutableStateOf(target?.str("url") ?: "http://") }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) { creating = false; editing = null } },
            title = { Text(if (target == null) "New remote browser" else "Edit remote browser") },
            text = {
                Column {
                    Field("Name", name, { name = it })
                    Field("Browser URL", url, { url = it }, supporting = "For example http://chrome:3000")
                    if (error.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = KumaColors.Down, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank() && url.isNotBlank() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = ""
                            try {
                                repository.saveRemoteBrowser(JSONObject().put("name", name.trim()).put("url", url.trim()), target?.long("id"))
                                creating = false
                                editing = null
                            } catch (e: Exception) {
                                error = e.message ?: "Could not save browser"
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

    deleting?.let { browser ->
        ConfirmDialog("Delete remote browser?", "Real-browser monitors using it fall back to the default.", "Delete", destructive = true, onDismiss = { deleting = null }) {
            scope.launch {
                try {
                    repository.deleteRemoteBrowser(browser.long("id"))
                    deleting = null
                } catch (e: Exception) {
                    error = e.message ?: "Could not delete browser"
                    deleting = null
                }
            }
        }
    }
}
