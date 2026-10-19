package com.rob.aikeyboard.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rob.aikeyboard.launch.AiApp
import com.rob.aikeyboard.text.Language

enum class Panel { KEYBOARD, EMOJI, CLIPBOARD, AI, TRANSLATE, VOICE }

/** A text box inside the keyboard (emoji search, translator source) that receives key presses. */
class InternalField(val hint: String) {
    var text by mutableStateOf("")
}

/** Lets the user take back a one-tap correction. */
data class QuickFixUndo(val original: String, val fixed: String)

class VoiceState {
    var listening by mutableStateOf(false)
    var partial by mutableStateOf("")
    var level by mutableStateOf(0f)
    var error by mutableStateOf<String?>(null)
    var lastInserted by mutableStateOf<String?>(null)
    var language by mutableStateOf(Language.RU)
    /** Write the dictation in this language (translated offline); null = as spoken. */
    var output by mutableStateOf<Language?>(null)
    var translating by mutableStateOf(false)
    var missingModels by mutableStateOf<Set<Language>>(emptySet())
    /** The built-in recogniser failed; offer Google's voice input window instead. */
    var offerFallback by mutableStateOf(false)
}

/** Observable UI state of the keyboard window, owned by [RobKeyboardService]. */
class ImeState {
    var panel by mutableStateOf(Panel.KEYBOARD)
    var suggestions by mutableStateOf<List<String>>(emptyList())
    /** Index in [suggestions] that will be applied automatically on space, or -1. */
    var autoCorrectIndex by mutableIntStateOf(-1)
    var language by mutableStateOf(Language.RU)
    var autoEmoji by mutableStateOf(false)
    var internalField by mutableStateOf<InternalField?>(null)
    var missingApp by mutableStateOf<AiApp?>(null)
    var message by mutableStateOf<String?>(null)
    var keyboardHeightPx by mutableIntStateOf(0)
    var clipboardRevision by mutableIntStateOf(0)
    /** True in password and other sensitive fields: no AI, no history, no suggestions. */
    var sensitiveField by mutableStateOf(false)
    var showAppsRow by mutableStateOf(false)
    var quickFixUndo by mutableStateOf<QuickFixUndo?>(null)
    var quickFixBusy by mutableStateOf(false)
    /** Screenshot just taken, offered for one-tap insertion. */
    var newScreenshot by mutableStateOf<com.rob.aikeyboard.clipboard.ClipItem?>(null)
    val voice = VoiceState()
}
