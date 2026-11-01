package com.example.subtitles

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenCaptureArchitectureIntegrationTest {
    @Test fun dynamicPipelineClassesArePackagedInTestedApp() {
        assertNotNull(Class.forName("com.example.subtitles.ScreenCaptureService"))
        assertNotNull(Class.forName("com.example.subtitles.DynamicTextRecognizer"))
        assertNotNull(Class.forName("com.example.subtitles.DynamicPanelCoordinator"))
        assertNotNull(Class.forName("com.example.subtitles.DynamicPanelOverlay"))
    }

    @Test fun legacyFixedPanelClassesAreNotRequiredByDynamicPipeline() {
        val coordinatorConstructors = DynamicPanelCoordinator::class.java.declaredConstructors
        assertTrue(coordinatorConstructors.isNotEmpty())
        val parameterTypes = coordinatorConstructors.flatMap { it.parameterTypes.asList() }.map { it.simpleName }
        assertTrue("DynamicTextRecognizer" in parameterTypes)
        assertTrue("DynamicPanelOverlay" in parameterTypes)
        assertTrue("TaskPanelDetector" !in parameterTypes)
        assertTrue("TaskPanelOverlay" !in parameterTypes)
    }
}
