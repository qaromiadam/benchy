package com.benchy.X

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.benchy.X.notifications.SoundGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `app name and generated wav alerts exist`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Benchy", appName)

        val (goodWav, badWav) = SoundGenerator.ensureSoundFiles(context)
        assertTrue(goodWav.exists() && goodWav.length() > 44)
        assertTrue(badWav.exists() && badWav.length() > 44)
    }
}
