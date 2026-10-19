package com.rob.aikeyboard

import com.rob.aikeyboard.clipboard.ScreenshotWatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotWatcherTest {
    @Test fun recognisesScreenshotFolders() {
        assertTrue(ScreenshotWatcher.isScreenshot("Screenshot_20261019_121212_TikTok.jpg", "DCIM/Screenshots/"))
        assertTrue(ScreenshotWatcher.isScreenshot("PXL.png", "Pictures/Screenshots/"))
        assertFalse(ScreenshotWatcher.isScreenshot("20261019_121212.jpg", "DCIM/Camera/"))
        assertFalse(ScreenshotWatcher.isScreenshot("IMG-WA0001.jpg", "Android/media/com.whatsapp/WhatsApp/Media/"))
    }
}
