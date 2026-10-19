package com.rob.aikeyboard.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.rob.aikeyboard.ime.RobKeyboardService

/**
 * Fallback voice input: shows the system/Google "speak now" window. Used when the keyboard's
 * built-in SpeechRecognizer is not available on the device. The keyboard inserts the result
 * as soon as the text field regains focus.
 */
class VoiceActivity : ComponentActivity() {
    private val recognize = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { RobKeyboardService.pendingVoiceText = it }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val language = intent.getStringExtra(EXTRA_LANGUAGE) ?: "ru-RU"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите…")
        }
        try {
            recognize.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "На телефоне нет сервиса голосового ввода Google", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    companion object {
        const val EXTRA_LANGUAGE = "language"
    }
}
