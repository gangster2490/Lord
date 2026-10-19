package com.rob.aikeyboard.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
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

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(language: Language, preferOffline: Boolean) {
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true; callback.onListening() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) = callback.onLevel(rmsdB)
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(callback::onPartial)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) callback.onError("Речь не распознана", false) else callback.onResult(text)
            }

            override fun onError(error: Int) {
                isListening = false
                val serviceProblem = error in setOf(
                    SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT, 11, 12, 13, // SERVER_DISCONNECTED, LANGUAGE_NOT_SUPPORTED/UNAVAILABLE
                )
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

    private fun errorText(code: Int) = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Речь не распознана"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Нет сети. Для офлайн-распознавания установите языковой пакет в настройках распознавания речи."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения на микрофон"
        SpeechRecognizer.ERROR_AUDIO -> "Ошибка записи звука"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят, попробуйте ещё раз"
        12, 13 -> "Язык не поддерживается распознавателем на этом устройстве" // ERROR_LANGUAGE_NOT_SUPPORTED / _UNAVAILABLE
        else -> "Ошибка распознавания ($code)"
    }
}
