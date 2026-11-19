package org.kumadroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.kumadroid.app.data.ConnectionStatus
import org.kumadroid.app.data.KumaRepository
import org.kumadroid.app.ui.ConnectingScreen
import org.kumadroid.app.ui.DashboardScreen
import org.kumadroid.app.ui.KumaColors
import org.kumadroid.app.ui.KumaDroidTheme
import org.kumadroid.app.ui.LoginScreen
import org.kumadroid.app.ui.MaintenanceEditScreen
import org.kumadroid.app.ui.MaintenancesScreen
import org.kumadroid.app.ui.MonitorDetailScreen
import org.kumadroid.app.ui.MonitorEditScreen
import org.kumadroid.app.ui.MonitorEventsScreen
import org.kumadroid.app.ui.Navigator
import org.kumadroid.app.ui.NavigatorSaver
import org.kumadroid.app.ui.NotificationEditScreen
import org.kumadroid.app.ui.NotificationsScreen
import org.kumadroid.app.ui.PublicStatusPageScreen
import org.kumadroid.app.ui.ReverseProxyScreen
import org.kumadroid.app.ui.Screen
import org.kumadroid.app.ui.ServerScreen
import org.kumadroid.app.ui.SettingsScreen
import org.kumadroid.app.ui.SetupScreen
import org.kumadroid.app.ui.StatusPageEditScreen
import org.kumadroid.app.ui.StatusPagesScreen
import org.kumadroid.app.ui.SubSettingsScreen
import org.kumadroid.app.ui.AppearanceScreen
import org.kumadroid.app.ui.TagsScreen
import org.kumadroid.app.ui.IncidentsScreen
import org.kumadroid.app.ui.tabScreens

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val repository = remember { KumaRepository(context.applicationContext) }
            DisposableEffect(repository) {
                onDispose { repository.close() }
            }
            val theme by repository.appTheme.collectAsStateWithLifecycle()
            val darkTheme = when (theme) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            KumaDroidTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    KumaApp(repository)
                }
            }
        }
    }
}

@Composable
private fun KumaApp(repository: KumaRepository) {
    val snackbar = remember { SnackbarHostState() }
    val navigator = rememberSaveable(saver = NavigatorSaver) { Navigator() }

    val status by repository.status.collectAsStateWithLifecycle()
    val statusMessage by repository.statusMessage.collectAsStateWithLifecycle()
    val loggedIn by repository.loggedIn.collectAsStateWithLifecycle()
    val needSetup by repository.needSetup.collectAsStateWithLifecycle()
    var editingServer by rememberSaveable { mutableStateOf(repository.serverUrl.isBlank()) }

    LaunchedEffect(repository) {
        // Start collecting before bootstrap: an invalid URL reports its error
        // synchronously, and that message must not be lost.
        launch { repository.messages.collect { snackbar.showSnackbar(it) } }
        repository.bootstrap()
    }

    if (loggedIn) {
        MainShell(repository, navigator, snackbar)
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                editingServer || repository.serverUrl.isBlank() -> ServerScreen(
                    repository = repository,
                    onConnected = { editingServer = false },
                )
                needSetup -> SetupScreen(repository)
                status == ConnectionStatus.Connecting ||
                    status == ConnectionStatus.Error ||
                    (status == ConnectionStatus.Idle && repository.serverUrl.isNotBlank()) -> ConnectingScreen(
                    repository = repository,
                    message = statusMessage,
                    onEditServer = {
                        repository.disconnect()
                        editingServer = true
                    },
                )
                else -> LoginScreen(
                    repository = repository,
                    onEditServer = {
                        repository.disconnect()
                        editingServer = true
                    },
                )
            }
        }
    }
}

@Composable
private fun MainShell(repository: KumaRepository, navigator: Navigator, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val status by repository.status.collectAsStateWithLifecycle()
    val current = navigator.current
    val showTabs = current in tabScreens
    val offline = status == ConnectionStatus.Error || status == ConnectionStatus.Connecting

    BackHandler(enabled = navigator.stack.size > 1) { navigator.pop() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showTabs) {
                KumaBottomBar(current) { target -> navigator.tab(target) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (offline) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(KumaColors.Pending.copy(alpha = 0.14f))
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = KumaColors.Pending)
                    Text(
                        if (status == ConnectionStatus.Connecting) "Connecting to server…" else "Reconnecting to server…",
                        style = MaterialTheme.typography.labelSmall,
                        color = KumaColors.Pending,
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                when (current) {
                    Screen.Dashboard -> DashboardScreen(
                        repository = repository,
                        onOpenMonitor = { navigator.push(Screen.MonitorDetail(it)) },
                        onAddMonitor = { navigator.push(Screen.MonitorEdit()) },
                        onEditMonitor = { navigator.push(Screen.MonitorEdit(it)) },
                        onOpenSettings = { navigator.tab(Screen.Settings) },
                        onOpenTags = { navigator.push(Screen.Tags) },
                    )
                    Screen.StatusPages -> StatusPagesScreen(
                        repository = repository,
                        onOpen = { navigator.push(Screen.StatusPageEdit(it)) },
                        onPreview = { navigator.push(Screen.PublicStatusPage(it)) },
                        onIncidents = { navigator.push(Screen.Incidents(it)) },
                    )
                    Screen.Maintenances -> MaintenancesScreen(
                        repository = repository,
                        onOpen = { navigator.push(Screen.MaintenanceEdit(it)) },
                        onAdd = { navigator.push(Screen.MaintenanceEdit()) },
                        onClone = { navigator.push(Screen.MaintenanceEdit(cloneFrom = it)) },
                    )
                    Screen.Settings -> SettingsScreen(
                        repository = repository,
                        onNavigate = { navigator.push(it) },
                        onLogout = {
                            scope.launch { repository.logout() }
                            navigator.reset()
                        },
                    )
                    is Screen.MonitorDetail -> MonitorDetailScreen(
                        repository = repository,
                        monitorId = (current as Screen.MonitorDetail).id,
                        onBack = { navigator.pop() },
                        onEdit = { navigator.push(Screen.MonitorEdit((current as Screen.MonitorDetail).id)) },
                        onEvents = { navigator.push(Screen.MonitorEvents((current as Screen.MonitorDetail).id, false)) },
                        onImportant = { navigator.push(Screen.MonitorEvents((current as Screen.MonitorDetail).id, true)) },
                        onClone = { navigator.push(Screen.MonitorEdit(cloneFrom = (current as Screen.MonitorDetail).id)) },
                        onDeleted = { navigator.pop() },
                    )
                    is Screen.MonitorEdit -> MonitorEditScreen(
                        repository = repository,
                        monitorId = (current as Screen.MonitorEdit).id,
                        parentId = (current as Screen.MonitorEdit).parent,
                        cloneFromId = (current as Screen.MonitorEdit).cloneFrom,
                        onBack = { navigator.pop() },
                        onSaved = { id ->
                            if ((current as Screen.MonitorEdit).id != null) {
                                navigator.pop()
                            } else {
                                navigator.replaceTop(Screen.MonitorDetail(id))
                            }
                        },
                    )
                    is Screen.MonitorEvents -> MonitorEventsScreen(
                        repository = repository,
                        monitorId = (current as Screen.MonitorEvents).id,
                        important = (current as Screen.MonitorEvents).important,
                        onBack = { navigator.pop() },
                    )
                    Screen.Tags -> TagsScreen(repository, onBack = { navigator.pop() })
                    Screen.Notifications -> NotificationsScreen(
                        repository = repository,
                        onBack = { navigator.pop() },
                        onEdit = { navigator.push(Screen.NotificationEdit(it?.toString())) },
                    )
                    is Screen.NotificationEdit -> NotificationEditScreen(
                        repository = repository,
                        draft = (current as Screen.NotificationEdit).draft,
                        onBack = { navigator.pop() },
                    )
                    Screen.ApiKeys -> SubSettingsScreen(repository, SubSettingsScreen.Kind.ApiKeys, { navigator.pop() })
                    Screen.Appearance -> AppearanceScreen(repository, onBack = { navigator.pop() })
                    Screen.DockerHosts -> SubSettingsScreen(repository, SubSettingsScreen.Kind.DockerHosts, { navigator.pop() })
                    Screen.Proxies -> SubSettingsScreen(repository, SubSettingsScreen.Kind.Proxies, { navigator.pop() })
                    Screen.ReverseProxy -> ReverseProxyScreen(repository, onBack = { navigator.pop() })
                    Screen.RemoteBrowsers -> SubSettingsScreen(repository, SubSettingsScreen.Kind.RemoteBrowsers, { navigator.pop() })
                    Screen.GeneralSettings -> SubSettingsScreen(repository, SubSettingsScreen.Kind.General, { navigator.pop() })
                    Screen.SecuritySettings -> SubSettingsScreen(repository, SubSettingsScreen.Kind.Security, { navigator.pop() })
                    Screen.DataSettings -> SubSettingsScreen(repository, SubSettingsScreen.Kind.Data, { navigator.pop() })
                    Screen.About -> SubSettingsScreen(repository, SubSettingsScreen.Kind.About, { navigator.pop() })
                    is Screen.StatusPageEdit -> StatusPageEditScreen(
                        repository = repository,
                        slug = (current as Screen.StatusPageEdit).slug,
                        onBack = { navigator.pop() },
                        onPreview = { navigator.push(Screen.PublicStatusPage((current as Screen.StatusPageEdit).slug)) },
                        onIncidents = { navigator.push(Screen.Incidents((current as Screen.StatusPageEdit).slug)) },
                    )
                    is Screen.PublicStatusPage -> PublicStatusPageScreen(
                        repository = repository,
                        slug = (current as Screen.PublicStatusPage).slug,
                        onBack = { navigator.pop() },
                        onIncidents = { navigator.push(Screen.Incidents((current as Screen.PublicStatusPage).slug)) },
                    )
                    is Screen.Incidents -> IncidentsScreen(
                        repository = repository,
                        slug = (current as Screen.Incidents).slug,
                        onBack = { navigator.pop() },
                    )
                    is Screen.MaintenanceEdit -> MaintenanceEditScreen(
                        repository = repository,
                        maintenanceId = (current as Screen.MaintenanceEdit).id,
                        cloneFromId = (current as Screen.MaintenanceEdit).cloneFrom,
                        onBack = { navigator.pop() },
                    )
                }
            }
        }
    }
}

@Composable
private fun KumaBottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    val items = listOf(
        Triple(Screen.Dashboard, "Monitors", Icons.Default.Dashboard),
        Triple(Screen.StatusPages, "Status", Icons.Default.Public),
        Triple(Screen.Maintenances, "Maintenance", Icons.Default.Build),
        Triple(Screen.Settings, "Settings", Icons.Default.Settings),
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        items.forEach { (screen, label, icon) ->
            NavigationBarItem(
                selected = current == screen,
                onClick = { onSelect(screen) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

@Composable
fun BrandMark(size: Int = 74) {
    Box(
        Modifier
            .size(size.dp)
            .background(
                Brush.linearGradient(listOf(KumaColors.Brand, Color(0xFF2BB673))),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text("K", style = MaterialTheme.typography.displaySmall, color = Color(0xFF07240F))
    }
}

@Composable
fun AuthHero(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark()
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp),
        )
    }
}
