package de.spardirekt.ugcagent.v3.ai

import android.util.Log

object AppLog {
    fun event(
        operation: String,
        provider: String?,
        model: String?,
        httpStatus: Int?,
        durationMs: Long,
        imageCount: Int,
        payloadBytes: Long,
    ) {
        Log.i(
            "UgcV3",
            "op=$operation provider=${provider ?: "-"} model=${model ?: "-"} status=${httpStatus ?: "-"} durationMs=$durationMs images=$imageCount payloadBytes=$payloadBytes",
        )
    }
}
