package org.kumadroid.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kumadroid.app.data.MonitorCatalog
import org.kumadroid.app.data.bool
import org.kumadroid.app.data.num
import org.kumadroid.app.data.str
import org.kumadroid.app.data.stringList

class MonitorCatalogTest {

    @Test
    fun `every catalog entry has a readable label`() {
        assertTrue(MonitorCatalog.types.size >= 30)
        MonitorCatalog.types.forEach { type ->
            val label = MonitorCatalog.label(type)
            assertTrue("missing label for $type", label.isNotBlank())
            assertNotEquals(type, label)
        }
    }

    @Test
    fun `http defaults match the server contract`() {
        val monitor = MonitorCatalog.defaultFor("http")

        assertEquals("http", monitor.str("type"))
        assertEquals("http://", monitor.str("url"))
        assertEquals("GET", monitor.str("method"))
        assertEquals("none", monitor.str("expectedTlsAlert"))
        assertEquals(60, monitor.num("interval"))
        assertEquals(48, monitor.num("timeout"))
        assertEquals(10, monitor.num("maxredirects"))
        assertEquals(1024, monitor.num("responseMaxLength"))
        assertTrue(monitor.bool("active"))
        assertFalse(monitor.bool("ignoreTls"))
        assertEquals("200-299", monitor.getJSONArray("accepted_statuscodes").getString(0))
    }

    @Test
    fun `type specific defaults are present`() {
        assertEquals(1, MonitorCatalog.defaultFor("manual").num("manual_status"))
        assertEquals("", MonitorCatalog.defaultFor("push").str("pushToken"))
        assertEquals("A", MonitorCatalog.defaultFor("dns").str("dns_resolve_type"))
        assertEquals("starttls", MonitorCatalog.defaultFor("smtp").str("smtpSecurity"))
        assertEquals("client_secret_basic", MonitorCatalog.defaultFor("http").str("oauth_auth_method"))
    }

    @Test
    fun `draft preserves unknown server fields and existing values`() {
        val existing = JSONObject()
            .put("type", "keyword")
            .put("name", "API health")
            .put("keyword", "healthy")
            .put("futureServerField", 42)
            .put("notificationIDList", JSONObject().put("7", true))

        val draft = MonitorCatalog.draft("keyword", existing)

        assertEquals("keyword", draft.str("type"))
        assertEquals("API health", draft.str("name"))
        assertEquals("healthy", draft.str("keyword"))
        assertEquals(42, draft.num("futureServerField"))
        assertTrue(draft.getJSONObject("notificationIDList").getBoolean("7"))
        // Defaults not supplied by the server are still filled in.
        assertEquals("GET", draft.str("method"))
    }

    @Test
    fun `draft keeps the requested type when an old row has none`() {
        val existing = JSONObject().put("name", "Legacy")
        val draft = MonitorCatalog.draft("port", existing)
        assertEquals("port", draft.str("type"))
        assertEquals("Legacy", draft.str("name"))
    }

    @Test
    fun `push tokens are 32 alphanumeric characters and unique`() {
        val tokens = List(200) { MonitorCatalog.newPushToken() }
        tokens.forEach { token ->
            assertEquals(32, token.length)
            assertTrue(token.all { it.isLetterOrDigit() })
        }
        assertEquals(tokens.size, tokens.toSet().size)
    }

    @Test
    fun `string list skips JSON null values`() {
        val array = JSONArray().put("a").put(JSONObject.NULL).put("b")
        assertEquals(listOf("a", "b"), array.stringList())
    }
}
