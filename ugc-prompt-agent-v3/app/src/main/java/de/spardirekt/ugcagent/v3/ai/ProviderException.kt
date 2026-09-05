package de.spardirekt.ugcagent.v3.ai

class ProviderException(
    val code: String,
    val statusLabel: String,
    message: String,
) : Exception(message) {
    companion object {
        fun missingKey() = ProviderException("NO_API_KEY", "Not Configured", "missing_api_key")
        fun invalidKey(raw: String = "") = ProviderException("INVALID_API_KEY", "Invalid Key", raw.ifBlank { "invalid_api_key" })
        fun rateLimited(raw: String = "") = ProviderException("RATE_LIMIT", "Rate Limited", raw.ifBlank { "rate_limited" })
        fun unavailable(raw: String = "") = ProviderException("MODEL_UNAVAILABLE", "Model Unavailable", raw.ifBlank { "model_unavailable" })
        fun timeout() = ProviderException("TIMEOUT", "Provider Error", "timeout")
        fun network() = ProviderException("NETWORK", "Provider Error", "no_internet")
        fun payload() = ProviderException("PAYLOAD", "Provider Error", "payload_too_large")
        fun parse(raw: String = "") = ProviderException("PARSE_ERROR", "Provider Error", raw.ifBlank { "invalid_json" })
        fun generic(raw: String = "") = ProviderException("GENERIC", "Provider Error", raw.ifBlank { "provider_error" })
        fun http(code: Int, raw: String): ProviderException = when (code) {
            401, 403 -> invalidKey(raw)
            404 -> unavailable(raw)
            408 -> timeout()
            413 -> payload()
            429 -> rateLimited(raw)
            else -> if (code >= 500) generic("HTTP $code: $raw") else generic("HTTP $code: $raw")
        }
    }
}

data class ApiImage(
    val id: String,
    val index: Int,
    val mime: String,
    val base64: String,
    val originalBytes: Long,
    val compressedBytes: Long,
    val width: Int,
    val height: Int,
)

data class PromptContext(
    val analysis: String,
    val scene: String,
    val speechLanguage: String,
    val captionLanguage: String,
    val targetGenerator: String,
    val strictProductLock: Boolean,
    val currentPrompt: String = "",
    val firstFrameNote: String = "Start from the selected original First Frame. Other references are supporting identity evidence. First Frame is the primary source of truth.",
    val fingerprint: String = "{}",
    val actionRisk: String = "{}",
    val readiness: String = "{}",
    val finalIdentityLock: String = "",
)
