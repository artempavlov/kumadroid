package org.kumadroid.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kumadroid.app.data.KumaRepository

@Composable
fun AppearanceScreen(repository: KumaRepository, onBack: () -> Unit) {
    val theme by repository.appTheme.collectAsStateWithLifecycle()
    val barStyle by repository.heartbeatBarStyle.collectAsStateWithLifecycle()
    val elapsedStyle by repository.elapsedTimeStyle.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            TopBar(
                "Appearance",
                subtitle = "Saved on this device",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        repository.setAppTheme("auto")
                        repository.setHeartbeatBarStyle("normal")
                        repository.setElapsedTimeStyle("with-line")
                    }) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset appearance")
                    }
                },
            )
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FormSection("Theme", "Choose how KumaDroid looks. Auto follows the Android system setting.") {
                    PickerField(
                        "Color theme",
                        theme,
                        listOf("auto" to "Auto", "light" to "Light", "dark" to "Dark"),
                        { repository.setAppTheme(it) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                FormSection("Heartbeat bar", "The compact history strip shown for each monitor.") {
                    PickerField(
                        "Position",
                        barStyle,
                        listOf(
                            "normal" to "Normal — above status details",
                            "bottom" to "Bottom — below status details",
                            "none" to "None — hide heartbeat bars",
                        ),
                        { repository.setHeartbeatBarStyle(it) },
                    )
                    Text(
                        "Hidden bars are still recorded on the server; only this device hides them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                FormSection("Elapsed time", "How last-check age is separated from the monitor status.") {
                    PickerField(
                        "Style",
                        elapsedStyle,
                        listOf(
                            "with-line" to "With separator — 12s ago",
                            "no-line" to "No separator — 12s ago",
                            "none" to "Hide elapsed time",
                        ),
                        { repository.setElapsedTimeStyle(it) },
                    )
                }
            }
        }
    }
}
