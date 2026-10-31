package com.rob.neon

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the REAL app (MainActivity + Compose UI) on the JVM with Robolectric native graphics.
 * This is NOT a device or emulator run. A few launcher activities are simulated as "installed"
 * so the diagnostics dots are visible; set -Drob.shots=<dir> to write PNGs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h892dp-440dpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun fakeInstall(pkg: String, cls: String) {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val sp = shadowOf(app.packageManager)
        val pi = PackageInfo().apply { packageName = pkg; applicationInfo = ApplicationInfo().apply { packageName = pkg } }
        sp.installPackage(pi)
        val ri = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = pkg; name = cls; applicationInfo = pi.applicationInfo } }
        sp.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg), ri)
    }

    private fun shot(name: String) {
        val dir = (System.getProperty("rob.shots") ?: "").ifEmpty { return }
        File(dir).mkdirs()
        repeat(8) { Thread.sleep(150); org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.waitForIdle() }
        rule.activityRule.scenario.onActivity { act ->
            val v = act.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(bmp))
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun run(tabs: List<String>, suffix: String) {
        fakeInstall("com.sec.android.app.camera", "com.sec.android.app.camera.Camera")
        fakeInstall("com.sec.android.gallery3d", "com.sec.android.gallery3d.app.GalleryActivity")
        fakeInstall("com.whatsapp", "com.whatsapp.Main")
        fakeInstall("org.telegram.messenger", "org.telegram.messenger.DefaultIcon")
        fakeInstall("com.google.android.youtube", "com.google.android.youtube.app.honeycomb.Shell\$HomeActivity")
        fakeInstall("com.spotify.music", "com.spotify.music.NewUnknownLauncherActivity") // simulated mismatch
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        shot("1_home_$suffix")
        rule.onNodeWithText(tabs[1]).performClick(); rule.waitForIdle(); shot("2_icons_$suffix")
        rule.onNodeWithText(tabs[2]).performClick(); rule.waitForIdle(); shot("3_wallpapers_$suffix")
        rule.onNodeWithText(tabs[3]).performClick(); rule.waitForIdle(); shot("4_howto_$suffix")
        rule.onNodeWithText(tabs[4]).performClick(); rule.waitForIdle(); shot("5_about_$suffix")
    }

    @Test @Config(qualifiers = "ru-w412dp-h892dp-440dpi")
    fun russian() = run(listOf("Главная", "Иконки", "Обои", "Шаги", "Инфо"), "ru")

    @Test @Config(qualifiers = "de-w412dp-h892dp-440dpi")
    fun german() = run(listOf("Start", "Symbole", "Wallpaper", "Schritte", "Info"), "de")

    @Test @Config(qualifiers = "ru-w412dp-h892dp-440dpi")
    fun searchFiltersCatalog() {
        rule.onNodeWithText("Иконки").performClick()
        rule.onNodeWithText("Поиск иконки или приложения").performTextInput("telegram")
        rule.waitForIdle()
        rule.onNodeWithText("Telegram").assertExists()
        shot("2b_icons_search_ru")
    }
}
