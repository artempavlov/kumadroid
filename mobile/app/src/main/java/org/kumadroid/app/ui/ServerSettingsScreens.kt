package org.kumadroid.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.kumadroid.app.BrandMark
import org.kumadroid.app.BuildConfig
import org.kumadroid.app.data.KumaRepository
import org.kumadroid.app.data.bool
import org.kumadroid.app.data.long
import org.kumadroid.app.data.str

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
fun GeneralSettingsScreen(repository: KumaRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val statusPages by repository.statusPages.collectAsStateWithLifecycle()
    var settings by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var testingChrome by remember { mutableStateOf(false) }
    var tlsDays by remember { mutableStateOf("7, 14, 21") }
    var domainDays by remember { mutableStateOf("7, 14, 21") }
    var retentionDays by remember { mutableStateOf("180") }
    var authWasDisabled by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val loaded = repository.getSettings()
            settings = loaded
            tlsDays = loaded.optJSONArray("tlsExpiryNotifyDays")?.joinInts() ?: "7, 14, 21"
            domainDays = loaded.optJSONArray("domainExpiryNotifyDays")?.joinInts() ?: "7, 14, 21"
            retentionDays = loaded.optInt("keepDataPeriodDays", 180).toString()
            authWasDisabled = loaded.bool("disableAuth")
        } catch (e: Exception) {
            error = e.message ?: "Could not load settings"
        }
    }

    fun update(key: String, value: Any) {
        settings = JSONObject((settings ?: JSONObject()).toString()).put(key, value)
    }

    if (settings == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar("General", onBack = onBack)
            if (error.isNotBlank()) InfoBanner(error, Modifier.padding(16.dp), KumaColors.Down)
            else EmptyState(Icons.Default.Info, "Loading settings…", "Fetching server configuration.")
        }
        return
    }

    val s = settings!!
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TopBar("General", subtitle = "Server behavior", onBack = onBack) }
        if (error.isNotBlank()) item { InfoBanner(error, Modifier.fillMaxWidth(), KumaColors.Down) }
        if (toast.isNotBlank()) item { InfoBanner(toast, Modifier.fillMaxWidth(), KumaColors.Up) }
        item {
            FormSection("Server") {
                Field("Server timezone", s.str("serverTimezone"), { update("serverTimezone", it) }, supporting = "IANA name such as Europe/Moscow")
                PickerField(
                    "Entry page", s.str("entryPage", "dashboard"),
                    listOf("dashboard" to "Dashboard") + statusPages.map { "statusPage-${it.slug}" to "Status page · ${it.title}" },
                    { update("entryPage", it) },
                )
                Field("Primary base URL", s.str("primaryBaseURL"), { update("primaryBaseURL", it) }, supporting = "Used in notification links, e.g. https://kuma.example.com")
            }
        }
        item {
            FormSection("Display") {
                SwitchRow("Search engine indexing", s.bool("searchEngineIndex"), { update("searchEngineIndex", it) })
                SwitchRow("Check for updates", s.bool("checkUpdate", true), { update("checkUpdate", it) })
                SwitchRow("Trust proxy", s.bool("trustProxy"), { update("trustProxy", it) })
                SwitchRow("Use NSCD", s.bool("nscd", true), { update("nscd", it) })
            }
        }
        item {
            FormSection("Data retention") {
                Field(
                    "Keep data for (days)", retentionDays,
                    { retentionDays = it.filter(Char::isDigit) },
                    supporting = "0 keeps data forever.",
                    keyboardType = KeyboardType.Number,
                )
                Field("TLS expiry notification days", tlsDays, { tlsDays = it }, supporting = "Comma-separated days before expiry.")
                Field("Domain expiry notification days", domainDays, { domainDays = it }, supporting = "Comma-separated days before expiry.")
            }
        }
        item {
            FormSection("Integrations") {
                Field("Steam API key", s.str("steamAPIKey"), { update("steamAPIKey", it) })
                Field("Globalping API token", s.str("globalpingApiToken"), { update("globalpingApiToken", it) })
                Field("Chrome executable", s.str("chromeExecutable"), { update("chromeExecutable", it) }, supporting = "Path used by real-browser monitors on the server.")
                OutlinedButton(
                    enabled = !testingChrome,
                    onClick = {
                        scope.launch {
                            testingChrome = true
                            error = ""
                            toast = ""
                            try {
                                toast = repository.testChrome(s.str("chromeExecutable"))
                            } catch (e: Exception) {
                                error = e.message ?: "Could not start Chrome"
                            } finally {
                                testingChrome = false
                            }
                        }
                    },
                ) { Text(if (testingChrome) "Testing Chrome…" else "Test Chrome executable") }
            }
        }
        item {
            FormSection("Authentication") {
                SwitchRow("Disable authentication", s.bool("disableAuth"), { update("disableAuth", it) })
                if (s.bool("disableAuth")) {
                    InfoBanner(
                        "Anyone who can reach this server gets full access. Only use it on a trusted network.",
                        Modifier.fillMaxWidth(),
                        KumaColors.Down,
                    )
                    if (!authWasDisabled) {
                        PasswordField(
                            "Current password", currentPassword, { currentPassword = it },
                            supporting = "Required to turn off authentication.",
                        )
                    }
                }
            }
        }
        item {
            val needsPassword = s.bool("disableAuth") && !authWasDisabled
            Button(
                enabled = !saving && (!needsPassword || currentPassword.isNotBlank()),
                onClick = {
                    scope.launch {
                        saving = true
                        error = ""
                        toast = ""
                        try {
                            val next = JSONObject(s.toString())
                            next.put("keepDataPeriodDays", retentionDays.toIntOrNull() ?: 180)
                            next.put("tlsExpiryNotifyDays", parseInts(tlsDays))
                            next.put("domainExpiryNotifyDays", parseInts(domainDays))
                            repository.setSettings(next, currentPassword.takeIf { needsPassword })
                            settings = next
                            authWasDisabled = next.bool("disableAuth")
                            currentPassword = ""
                            toast = "Settings saved."
                        } catch (e: Exception) {
                            error = e.message ?: "Could not save settings"
                        } finally {
                            saving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Saving…" else "Save settings") }
        }
    }
}

private fun JSONArray.joinInts(): String = (0 until length()).joinToString(", ") { optInt(it).toString() }

private fun parseInts(text: String): JSONArray {
    val array = JSONArray()
    text.split(',', ' ', ';').mapNotNull { it.trim().toIntOrNull() }.distinct().sorted().forEach { array.put(it) }
    return array
}

@Composable
fun SecuritySettingsScreen(repository: KumaRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var twoFa by remember { mutableStateOf<Boolean?>(null) }
    var error by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var changing by remember { mutableStateOf(false) }
    var faPassword by remember { mutableStateOf("") }
    var setupUri by remember { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf("") }
    var tokenValid by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var enabling by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var disabling by remember { mutableStateOf(false) }
    var disablePassword by remember { mutableStateOf("") }
    var disableBusy by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var disconnectBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            twoFa = repository.twoFaStatus()
        } catch (e: Exception) {
            error = e.message ?: "Could not read 2FA status"
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TopBar("Security", subtitle = "Password and two-factor authentication", onBack = onBack) }
        if (error.isNotBlank()) item { InfoBanner(error, Modifier.fillMaxWidth(), KumaColors.Down) }
        if (toast.isNotBlank()) item { InfoBanner(toast, Modifier.fillMaxWidth(), KumaColors.Up) }

        item {
            FormSection("Change password") {
                PasswordField("Current password", currentPassword, { currentPassword = it })
                PasswordField("New password", newPassword, { newPassword = it })
                PasswordField("Confirm new password", confirmPassword, { confirmPassword = it })
                Button(
                    enabled = currentPassword.isNotBlank() && newPassword.length >= 6 && newPassword == confirmPassword && !changing,
                    onClick = {
                        scope.launch {
                            changing = true
                            error = ""
                            toast = ""
                            try {
                                repository.changePassword(currentPassword, newPassword)
                                currentPassword = ""
                                newPassword = ""
                                confirmPassword = ""
                                toast = "Password changed."
                            } catch (e: Exception) {
                                error = e.message ?: "Could not change password"
                            } finally {
                                changing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (changing) "Saving…" else "Change password") }
            }
        }

        item {
            FormSection("Two-factor authentication") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (twoFa == true) Icons.Default.CheckCircle else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (twoFa == true) KumaColors.Up else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        when (twoFa) {
                            null -> "Checking status…"
                            true -> "2FA is enabled"
                            false -> "2FA is not enabled"
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                if (twoFa == false) {
                    Text(
                        "Use an authenticator app such as Aegis or Google Authenticator.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PasswordField("Account password", faPassword, { faPassword = it })
                    Button(
                        enabled = faPassword.isNotBlank() && !preparing,
                        onClick = {
                            scope.launch {
                                preparing = true
                                error = ""
                                toast = ""
                                try {
                                    setupUri = repository.prepare2FA(faPassword)
                                    token = ""
                                    tokenValid = false
                                } catch (e: Exception) {
                                    error = e.message ?: "Could not start 2FA setup"
                                } finally {
                                    preparing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (preparing) "Preparing…" else "Set up authenticator") }
                }
                if (twoFa == true) {
                    PasswordField("Account password", faPassword, { faPassword = it })
                    OutlinedButton(
                        enabled = faPassword.isNotBlank(),
                        onClick = { disabling = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Disable 2FA") }
                }
            }
        }

        item {
            FormSection("Sessions") {
                Text(
                    "Revoke every other browser and API session connected to this account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    enabled = !disconnectBusy,
                    onClick = { confirmDisconnect = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (disconnectBusy) "Working…" else "Disconnect other sessions") }
            }
        }
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = "Disconnect other sessions?",
            message = "Every other browser and API client signed in to this account will be signed out.",
            confirmLabel = "Disconnect",
            destructive = true,
            onDismiss = { if (!disconnectBusy) confirmDisconnect = false },
            onConfirm = {
                scope.launch {
                    disconnectBusy = true
                    error = ""
                    toast = ""
                    try {
                        repository.disconnectOtherClients()
                        toast = "Other sessions disconnected."
                        confirmDisconnect = false
                    } catch (e: Exception) {
                        error = e.message ?: "Could not disconnect sessions"
                    } finally {
                        disconnectBusy = false
                    }
                }
            },
        )
    }

    setupUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { if (!enabling) setupUri = null },
            title = { Text("Scan the QR code") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    QrCode(uri, Modifier.size(210.dp))
                    Text(
                        uri,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Field("6-digit code", token, {
                        token = it.filter(Char::isLetterOrDigit).take(6)
                        tokenValid = false
                    }, keyboardType = KeyboardType.NumberPassword)
                    OutlinedButton(
                        enabled = token.length >= 6 && !verifying,
                        onClick = {
                            scope.launch {
                                verifying = true
                                error = ""
                                try {
                                    tokenValid = repository.verifyToken(token, faPassword)
                                    if (!tokenValid) error = "The code is not valid. Check the device clock and try again."
                                } catch (e: Exception) {
                                    error = e.message ?: "Could not verify code"
                                } finally {
                                    verifying = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (verifying) "Verifying…" else "Verify code") }
                    if (tokenValid) {
                        Text("Code verified. Enable 2FA to finish.", color = KumaColors.Up, style = MaterialTheme.typography.bodySmall)
                    }
                    if (error.isNotBlank()) {
                        Text(error, color = KumaColors.Down, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = tokenValid && !enabling,
                    onClick = {
                        scope.launch {
                            enabling = true
                            error = ""
                            try {
                                repository.save2FA(faPassword)
                                twoFa = true
                                setupUri = null
                                faPassword = ""
                                toast = "Two-factor authentication enabled."
                            } catch (e: Exception) {
                                error = e.message ?: "Could not enable 2FA"
                            } finally {
                                enabling = false
                            }
                        }
                    },
                ) { Text(if (enabling) "Enabling…" else "Enable 2FA") }
            },
            dismissButton = { TextButton(onClick = { setupUri = null }) { Text("Close") } },
        )
    }

    if (disabling) {
        AlertDialog(
            onDismissRequest = { if (!disableBusy) disabling = false },
            title = { Text("Disable 2FA?") },
            text = {
                Column {
                    Text("Future sign-ins will only require your password.")
                    Spacer(Modifier.height(8.dp))
                    PasswordField("Account password", disablePassword, { disablePassword = it })
                    if (error.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(error, color = KumaColors.Down, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = disablePassword.isNotBlank() && !disableBusy,
                    onClick = {
                        scope.launch {
                            disableBusy = true
                            error = ""
                            try {
                                repository.disable2FA(disablePassword)
                                twoFa = false
                                disablePassword = ""
                                faPassword = ""
                                disabling = false
                                toast = "Two-factor authentication disabled."
                            } catch (e: Exception) {
                                error = e.message ?: "Could not disable 2FA"
                            } finally {
                                disableBusy = false
                            }
                        }
                    },
                ) { Text(if (disableBusy) "Disabling…" else "Disable") }
            },
            dismissButton = {
                TextButton(onClick = { if (!disableBusy) disabling = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun DataSettingsScreen(repository: KumaRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<JSONObject?>(null) }
    var retention by remember { mutableStateOf("180") }
    var databaseSize by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val retentionValue = retention.toIntOrNull()
    val retentionValid = retentionValue != null && retentionValue >= 0

    fun loadSize() {
        scope.launch {
            try {
                databaseSize = repository.getDatabaseSize().long("size")
            } catch (e: Exception) {
                error = e.message ?: "Could not read database size"
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val loaded = repository.getSettings()
            settings = loaded
            retention = loaded.optInt("keepDataPeriodDays", 180).toString()
        } catch (e: Exception) {
            error = e.message ?: "Could not load settings"
        }
        loadSize()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TopBar("Data", subtitle = "Retention and maintenance", onBack = onBack) }
        if (error.isNotBlank()) item { InfoBanner(error, Modifier.fillMaxWidth(), KumaColors.Down) }
        if (toast.isNotBlank()) item { InfoBanner(toast, Modifier.fillMaxWidth(), KumaColors.Up) }
        item {
            FormSection("Data retention") {
                Field(
                    "Keep data for (days)", retention,
                    { retention = it.filter(Char::isDigit) },
                    supporting = if (retention.isNotBlank() && !retentionValid) {
                        "Enter 0 or a positive number of days."
                    } else {
                        "0 keeps all data forever."
                    },
                    keyboardType = KeyboardType.Number,
                )
                Button(
                    enabled = settings != null && retentionValid && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = ""
                            toast = ""
                            try {
                                val next = JSONObject((settings ?: JSONObject()).toString())
                                next.put("keepDataPeriodDays", retentionValue ?: 0)
                                repository.setSettings(next)
                                settings = next
                                toast = "Retention updated."
                            } catch (e: Exception) {
                                error = e.message ?: "Could not save retention"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save retention") }
            }
        }
        item {
            FormSection("Database") {
                InfoRow("Database size", databaseSize?.let(::formatBytes) ?: "—")
                InfoRow("Database type", repository.serverInfo.value?.dbType?.ifBlank { "unknown" } ?: "unknown")
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = ""
                            toast = ""
                            try {
                                repository.shrinkDatabase()
                                loadSize()
                                toast = "Database compacted."
                            } catch (e: Exception) {
                                error = e.message ?: "Could not compact database"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "Working…" else "Shrink database (VACUUM)") }
            }
        }
        item {
            FormSection("Danger zone") {
                Text(
                    "Clear all statistics and calculated uptime for every monitor. Heartbeat history is kept according to retention.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    enabled = !busy,
                    onClick = { confirmClear = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Clear all statistics", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            "Clear all statistics?",
            "Uptime and ping aggregates for every monitor will be recalculated from heartbeat history.",
            "Clear statistics",
            destructive = true,
            onDismiss = { confirmClear = false },
        ) {
            scope.launch {
                busy = true
                error = ""
                try {
                    repository.clearStatistics()
                    toast = "Statistics cleared."
                } catch (e: Exception) {
                    error = e.message ?: "Could not clear statistics"
                } finally {
                    busy = false
                    confirmClear = false
                }
            }
        }
    }
}

@Composable
fun AboutScreen(repository: KumaRepository, onBack: () -> Unit) {
    val info by repository.serverInfo.collectAsStateWithLifecycle()
    val url = repository.serverUrl
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TopBar("About", subtitle = "KumaDroid", onBack = onBack) }
        item {
            KumaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(44)
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text("KumaDroid", style = MaterialTheme.typography.titleLarge)
                        Text("Native Android client for Uptime Kuma", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(14.dp))
                InfoRow("App version", BuildConfig.VERSION_NAME)
                InfoRow("Server", url)
                InfoRow("Server version", info?.version?.ifBlank { "—" } ?: "—")
                InfoRow("Latest version", info?.latestVersion?.ifBlank { "—" } ?: "—")
                InfoRow("Database", info?.dbType?.ifBlank { "—" } ?: "—")
                InfoRow("Node.js", info?.nodeVersion?.ifBlank { "—" } ?: "—")
                InfoRow("Server mode", if (info?.isContainer == true) "Docker container" else "Host process")
            }
        }
        item {
            KumaCard {
                Text("Compatibility", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "KumaDroid talks to Uptime Kuma over its native Socket.IO protocol. It is tested against Uptime Kuma 2.5.5 and also supports 1.23.x installations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "This application stores the server address, your session token and the optional self-signed certificate preference on this device. It does not store your password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            KumaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(8.dp))
                    Text("Protocol: Engine.IO 4 · Socket.IO 4 · WebSocket", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun QrCode(content: String, modifier: Modifier = Modifier) {
    val bitmap = remember(content) {
        runCatching {
            val matrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                480,
                480,
                mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8"),
            )
            val pixels = IntArray(matrix.width * matrix.height)
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    pixels[y * matrix.width + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
            }
            Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
            }
        }.getOrNull()
    }
    bitmap?.let {
        Image(it.asImageBitmap(), contentDescription = "Two-factor authentication QR code", modifier = modifier)
    }
}
