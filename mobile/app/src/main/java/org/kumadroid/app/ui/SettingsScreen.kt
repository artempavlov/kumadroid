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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kumadroid.app.BrandMark
import org.kumadroid.app.data.ConnectionStatus
import org.kumadroid.app.data.KumaRepository
import org.kumadroid.app.data.bool
import org.kumadroid.app.data.str
import org.kumadroid.app.ui.Screen

@Composable
fun SettingsScreen(
    repository: KumaRepository,
    onNavigate: (Screen) -> Unit,
    onLogout: () -> Unit,
) {
    val server by repository.serverInfo.collectAsStateWithLifecycle()
    val status by repository.status.collectAsStateWithLifecycle()
    val username by repository.username.collectAsStateWithLifecycle()
    val baseUrl = repository.serverUrl
    var logoutConfirm by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item { PageTitle("Settings", "KumaDroid · ${server.version.ifBlank { "Uptime Kuma" }}") }
        item {
            KumaCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = KumaColors.Brand.copy(alpha = 0.16f),
                    ) {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = null,
                            tint = KumaColors.Brand,
                            modifier = Modifier.padding(12.dp).size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(baseUrl.ifBlank { "Not connected" }, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            buildString {
                                append(if (status == ConnectionStatus.Connected) "Connected" else status.name)
                                if (username.isNotBlank()) append(" · $username")
                                if (server.version.isNotBlank()) append(" · v${server.version}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusDot(if (status == ConnectionStatus.Connected) 1 else 2)
                }
                val isContainer = server.raw.bool("isContainer")
                if (isContainer) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Docker container · ${server.raw.str("nodeVersion")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SettingsGroup("Monitors") }
        item {
            SettingsRow(Icons.Default.Sell, "Tags", "Labels shared by monitors") { onNavigate(Screen.Tags) }
            SettingsRow(Icons.Default.Notifications, "Notifications", "Notification providers and defaults") { onNavigate(Screen.Notifications) }
        }

        item { SettingsGroup("App") }
        item {
            SettingsRow(Icons.Default.Palette, "Appearance", "Theme, heartbeat bars and elapsed time") { onNavigate(Screen.Appearance) }
        }

        item { SettingsGroup("Integrations") }
        item {
            SettingsRow(Icons.Default.Dns, "Docker hosts", "Remote Docker daemons") { onNavigate(Screen.DockerHosts) }
            SettingsRow(Icons.Default.Language, "Proxies", "HTTP and SOCKS proxies") { onNavigate(Screen.Proxies) }
            SettingsRow(Icons.Default.TravelExplore, "Remote browsers", "Browsers used by real-browser checks") { onNavigate(Screen.RemoteBrowsers) }
            SettingsRow(Icons.Default.Cloud, "Reverse proxy", "Cloudflare Tunnel and proxy guidance") { onNavigate(Screen.ReverseProxy) }
        }

        item { SettingsGroup("Account & server") }
        item {
            SettingsRow(Icons.Default.Settings, "General", "Server name, timezone, entry page and retention") { onNavigate(Screen.GeneralSettings) }
            SettingsRow(Icons.Default.Security, "Security", "Password, two-factor authentication and API keys") { onNavigate(Screen.SecuritySettings) }
            SettingsRow(Icons.Default.Storage, "Data", "Database size, maintenance and backups") { onNavigate(Screen.DataSettings) }
            SettingsRow(Icons.Default.Api, "API keys", "Keys for the push and badge endpoints") { onNavigate(Screen.ApiKeys) }
        }

        item { SettingsGroup("About") }
        item {
            SettingsRow(Icons.Default.Info, "About KumaDroid", "Version, uptime and open-source licenses") { onNavigate(Screen.About) }
            SettingsRow(Icons.AutoMirrored.Filled.Logout, "Sign out", "Forget the saved session", tint = MaterialTheme.colorScheme.error) {
                logoutConfirm = true
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMark(size = 42)
                Spacer(Modifier.height(8.dp))
                Text("KumaDroid", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Native Android client for Uptime Kuma",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Uptime Kuma is © Louis Lam and contributors",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (logoutConfirm) {
        ConfirmDialog(
            title = "Sign out?",
            message = "The saved token is removed from this device. Your Uptime Kuma server is not changed.",
            confirmLabel = "Sign out",
            destructive = true,
            onDismiss = { logoutConfirm = false },
        ) {
            logoutConfirm = false
            onLogout()
        }
    }
}

@Composable
private fun SettingsGroup(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 22.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = if (tint == MaterialTheme.colorScheme.onSurface) KumaColors.Brand else tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
