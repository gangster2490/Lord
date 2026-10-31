package com.rob.neon.system

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object Actions {
    const val THEME_PARK = "com.samsung.android.themedesigner"
    const val GOOD_LOCK = "com.samsung.android.goodlock"

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun start(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }

    fun openApp(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return start(ctx, i)
    }

    /** Opens the Theme Park listing in Galaxy Store, or the web page as a fallback. */
    fun openThemeParkStore(ctx: Context): Boolean {
        val store = Intent(Intent.ACTION_VIEW, Uri.parse("samsungapps://ProductDetail/$THEME_PARK"))
        if (start(ctx, store)) return true
        return start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://galaxystore.samsung.com/detail/$THEME_PARK")))
    }

    /** Copies the packaged wallpaper to cache and returns a content:// Uri (FileProvider, no storage permission). */
    fun wallpaperUri(ctx: Context, kind: String): Uri {
        val dir = File(ctx.cacheDir, "wallpapers").apply { mkdirs() }
        val f = File(dir, "rob_neon_$kind.jpg")
        ctx.assets.open("wallpapers/$kind.jpg").use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }

    /** System crop-and-set flow (WallpaperManager). The system dialog chooses home / lock / both. */
    fun cropAndSet(ctx: Context, kind: String): Boolean {
        val uri = wallpaperUri(ctx, kind)
        return try {
            val i = WallpaperManager.getInstance(ctx).getCropAndSetWallpaperIntent(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            start(ctx, i)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    /** "Set as" through any app that handles ACTION_ATTACH_DATA (e.g. the Samsung Gallery). */
    fun attachChooser(ctx: Context, kind: String): Boolean {
        val uri = wallpaperUri(ctx, kind)
        val i = Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(uri, "image/jpeg")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return start(ctx, Intent.createChooser(i, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    fun systemWallpaperPicker(ctx: Context): Boolean =
        start(ctx, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), null))
}
