package org.kumadroid.app

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.kumadroid.app.ui.Navigator
import org.kumadroid.app.ui.NavigatorSaver
import org.kumadroid.app.ui.Screen

class NavigatorSaverTest {

    private val scope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    @Test
    fun `round trips every navigation argument`() {
        val navigator = Navigator()
        navigator.push(Screen.MonitorDetail(42))
        navigator.push(Screen.MonitorEdit(id = 7, parent = 3, cloneFrom = 9))
        navigator.push(Screen.NotificationEdit("""{"name":"Discord","type":"discord"}"""))
        navigator.push(Screen.MaintenanceEdit(id = 5, cloneFrom = 6))
        navigator.push(Screen.StatusPageEdit("status-page"))

        val encoded = with(NavigatorSaver) { scope.save(navigator) }
        assertNotNull(encoded)

        val restored = NavigatorSaver.restore(encoded!!)
        assertNotNull(restored)
        assertEquals(navigator.stack.toList(), restored!!.stack.toList())
    }
}
