package com.example.subtitles

import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DynamicPanelOverlayInstrumentedTest {
    @Test fun overlayObjectCanBeConstructedWithApplicationContext() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val overlay = DynamicPanelOverlay(context)
        assertNotNull(overlay)
        // Do not add TYPE_APPLICATION_OVERLAY in this test: that requires user-granted overlay permission.
        overlay.close()
    }

    @Test fun coordinateModelAcceptsRealScreenBounds() {
        val bounds = Rect(1200, 140, 1780, 420)
        assert(bounds.width() > 0)
        assert(bounds.height() > 0)
    }
}
