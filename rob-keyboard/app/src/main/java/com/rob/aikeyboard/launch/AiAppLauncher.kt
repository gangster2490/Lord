package com.rob.aikeyboard.launch

import android.content.Context
import android.content.Intent
import android.net.Uri

enum class AiApp(val label: String, val packageName: String, val website: String) {
    CHATGPT("ChatGPT", "com.openai.chatgpt", "https://chatgpt.com"),
    CLAUDE("Claude", "com.anthropic.claude", "https://claude.ai"),
    GEMINI("Gemini", "com.google.android.apps.bard", "https://gemini.google.com"),
}

/** Opens the official AI apps through a normal launch intent. No API keys involved. */
object AiAppLauncher {

    fun isInstalled(context: Context, app: AiApp): Boolean =
        context.packageManager.getLaunchIntentForPackage(app.packageName) != null

    /** @return false if the app is not installed (caller then offers the website). */
    fun open(context: Context, app: AiApp): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun openWebsite(context: Context, app: AiApp) = view(context, app.website)

    fun openStore(context: Context, app: AiApp) {
        if (!view(context, "market://details?id=${app.packageName}")) {
            view(context, "https://play.google.com/store/apps/details?id=${app.packageName}")
        }
    }

    private fun view(context: Context, url: String): Boolean = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
