package org.kumadroid.app.data

import android.annotation.SuppressLint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

sealed class SocketEvent {
    data class Connected(val url: String) : SocketEvent()
    data class Event(val name: String, val args: JSONArray) : SocketEvent()
    data class Disconnected(val reason: String, val code: Int = 0) : SocketEvent()
    data class Error(val message: String) : SocketEvent()
}

/**
 * The insecure mode is an explicit, opt-in user choice for self-signed Kuma servers.
 * Keep the trust-all setup in one place so socket and HTTP clients behave the same.
 */
@SuppressLint("CustomX509TrustManager")
internal fun OkHttpClient.Builder.configureTls(insecure: Boolean): OkHttpClient.Builder {
    if (!insecure) return this
    val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val context = SSLContext.getInstance("TLS")
    context.init(null, arrayOf(trustAll), SecureRandom())
    sslSocketFactory(context.socketFactory, trustAll)
    hostnameVerifier { _, _ -> true }
    return this
}

/**
 * Minimal, dependency-free Socket.IO v4 / Engine.IO v4 WebSocket client.
 *
 * Uptime Kuma has no public REST management API, every action goes through
 * Socket.IO. We only need the websocket transport: Engine.IO open, Socket.IO
 * connect, events with acknowledgements, and ping/pong keepalive.
 */
class KumaSocket(private val scope: CoroutineScope) {

    private val _events = MutableSharedFlow<SocketEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SocketEvent> = _events.asSharedFlow()

    @Volatile
    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JSONArray>>()
    private val nextId = AtomicInteger(1)
    private val sendMutex = Mutex()

    @Volatile
    private var socketConnected = false

    @Volatile
    private var closedByUser = false

    fun connect(baseUrl: String, allowInsecureTls: Boolean) {
        closeInternal(notify = false)
        closedByUser = false
        val wsUrl = buildWebSocketUrl(baseUrl) ?: run {
            // Report a terminal failure so the repository does not stay stuck in Connecting.
            _events.tryEmit(SocketEvent.Error("Invalid server URL"))
            _events.tryEmit(SocketEvent.Disconnected("Invalid server URL"))
            return
        }
        val okHttp = buildClient(allowInsecureTls)
        client = okHttp
        val request = Request.Builder()
            .url(wsUrl)
            .header("User-Agent", "KumaDroid/1.0")
            .build()
        webSocket = okHttp.newWebSocket(request, Listener(wsUrl))
    }

    fun disconnect() {
        closedByUser = true
        // The repository sets the visible connection state itself. Emitting a
        // disconnect event here can race with a deliberate reconnect (logout)
        // and schedule a second connection.
        closeInternal(notify = false)
    }

    private fun closeInternal(notify: Boolean) {
        val ws = webSocket
        val oldClient = client
        webSocket = null
        client = null
        socketConnected = false
        pending.values.forEach { it.completeExceptionally(KumaException("Disconnected from the server")) }
        pending.clear()
        try {
            ws?.close(1000, "client closing")
        } catch (_: Exception) {
        }
        oldClient?.connectionPool?.evictAll()
        oldClient?.dispatcher?.executorService?.shutdown()
        if (notify) {
            _events.tryEmit(SocketEvent.Disconnected("Disconnected", 1000))
        }
    }

    suspend fun request(event: String, vararg args: Any?, timeoutMs: Long = 20_000): JSONArray {
        val ws = webSocket
        if (ws == null || !socketConnected) {
            throw KumaException("Not connected to the server")
        }
        val id = nextId.getAndIncrement()
        val deferred = CompletableDeferred<JSONArray>()
        pending[id] = deferred
        val payload = JSONArray().put(event)
        args.forEach { payload.put(it ?: JSONObject.NULL) }
        val frame = "42$id$payload"
        sendMutex.withLock {
            if (!ws.send(frame)) {
                pending.remove(id)
                throw KumaException("Connection lost")
            }
        }
        try {
            return withTimeout(timeoutMs) { deferred.await() }
        } finally {
            pending.remove(id)
        }
    }

    suspend fun requestObject(event: String, vararg args: Any?, timeoutMs: Long = 20_000): JSONObject {
        val ack = request(event, *args, timeoutMs = timeoutMs)
        return ack.optJSONObject(0) ?: JSONObject()
    }

    /** Fire-and-forget Socket.IO event for handlers that do not acknowledge. */
    suspend fun emit(event: String, vararg args: Any?) {
        val ws = webSocket
        if (ws == null || !socketConnected) throw KumaException("Not connected to the server")
        val payload = JSONArray().put(event)
        args.forEach { payload.put(it ?: JSONObject.NULL) }
        sendMutex.withLock {
            if (!ws.send("42$payload")) throw KumaException("Connection lost")
        }
    }

    private fun buildClient(insecure: Boolean): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .configureTls(insecure)
        .build()

    private fun buildWebSocketUrl(baseUrl: String): String? {
        var input = baseUrl.trim()
        if (input.isEmpty()) return null
        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            input = "http://$input"
        }
        val http = input.toHttpUrlOrNull() ?: return null
        val scheme = if (http.isHttps) "wss" else "ws"
        val basePath = http.encodedPath.trimEnd('/')
        val path = if (basePath.isEmpty()) "/socket.io/" else "$basePath/socket.io/"
        val isDefaultPort = (http.isHttps && http.port == 443) || (!http.isHttps && http.port == 80)
        val portPart = if (isDefaultPort) "" else ":${http.port}"
        val host = if (http.host.contains(':')) "[${http.host}]" else http.host
        return "$scheme://$host$portPart$path?EIO=4&transport=websocket"
    }

    private inner class Listener(private val url: String) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (this@KumaSocket.webSocket !== webSocket) return
            _events.tryEmit(SocketEvent.Connected(url))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (this@KumaSocket.webSocket !== webSocket) return
            // Engine.IO may batch packets using the record separator.
            for (packet in text.split('\u001e')) {
                if (packet.isNotEmpty()) handleEnginePacket(webSocket, packet)
            }
        }

        private fun handleEnginePacket(ws: WebSocket, packet: String) {
            when (packet[0]) {
                '0' -> {
                    // Engine.IO handshake completed. Start a Socket.IO namespace.
                    ws.send("40")
                }
                '2' -> {
                    // ping -> pong
                    ws.send("3" + packet.substring(1))
                }
                '1' -> {
                    // Engine.IO close. Forget this socket so a later WebSocket
                    // close callback does not report a duplicate disconnect.
                    socketConnected = false
                    if (this@KumaSocket.webSocket === ws) this@KumaSocket.webSocket = null
                    pending.values.forEach { it.completeExceptionally(KumaException("Connection closed")) }
                    pending.clear()
                    _events.tryEmit(SocketEvent.Disconnected("Server closed the connection"))
                }
                '4' -> handleSocketPacket(ws, packet.substring(1))
                else -> Unit
            }
        }

        private fun handleSocketPacket(ws: WebSocket, packet: String) {
            if (packet.isEmpty()) return
            when (packet[0]) {
                '0' -> {
                    socketConnected = true
                    _events.tryEmit(SocketEvent.Event("__connected", JSONArray()))
                }
                '1' -> {
                    socketConnected = false
                    _events.tryEmit(SocketEvent.Disconnected("Server closed the session", 1000))
                }
                '2' -> {
                    val body = packet.substring(1)
                    val arrayStart = body.indexOf('[')
                    if (arrayStart < 0) return
                    val parsed = try {
                        JSONArray(body.substring(arrayStart))
                    } catch (_: Exception) {
                        return
                    }
                    val name = parsed.optString(0)
                    val args = JSONArray()
                    for (i in 1 until parsed.length()) args.put(parsed.opt(i))
                    _events.tryEmit(SocketEvent.Event(name, args))
                }
                '3' -> {
                    val body = packet.substring(1)
                    var i = 0
                    while (i < body.length && body[i].isDigit()) i++
                    val id = body.substring(0, i).toIntOrNull() ?: return
                    val arrayStart = body.indexOf('[', i)
                    val ack = if (arrayStart >= 0) {
                        try {
                            JSONArray(body.substring(arrayStart))
                        } catch (_: Exception) {
                            JSONArray()
                        }
                    } else JSONArray()
                    pending.remove(id)?.complete(ack)
                }
                '4' -> {
                    _events.tryEmit(SocketEvent.Error("Socket.IO error: ${packet.substring(1)}"))
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (this@KumaSocket.webSocket !== webSocket) return
            this@KumaSocket.webSocket = null
            socketConnected = false
            pending.values.forEach { it.completeExceptionally(KumaException(t.message ?: "Connection failed")) }
            pending.clear()
            if (!closedByUser) {
                _events.tryEmit(SocketEvent.Disconnected(t.message ?: "Connection failed", 0))
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (this@KumaSocket.webSocket !== webSocket) return
            this@KumaSocket.webSocket = null
            socketConnected = false
            pending.values.forEach { it.completeExceptionally(KumaException("Connection closed")) }
            pending.clear()
            if (!closedByUser) {
                _events.tryEmit(SocketEvent.Disconnected(reason.ifBlank { "Connection closed" }, code))
            }
        }
    }
}

class KumaException(message: String) : Exception(message)
