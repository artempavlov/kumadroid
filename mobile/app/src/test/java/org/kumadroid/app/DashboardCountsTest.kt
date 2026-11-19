package org.kumadroid.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.kumadroid.app.data.Beat
import org.kumadroid.app.data.Monitor
import org.kumadroid.app.ui.dashboardCounts

class DashboardCountsTest {

    @Test
    fun `summary includes monitors nested inside groups`() {
        val group = Monitor(1, JSONObject().put("name", "Group").put("type", "group"))
        val groupedUp = Monitor(
            2,
            JSONObject().put("name", "Grouped up").put("type", "http").put("parent", 1).put("active", 1),
        )
        val groupedPaused = Monitor(
            3,
            JSONObject().put("name", "Grouped paused").put("type", "http").put("parent", 1).put("active", 0),
        )
        val rootDown = Monitor(
            4,
            JSONObject().put("name", "Root down").put("type", "http").put("active", 1),
        )
        val heartbeats = mapOf(
            1L to listOf(Beat(JSONObject().put("status", 0))),
            2L to listOf(Beat(JSONObject().put("status", 1))),
            3L to listOf(Beat(JSONObject().put("status", 1))),
            4L to listOf(Beat(JSONObject().put("status", 0))),
        )

        val counts = dashboardCounts(listOf(group, groupedUp, groupedPaused, rootDown), heartbeats)

        assertEquals(1, counts.up)
        assertEquals(1, counts.down)
        assertEquals(1, counts.paused)
    }

    @Test
    fun `summary ignores group rows and handles missing heartbeats`() {
        val group = Monitor(1, JSONObject().put("type", "group"))
        val pending = Monitor(2, JSONObject().put("type", "http").put("parent", 1))

        val counts = dashboardCounts(listOf(group, pending), emptyMap())

        assertEquals(0, counts.up)
        assertEquals(0, counts.down)
        assertEquals(0, counts.paused)
    }
}
