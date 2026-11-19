package org.kumadroid.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/*
 * Uptime Kuma speaks JSON with a lot of optional, monitor-type specific fields.
 * Keeping the original object around means every field supported by the server
 * survives an edit round-trip, including fields this app does not render yet.
 */

fun JSONObject.str(key: String, def: String = ""): String {
    if (!has(key) || isNull(key)) return def
    return optString(key, def)
}

fun JSONObject.num(key: String, def: Int = 0): Int {
    if (!has(key) || isNull(key)) return def
    return optInt(key, def)
}

fun JSONObject.long(key: String, def: Long = 0L): Long {
    if (!has(key) || isNull(key)) return def
    return optLong(key, def)
}

fun JSONObject.dbl(key: String, def: Double? = null): Double? {
    if (!has(key) || isNull(key)) return def
    return optDouble(key, def ?: 0.0)
}

fun JSONObject.bool(key: String, def: Boolean = false): Boolean {
    if (!has(key) || isNull(key)) return def
    // Uptime Kuma/RedBean often serialises SQLite booleans as 1/0.
    return when (val value = opt(key)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value == "1" || value.equals("true", ignoreCase = true)
        else -> def
    }
}

fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)
fun JSONObject.arr(key: String): JSONArray? = optJSONArray(key)

/** Deep copy, used so edit forms never mutate live state. */
fun JSONObject.deepCopy(): JSONObject = JSONObject(toString())

data class Monitor(
    val id: Long,
    val json: JSONObject,
    val revision: Long = 0L,
) {
    val name: String get() = json.str("name", "Monitor #$id")
    val type: String get() = json.str("type", "http")
    val active: Boolean get() = json.bool("active", true)
    val parent: Long? get() = if (json.has("parent") && !json.isNull("parent")) json.optLong("parent") else null
    val url: String get() = json.str("url")
    val hostname: String get() = json.str("hostname")
    val description: String get() = json.str("description")
    val pathName: String get() = json.str("pathName")
    val interval: Int get() = json.num("interval", 60)
    val weight: Int get() = json.num("weight", 0)
    val tags: List<MonitorTag> get() = json.arr("tags")?.let { a ->
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::MonitorTag) }
    } ?: emptyList()
    val maintenance: Boolean get() = json.bool("maintenance", false)
    val childrenIds: List<Long> get() = json.arr("childrenIDs")?.let { a ->
        (0 until a.length()).map { a.optLong(it) }
    } ?: emptyList()

    fun displayTarget(): String = when (type) {
        "http", "keyword", "json-query", "real-browser", "websocket-upgrade", "grpc-keyword" -> url
        "port" -> "$hostname:${json.num("port")}"
        "docker" -> "${json.str("docker_container")} @ ${json.str("docker_host")}"
        "push", "manual", "group" -> ""
        else -> hostname.ifBlank { url }
    }
}

data class MonitorTag(val json: JSONObject) {
    val id: Long get() = if (json.has("tag_id")) json.long("tag_id") else json.long("id")
    val name: String get() = json.str("name")
    val color: String get() = json.str("color", "#4b5563")
    val value: String get() = json.str("value")
}

data class Tag(val json: JSONObject) {
    val id: Long get() = json.long("id")
    val name: String get() = json.str("name")
    val color: String get() = json.str("color", "#4b5563")
}

data class Beat(val json: JSONObject) {
    val monitorId: Long get() = if (json.has("monitorID")) json.long("monitorID") else json.long("monitor_id")
    val status: Int get() = json.num("status", -1)
    val time: String get() = json.str("time")
    val msg: String get() = json.str("msg")
    val ping: Double? get() = json.dbl("ping")
    val important: Boolean get() = json.bool("important", false)
    val retries: Int get() = json.num("retries")
    val duration: Long get() = json.long("duration")
    val downCount: Int get() = json.num("down_count")
    val response: String get() = json.str("response")

    val timeMillis: Long
        get() {
            if (time.isBlank()) return 0L
            val normalized = time.trim().replace(' ', 'T')
            // Kuma stores UTC timestamps without an offset, but some databases and
            // proxies return ISO strings with a positive or negative offset.
            return runCatching { Instant.parse(normalized).toEpochMilli() }
                .recoverCatching { OffsetDateTime.parse(normalized).toInstant().toEpochMilli() }
                .recoverCatching { LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC).toEpochMilli() }
                .getOrElse { 0L }
        }

    fun timeLabel(): String {
        val millis = timeMillis
        if (millis == 0L) return time
        val fmt = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss").withZone(ZoneId.systemDefault())
        return fmt.format(Instant.ofEpochMilli(millis))
    }

    fun relativeTime(): String {
        val millis = timeMillis
        if (millis == 0L) return time
        val diff = (System.currentTimeMillis() - millis).coerceAtLeast(0L)
        return when {
            diff < 60_000 -> "${diff / 1000}s ago"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            else -> "${diff / 86_400_000}d ago"
        }
    }
}

object KumaStatus {
    const val DOWN = 0
    const val UP = 1
    const val PENDING = 2
    const val MAINTENANCE = 3

    fun label(status: Int): String = when (status) {
        UP -> "Up"
        DOWN -> "Down"
        PENDING -> "Pending"
        MAINTENANCE -> "Maintenance"
        else -> "Unknown"
    }
}

enum class ConnectionStatus { Idle, Connecting, Connected, LoggedOut, Error }

data class ServerInfo(
    val version: String = "",
    val latestVersion: String = "",
    val dbType: String = "",
    val timezone: String = "",
    val raw: JSONObject = JSONObject(),
) {
    val nodeVersion: String get() = raw.str("nodeVersion")
    val isContainer: Boolean get() = raw.bool("isContainer")
}

data class StatusPage(
    val id: Long,
    val json: JSONObject,
) {
    val slug: String get() = json.str("slug")
    val title: String get() = json.str("title", slug)
    val published: Boolean get() = json.bool("published")
}

data class Maintenance(
    val id: Long,
    val json: JSONObject,
) {
    val title: String get() = json.str("title", "Maintenance #$id")
    val description: String get() = json.str("description")
    val strategy: String get() = json.str("strategy", "manual")
    val active: Boolean get() = json.bool("active")
    val status: String get() = json.str("status", "inactive")
}

/** Platform/architecture label from the `runtime` object in the server info event. */
fun runtimeLabel(raw: JSONObject): String {
    val runtime = raw.optJSONObject("runtime") ?: return ""
    return listOf(runtime.str("platform"), runtime.str("arch"))
        .filter { it.isNotBlank() }
        .joinToString("/")
}

/**
 * `testChrome` acknowledges an i18n message object, not a plain string.
 * Unwrap the version value so the UI does not show raw JSON.
 */
fun chromeTestMessage(raw: Any?, fallback: String = "Chrome test passed"): String = when (raw) {
    is String -> raw.ifBlank { fallback }
    is JSONObject -> raw.optJSONArray("values")?.optString(0).orEmpty().ifBlank {
        raw.str("key", fallback)
    }
    else -> fallback
}
