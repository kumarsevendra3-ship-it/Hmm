package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.actions.DeviceActionExecutor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Arushi", appName)
    }

    @Test
    fun `device action executor handles openWhatsApp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = DeviceActionExecutor(context)
        val result = executor.execute("openWhatsApp", JSONObject())
        assertNotNull(result)
        assertNotNull(result.outputJson)
    }

    @Test
    fun `device action executor handles unknown tool`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = DeviceActionExecutor(context)
        val result = executor.execute("unknownTool", JSONObject())
        assertEquals(false, result.success)
    }
}
