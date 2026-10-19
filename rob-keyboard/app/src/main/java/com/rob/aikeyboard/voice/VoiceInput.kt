package com.rob.aikeyboard.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.rob.aikeyboard.text.Language

/** Wraps the system [SpeechRecognizer]. Must be used from the main thread. */
class VoiceInput(private val context: Context, private val callback: Callback) {

    interface Callback {
        fun onListening()
        fun onPartial(text: String)
        fun onLevel(rms: Float)
        fun onResult(text: String)
        /** @param recognizerProblem the recognition service itself failed (not just silence). */
        fun onError(message: String, recognizerProblem: Boolean)
    }

    private var recognizer: SpeechRecognizer? = null
    var isListening = false
        private set

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context) || services().isNotEmpty()

    /**
     * Installed recognition services, Google's first: the Samsung default often understands only
     * the phone's system language, while Google's handles Russian, German and English.
     * A null entry stands for the system default service.
     */
    private fun services(): List<ComponentName?> {
        val found = context.packageManager
            .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
        val ranked = found.sortedBy { c ->
            PREFERRED.indexOf(c.packageName).let { if (it < 0) PREFERRED.size else it }
        }
        return (ranked + listOf<ComponentName?>(null)).distinct()
    }

    fun start(language: Language, preferOffline: Boolean) {
        stop()
        startWith(services(), 0, language, preferOffline)
    }

    private fun startWith(candidates: List<ComponentName?>, index: Int, language: Language, preferOffline: Boolean) {
        val component = candidates[index]
        val r = runCatching {
            if (component != null) SpeechRecognizer.createSpeechRecognizer(context, component)
            else SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrNull()
        if (r == null) {
            if (index + 1 < candidates.size) startWith(candidates, index + 1, language, preferOffline)
            else callback.onError("Распознавание речи недоступно", true)
            return
        }
        recognizer = r
        var finished = false
        r.setRecognitionListener(object : RecognitionListener {
            // Events from a recogniser we already replaced, or after it reported an error, are ignored.
            private fun current() = recognizer === r && !finished

            override fun onReadyForSpeech(params: Bundle?) {
                if (!current()) return
                isListening = true
                callback.onListening()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { if (current()) callback.onLevel(rmsdB) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                if (!current()) return
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(callback::onPartial)
            }

            override fun onResults(results: Bundle?) {
                if (!current()) return
                finished = true
                isListening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) callback.onError("Речь не распознана", false) else callback.onResult(text)
            }

            override fun onError(error: Int) {
                if (!current()) return
                finished = true
                isListening = false
                val serviceProblem = error in SERVICE_ERRORS
                // This service cannot handle the language (or broke): quietly try the next one.
                if (serviceProblem && index + 1 < candidates.size) {
                    r.destroy()
                    recognizer = null
                    startWith(candidates, index + 1, language, preferOffline)
                    return
                }
                callback.onError(errorText(error), serviceProblem)
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.bcp47)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.bcp47)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        r.startListening(intent)
    }

    /** Stops listening; the recogniser still delivers a final result for what was heard. */
    fun stopListening() {
        recognizer?.stopListening()
    }

    fun stop() {
        isListening = false
        recognizer?.run {
            cancel()
            destroy()
        }
        recognizer = null
    }

    companion object {
        private val PREFERRED = listOf(
            "com.google.android.googlequicksearchbox", // Google app
            "com.google.android.tts", // Speech Services by Google (on-device)
            "com.google.android.as",
        )
        private val SERVICE_ERRORS = setOf(
            SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_SERVER,
            11, 12, 13, // SERVER_DISCONNECTED, LANGUAGE_NOT_SUPPORTED, LANGUAGE_UNAVAILABLE
        )
    }

    private fun errorText(code: Int) = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Речь не распознана"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Нет сети. Для офлайн-распознавания установите языковой пакет в настройках распознавания речи."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения на микрофон"
        SpeechRecognizer.ERROR_AUDIO -> "Ошибка записи звука"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят, попробуйте ещё раз"
        12, 13 -> "Этот язык не поддерживается распознаванием речи на телефоне — попробуйте «Голосовой ввод Google»"
        else -> "Ошибка распознавания ($code)"
    }
}
