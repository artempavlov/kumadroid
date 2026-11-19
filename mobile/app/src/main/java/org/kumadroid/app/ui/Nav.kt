package org.kumadroid.app.ui

import androidx.compose.runtime.mutableStateListOf
import org.json.JSONObject

sealed class Screen {
    data object Dashboard : Screen()
    data object StatusPages : Screen()
    data object Maintenances : Screen()
    data object Settings : Screen()

    data class MonitorDetail(val id: Long) : Screen()
    data class MonitorEdit(val id: Long? = null, val parent: Long? = null, val cloneFrom: Long? = null) : Screen()
    data class MonitorEvents(val id: Long, val important: Boolean) : Screen()

    data object Tags : Screen()
    data object Notifications : Screen()
    data class NotificationEdit(val draft: String? = null) : Screen()
    data object ApiKeys : Screen()
    data object Appearance : Screen()
    data object DockerHosts : Screen()
    data object Proxies : Screen()
    data object ReverseProxy : Screen()
    data object RemoteBrowsers : Screen()
    data object GeneralSettings : Screen()
    data object SecuritySettings : Screen()
    data object DataSettings : Screen()
    data object About : Screen()

    data class StatusPageEdit(val slug: String) : Screen()
    data class PublicStatusPage(val slug: String) : Screen()
    data class Incidents(val slug: String) : Screen()

    data class MaintenanceEdit(val id: Long? = null, val cloneFrom: Long? = null) : Screen()
}

val tabScreens = setOf<Screen>(Screen.Dashboard, Screen.StatusPages, Screen.Maintenances, Screen.Settings)

class Navigator(start: Screen = Screen.Dashboard) {
    val stack = mutableStateListOf<Screen>(start)
    val current: Screen get() = stack.last()

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun replace(screen: Screen) {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        stack[0] = screen
    }

    fun replaceTop(screen: Screen) {
        if (stack.isEmpty()) {
            stack.add(screen)
        } else {
            stack[stack.lastIndex] = screen
        }
    }

    fun tab(screen: Screen) {
        stack.clear()
        stack.add(screen)
    }

    fun reset() {
        stack.clear()
        stack.add(Screen.Dashboard)
    }
}

fun notificationDraft(json: JSONObject?): String? = json?.toString()

fun parseDraft(value: String?): JSONObject? = value?.let {
    runCatching { JSONObject(it) }.getOrNull()
}

fun humanInterval(seconds: Int): String {
    if (seconds <= 0) return "—"
    return when {
        seconds < 60 -> "${seconds}s"
        seconds % 3600 == 0 -> "${seconds / 3600}h"
        seconds % 60 == 0 -> "${seconds / 60}m"
        else -> "${seconds}s"
    }
}

fun beatAgo(millis: Long): String {
    if (millis <= 0) return "never"
    val diff = (System.currentTimeMillis() - millis).coerceAtLeast(0)
    return when {
        diff < 60_000 -> "${diff / 1000}s ago"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}

fun compactNumber(value: Long): String = when {
    value >= 1_000_000_000 -> String.format("%.1f GB", value / 1_000_000_000.0)
    value >= 1_000_000 -> String.format("%.1f MB", value / 1_000_000.0)
    value >= 1_000 -> String.format("%.1f KB", value / 1_000.0)
    else -> "$value B"
}
