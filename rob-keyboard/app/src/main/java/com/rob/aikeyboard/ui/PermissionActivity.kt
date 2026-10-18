package com.rob.aikeyboard.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Transparent activity: a keyboard cannot show permission dialogs itself, so the microphone
 * permission is requested here, only when the user first taps the mic.
 */
class PermissionActivity : ComponentActivity() {
    private val request = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(
            this,
            if (granted) "Микрофон разрешён — нажмите 🎤 ещё раз" else "Без разрешения голосовой ввод недоступен",
            Toast.LENGTH_LONG,
        ).show()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permission = intent.getStringExtra(EXTRA_PERMISSION) ?: return finish()
        if (savedInstanceState == null) request.launch(permission)
    }

    companion object {
        const val EXTRA_PERMISSION = "permission"
    }
}
