package org.kumadroid.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kumadroid.app.data.Beat
import org.kumadroid.app.data.KumaStatus
import org.kumadroid.app.data.Monitor
import org.kumadroid.app.data.bool
import org.kumadroid.app.data.deepCopy
import org.kumadroid.app.data.dbl
import org.kumadroid.app.data.long
import org.kumadroid.app.data.num
import org.kumadroid.app.data.str

class ModelsTest {

    @Test
    fun `json helpers treat missing and null as defaults`() {
        val json = JSONObject().put("nullValue", JSONObject.NULL)

        assertEquals("fallback", json.str("missing", "fallback"))
        assertEquals("fallback", json.str("nullValue", "fallback"))
        assertEquals(9, json.num("missing", 9))
        assertEquals(9L, json.long("missing", 9L))
        assertNull(json.dbl("missing"))
        assertFalse(json.bool("missing"))
        assertTrue(json.bool("missing", true))
    }

    @Test
    fun `json helpers accept sqlite style scalars`() {
        val json = JSONObject()
            .put("one", 1)
            .put("zero", 0)
            .put("trueString", "true")
            .put("falseString", "false")
            .put("float", 12.5)

        assertTrue(json.bool("one"))
        assertFalse(json.bool("zero"))
        assertTrue(json.bool("trueString"))
        assertFalse(json.bool("falseString"))
        assertEquals(12.5, json.dbl("float")!!, 0.0)
    }

    @Test
    fun `deep copy does not mutate live state`() {
        val original = JSONObject().put("name", "A").put("nested", JSONObject().put("x", 1))
        val copy = original.deepCopy()
        copy.put("name", "B")
        copy.getJSONObject("nested").put("x", 2)

        assertEquals("A", original.str("name"))
        assertEquals(1, original.getJSONObject("nested").getInt("x"))
    }

    @Test
    fun `monitor exposes server fields and display target`() {
        val http = Monitor(1, JSONObject().put("name", "Web").put("type", "http").put("url", "https://example.com"))
        assertEquals("Web", http.name)
        assertEquals("https://example.com", http.displayTarget())

        val port = Monitor(2, JSONObject().put("type", "port").put("hostname", "db.local").put("port", 5432))
        assertEquals("db.local:5432", port.displayTarget())

        val push = Monitor(3, JSONObject().put("type", "push"))
        assertEquals("", push.displayTarget())
    }

    @Test
    fun `monitor children and tags are parsed defensively`() {
        val json = JSONObject()
            .put("type", "group")
            .put("childrenIDs", org.json.JSONArray().put(4).put(5))
            .put("tags", org.json.JSONArray().put(JSONObject().put("tag_id", 7).put("name", "prod")))
        val monitor = Monitor(1, json)

        assertEquals(listOf(4L, 5L), monitor.childrenIds)
        assertEquals(7L, monitor.tags.single().id)
        assertEquals("prod", monitor.tags.single().name)
    }

    @Test
    fun `beat parses sqlite and iso timestamps`() {
        val sqlite = Beat(JSONObject().put("time", "2026-01-02 03:04:05"))
        assertTrue(sqlite.timeMillis > 0)
        assertTrue(sqlite.timeLabel().isNotBlank())

        val iso = Beat(JSONObject().put("time", "2026-01-02T03:04:05Z"))
        assertEquals(sqlite.timeMillis, iso.timeMillis)

        val invalid = Beat(JSONObject().put("time", "not-a-date"))
        assertEquals(0L, invalid.timeMillis)
    }

    @Test
    fun `status labels cover all kuma states`() {
        assertEquals("Up", KumaStatus.label(KumaStatus.UP))
        assertEquals("Down", KumaStatus.label(KumaStatus.DOWN))
        assertEquals("Pending", KumaStatus.label(KumaStatus.PENDING))
        assertEquals("Maintenance", KumaStatus.label(KumaStatus.MAINTENANCE))
        assertEquals("Unknown", KumaStatus.label(99))
    }
}
