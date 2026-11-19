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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.kumadroid.app.data.KumaRepository
import org.kumadroid.app.data.bool
import org.kumadroid.app.data.long
import org.kumadroid.app.data.str

private data class NotificationField(
    val key: String,
    val label: String,
    val secret: Boolean = false,
    val multiline: Boolean = false,
    val numeric: Boolean = false,
    val toggle: Boolean = false,
)

private val notificationTypes = listOf(
    "alerta", "AlertNow", "AliyunSMS", "amootsms", "apprise", "bale", "Bark", "bearsms", "Bitrix24", "Brevo",
    "CallMeBot", "Cellsynt", "clicksendsms", "ClickUp", "DingDing", "discord", "egosms", "Elks", "evolution",
    "Feishu", "FlashDuty", "Flowtriq", "fluxer", "FreeMobile", "GoAlert", "GoogleChat", "GoogleSheets", "gorush",
    "gotify", "GrafanaOncall", "gtxmessaging", "HaloPSA", "HeiiOnCall", "HomeAssistant", "JiraServiceManagement",
    "Keep", "Kook", "line", "lunasea", "matrix", "mattermost", "max", "Milky", "nextcloudtalk", "nostr",
    "notifery", "notifyapp", "ntfy", "octopush", "OneBot", "OneChat", "Onesender", "Ooredoo", "openwa",
    "Opsgenie", "PagerDuty", "PagerTree", "pinglet", "plivo", "promosms", "pumble", "pushbullet",
    "PushByTechulus", "PushDeer", "pushover", "PushPlus", "pushy", "Resend", "rocket.chat", "SendGrid",
    "ServerChan", "serwersms", "SevenIO", "signal", "signalgrid", "SIGNL4", "slack", "smsc", "SMSEagle",
    "SMSGateway", "smsir", "SMSManager", "SMSPartner", "SMSPlanet", "smtp", "Splunk", "SpugPush", "squadcast",
    "stackfield", "teams", "telegram", "telnyx", "Teltonika", "threema", "TurboSMTP", "twilio", "VK", "VKTeams",
    "waha", "webhook", "Webpush", "WeCom", "whapi", "Whatsapp360messenger", "WPush", "WxPusher", "YZJ", "ZohoCliq",
)

private val notificationFields: Map<String, List<NotificationField>> = mapOf(
    "telegram" to listOf(
        NotificationField("telegramBotToken", "Bot token", secret = true),
        NotificationField("telegramChatID", "Chat ID"),
        NotificationField("telegramMessageThreadID", "Message thread ID", numeric = true),
        NotificationField("telegramServerUrl", "Telegram server URL"),
        NotificationField("telegramTemplate", "Message template", multiline = true),
        NotificationField("telegramTemplateParseMode", "Template parse mode"),
    ),
    "discord" to listOf(
        NotificationField("discordWebhookUrl", "Webhook URL", secret = true),
        NotificationField("discordUsername", "Display name"),
        NotificationField("discordPrefixMessage", "Prefix message", multiline = true),
        NotificationField("discordChannelType", "Channel type"),
        NotificationField("threadId", "Thread ID"),
        NotificationField("postName", "Forum post name"),
        NotificationField("discordMessageTemplate", "Message template", multiline = true),
        NotificationField("discordMessageFormat", "Message format"),
    ),
    "slack" to listOf(
        NotificationField("slackwebhookURL", "Webhook URL", secret = true),
        NotificationField("slackusername", "Username"),
        NotificationField("slackiconemo", "Icon emoji"),
        NotificationField("slackchannel", "Channel"),
        NotificationField("slackTemplate", "Message template", multiline = true),
    ),
    "webhook" to listOf(
        NotificationField("webhookURL", "Webhook URL", secret = true),
        NotificationField("httpMethod", "HTTP method"),
        NotificationField("webhookContentType", "Content type"),
        NotificationField("webhookCustomBody", "Custom body", multiline = true),
        NotificationField("webhookAdditionalHeaders", "Additional headers (JSON)", multiline = true),
    ),
    "smtp" to listOf(
        NotificationField("smtpHost", "SMTP host"),
        NotificationField("smtpPort", "SMTP port", numeric = true),
        NotificationField("smtpSecure", "Secure (TLS)", toggle = true),
        NotificationField("smtpIgnoreTLSError", "Ignore TLS error", toggle = true),
        NotificationField("smtpIgnoreSTARTTLS", "Ignore STARTTLS", toggle = true),
        NotificationField("smtpUsername", "Username"),
        NotificationField("smtpPassword", "Password", secret = true),
        NotificationField("smtpFrom", "From"),
        NotificationField("smtpTo", "To"),
        NotificationField("smtpCC", "CC"),
        NotificationField("smtpBCC", "BCC"),
        NotificationField("customSubject", "Custom subject"),
        NotificationField("customBody", "Custom body", multiline = true),
    ),
    "gotify" to listOf(
        NotificationField("gotifyserverurl", "Server URL"),
        NotificationField("gotifyapplicationToken", "Application token", secret = true),
        NotificationField("gotifyPriority", "Priority", numeric = true),
    ),
    "ntfy" to listOf(
        NotificationField("ntfyserverurl", "Server URL"),
        NotificationField("ntfytopic", "Topic"),
        NotificationField("ntfyAuthenticationMethod", "Authentication method"),
        NotificationField("ntfyusername", "Username"),
        NotificationField("ntfypassword", "Password", secret = true),
        NotificationField("ntfyaccesstoken", "Access token", secret = true),
        NotificationField("ntfyPriority", "Priority", numeric = true),
        NotificationField("ntfyIcon", "Icon URL"),
        NotificationField("ntfyCustomTitle", "Custom title"),
        NotificationField("ntfyCustomMessage", "Custom message", multiline = true),
    ),
    "pushover" to listOf(
        NotificationField("pushoveruserkey", "User key", secret = true),
        NotificationField("pushoverapptoken", "Application token", secret = true),
        NotificationField("pushoverdevice", "Device"),
        NotificationField("pushovertitle", "Title"),
        NotificationField("pushoverpriority", "Priority", numeric = true),
        NotificationField("pushoversounds", "Sound"),
        NotificationField("pushoverttl", "TTL", numeric = true),
    ),
    "teams" to listOf(
        NotificationField("webhookUrl", "Webhook URL", secret = true),
    ),
    "mattermost" to listOf(
        NotificationField("mattermostWebhookUrl", "Webhook URL", secret = true),
        NotificationField("mattermostchannel", "Channel"),
        NotificationField("mattermostusername", "Username"),
        NotificationField("mattermosticonemo", "Icon emoji"),
        NotificationField("mattermosticonurl", "Icon URL"),
    ),
    "GoogleChat" to listOf(
        NotificationField("googleChatWebhookURL", "Webhook URL", secret = true),
        NotificationField("googleChatTemplate", "Message template", multiline = true),
    ),
    "apprise" to listOf(
        NotificationField("appriseURL", "Apprise URL", secret = true),
        NotificationField("title", "Title"),
    ),
    "signal" to listOf(
        NotificationField("signalURL", "Signal API URL", secret = true),
        NotificationField("signalNumber", "Sender number"),
        NotificationField("signalRecipients", "Recipients"),
        NotificationField("signalTemplate", "Message template", multiline = true),
    ),
    "twilio" to listOf(
        NotificationField("twilioAccountSID", "Account SID", secret = true),
        NotificationField("twilioAuthToken", "Auth token", secret = true),
        NotificationField("twilioApiKey", "API key", secret = true),
        NotificationField("twilioFromNumber", "From number"),
        NotificationField("twilioToNumber", "To number"),
        NotificationField("twilioMessagingServiceSID", "Messaging service SID"),
    ),
    "matrix" to listOf(
        NotificationField("homeserverUrl", "Homeserver URL"),
        NotificationField("accessToken", "Access token", secret = true),
        NotificationField("internalRoomId", "Room ID"),
        NotificationField("matrixTemplate", "Message template", multiline = true),
    ),
    "rocket.chat" to listOf(
        NotificationField("rocketwebhookURL", "Webhook URL", secret = true),
        NotificationField("rocketchannel", "Channel"),
        NotificationField("rocketusername", "Username"),
        NotificationField("rocketiconemo", "Icon emoji"),
    ),
    "PagerDuty" to listOf(
        NotificationField("pagerdutyIntegrationKey", "Integration key", secret = true),
        NotificationField("pagerdutyIntegrationUrl", "Integration URL"),
        NotificationField("pagerdutyPriority", "Priority"),
        NotificationField("pagerdutyAutoResolve", "Auto resolve", toggle = true),
    ),
    "Opsgenie" to listOf(
        NotificationField("opsgenieApiKey", "API key", secret = true),
        NotificationField("opsgenieRegion", "Region"),
        NotificationField("opsgeniePriority", "Priority"),
    ),
)

private fun emptyNotification(type: String = "telegram"): JSONObject = JSONObject()
    .put("name", "")
    .put("type", type)
    .put("active", true)
    .put("isDefault", false)
    .put("applyExisting", false)

@Composable
fun NotificationsScreen(repository: KumaRepository, onBack: () -> Unit, onEdit: (JSONObject?) -> Unit) {
    val notifications by repository.notifications.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            TopBar("Notifications", subtitle = "${notifications.size} provider${if (notifications.size == 1) "" else "s"}", onBack = onBack, actions = {
                IconButton(onClick = { onEdit(null) }) { Icon(Icons.Default.Add, contentDescription = "Add notification") }
            })
        }
        if (error.isNotBlank()) item { InfoBanner(error, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), KumaColors.Down) }
        if (toast.isNotBlank()) item { InfoBanner(toast, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), KumaColors.Up) }
        if (notifications.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Notifications,
                    title = "No notifications",
                    message = "Add a provider and attach it to monitors to receive alerts.",
                    actionLabel = "Add notification",
                    onAction = { onEdit(null) },
                )
            }
        }
        items(notifications, key = { it.long("id") }) { notification ->
            KumaCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(notification.str("name", "Notification"), style = MaterialTheme.typography.titleMedium)
                        Text(notification.str("type"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            buildString {
                                append(if (notification.bool("active")) "Active" else "Disabled")
                                if (notification.bool("isDefault")) append(" · default")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = notification.bool("active"),
                        onCheckedChange = { enabled ->
                            scope.launch {
                                try {
                                    repository.saveNotification(JSONObject(notification.toString()).put("active", enabled), notification.long("id"))
                                } catch (e: Exception) {
                                    error = e.message ?: "Could not update notification"
                                }
                            }
                        },
                    )
                    IconButton(onClick = { onEdit(notification) }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { deleting = notification }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallActionButton("Test", onClick = {
                        scope.launch {
                            error = ""
                            toast = ""
                            try {
                                toast = repository.testNotification(notification)
                            } catch (e: Exception) {
                                error = e.message ?: "Test failed"
                            }
                        }
                    })
                }
            }
        }
    }

    deleting?.let { notification ->
        ConfirmDialog(
            "Delete notification?",
            "Monitors using “${notification.str("name", "this provider")}” stop sending alerts to it.",
            "Delete",
            destructive = true,
            onDismiss = { deleting = null },
        ) {
            scope.launch {
                try {
                    repository.deleteNotification(notification.long("id"))
                    deleting = null
                } catch (e: Exception) {
                    error = e.message ?: "Could not delete notification"
                    deleting = null
                }
            }
        }
    }
}

@Composable
fun NotificationEditScreen(repository: KumaRepository, draft: String?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val initial = remember(draft) {
        draft?.let { runCatching { JSONObject(it) }.getOrNull() } ?: emptyNotification()
    }
    var config by remember(draft) { mutableStateOf(initial) }
    var type by remember(draft) { mutableStateOf(initial.str("type", "telegram")) }
    var name by remember(draft) { mutableStateOf(initial.str("name")) }
    var active by remember(draft) { mutableStateOf(initial.bool("active", true)) }
    var isDefault by remember(draft) { mutableStateOf(initial.bool("isDefault")) }
    var applyExisting by remember(draft) { mutableStateOf(initial.bool("applyExisting")) }
    var rawMode by remember(draft) { mutableStateOf(notificationFields[initial.str("type")].isNullOrEmpty()) }
    var rawText by remember(draft) { mutableStateOf(initial.toString(2)) }
    var error by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val fields = notificationFields[type].orEmpty()

    fun currentConfig(): JSONObject {
        if (rawMode) {
            val parsed = JSONObject(rawText)
            parsed.put("type", type)
            parsed.put("name", name)
            parsed.put("active", active)
            parsed.put("isDefault", isDefault)
            parsed.put("applyExisting", applyExisting)
            return parsed
        }
        val out = JSONObject(config.toString())
        out.put("type", type)
        out.put("name", name)
        out.put("active", active)
        out.put("isDefault", isDefault)
        out.put("applyExisting", applyExisting)
        return out
    }

    fun setField(key: String, value: Any) {
        config = JSONObject(config.toString()).put(key, value)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TopBar(
                if (draft == null) "New notification" else "Edit notification",
                subtitle = type,
                onBack = onBack,
                actions = {
                    TextButton(
                        enabled = name.isNotBlank() && !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                error = ""
                                toast = ""
                                try {
                                    repository.saveNotification(currentConfig(), initial.optLong("id").takeIf { it > 0 })
                                    onBack()
                                } catch (e: Exception) {
                                    error = e.message ?: "Could not save notification"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) { Text("Save") }
                },
            )
        }
        if (error.isNotBlank()) item { InfoBanner(error, Modifier.fillMaxWidth(), KumaColors.Down) }
        if (toast.isNotBlank()) item { InfoBanner(toast, Modifier.fillMaxWidth(), KumaColors.Up) }
        item {
            FormSection("Provider") {
                Field("Name", name, { name = it })
                PickerField("Type", type, notificationTypes.map { it to it }, {
                    type = it
                    config = JSONObject(config.toString()).put("type", it)
                    rawMode = notificationFields[it].isNullOrEmpty()
                    rawText = config.toString(2)
                })
                SwitchRow("Active", active, { active = it })
                SwitchRow("Default for new monitors", isDefault, { isDefault = it })
                if (draft != null) {
                    SwitchRow("Apply to existing monitors", applyExisting, { applyExisting = it })
                }
            }
        }
        if (fields.isNotEmpty()) {
            item {
                FormSection("Configuration") {
                    if (rawMode) {
                        OutlinedButton(onClick = {
                            rawMode = false
                            config = runCatching { JSONObject(rawText) }.getOrElse { config }
                        }) { Text("Use guided fields") }
                    }
                    fields.forEach { field ->
                        val value = config.str(field.key)
                        if (field.toggle) {
                            SwitchRow(field.label, config.bool(field.key), { setField(field.key, it) })
                        } else if (field.secret) {
                            PasswordField(field.label, value, { setField(field.key, it) })
                        } else {
                            Field(
                                field.label,
                                value,
                                {
                                    setField(
                                        field.key,
                                        if (field.numeric) it.filter(Char::isDigit).toIntOrNull() ?: 0 else it,
                                    )
                                },
                                singleLine = !field.multiline,
                                keyboardType = if (field.numeric) KeyboardType.Number else KeyboardType.Text,
                            )
                        }
                    }
                    OutlinedButton(onClick = {
                        rawText = currentConfig().toString(2)
                        rawMode = true
                    }) { Text("Edit raw JSON") }
                }
            }
        }
        if (rawMode) {
            item {
                FormSection("Raw JSON") {
                    Text(
                        "All provider fields are supported here. Use the exact field names accepted by the server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Field(
                        "Configuration", rawText, { rawText = it },
                        singleLine = false,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = ""
                            toast = ""
                            try {
                                toast = repository.testNotification(currentConfig())
                            } catch (e: Exception) {
                                error = e.message ?: "Test failed"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(if (busy) "Working…" else "Send test") }
                Button(
                    enabled = name.isNotBlank() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = ""
                            try {
                                repository.saveNotification(currentConfig(), initial.optLong("id").takeIf { it > 0 })
                                onBack()
                            } catch (e: Exception) {
                                error = e.message ?: "Could not save notification"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(if (busy) "Saving…" else "Save") }
            }
        }
        if (fields.isEmpty() && !rawMode) {
            item { InfoBanner("This provider uses the raw JSON editor.", Modifier.fillMaxWidth(), KumaColors.Pending) }
        }
    }
}
