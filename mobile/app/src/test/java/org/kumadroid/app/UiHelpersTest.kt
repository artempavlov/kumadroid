package org.kumadroid.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.kumadroid.app.data.MonitorCatalog
import org.kumadroid.app.data.chromeTestMessage
import org.kumadroid.app.data.runtimeLabel

class UiHelpersTest {

    @Test
    fun `runtime label combines platform and architecture`() {
        val raw = JSONObject()
            .put("runtime", JSONObject().put("platform", "linux").put("arch", "x64"))
        assertEquals("linux/x64", runtimeLabel(raw))
    }

    @Test
    fun `runtime label is empty when the server omits runtime`() {
        assertEquals("", runtimeLabel(JSONObject()))
        assertEquals("", runtimeLabel(JSONObject().put("runtime", JSONObject.NULL)))
    }

    @Test
    fun `chrome test unwraps the i18n message object`() {
        val msg = JSONObject()
            .put("key", "foundChromiumVersion")
            .put("values", JSONArray().put("Chromium 120.0"))
        assertEquals("Chromium 120.0", chromeTestMessage(msg))
    }

    @Test
    fun `chrome test keeps a plain string message`() {
        assertEquals("Chrome works", chromeTestMessage("Chrome works"))
        assertEquals("Chrome test passed", chromeTestMessage(null))
    }

    @Test
    fun `ip family options use the values expected by uptime kuma`() {
        assertEquals(listOf("", "ipv4", "ipv6"), MonitorCatalog.ipFamilyOptions.map { it.first })
    }
}
