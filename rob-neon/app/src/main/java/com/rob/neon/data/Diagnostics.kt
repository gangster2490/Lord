package com.rob.neon.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

enum class Match { NOT_INSTALLED, MATCHED, MISMATCH }

data class IconStatus(
    val entry: IconEntry,
    val match: Match,
    /** Launcher activities actually found on this phone for the entry's packages. */
    val launchers: List<String>,
)

/** Compares apps installed on THIS phone with the pack's component list. Read-only. */
object Diagnostics {
    @Suppress("DEPRECATION")
    fun scan(ctx: Context, entries: List<IconEntry>): List<IconStatus> {
        val pm = ctx.packageManager
        return entries.map { e ->
            val found = mutableListOf<String>()
            for (pkg in e.packages) {
                val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
                val list = if (Build.VERSION.SDK_INT >= 33) {
                    pm.queryIntentActivities(q, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    pm.queryIntentActivities(q, 0)
                }
                list.forEach { found += "$pkg/${it.activityInfo.name}" }
            }
            val set = e.components.toSet()
            val match = when {
                found.isEmpty() -> Match.NOT_INSTALLED
                found.any { it in set } -> Match.MATCHED
                else -> Match.MISMATCH
            }
            IconStatus(e, match, found)
        }
    }
}
