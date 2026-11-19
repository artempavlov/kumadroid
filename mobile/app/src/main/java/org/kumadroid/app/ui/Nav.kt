package org.kumadroid.app.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import org.json.JSONArray
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

/** Keeps the navigation stack across configuration changes and process restarts. */
val NavigatorSaver: Saver<Navigator, String> = Saver(
    save = { navigator ->
        val array = JSONArray()
        navigator.stack.forEach { array.put(it.toJson()) }
        array.toString()
    },
    restore = { encoded ->
        runCatching {
            val array = JSONArray(encoded)
            val navigator = Navigator()
            navigator.stack.clear()
            for (index in 0 until array.length()) {
                val screen = array.optJSONObject(index)?.toScreen()
                if (screen != null) navigator.stack.add(screen)
            }
            if (navigator.stack.isEmpty()) navigator.stack.add(Screen.Dashboard)
            navigator
        }.getOrNull()
    },
)

private fun Screen.toJson(): JSONObject = JSONObject().apply {
    when (this@toJson) {
        Screen.Dashboard -> put("kind", "dashboard")
        Screen.StatusPages -> put("kind", "statusPages")
        Screen.Maintenances -> put("kind", "maintenances")
        Screen.Settings -> put("kind", "settings")
        Screen.Tags -> put("kind", "tags")
        Screen.Notifications -> put("kind", "notifications")
        Screen.ApiKeys -> put("kind", "apiKeys")
        Screen.Appearance -> put("kind", "appearance")
        Screen.DockerHosts -> put("kind", "dockerHosts")
        Screen.Proxies -> put("kind", "proxies")
        Screen.ReverseProxy -> put("kind", "reverseProxy")
        Screen.RemoteBrowsers -> put("kind", "remoteBrowsers")
        Screen.GeneralSettings -> put("kind", "generalSettings")
        Screen.SecuritySettings -> put("kind", "securitySettings")
        Screen.DataSettings -> put("kind", "dataSettings")
        Screen.About -> put("kind", "about")
        is Screen.MonitorDetail -> put("kind", "monitorDetail").put("id", id)
        is Screen.MonitorEdit -> put("kind", "monitorEdit").apply {
            id?.let { put("id", it) }
            parent?.let { put("parent", it) }
            cloneFrom?.let { put("cloneFrom", it) }
        }
        is Screen.MonitorEvents -> put("kind", "monitorEvents").put("id", id).put("important", important)
        is Screen.NotificationEdit -> put("kind", "notificationEdit").apply {
            draft?.let { put("draft", it) }
        }
        is Screen.StatusPageEdit -> put("kind", "statusPageEdit").put("slug", slug)
        is Screen.PublicStatusPage -> put("kind", "publicStatusPage").put("slug", slug)
        is Screen.Incidents -> put("kind", "incidents").put("slug", slug)
        is Screen.MaintenanceEdit -> put("kind", "maintenanceEdit").apply {
            id?.let { put("id", it) }
            cloneFrom?.let { put("cloneFrom", it) }
        }
    }
}

private fun JSONObject.toScreen(): Screen? = when (optString("kind")) {
    "dashboard" -> Screen.Dashboard
    "statusPages" -> Screen.StatusPages
    "maintenances" -> Screen.Maintenances
    "settings" -> Screen.Settings
    "tags" -> Screen.Tags
    "notifications" -> Screen.Notifications
    "apiKeys" -> Screen.ApiKeys
    "appearance" -> Screen.Appearance
    "dockerHosts" -> Screen.DockerHosts
    "proxies" -> Screen.Proxies
    "reverseProxy" -> Screen.ReverseProxy
    "remoteBrowsers" -> Screen.RemoteBrowsers
    "generalSettings" -> Screen.GeneralSettings
    "securitySettings" -> Screen.SecuritySettings
    "dataSettings" -> Screen.DataSettings
    "about" -> Screen.About
    "monitorDetail" -> Screen.MonitorDetail(optLong("id"))
    "monitorEdit" -> Screen.MonitorEdit(
        id = optionalLong("id"),
        parent = optionalLong("parent"),
        cloneFrom = optionalLong("cloneFrom"),
    )
    "monitorEvents" -> Screen.MonitorEvents(optLong("id"), optBoolean("important"))
    "notificationEdit" -> Screen.NotificationEdit(if (has("draft")) optString("draft") else null)
    "statusPageEdit" -> Screen.StatusPageEdit(optString("slug"))
    "publicStatusPage" -> Screen.PublicStatusPage(optString("slug"))
    "incidents" -> Screen.Incidents(optString("slug"))
    "maintenanceEdit" -> Screen.MaintenanceEdit(
        id = optionalLong("id"),
        cloneFrom = optionalLong("cloneFrom"),
    )
    else -> null
}

private fun JSONObject.optionalLong(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key) else null

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
