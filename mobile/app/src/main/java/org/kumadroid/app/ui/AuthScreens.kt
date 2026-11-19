package org.kumadroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.kumadroid.app.AuthHero
import org.kumadroid.app.BrandMark
import org.kumadroid.app.data.ConnectionStatus
import org.kumadroid.app.data.KumaRepository

@Composable
fun ServerScreen(repository: KumaRepository, onConnected: () -> Unit) {
    var url by remember { mutableStateOf(repository.serverUrl) }
    var insecure by remember { mutableStateOf(repository.allowInsecureTls) }
    var error by remember { mutableStateOf("") }
    val status by repository.status.collectAsStateWithLifecycle()
    val statusMessage by repository.statusMessage.collectAsStateWithLifecycle()

    LaunchedEffect(status) {
        if (status == ConnectionStatus.LoggedOut || status == ConnectionStatus.Connected) onConnected()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AuthHero(
            "KumaDroid",
            "A native client for Uptime Kuma. Connect to your self-hosted instance to monitor everything from your phone.",
        )
        Spacer(Modifier.height(18.dp))
        Field(
            label = "Server URL",
            value = url,
            onValueChange = {
                url = it
                error = ""
            },
            placeholder = "https://kuma.example.com",
            keyboardType = KeyboardType.Uri,
            supporting = "Include http:// or https:// and the port if needed.",
        )
        Spacer(Modifier.height(12.dp))
        KumaCard {
            SwitchRow(
                title = "Allow insecure TLS",
                subtitle = "Accept self-signed certificates. Only use this for servers you trust.",
                checked = insecure,
                onCheckedChange = { insecure = it },
            )
        }
        if (error.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            InfoBanner(error, tone = KumaColors.Down)
        }
        if (status == ConnectionStatus.Error && statusMessage.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            InfoBanner(statusMessage, tone = KumaColors.Down)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (url.isBlank()) {
                    error = "Enter the address of your Uptime Kuma server."
                } else {
                    repository.connectServer(url, insecure)
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (status == ConnectionStatus.Connecting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Connect")
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "KumaDroid talks to the same Socket.IO API as the Uptime Kuma web dashboard. Your credentials stay on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ConnectingScreen(repository: KumaRepository, message: String, onEditServer: () -> Unit) {
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(84)
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text("Connecting", style = MaterialTheme.typography.titleLarge)
        Text(
            message.ifBlank { repository.serverUrl },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(18.dp))
        TextButton(onClick = { repository.connectServer(repository.serverUrl, repository.allowInsecureTls) }) {
            Text("Retry")
        }
        TextButton(onClick = onEditServer) { Text("Change server") }
    }
}

@Composable
fun LoginScreen(repository: KumaRepository, onEditServer: () -> Unit) {
    var username by remember { mutableStateOf(repository.username.value) }
    var password by remember { mutableStateOf("") }
    var totp by remember { mutableStateOf("") }
    var needsTotp by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val status by repository.status.collectAsStateWithLifecycle()
    val statusMessage by repository.statusMessage.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AuthHero("Welcome back", "Sign in to ${repository.serverUrl.removePrefix("https://").removePrefix("http://")}")
        Spacer(Modifier.height(8.dp))
        if (status == ConnectionStatus.Error) {
            InfoBanner(statusMessage.ifBlank { "Cannot reach the server." }, tone = KumaColors.Down)
            Spacer(Modifier.height(12.dp))
        }
        KumaCard {
            Field(
                label = "Username",
                value = username,
                onValueChange = { username = it; error = "" },
                keyboardType = KeyboardType.Text,
            )
            PasswordField(
                label = "Password",
                value = password,
                onValueChange = { password = it; error = "" },
            )
            if (needsTotp) {
                Field(
                    label = "Two-factor code",
                    value = totp,
                    onValueChange = { totp = it; error = "" },
                    placeholder = "123456",
                    keyboardType = KeyboardType.Number,
                    supporting = "Enter the 6-digit code from your authenticator app, or a backup code.",
                )
            }
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        error = "Enter your username and password."
                        return@Button
                    }
                    if (needsTotp && totp.isBlank()) {
                        error = "Enter your two-factor code."
                        return@Button
                    }
                    loading = true
                    error = ""
                    scope.launch {
                        try {
                            val ok = repository.login(username, password, totp.takeIf { needsTotp })
                            if (!ok) {
                                needsTotp = true
                                error = "Two-factor authentication is enabled for this account."
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Login failed"
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Sign in")
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onEditServer) { Text("Change server") }
            Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { error = ""; repository.connectServer(repository.serverUrl, repository.allowInsecureTls) }) {
                Text("Reconnect")
            }
        }
        Text(
            "Connected to ${repository.serverUrl}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SetupScreen(repository: KumaRepository) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AuthHero(
            "Set up KumaDroid",
            "This Uptime Kuma instance has no administrator yet. Create the first account to continue.",
        )
        Spacer(Modifier.height(10.dp))
        KumaCard {
            Field("Username", username, { username = it; error = "" })
            PasswordField("Password", password, { password = it; error = "" })
            PasswordField("Confirm password", confirm, { confirm = it; error = "" })
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    when {
                        username.isBlank() || password.isBlank() -> error = "Username and password are required."
                        password != confirm -> error = "Passwords do not match."
                        password.length < 6 -> error = "Use a password with at least 6 characters."
                        else -> {
                            loading = true
                            scope.launch {
                                try {
                                    repository.setup(username, password)
                                } catch (e: Exception) {
                                    error = e.message ?: "Setup failed"
                                } finally {
                                    loading = false
                                }
                            }
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Create administrator")
            }
        }
    }
}
