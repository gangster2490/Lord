package de.spardirekt.veoprompt.ultra.diagnostics

import android.util.Log

object DebugLog {
    @Volatile var enabled: Boolean = false
    @Volatile var lastSafeError: String = ""
        private set

    private const val TAG = "VeoPromptUltra"

    fun d(message: String) {
        if (!enabled) return
        runCatching { Log.d(TAG, sanitize(message)) }
    }

    fun e(message: String, t: Throwable? = null) {
        val safe = sanitize(message)
        lastSafeError = safe
        if (enabled) runCatching { Log.e(TAG, safe, t) }
    }

    fun recordSafeError(message: String) {
        lastSafeError = sanitize(message)
    }

    fun sanitize(text: String): String = ErrorMapper.sanitize(text)
}
