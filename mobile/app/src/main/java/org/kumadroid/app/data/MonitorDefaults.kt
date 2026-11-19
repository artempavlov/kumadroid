package org.kumadroid.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

object MonitorCatalog {
    private const val TOKEN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    fun newPushToken(): String {
        val random = SecureRandom()
        return buildString(32) {
            repeat(32) { append(TOKEN_ALPHABET[random.nextInt(TOKEN_ALPHABET.length)]) }
        }
    }


    val types = listOf(
        "http", "keyword", "json-query", "port", "ping", "dns", "docker", "system-service",
        "pm2", "real-browser", "websocket-upgrade", "sftp", "group", "push", "manual",
        "globalping", "grpc-keyword", "kafka-producer", "mqtt", "ntp", "rabbitmq",
        "sip-options", "smtp", "snmp", "tailscale-ping", "gamedig", "steam", "sqlserver",
        "mongodb", "mysql", "oracledb", "postgres", "radius", "redis",
    )

    fun label(type: String): String = when (type) {
        "http" -> "HTTP(s)"
        "keyword" -> "HTTP(s) - Keyword"
        "json-query" -> "HTTP(s) - Json Query"
        "port" -> "TCP Port"
        "ping" -> "Ping"
        "dns" -> "DNS"
        "docker" -> "Docker Container"
        "system-service" -> "System Service"
        "pm2" -> "PM2 Process"
        "real-browser" -> "Real Browser"
        "websocket-upgrade" -> "HTTP(s) - WebSocket Upgrade"
        "sftp" -> "SFTP"
        "group" -> "Group"
        "push" -> "Push"
        "manual" -> "Manual"
        "globalping" -> "Globalping"
        "grpc-keyword" -> "gRPC - Keyword"
        "kafka-producer" -> "Kafka Producer"
        "mqtt" -> "MQTT"
        "ntp" -> "NTP"
        "rabbitmq" -> "RabbitMQ"
        "sip-options" -> "SIP Options"
        "smtp" -> "SMTP"
        "snmp" -> "SNMP"
        "tailscale-ping" -> "Tailscale Ping"
        "gamedig" -> "GameDig"
        "steam" -> "Steam"
        "sqlserver" -> "SQL Server"
        "mongodb" -> "MongoDB"
        "mysql" -> "MySQL"
        "oracledb" -> "Oracle DB"
        "postgres" -> "PostgreSQL"
        "radius" -> "Radius"
        "redis" -> "Redis"
        else -> type
    }

    fun usesUrl(type: String) = type in setOf(
        "http", "keyword", "json-query", "real-browser", "websocket-upgrade",
    )

    fun usesHostname(type: String) = type in setOf(
        "port", "ping", "dns", "steam", "gamedig", "mqtt", "radius", "tailscale-ping",
        "smtp", "snmp", "sip-options", "sftp", "ntp",
    )

    fun usesPort(type: String) = type in setOf(
        "port", "steam", "gamedig", "mqtt", "radius", "smtp", "snmp", "sip-options", "sftp", "ntp",
    )

    fun isDatabase(type: String) = type in setOf(
        "postgres", "mysql", "mongodb", "sqlserver", "oracledb", "redis",
    )

    fun needsDescriptionOnly(type: String) = type in setOf("manual", "group")

    /** Uptime Kuma expects "ipv4"/"ipv6" here, not a bare 4/6. */
    val ipFamilyOptions = listOf("" to "Automatic", "ipv4" to "IPv4", "ipv6" to "IPv6")

    fun defaultPort(type: String, subtype: String? = null): Int = when (type) {
        "dns" -> 53
        "radius" -> 1812
        "snmp" -> 161
        "ntp" -> 123
        "sftp" -> 22
        "globalping" -> if (subtype == "ping") 80 else 0
        else -> 0
    }

    fun defaultTimeout(type: String): Int = when (type) {
        "ping" -> 10
        "snmp" -> 5
        "kafka-producer" -> 1
        else -> 48
    }

    fun defaultFor(type: String): JSONObject = JSONObject().apply {
        put("type", type)
        put("name", "")
        put("description", "")
        put("parent", JSONObject.NULL)
        put("url", if (type == "websocket-upgrade") "wss://" else "https://")
        put("hostname", "")
        put("port", defaultPort(type, "http"))
        put("method", "GET")
        put("body", "")
        put("headers", "")
        put("httpBodyEncoding", "json")
        put("authMethod", JSONObject.NULL)
        put("basic_auth_user", "")
        put("basic_auth_pass", "")
        put("bearer_token", "")
        put("authDomain", "")
        put("authWorkstation", "")
        put("oauth_auth_method", "client_secret_basic")
        put("oauth_token_url", "")
        put("oauth_client_id", "")
        put("oauth_client_secret", "")
        put("oauth_scopes", "")
        put("oauth_audience", "")
        put("tlsCa", "")
        put("tlsCert", "")
        put("tlsKey", "")
        put("keyword", "")
        put("invertKeyword", false)
        put("jsonPath", "$")
        put("jsonPathOperator", "==")
        put("expectedValue", "")
        put("expectedTlsAlert", "none")
        put("wsSubprotocol", "")
        put("wsIgnoreSecWebsocketAcceptHeader", false)
        put("grpcUrl", "")
        put("grpcProtobuf", false)
        put("grpcServiceName", "")
        put("grpcMethod", "")
        put("grpcBody", "")
        put("grpcEnableTls", false)
        put("grpcMetadata", "")
        put("screenshot_delay", 0)
        put("remote_browser", JSONObject.NULL)
        put("game", "")
        put("gamedigGivenPortOnly", true)
        put("gamedigToken", "")
        put("dns_resolve_type", "A")
        put("dns_resolve_server", "")
        put("ipFamily", JSONObject.NULL)
        put("location", "world")
        put("subtype", "http")
        put("protocol", JSONObject.NULL)
        put("docker_container", "")
        put("docker_host", JSONObject.NULL)
        put("system_service_name", "")
        put("mqttUsername", "")
        put("mqttPassword", "")
        put("mqttTopic", "")
        put("mqttWebsocketPath", "")
        put("mqttSuccessMessage", "")
        put("mqttCheckType", "keyword")
        put("rabbitmqNodes", JSONArray())
        put("rabbitmqUsername", "")
        put("rabbitmqPassword", "")
        put("kafkaProducerBrokers", JSONArray())
        put("kafkaProducerTopic", "")
        put("kafkaProducerMessage", "")
        put("kafkaProducerSsl", false)
        put("kafkaProducerAllowAutoTopicCreation", false)
        put("kafkaProducerSaslOptions", JSONObject().put("mechanism", "None"))
        put("radiusUsername", "")
        put("radiusPassword", "")
        put("radiusSecret", "")
        put("radiusCalledStationId", "")
        put("radiusCallingStationId", "")
        put("databaseConnectionString", "")
        put("databaseQuery", "")
        put("sshUsername", "")
        put("sshPassword", "")
        put("sshPrivateKey", "")
        put("sshPassphrase", "")
        put("sshAuthMethod", "password")
        put("sftpPath", "")
        put("smtpSecurity", "starttls")
        put("snmpVersion", if (type == "snmp") "2c" else "1")
        put("snmpOid", "")
        put("snmpV3Username", "")
        put("ntpStratumThreshold", 5)
        put("ntpTimeOffsetThreshold", 1000)
        put("ntpRootDispersionThreshold", 500)
        put("packetSize", 56)
        put("ping_count", 5)
        put("ping_numeric", false)
        put("ping_per_request_timeout", 0)
        // NTP servers may rate-limit frequent queries, so the web UI uses 5 minutes.
        put("interval", if (type == "ntp") 300 else 60)
        put("retryInterval", 60)
        put("resendInterval", 0)
        put("maxretries", 0)
        put("retryOnlyOnStatusCodeFailure", false)
        put("timeout", defaultTimeout(type))
        put("active", true)
        put("manual_status", 1)
        put("ignoreTls", false)
        put("upsideDown", false)
        put("expiryNotification", false)
        put("domainExpiryNotification", true)
        put("maxredirects", 10)
        put(
            "accepted_statuscodes",
            if (type == "websocket-upgrade") JSONArray().put("1000") else JSONArray().put("200-299"),
        )
        put("saveResponse", false)
        put("saveErrorResponse", true)
        put("responseMaxLength", 1024)
        put("cacheBust", false)
        put("proxyId", JSONObject.NULL)
        put("notificationIDList", JSONObject())
        put("conditions", JSONArray())
        put("tags", JSONArray())
        put("pushToken", "")
    }

    fun draft(type: String, existing: JSONObject?): JSONObject {
        val base = defaultFor(type)
        if (existing != null) {
            val keys = existing.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                base.put(key, existing.get(key))
            }
            if (!existing.has("type")) base.put("type", type)
        }
        return base
    }
}

fun JSONObject.numOr(key: String, fallback: Int): Int {
    if (isNull(key)) return fallback
    return optInt(key, fallback)
}

fun JSONObject.boolOr(key: String, fallback: Boolean = false): Boolean = bool(key, fallback)

fun JSONArray.stringList(): List<String> {
    val result = mutableListOf<String>()
    for (i in 0 until length()) {
        val value = opt(i)
        if (value != null && value != JSONObject.NULL) result.add(value.toString())
    }
    return result
}
