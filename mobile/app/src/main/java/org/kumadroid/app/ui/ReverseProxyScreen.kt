package org.kumadroid.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kumadroid.app.data.KumaRepository
import org.kumadroid.app.data.bool

@Composable
fun ReverseProxyScreen(repository: KumaRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val installed by repository.cloudflaredInstalled.collectAsStateWithLifecycle()
    val running by repository.cloudflaredRunning.collectAsStateWithLifecycle()
    val message by repository.cloudflaredMessage.collectAsStateWithLifecycle()
    val serverError by repository.cloudflaredError.collectAsStateWithLifecycle()
    val savedToken by repository.cloudflaredToken.collectAsStateWithLifecycle()
    val settings by repository.settings.collectAsStateWithLifecycle()

    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var askPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    val disabledAuth = settings?.bool("disableAuth") == true

    LaunchedEffect(savedToken) {
        // Do not replace a token the user is currently typing when the server pushes an update.
        if (token.isBlank()) token = savedToken
    }

    LaunchedEffect(Unit) {
        try {
            repository.joinCloudflared()
        } catch (e: Exception) {
            error = e.message ?: "Could not load Cloudflare Tunnel status"
        }
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { repository.leaveCloudflared() }
        }
    }

    fun start() {
        busy = true
        error = ""
        scope.launch {
            try {
                repository.startCloudflared(token.trim())
            } catch (e: Exception) {
                error = e.message ?: "Could not start cloudflared"
            } finally {
                busy = false
            }
        }
    }

    fun stop(secret: String) {
        busy = true
        error = ""
        scope.launch {
            try {
                repository.stopCloudflared(secret)
            } catch (e: Exception) {
                error = e.message ?: "Could not stop cloudflared"
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("Reverse proxy", "Cloudflare Tunnel", onBack = onBack)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            KumaCard {
                InfoRow("cloudflared", if (installed) "Installed" else "Not installed")
                InfoRow("Status", if (running) "Running" else "Not running")
                if (!installed) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Install cloudflared on the Uptime Kuma host, then reopen this screen. " +
                            "The web interface has the installation guide.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (installed) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                KumaCard {
                    PasswordField(
                        "Tunnel token",
                        token,
                        { token = it },
                        placeholder = "eyJhIjoi…",
                        supporting = if (running) "Stop the tunnel to change the token." else "Token from the Cloudflare Zero Trust dashboard.",
                        enabled = !running,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { if (running) askPassword = true else start() },
                        enabled = !busy && (running || token.isNotBlank()),
                    ) {
                        Text(if (running) "Stop cloudflared" else "Start cloudflared")
                    }
                    if (savedToken.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                busy = true
                                error = ""
                                scope.launch {
                                    try {
                                        repository.removeCloudflaredToken()
                                        if (token == savedToken) token = ""
                                    } catch (e: Exception) {
                                        error = e.message ?: "Could not remove token"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !running && !busy,
                        ) { Text("Remove saved token") }
                    }
                }
            }
        }
        if (error.isNotBlank()) {
            InfoBanner(error, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), KumaColors.Down)
        }
        if (serverError.isNotBlank()) {
            InfoBanner(serverError, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), KumaColors.Down)
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            KumaCard {
                Text("HTTP headers", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Trust proxy is configured in General settings. Enable it when Uptime Kuma runs behind nginx, Traefik, Cloudflare or another reverse proxy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (askPassword) {
        var dialogError by remember { mutableStateOf("") }
        AlertInputDialog(
            title = "Stop cloudflared?",
            message = if (disabledAuth) {
                "The current tunnel connection will be closed."
            } else {
                "The current connection may be lost if you use this tunnel. Enter your password to stop it."
            },
            value = password,
            onValueChange = { password = it },
            password = !disabledAuth,
            error = dialogError,
            confirmLabel = "Stop",
            onDismiss = { askPassword = false; password = "" },
            onConfirm = {
                if (!disabledAuth && password.isBlank()) {
                    dialogError = "Password is required"
                } else {
                    val secret = password
                    askPassword = false
                    password = ""
                    stop(secret)
                }
            },
        )
    }
}

@Composable
private fun AlertInputDialog(
    title: String,
    message: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean,
    error: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                if (password) {
                    PasswordField("Current password", value, onValueChange)
                }
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
