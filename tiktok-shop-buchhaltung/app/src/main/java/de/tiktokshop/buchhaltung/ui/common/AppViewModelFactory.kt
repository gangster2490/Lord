package de.tiktokshop.buchhaltung.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import de.tiktokshop.buchhaltung.TiktokBuchhaltungApplication

@Composable
fun rememberApp(): TiktokBuchhaltungApplication {
    val context = LocalContext.current.applicationContext
    return context as TiktokBuchhaltungApplication
}

fun Context.asApp(): TiktokBuchhaltungApplication = applicationContext as TiktokBuchhaltungApplication
