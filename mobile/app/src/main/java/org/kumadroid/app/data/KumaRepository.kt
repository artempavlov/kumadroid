package org.kumadroid.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class KumaRepository(private val context: Context) {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val socket = KumaSocket(scope)
    private val prefs = context.getSharedPreferences("kuma_session", Context.MODE_PRIVATE)

    var serverUrl: String = prefs.getString("baseUrl", "") ?: ""
        private set
    var allowInsecureTls: Boolean = prefs.getBoolean("allowInsecure", false)
        private set

    @Volatile
    private var http = buildHttpClient()

    private var token: String? = prefs.getString("token", null)
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var manualDisconnect = false

    val status = MutableStateFlow(ConnectionStatus.Idle)
    val statusMessage = MutableStateFlow("")
    val serverInfo = MutableStateFlow(ServerInfo())
    val needSetup = MutableStateFlow(false)
    val loggedIn = MutableStateFlow(false)
    val username = MutableStateFlow(prefs.getString("username", "") ?: "")
    val appTheme = MutableStateFlow(prefs.getString("appTheme", "auto") ?: "auto")
    val heartbeatBarStyle = MutableStateFlow(prefs.getString("heartbeatBarStyle", "normal") ?: "normal")
    val elapsedTimeStyle = MutableStateFlow(prefs.getString("elapsedTimeStyle", "with-line") ?: "with-line")

    val monitors = MutableStateFlow<List<Monitor>>(emptyList())
    val tags = MutableStateFlow<List<Tag>>(emptyList())
    val notifications = MutableStateFlow<List<JSONObject>>(emptyList())
    val maintenances = MutableStateFlow<List<Maintenance>>(emptyList())
    val statusPages = MutableStateFlow<List<StatusPage>>(emptyList())
    val apiKeys = MutableStateFlow<List<JSONObject>>(emptyList())
    val dockerHosts = MutableStateFlow<List<JSONObject>>(emptyList())
    val proxies = MutableStateFlow<List<JSONObject>>(emptyList())
    val remoteBrowsers = MutableStateFlow<List<JSONObject>>(emptyList())
    val monitorTypes = MutableStateFlow<JSONObject>(JSONObject())
    val settings = MutableStateFlow<JSONObject?>(null)
    val cloudflaredInstalled = MutableStateFlow(false)
    val cloudflaredRunning = MutableStateFlow(false)
    val cloudflaredMessage = MutableStateFlow("")
    val cloudflaredError = MutableStateFlow("")
    val cloudflaredToken = MutableStateFlow("")

    val heartbeats = MutableStateFlow<Map<Long, List<Beat>>>(emptyMap())
    val importantBeats = MutableStateFlow<Map<Long, List<Beat>>>(emptyMap())
    val avgPing = MutableStateFlow<Map<Long, Double>>(emptyMap())
    val uptime = MutableStateFlow<Map<String, Double>>(emptyMap())
    val certInfo = MutableStateFlow<Map<Long, JSONObject>>(emptyMap())
    val domainInfo = MutableStateFlow<Map<Long, JSONObject>>(emptyMap())

    val busy = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var monitorRevision = 0L

    init {
        scope.launch {
            socket.events.collect { event ->
                when (event) {
                    is SocketEvent.Connected -> {
                        status.value = ConnectionStatus.Connecting
                        statusMessage.value = "Opening session…"
                    }
                    is SocketEvent.Error -> {
                        statusMessage.value = event.message
                        _messages.tryEmit(event.message)
                    }
                    is SocketEvent.Disconnected -> handleDisconnect(event.reason)
                    is SocketEvent.Event -> handleEvent(event.name, event.args)
                }
            }
        }
    }

    fun bootstrap() {
        if (serverUrl.isNotBlank()) {
            openSocket()
        }
    }

    fun configureServer(url: String, insecure: Boolean) {
        val tlsChanged = allowInsecureTls != insecure
        serverUrl = normalizeUrl(url)
        allowInsecureTls = insecure
        prefs.edit().putString("baseUrl", serverUrl).putBoolean("allowInsecure", insecure).apply()
        if (tlsChanged) resetHttpClient()
    }

    private fun buildHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .configureTls(allowInsecureTls)
        .build()

    private fun resetHttpClient() {
        val previous = http
        http = buildHttpClient()
        previous.connectionPool.evictAll()
        previous.dispatcher.executorService.shutdown()
    }

    fun setAppTheme(value: String) {
        if (value !in setOf("auto", "light", "dark")) return
        appTheme.value = value
        prefs.edit().putString("appTheme", value).apply()
    }

    fun setHeartbeatBarStyle(value: String) {
        if (value !in setOf("normal", "bottom", "none")) return
        heartbeatBarStyle.value = value
        prefs.edit().putString("heartbeatBarStyle", value).apply()
    }

    fun setElapsedTimeStyle(value: String) {
        if (value !in setOf("with-line", "no-line", "none")) return
        elapsedTimeStyle.value = value
        prefs.edit().putString("elapsedTimeStyle", value).apply()
    }

    fun connectServer(url: String, insecure: Boolean) {
        configureServer(url, insecure)
        manualDisconnect = false
        reconnectAttempts = 0
        reconnectJob?.cancel()
        openSocket()
    }

    private fun openSocket() {
        if (serverUrl.isBlank()) return
        status.value = ConnectionStatus.Connecting
        statusMessage.value = "Connecting to $serverUrl"
        socket.connect(serverUrl, allowInsecureTls)
    }

    fun disconnect() {
        manualDisconnect = true
        reconnectJob?.cancel()
        socket.disconnect()
        status.value = ConnectionStatus.Idle
        statusMessage.value = ""
        loggedIn.value = false
    }

    /** Releases the socket, reconnect loop, and HTTP resources when the UI goes away. */
    fun close() {
        manualDisconnect = true
        reconnectJob?.cancel()
        socket.disconnect()
        job.cancel()
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    private fun handleDisconnect(reason: String) {
        if (manualDisconnect) return
        status.value = ConnectionStatus.Error
        statusMessage.value = reason
        if (serverUrl.isNotBlank()) {
            reconnectJob?.cancel()
            val delayMs = (2_000L shl reconnectAttempts.coerceIn(0, 4)).coerceAtMost(30_000L)
            reconnectAttempts++
            reconnectJob = scope.launch {
                delay(delayMs)
                if (!manualDisconnect && serverUrl.isNotBlank()) openSocket()
            }
        }
    }

    private fun handleEvent(name: String, args: JSONArray) {
        when (name) {
            "__connected" -> {
                statusMessage.value = "Authenticating…"
            }
            "info" -> {
                val obj = args.optJSONObject(0) ?: return
                serverInfo.value = ServerInfo(
                    version = obj.str("version"),
                    latestVersion = obj.str("latestVersion"),
                    dbType = obj.str("dbType"),
                    timezone = obj.str("serverTimezone"),
                    raw = obj,
                )
            }
            "setup", "needSetup" -> {
                if (name == "needSetup") {
                    needSetup.value = args.optBoolean(0, false)
                } else {
                    needSetup.value = true
                }
                if (needSetup.value) {
                    status.value = ConnectionStatus.LoggedOut
                    statusMessage.value = "Server setup required"
                }
            }
            "autoLogin" -> {
                loggedIn.value = true
                needSetup.value = false
                reconnectAttempts = 0
                status.value = ConnectionStatus.Connected
                statusMessage.value = "Connected"
                scope.launch { loadTags() }
            }
            "loginRequired" -> {
                if (needSetup.value) return
                val saved = token
                if (!saved.isNullOrBlank()) {
                    scope.launch { tryTokenLogin(saved) }
                } else {
                    loggedIn.value = false
                    status.value = ConnectionStatus.LoggedOut
                    statusMessage.value = "Sign in to $serverUrl"
                }
            }
            "cloudflared_installed" -> cloudflaredInstalled.value = args.optBoolean(0)
            "cloudflared_running" -> cloudflaredRunning.value = args.optBoolean(0)
            "cloudflared_message" -> cloudflaredMessage.value = args.opt(0)?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
            "cloudflared_errorMessage" -> {
                val value = args.opt(0)
                cloudflaredError.value = if (value == null || value == JSONObject.NULL) "" else value.toString()
            }
            "cloudflared_token" -> {
                val value = args.opt(0)
                cloudflaredToken.value = if (value == null || value == JSONObject.NULL) "" else value.toString()
            }
            "monitorList" -> {
                val obj = args.optJSONObject(0) ?: return
                val list = mutableListOf<Monitor>()
                monitorRevision++
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val monitor = obj.optJSONObject(id) ?: continue
                    list += Monitor(monitor.long("id", id.toLongOrNull() ?: 0L), monitor, monitorRevision)
                }
                monitors.value = sortMonitors(list)
            }
            "updateMonitorIntoList" -> {
                // Uptime Kuma emits a map keyed by monitor ID here, not a single monitor.
                val list = args.optJSONObject(0) ?: return
                monitorRevision++
                val updates = mutableListOf<Monitor>()
                val keys = list.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val monitor = list.optJSONObject(key) ?: continue
                    val id = monitor.long("id", key.toLongOrNull() ?: 0L)
                    if (id > 0) updates += Monitor(id, monitor, monitorRevision)
                }
                if (updates.isEmpty()) return
                val updatedIds = updates.mapTo(mutableSetOf()) { it.id }
                monitors.update { current ->
                    sortMonitors(current.filterNot { it.id in updatedIds } + updates)
                }
            }
            "deleteMonitorFromList" -> {
                val id = args.optLong(0)
                monitors.update { current -> current.filterNot { it.id == id } }
                heartbeats.update { it - id }
                importantBeats.update { it - id }
            }
            "monitorTypeList" -> {
                monitorTypes.value = args.optJSONObject(0) ?: JSONObject()
            }
            "heartbeatList" -> {
                val monitorId = args.optLong(0)
                val array = args.optJSONArray(1) ?: JSONArray()
                val overwrite = args.optBoolean(2, false)
                val incoming = (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(::Beat) }
                heartbeats.update { current ->
                    val existing = current[monitorId].orEmpty()
                    val merged = if (overwrite || existing.isEmpty()) {
                        incoming
                    } else {
                        (incoming + existing)
                            .distinctBy { "${it.time}|${it.status}|${it.msg}" }
                            .sortedBy { it.timeMillis }
                            .takeLast(150)
                    }
                    current + (monitorId to merged)
                }
            }
            "heartbeat" -> {
                val obj = args.optJSONObject(0) ?: return
                val beat = Beat(obj)
                heartbeats.update { current ->
                    val list = (current[beat.monitorId].orEmpty() + beat).takeLast(150)
                    current + (beat.monitorId to list)
                }
                if (beat.important) {
                    importantBeats.update { current ->
                        current + (beat.monitorId to (listOf(beat) + current[beat.monitorId].orEmpty()).take(500))
                    }
                }
            }
            "importantHeartbeatList" -> {
                val monitorId = args.optLong(0)
                val array = args.optJSONArray(1) ?: JSONArray()
                val list = (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::Beat) }
                importantBeats.update { it + (monitorId to list) }
            }
            "avgPing" -> {
                val id = args.optLong(0)
                if (args.isNull(1)) {
                    avgPing.update { it - id }
                } else {
                    avgPing.update { it + (id to args.optDouble(1)) }
                }
            }
            "uptime" -> {
                val id = args.optLong(0)
                val type = args.optString(1)
                uptime.update { it + ("${id}_$type" to args.optDouble(2) * 100.0) }
            }
            "certInfo" -> {
                val id = args.optLong(0)
                val raw = args.opt(1)
                val obj = when (raw) {
                    is JSONObject -> raw
                    is String -> try { JSONObject(raw) } catch (_: Exception) { null }
                    else -> null
                }
                if (obj != null) certInfo.update { it + (id to obj) }
            }
            "domainInfo" -> {
                val id = args.optLong(0)
                val obj = JSONObject()
                    .put("daysRemaining", args.opt(1))
                    .put("expiresOn", args.opt(2))
                domainInfo.update { it + (id to obj) }
            }
            "maintenanceList" -> {
                val obj = args.optJSONObject(0) ?: return
                val list = mutableListOf<Maintenance>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    obj.optJSONObject(key)?.let { list += Maintenance(it.long("id", key.toLongOrNull() ?: 0L), it) }
                }
                maintenances.value = list.sortedBy { it.title.lowercase() }
            }
            "notificationList" -> {
                val array = args.optJSONArray(0) ?: return
                notifications.value = (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::normalizeNotification) }
            }
            "statusPageList" -> {
                val obj = args.optJSONObject(0) ?: return
                val list = mutableListOf<StatusPage>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    obj.optJSONObject(key)?.let { list += StatusPage(it.long("id", key.toLongOrNull() ?: 0L), it) }
                }
                statusPages.value = list.sortedBy { it.title.lowercase() }
            }
            "apiKeyList" -> {
                val array = args.optJSONArray(0) ?: return
                apiKeys.value = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
            }
            "proxyList" -> {
                val array = args.optJSONArray(0) ?: return
                proxies.value = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
            }
            "dockerHostList" -> {
                val array = args.optJSONArray(0) ?: return
                dockerHosts.value = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
            }
            "remoteBrowserList" -> {
                val array = args.optJSONArray(0) ?: return
                remoteBrowsers.value = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
            }
            "refresh" -> {
                scope.launch { runCatching { refreshAll() } }
            }
        }
    }

    private suspend fun tryTokenLogin(value: String) {
        try {
            val ack = socket.requestObject("loginByToken", value)
            if (ack.bool("ok")) {
                loggedIn.value = true
                needSetup.value = false
                reconnectAttempts = 0
                status.value = ConnectionStatus.Connected
                statusMessage.value = "Connected"
                loadTags()
            } else {
                token = null
                prefs.edit().remove("token").apply()
                loggedIn.value = false
                status.value = ConnectionStatus.LoggedOut
                statusMessage.value = "Session expired. Please sign in."
            }
        } catch (e: Exception) {
            status.value = ConnectionStatus.Error
            statusMessage.value = e.message ?: "Login failed"
        }
    }

    suspend fun login(user: String, password: String, totp: String? = null): Boolean {
        val payload = JSONObject().put("username", user).put("password", password)
        if (!totp.isNullOrBlank()) payload.put("token", totp)
        val ack = socket.requestObject("login", payload)
        if (ack.bool("tokenRequired")) return false
        if (!ack.bool("ok")) throw KumaException(ack.str("msg", "Login failed"))
        token = ack.str("token")
        prefs.edit().putString("token", token).putString("username", user).apply()
        username.value = user
        loggedIn.value = true
        needSetup.value = false
        reconnectAttempts = 0
        status.value = ConnectionStatus.Connected
        statusMessage.value = "Connected"
        loadTags()
        return true
    }

    suspend fun setup(user: String, password: String) {
        val ack = socket.requestObject("setup", user, password)
        if (!ack.bool("ok")) throw KumaException(ack.str("msg", "Setup failed"))
        needSetup.value = false
        login(user, password)
    }

    suspend fun logout() {
        manualDisconnect = true
        reconnectJob?.cancel()
        runCatching { socket.request("logout") }
        token = null
        prefs.edit().remove("token").apply()
        loggedIn.value = false
        needSetup.value = false
        socket.disconnect()
        status.value = ConnectionStatus.LoggedOut
        statusMessage.value = "Signed out"
        delay(150)
        manualDisconnect = false
        reconnectAttempts = 0
        openSocket()
    }

    suspend fun refreshAll() {
        val ack = socket.requestObject("getMonitorList")
        ensureOk(ack)
    }

    suspend fun loadTags() {
        runCatching {
            val ack = socket.requestObject("getTags")
            if (ack.bool("ok")) {
                val array = ack.optJSONArray("tags") ?: JSONArray()
                tags.value = (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::Tag) }
                    .sortedBy { it.name.lowercase() }
            }
        }
    }

    suspend fun getMonitor(id: Long): JSONObject? {
        val ack = socket.requestObject("getMonitor", id)
        ensureOk(ack)
        return ack.optJSONObject("monitor")
    }

    suspend fun addMonitor(monitor: JSONObject): Long {
        val ack = socket.requestObject("add", monitor, timeoutMs = 30_000)
        ensureOk(ack)
        return ack.long("monitorID")
    }

    suspend fun editMonitor(monitor: JSONObject) {
        val ack = socket.requestObject("editMonitor", monitor, timeoutMs = 30_000)
        ensureOk(ack)
    }

    suspend fun deleteMonitor(id: Long, deleteChildren: Boolean = true) {
        val ack = socket.requestObject("deleteMonitor", id, deleteChildren)
        ensureOk(ack)
    }

    suspend fun pauseMonitor(id: Long) {
        ensureOk(socket.requestObject("pauseMonitor", id))
    }

    suspend fun resumeMonitor(id: Long) {
        ensureOk(socket.requestObject("resumeMonitor", id))
    }

    suspend fun getMonitorBeats(id: Long, periodHours: Int): List<Beat> {
        val ack = socket.requestObject("getMonitorBeats", id, periodHours)
        ensureOk(ack)
        val array = ack.optJSONArray("data") ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::Beat) }
    }

    suspend fun getImportantBeats(id: Long, offset: Int = 0, count: Int = 100): List<Beat> {
        val ack = socket.requestObject("monitorImportantHeartbeatListPaged", id, offset, count)
        ensureOk(ack)
        val array = ack.optJSONArray("data") ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::Beat) }
    }

    suspend fun getChartData(id: Long, periodHours: Int): List<JSONObject> {
        val ack = socket.requestObject("getMonitorChartData", id, periodHours)
        ensureOk(ack)
        val array = ack.optJSONArray("data") ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    suspend fun clearEvents(id: Long) {
        ensureOk(socket.requestObject("clearEvents", id))
    }

    suspend fun clearHeartbeats(id: Long) {
        ensureOk(socket.requestObject("clearHeartbeats", id))
    }

    suspend fun addTag(name: String, color: String): Long {
        val ack = socket.requestObject("addTag", JSONObject().put("name", name).put("color", color))
        ensureOk(ack)
        loadTags()
        return ack.long("tagID")
    }

    suspend fun editTag(id: Long, name: String, color: String) {
        ensureOk(socket.requestObject("editTag", JSONObject().put("id", id).put("name", name).put("color", color)))
        loadTags()
    }

    suspend fun deleteTag(id: Long) {
        ensureOk(socket.requestObject("deleteTag", id))
        loadTags()
    }

    suspend fun addMonitorTag(tagId: Long, monitorId: Long, value: String) {
        ensureOk(socket.requestObject("addMonitorTag", tagId, monitorId, value))
    }

    suspend fun editMonitorTag(tagId: Long, monitorId: Long, value: String) {
        ensureOk(socket.requestObject("editMonitorTag", tagId, monitorId, value))
    }

    suspend fun deleteMonitorTag(tagId: Long, monitorId: Long, value: String) {
        ensureOk(socket.requestObject("deleteMonitorTag", tagId, monitorId, value))
    }

    suspend fun syncMonitorTags(monitorId: Long, oldTags: List<MonitorTag>, newTags: List<MonitorTag>) {
        val oldById = oldTags.associateBy { it.id }
        val newById = newTags.associateBy { it.id }
        for ((id, old) in oldById) {
            val new = newById[id]
            if (new == null) {
                deleteMonitorTag(id, monitorId, old.value)
            } else if (new.value != old.value) {
                editMonitorTag(id, monitorId, new.value)
            }
        }
        for ((id, new) in newById) {
            if (!oldById.containsKey(id)) addMonitorTag(id, monitorId, new.value)
        }
    }

    suspend fun saveNotification(notification: JSONObject, id: Long? = null): Long {
        val ack = socket.requestObject("addNotification", notification, id, timeoutMs = 40_000)
        ensureOk(ack)
        return ack.long("id")
    }

    suspend fun deleteNotification(id: Long) {
        ensureOk(socket.requestObject("deleteNotification", id))
    }

    suspend fun testNotification(notification: JSONObject): String {
        val ack = socket.requestObject("testNotification", notification, timeoutMs = 60_000)
        ensureOk(ack)
        return ack.str("msg", "Sent")
    }

    suspend fun getSettings(): JSONObject {
        val ack = socket.requestObject("getSettings")
        ensureOk(ack)
        return ack.optJSONObject("data") ?: JSONObject()
    }

    suspend fun setSettings(data: JSONObject) {
        ensureOk(socket.requestObject("setSettings", data, null))
    }

    suspend fun changePassword(current: String, newPassword: String) {
        val ack = socket.requestObject("changePassword", JSONObject().put("currentPassword", current).put("newPassword", newPassword))
        ensureOk(ack)
        // A password change invalidates the old JWT; the server returns its replacement.
        val fresh = ack.optString("token")
        if (fresh.isNotBlank()) {
            token = fresh
            prefs.edit().putString("token", fresh).apply()
        }
    }

    suspend fun addStatusPage(title: String, slug: String): String {
        val ack = socket.requestObject("addStatusPage", title, slug)
        ensureOk(ack)
        refreshStatusPages()
        return ack.str("slug", slug.lowercase())
    }

    suspend fun deleteStatusPage(slug: String) {
        ensureOk(socket.requestObject("deleteStatusPage", slug))
        refreshStatusPages()
    }

    /** The server only sends the status page list after a fresh login, so re-authenticate quietly. */
    suspend fun refreshStatusPages() {
        val saved = prefs.getString("token", null) ?: return
        runCatching {
            val ack = socket.requestObject("loginByToken", saved, timeoutMs = 20_000)
            if (ack.bool("ok")) {
                loggedIn.value = true
            }
        }
    }

    suspend fun getStatusPageConfig(slug: String): JSONObject {
        val ack = socket.requestObject("getStatusPage", slug)
        ensureOk(ack)
        return ack.optJSONObject("config") ?: JSONObject()
    }

    suspend fun saveStatusPage(
        slug: String,
        config: JSONObject,
        imageDataUrl: String,
        publicGroupList: JSONArray,
    ): JSONArray {
        val ack = socket.requestObject("saveStatusPage", slug, config, imageDataUrl, publicGroupList, timeoutMs = 40_000)
        ensureOk(ack)
        return ack.optJSONArray("publicGroupList") ?: JSONArray()
    }

    suspend fun fetchImage(path: String): ByteArray = withContext(Dispatchers.IO) {
        val url = if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            normalizeUrl(serverUrl).trimEnd('/') + "/" + path.trimStart('/')
        }
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw KumaException("Image not available (${response.code})")
            response.body?.bytes() ?: throw KumaException("Empty image response")
        }
    }

    suspend fun fetchStatusPageData(slug: String): JSONObject = withContext(Dispatchers.IO) {
        val base = normalizeUrl(serverUrl).trimEnd('/')
        val request = Request.Builder().url("$base/api/status-page/$slug").get().build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw KumaException("Status page not found")
            JSONObject(body)
        }
    }

    suspend fun fetchStatusPageHeartbeats(slug: String): JSONObject = withContext(Dispatchers.IO) {
        val base = normalizeUrl(serverUrl).trimEnd('/')
        val request = Request.Builder().url("$base/api/status-page/heartbeat/$slug").get().build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw KumaException("Status page heartbeat data not found")
            JSONObject(body)
        }
    }

    suspend fun getIncidentHistory(slug: String, cursor: String? = null): JSONObject {
        val ack = socket.requestObject("getIncidentHistory", slug, cursor ?: JSONObject.NULL)
        ensureOk(ack)
        return ack
    }

    suspend fun postIncident(slug: String, incident: JSONObject) {
        ensureOk(socket.requestObject("postIncident", slug, incident))
    }

    suspend fun editIncident(slug: String, id: Long, incident: JSONObject) {
        ensureOk(socket.requestObject("editIncident", slug, id, incident))
    }

    suspend fun resolveIncident(slug: String, id: Long) {
        ensureOk(socket.requestObject("resolveIncident", slug, id))
    }

    suspend fun deleteIncident(slug: String, id: Long) {
        ensureOk(socket.requestObject("deleteIncident", slug, id))
    }

    suspend fun unpinIncidents(slug: String) {
        ensureOk(socket.requestObject("unpinIncident", slug))
    }

    suspend fun getMaintenance(id: Long): JSONObject? {
        val ack = socket.requestObject("getMaintenance", id)
        ensureOk(ack)
        return ack.optJSONObject("maintenance")
    }

    suspend fun addMaintenance(maintenance: JSONObject): Long {
        val ack = socket.requestObject("addMaintenance", maintenance)
        ensureOk(ack)
        return ack.long("maintenanceID")
    }

    suspend fun editMaintenance(maintenance: JSONObject) {
        ensureOk(socket.requestObject("editMaintenance", maintenance))
    }

    suspend fun deleteMaintenance(id: Long) {
        ensureOk(socket.requestObject("deleteMaintenance", id))
    }

    suspend fun pauseMaintenance(id: Long) {
        ensureOk(socket.requestObject("pauseMaintenance", id))
    }

    suspend fun resumeMaintenance(id: Long) {
        ensureOk(socket.requestObject("resumeMaintenance", id))
    }

    suspend fun setMaintenanceMonitors(id: Long, monitorIds: List<Long>) {
        val array = JSONArray()
        monitorIds.forEach { array.put(JSONObject().put("id", it)) }
        ensureOk(socket.requestObject("addMonitorMaintenance", id, array))
    }

    suspend fun setMaintenanceStatusPages(id: Long, statusPageIds: List<Long>) {
        val array = JSONArray()
        statusPageIds.forEach { array.put(JSONObject().put("id", it)) }
        ensureOk(socket.requestObject("addMaintenanceStatusPage", id, array))
    }

    suspend fun getMaintenanceMonitorIds(id: Long): List<Long> {
        val ack = socket.requestObject("getMonitorMaintenance", id)
        ensureOk(ack)
        val array = ack.optJSONArray("monitors") ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.long("id") }
    }

    suspend fun getMaintenanceStatusPageIds(id: Long): List<Long> {
        val ack = socket.requestObject("getMaintenanceStatusPage", id)
        ensureOk(ack)
        val array = ack.optJSONArray("statusPages") ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.long("id") }
    }

    suspend fun addApiKey(name: String, expires: String): String {
        val ack = socket.requestObject("addAPIKey", JSONObject().put("name", name).put("active", true).put("expires", expires))
        ensureOk(ack)
        return ack.str("key")
    }

    suspend fun deleteApiKey(id: Long) {
        ensureOk(socket.requestObject("deleteAPIKey", id))
    }

    suspend fun setApiKeyActive(id: Long, active: Boolean) {
        ensureOk(socket.requestObject(if (active) "enableAPIKey" else "disableAPIKey", id))
    }

    suspend fun getDatabaseSize(): JSONObject {
        val ack = socket.requestObject("getDatabaseSize")
        ensureOk(ack)
        return ack
    }

    suspend fun clearStatistics() {
        ensureOk(socket.requestObject("clearStatistics", timeoutMs = 120_000))
    }

    suspend fun shrinkDatabase() {
        ensureOk(socket.requestObject("shrinkDatabase", timeoutMs = 120_000))
    }

    suspend fun testDockerHost(host: JSONObject): String {
        val ack = socket.requestObject("testDockerHost", host, timeoutMs = 60_000)
        ensureOk(ack)
        return ack.str("msg", "Connected")
    }

    suspend fun saveDockerHost(host: JSONObject, id: Long? = null) {
        ensureOk(socket.requestObject("addDockerHost", host, id))
    }

    suspend fun deleteDockerHost(id: Long) {
        ensureOk(socket.requestObject("deleteDockerHost", id))
    }

    suspend fun saveProxy(proxy: JSONObject, id: Long? = null) {
        ensureOk(socket.requestObject("addProxy", proxy, id))
    }

    suspend fun deleteProxy(id: Long) {
        ensureOk(socket.requestObject("deleteProxy", id))
    }

    suspend fun saveRemoteBrowser(browser: JSONObject, id: Long? = null) {
        ensureOk(socket.requestObject("addRemoteBrowser", browser, id))
    }

    suspend fun deleteRemoteBrowser(id: Long) {
        ensureOk(socket.requestObject("deleteRemoteBrowser", id))
    }

    suspend fun testRemoteBrowser(browser: JSONObject): String {
        val ack = socket.requestObject("testRemoteBrowser", browser, timeoutMs = 60_000)
        ensureOk(ack)
        return ack.str("msg", "Connected")
    }

    suspend fun getPushExample(language: String): String {
        val ack = socket.requestObject("getPushExample", language)
        ensureOk(ack)
        return ack.str("code")
    }

    suspend fun getGameList(): JSONArray {
        val ack = socket.requestObject("getGameList", timeoutMs = 40_000)
        ensureOk(ack)
        return ack.optJSONArray("gameList") ?: JSONArray()
    }

    suspend fun getPM2ProcessList(): JSONArray {
        val ack = socket.requestObject("getPM2ProcessList")
        ensureOk(ack)
        return ack.optJSONArray("processList") ?: JSONArray()
    }

    suspend fun testChrome(executable: String): String {
        val ack = socket.requestObject("testChrome", executable, timeoutMs = 60_000)
        ensureOk(ack)
        return ack.str("msg", "Connected")
    }

    suspend fun disconnectOtherClients() {
        socket.emit("disconnectOtherSocketClients")
    }

    suspend fun twoFaStatus(): Boolean {
        val ack = socket.requestObject("twoFAStatus")
        ensureOk(ack)
        return ack.bool("status")
    }

    suspend fun prepare2FA(password: String): String {
        val ack = socket.requestObject("prepare2FA", password)
        ensureOk(ack)
        return ack.str("uri")
    }

    suspend fun verifyToken(token: String, password: String): Boolean {
        val ack = socket.requestObject("verifyToken", token, password)
        // Invalid codes come back as {ok:false, valid:false}; surface that as a
        // normal "wrong code" result instead of a generic request failure.
        if (!ack.bool("ok", true) && ack.has("valid")) return ack.bool("valid")
        ensureOk(ack)
        return ack.bool("valid")
    }

    suspend fun save2FA(password: String) {
        ensureOk(socket.requestObject("save2FA", password))
    }

    suspend fun disable2FA(password: String) {
        ensureOk(socket.requestObject("disable2FA", password))
    }

    private fun normalizeNotification(raw: JSONObject): JSONObject {
        val out = JSONObject(raw.toString())
        val config = raw.opt("config")
        if (config is String && config.isNotBlank()) {
            runCatching { JSONObject(config) }.getOrNull()?.let { parsed ->
                val keys = parsed.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    out.put(key, parsed.get(key))
                }
                out.remove("config")
            }
        }
        out.put("active", raw.bool("active", out.bool("active", true)))
        out.put("isDefault", raw.bool("isDefault", out.bool("isDefault", false)))
        return out
    }

    private fun ensureOk(ack: JSONObject) {
        if (!ack.bool("ok", true)) throw KumaException(ack.str("msg", "Request failed"))
    }

    private fun normalizeUrl(raw: String): String {
        var value = raw.trim()
        if (value.isEmpty()) return value
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://$value"
        return value.trimEnd('/')
    }

    suspend fun joinCloudflared() {
        socket.emit("cloudflared_join")
    }

    suspend fun leaveCloudflared() {
        runCatching { socket.emit("cloudflared_leave") }
    }

    suspend fun startCloudflared(token: String) {
        socket.emit("cloudflared_start", token)
    }

    suspend fun stopCloudflared(password: String) {
        try {
            val ack = socket.request("cloudflared_stop", password, timeoutMs = 1_500).optJSONObject(0)
            if (ack != null && !ack.optBoolean("ok", true)) {
                throw KumaException(ack.optString("msg", "Could not stop cloudflared"))
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            // A successful stop does not acknowledge; the server emits the state change instead.
        }
    }

    suspend fun removeCloudflaredToken() {
        socket.emit("cloudflared_removeToken")
    }

    private fun sortMonitors(list: List<Monitor>): List<Monitor> {
        return list.sortedWith(compareBy<Monitor> { it.weight }.thenBy { it.name.lowercase() })
    }
}
