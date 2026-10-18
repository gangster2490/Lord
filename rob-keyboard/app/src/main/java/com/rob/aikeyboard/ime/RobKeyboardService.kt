package com.rob.aikeyboard.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.rob.aikeyboard.RobApp
import com.rob.aikeyboard.clipboard.ClipItem
import com.rob.aikeyboard.data.SpaceSwipe
import com.rob.aikeyboard.data.Vibration
import com.rob.aikeyboard.emoji.AutoEmoji
import com.rob.aikeyboard.keyboard.EnterAction
import com.rob.aikeyboard.keyboard.Key
import com.rob.aikeyboard.keyboard.KeyType
import com.rob.aikeyboard.keyboard.KeyboardLayout
import com.rob.aikeyboard.keyboard.KeyboardView
import com.rob.aikeyboard.keyboard.Layouts
import com.rob.aikeyboard.keyboard.ShiftState
import com.rob.aikeyboard.launch.AiApp
import com.rob.aikeyboard.launch.AiAppLauncher
import com.rob.aikeyboard.text.Language
import com.rob.aikeyboard.ui.PermissionActivity
import com.rob.aikeyboard.ui.PhotoPickerActivity
import com.rob.aikeyboard.ui.ime.ImeRoot
import com.rob.aikeyboard.voice.VoiceInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.BreakIterator

class RobKeyboardService : InputMethodService(), KeyboardView.Listener, VoiceInput.Callback, LifecycleOwner {

    private val app get() = RobApp.instance
    private val prefs get() = app.prefs
    val state = ImeState()
    private val owner = ImeLifecycleOwner()
    override val lifecycle: Lifecycle get() = owner.lifecycle

    lateinit var keyboardView: KeyboardView
        private set
    private lateinit var voice: VoiceInput
    private lateinit var clipboardManager: ClipboardManager

    // ---- Editor state ----
    private val composing = StringBuilder()
    private var shift = ShiftState.OFF
    private var lastShiftTap = 0L
    private var alphaLayout = true
    private var isPassword = false
    private var suggestionsAllowed = true
    private var autoCorrectAllowed = true
    private var learningAllowed = true
    private var autoCapsAllowed = true
    private var technicalField = false
    private var autoSpaceInserted = false
    private var lastSpaceAt = 0L
    private var ignoreNextClip = false
    private var undo: Undo? = null

    // Suggestions are computed off the main thread; a newer request makes older results stale.
    private val suggestExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var suggestGeneration = 0
    private var lastResult: com.rob.aikeyboard.text.SuggestResult? = null

    private sealed class Undo {
        data class AutoCorrect(val original: String, val corrected: String, val separator: String) : Undo()
        data class AutoEmoji(val inserted: String, val separator: String) : Undo()
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applyPrefs() }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClipboard() }

    // ---- Lifecycle ----

    override fun onCreate() {
        super.onCreate()
        owner.onCreate()
        voice = VoiceInput(this, this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        prefs.raw.registerOnSharedPreferenceChangeListener(prefListener)
        app.clipboard.addListener(clipChanged)
        keyboardView = KeyboardView(this, this)
        // Panels (emoji, clipboard, AI) take the same height as the keys so nothing jumps.
        keyboardView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.height > 0 && v.height != state.keyboardHeightPx) state.keyboardHeightPx = v.height
        }
        state.language = prefs.currentLanguage
        state.voice.language = state.language
        applyPrefs()
    }

    private val clipChanged: () -> Unit = { keyboardView.post { state.clipboardRevision++ } }

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        prefs.raw.unregisterOnSharedPreferenceChangeListener(prefListener)
        app.clipboard.removeListener(clipChanged)
        voice.stop()
        suggestExecutor.shutdownNow()
        app.io.execute { app.userModel.saveIfDirty() }
        owner.onDestroy()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        window.window?.decorView?.let(owner::attachTo)
        (keyboardView.parent as? android.view.ViewGroup)?.removeView(keyboardView)
        return ComposeView(this).apply {
            owner.attachTo(this)
            setContent { ImeRoot(this@RobKeyboardService) }
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        owner.onResume()
        composing.clear()
        undo = null
        autoSpaceInserted = false
        state.message = null
        state.missingApp = null
        state.quickFixUndo = null
        configureForEditor(info)
        if (!restarting) {
            state.panel = Panel.KEYBOARD
            state.internalField = null
        }
        updateShift(auto = true)
        showPredictions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        currentInputConnection?.finishComposingText()
        composing.clear()
        if (voice.isListening) voice.stop()
        state.voice.listening = false
        owner.onPause()
        app.io.execute { app.userModel.saveIfDirty() }
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // The user moved the cursor (or the app changed the text): drop our composing word.
        val cursorOutsideComposing = composing.isNotEmpty() &&
            (newSelStart != newSelEnd || newSelStart != candidatesEnd || candidatesStart < 0)
        if (cursorOutsideComposing) {
            composing.clear()
            currentInputConnection?.finishComposingText()
            undo = null
            showPredictions()
        }
        if (composing.isEmpty() && (newSelStart != oldSelStart || newSelEnd != oldSelEnd)) updateShift(auto = true)
    }

    private fun configureForEditor(info: EditorInfo) {
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        isPassword = (cls == InputType.TYPE_CLASS_TEXT && variation in PASSWORD_VARIATIONS) ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val noSuggestionsFlag = info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
        val technical = cls == InputType.TYPE_CLASS_TEXT && variation in TECHNICAL_VARIATIONS
        // Search boxes (Google, browsers) set NO_SUGGESTIONS because they list their own results.
        // Like the Samsung keyboard we still show the strip there, but never replace words on our own.
        suggestionsAllowed = cls == InputType.TYPE_CLASS_TEXT && !isPassword && prefs.showSuggestions
        autoCorrectAllowed = suggestionsAllowed && !technical && !noSuggestionsFlag && prefs.autoCorrect
        learningAllowed = !isPassword && prefs.personalization &&
            info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
        autoCapsAllowed = prefs.autoCapitalize && !isPassword && !technical
        technicalField = technical || noSuggestionsFlag
        state.sensitiveField = isPassword

        val layout: KeyboardLayout = when (cls) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> Layouts.numberPad
            InputType.TYPE_CLASS_PHONE -> Layouts.phonePad
            else -> Layouts.forLanguage(state.language)
        }
        alphaLayout = layout.id == state.language.code
        keyboardView.layout = layout
        keyboardView.enterAction = enterActionFor(info)
        state.suggestions = emptyList()
    }

    private fun enterActionFor(info: EditorInfo): EnterAction {
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EnterAction.NEWLINE
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> EnterAction.GO
            EditorInfo.IME_ACTION_SEARCH -> EnterAction.SEARCH
            EditorInfo.IME_ACTION_SEND -> EnterAction.SEND
            EditorInfo.IME_ACTION_NEXT -> EnterAction.NEXT
            EditorInfo.IME_ACTION_DONE -> EnterAction.DONE
            EditorInfo.IME_ACTION_PREVIOUS -> EnterAction.PREVIOUS
            else -> EnterAction.NEWLINE
        }
    }

    private fun applyPrefs() {
        state.autoEmoji = prefs.autoEmoji
        keyboardView.heightScale = prefs.keyHeightScale
        keyboardView.showHints = prefs.numberHints
        keyboardView.spaceSwipeMovesCursor = prefs.spaceSwipe == SpaceSwipe.CURSOR
        keyboardView.showLanguageArrows = prefs.spaceSwipe == SpaceSwipe.LANGUAGE && prefs.enabledLanguages.size > 1
        if (state.language !in prefs.enabledLanguages) setLanguage(prefs.currentLanguage)
        keyboardView.spaceLabel = state.language.displayName
    }

    // ---- KeyboardView.Listener ----

    override fun onKeyDown(key: Key) = feedback(key)

    override fun onText(text: String) {
        state.internalField?.let { field ->
            field.text += text
            return
        }
        val ic = currentInputConnection ?: return
        state.quickFixUndo = null
        val ch = text.singleOrNull()
        when {
            ch != null && (ch.isLetter() || (ch == '\'' && composing.isNotEmpty())) && suggestionsAllowed -> {
                undo = null
                autoSpaceInserted = false
                composing.append(text)
                ic.setComposingText(composing, 1)
                if (shift == ShiftState.ON) updateShift(auto = false, force = ShiftState.OFF)
                updateSuggestions()
            }
            ch != null && ch in WORD_SEPARATORS -> finishWord(text)
            else -> {
                // Digits, symbols, emoji and all input in fields without suggestions.
                commitComposing()
                undo = null
                autoSpaceInserted = false
                ic.commitText(text, 1)
                if (shift == ShiftState.ON && ch?.isLetter() == true) updateShift(auto = false, force = ShiftState.OFF)
                updateShift(auto = true)
                showPredictions()
            }
        }
    }

    override fun onFunction(type: KeyType) {
        when (type) {
            KeyType.DELETE -> onDelete()
            KeyType.SHIFT -> onShiftTap()
            KeyType.ENTER -> onEnter()
            KeyType.SYMBOLS -> { keyboardView.layout = Layouts.symbols; alphaLayout = false }
            KeyType.SYMBOLS_MORE -> { keyboardView.layout = Layouts.symbolsMore; alphaLayout = false }
            KeyType.ALPHA -> { keyboardView.layout = Layouts.forLanguage(state.language); alphaLayout = true; updateShift(auto = true) }
            KeyType.EMOJI -> togglePanel(Panel.EMOJI)
            else -> {}
        }
    }

    override fun onShiftLock() = updateShift(auto = false, force = ShiftState.LOCKED)

    override fun onSpaceSwipe(direction: Int) {
        val langs = prefs.enabledLanguages
        if (langs.size < 2) return
        val i = langs.indexOf(state.language).coerceAtLeast(0)
        setLanguage(langs[(i + direction + langs.size) % langs.size])
    }

    override fun onCursorMove(steps: Int) {
        val ic = currentInputConnection ?: return
        commitComposing()
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(steps)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    override fun onDeleteWord() {
        state.internalField?.let { f -> f.text = f.text.trimEnd().substringBeforeLast(' ', "").let { if (it.isEmpty()) it else "$it " }; return }
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            composing.clear()
            ic.commitText("", 1)
        } else {
            val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
            val trimmed = before.trimEnd()
            val wordStart = trimmed.indexOfLast { it.isWhitespace() } + 1
            val count = before.length - wordStart
            if (count > 0) ic.deleteSurroundingText(count, 0) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
        undo = null
        updateShift(auto = true)
        showPredictions()
    }

    override fun onVoice() = togglePanel(Panel.VOICE)

    // ---- Text editing ----

    private fun onDelete() {
        state.internalField?.let { f ->
            if (f.text.isNotEmpty()) f.text = f.text.substring(0, f.text.length - lastGraphemeLength(f.text))
            return
        }
        val ic = currentInputConnection ?: return
        state.quickFixUndo = null
        autoSpaceInserted = false
        if (performUndo(ic)) return
        if (composing.isNotEmpty()) {
            composing.setLength(composing.length - lastGraphemeLength(composing.toString()))
            if (composing.isEmpty()) ic.commitText("", 1) else ic.setComposingText(composing, 1)
            updateSuggestions()
            return
        }
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            val before = ic.getTextBeforeCursor(16, 0)
            if (before.isNullOrEmpty()) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            } else {
                ic.deleteSurroundingText(lastGraphemeLength(before.toString()), 0)
            }
        }
        updateShift(auto = true)
        showPredictions()
    }

    /** Backspace right after an automatic change reverts it. */
    private fun performUndo(ic: InputConnection): Boolean {
        val u = undo ?: return false
        undo = null
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return false
        when (u) {
            is Undo.AutoEmoji -> if (before.endsWith(u.inserted)) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(u.inserted.length, 0)
                ic.commitText(u.separator, 1)
                ic.endBatchEdit()
                return true
            }
            is Undo.AutoCorrect -> if (before.endsWith(u.corrected + u.separator)) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(u.corrected.length + u.separator.length, 0)
                composing.clear()
                composing.append(u.original)
                ic.setComposingText(composing, 1)
                ic.endBatchEdit()
                if (learningAllowed) app.userModel.model.addWord(u.original)
                updateSuggestions()
                return true
            }
        }
        return false
    }

    private fun lastGraphemeLength(text: String): Int {
        if (text.isEmpty()) return 0
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val end = it.last()
        val start = it.previous()
        return if (start == BreakIterator.DONE) 1 else end - start
    }

    private fun finishWord(separator: String) {
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()
        val sep = separator.first()

        // Double space → ". "
        if (sep == ' ' && composing.isEmpty() && prefs.doubleSpacePeriod && now - lastSpaceAt < 450 && !isPassword) {
            val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (before.length == 2 && before[1] == ' ' && (before[0].isLetterOrDigit() || AutoEmoji.containsEmoji(before))) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                lastSpaceAt = 0
                undo = null
                updateShift(auto = true)
                showPredictions()
                return
            }
        }
        lastSpaceAt = if (sep == ' ') now else 0

        // "word |," → "word, " after a picked suggestion added a space.
        if (autoSpaceInserted && composing.isEmpty() && sep in ".,!?;:") {
            val before = ic.getTextBeforeCursor(1, 0)
            if (before == " ") ic.deleteSurroundingText(1, 0)
            autoSpaceInserted = false
            ic.commitText("$separator ", 1)
            updateShift(auto = true)
            showPredictions()
            return
        }
        autoSpaceInserted = false

        val word = composing.toString()
        ic.beginBatchEdit()
        var finalWord = word
        undo = null
        if (word.isNotEmpty()) {
            suggestGeneration++
            val result = lastResult?.takeIf { it.typed == word }
                ?: app.engine.suggest(word, suggestionLanguages(), autoCorrectAllowed)
            val correction = if (autoCorrectAllowed && sep != '\'') result.autoCorrection else null
            finalWord = correction ?: word
            ic.commitText(finalWord, 1)
            composing.clear()
            if (correction != null && correction != word) undo = Undo.AutoCorrect(word, correction, separator)
            learn(finalWord)
        }

        val emoji = if (state.autoEmoji && suggestionsAllowed && !technicalField && finalWord.isNotEmpty()) {
            val before = ic.getTextBeforeCursor(120, 0)?.toString().orEmpty()
            app.autoEmoji.onWordFinished(before, sep, prefs.emojiIntensity, detectLanguage(before))
        } else null

        if (emoji != null) {
            val inserted = when (emoji.placement) {
                AutoEmoji.Placement.BEFORE_SEPARATOR -> " ${emoji.emoji}$separator"
                AutoEmoji.Placement.AFTER_SEPARATOR -> "$separator ${emoji.emoji}"
            }
            ic.commitText(inserted, 1)
            undo = Undo.AutoEmoji(inserted, separator)
        } else {
            ic.commitText(separator, 1)
        }
        ic.endBatchEdit()
        updateShift(auto = true)
        showPredictions()
    }

    /** Keeps the word being typed as-is (no autocorrect), e.g. before a symbol or panel switch. */
    private fun commitComposing() {
        if (composing.isEmpty()) return
        currentInputConnection?.finishComposingText()
        learn(composing.toString())
        composing.clear()
    }

    private fun onEnter() {
        val ic = currentInputConnection ?: return
        state.internalField?.let { return }
        commitComposing()
        undo = null
        autoSpaceInserted = false
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterAction = info != null && info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            val multiLine = info != null && info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
            if (multiLine) ic.commitText("\n", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        updateShift(auto = true)
        showPredictions()
    }

    private fun onShiftTap() {
        val now = System.currentTimeMillis()
        val next = when {
            shift == ShiftState.LOCKED -> ShiftState.OFF
            shift == ShiftState.ON && now - lastShiftTap < 350 -> ShiftState.LOCKED
            shift == ShiftState.ON -> ShiftState.OFF
            else -> ShiftState.ON
        }
        lastShiftTap = now
        updateShift(auto = false, force = next)
    }

    /** Sets shift explicitly ([force]) or recomputes automatic capitalisation. */
    private fun updateShift(auto: Boolean, force: ShiftState? = null) {
        shift = when {
            force != null -> force
            shift == ShiftState.LOCKED -> ShiftState.LOCKED
            auto && alphaLayout && needsCaps() -> ShiftState.ON
            else -> ShiftState.OFF
        }
        keyboardView.shiftState = shift
    }

    private fun needsCaps(): Boolean {
        if (!autoCapsAllowed || composing.isNotEmpty()) return false
        val info = currentInputEditorInfo ?: return false
        if (info.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        val ic = currentInputConnection ?: return false
        if (ic.getCursorCapsMode(info.inputType) != 0) return true
        // Also capitalise after "? 😊 " which the platform check does not recognise.
        val wantsSentences = info.inputType and (InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0
        if (!wantsSentences) return false
        val before = ic.getTextBeforeCursor(24, 0)?.toString() ?: return false
        if (before.isEmpty() || !before.last().isWhitespace()) return false
        var i = before.length - 1
        while (i >= 0) {
            val cp = Character.codePointBefore(before, i + 1)
            val c = before[i]
            when {
                c.isWhitespace() || AutoEmoji.isEmojiCodePoint(cp) || c == '️' || c == '‍' ->
                    i -= Character.charCount(cp)
                else -> return c in ".!?…"
            }
        }
        return true
    }

    private fun learn(word: String) {
        if (!learningAllowed || word.isEmpty()) return
        val before = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        // A picked suggestion is committed with a trailing space; strip it before cutting off the word.
        val previous = previousWord(before.trimEnd().removeSuffix(word))
        app.userModel.model.learn(previous, word)
    }

    private fun previousWord(text: String): String? {
        val t = text.trimEnd()
        if (t.isEmpty() || t.last() in ".!?\n") return null
        val m = Regex("([\\p{L}']+)\\W*$").find(t) ?: return null
        return m.groupValues[1]
    }

    // ---- Suggestions ----

    private fun suggestionLanguages(): List<Language> {
        val primary = state.language
        val latin = listOf(Language.DE, Language.EN).filter { it in prefs.enabledLanguages || it == primary }
        return when (primary) {
            Language.RU -> listOf(Language.RU)
            else -> {
                var order = listOf(primary) + latin.filter { it != primary }
                if (prefs.autoDetectLanguage) {
                    val before = currentInputConnection?.getTextBeforeCursor(200, 0)?.toString().orEmpty()
                    val detected = app.detector.detect(before)
                    if (detected != null && detected != primary && detected in order) order = listOf(detected) + (order - detected)
                }
                order
            }
        }
    }

    private fun detectLanguage(text: String): Language? =
        if (prefs.autoDetectLanguage) app.detector.detect(text, state.language) else state.language

    private fun updateSuggestions() {
        if (!suggestionsAllowed) { state.suggestions = emptyList(); return }
        if (composing.isEmpty()) { showPredictions(); return }
        val typed = composing.toString()
        val languages = suggestionLanguages()
        val autoCorrect = autoCorrectAllowed
        val generation = ++suggestGeneration
        suggestExecutor.execute {
            val r = app.engine.suggest(typed, languages, autoCorrect)
            keyboardView.post {
                if (generation != suggestGeneration) return@post
                lastResult = r
                state.suggestions = r.suggestions
                state.autoCorrectIndex = if (r.autoCorrection != null) r.suggestions.indexOf(r.autoCorrection) else -1
            }
        }
    }

    private fun showPredictions() {
        suggestGeneration++
        if (!suggestionsAllowed || state.sensitiveField) { state.suggestions = emptyList(); return }
        val before = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        // Ignore emoji after the last word, so "дела. 😊 " still counts as a sentence end.
        val tail = before.trimEnd { it.isWhitespace() || it.isSurrogate() || it == '\uFE0F' || it == '\u200D' }
        val prev = if (tail.isEmpty() || tail.last() in ".!?") null else previousWord(tail)
        val list = app.engine.predictNext(prev, suggestionLanguages())
        state.suggestions = list.map { if (shift != ShiftState.OFF) it.replaceFirstChar(Char::uppercaseChar) else it }
        state.autoCorrectIndex = -1
    }

    fun pickSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        feedback(null)
        ic.beginBatchEdit()
        if (composing.isNotEmpty()) {
            ic.commitText("$word ", 1)
        } else {
            val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
            val prefix = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
            ic.commitText("$prefix$word ", 1)
        }
        ic.endBatchEdit()
        composing.clear()
        learn(word)
        undo = null
        autoSpaceInserted = true
        updateShift(auto = true)
        showPredictions()
    }

    // ---- Language & panels ----

    fun setLanguage(language: Language) {
        commitComposing()
        state.language = language
        state.voice.language = language
        prefs.currentLanguage = language
        keyboardView.spaceLabel = language.displayName
        if (alphaLayout || keyboardView.layout.id in Language.entries.map { it.code }) {
            keyboardView.layout = Layouts.forLanguage(language)
            alphaLayout = true
        }
        app.io.execute { app.ensureDictionary(language) }
        updateShift(auto = true)
        showPredictions()
    }

    fun togglePanel(panel: Panel) {
        commitComposing()
        state.internalField = null
        state.showAppsRow = false
        if (state.panel == Panel.VOICE && panel != Panel.VOICE) stopVoice()
        state.panel = if (state.panel == panel) Panel.KEYBOARD else panel
        if (state.panel == Panel.VOICE) startVoice()
        if (state.panel == Panel.KEYBOARD) showPredictions()
    }

    fun toggleAutoEmoji() {
        prefs.autoEmoji = !prefs.autoEmoji
        state.autoEmoji = prefs.autoEmoji
        state.message = if (state.autoEmoji) "✨ Auto Emoji включён" else "Auto Emoji выключен"
    }

    fun openApp(appToOpen: AiApp) {
        state.showAppsRow = false
        if (AiAppLauncher.open(this, appToOpen)) state.missingApp = null else state.missingApp = appToOpen
    }

    fun openSettings() {
        startActivity(Intent(this, com.rob.aikeyboard.ui.settings.SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun hideKeyboard() = requestHideSelf(0)

    // ---- Public editing helpers for panels ----

    fun commitText(text: String) {
        val ic = currentInputConnection ?: return
        commitComposing()
        ic.commitText(text, 1)
        updateShift(auto = true)
    }

    fun commitEmoji(emoji: String) {
        commitText(emoji)
        app.emojiHistory.use(emoji)
        app.io.execute { app.saveEmojiHistory() }
    }

    /** Text the AI/translator panels operate on: the selection, else the whole field. */
    data class SourceText(val text: String, val isSelection: Boolean)

    fun sourceText(): SourceText? {
        val ic = currentInputConnection ?: return null
        commitComposing()
        ic.getSelectedText(0)?.toString()?.takeIf { it.isNotBlank() }?.let { return SourceText(it, true) }
        val extracted = ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 4000 }, 0)
        val full = extracted?.text?.toString()
            ?: ((ic.getTextBeforeCursor(2000, 0)?.toString().orEmpty()) + (ic.getTextAfterCursor(2000, 0)?.toString().orEmpty()))
        return full.takeIf { it.isNotBlank() }?.let { SourceText(it, false) }
    }

    /** Replaces the selection (or whole field) with [text], or appends it at the cursor. */
    fun applyResult(source: SourceText?, text: String, append: Boolean) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (append || source == null) {
            ic.commitText(text, 1)
        } else if (source.isSelection) {
            ic.commitText(text, 1)
        } else {
            val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
            val len = extracted?.text?.length
            if (len != null) {
                ic.setSelection(0, len)
                ic.commitText(text, 1)
            } else {
                val before = ic.getTextBeforeCursor(4000, 0)?.length ?: 0
                val after = ic.getTextAfterCursor(4000, 0)?.length ?: 0
                ic.deleteSurroundingText(before, after)
                ic.commitText(text, 1)
            }
        }
        ic.endBatchEdit()
        updateShift(auto = true)
    }

    // ---- One-tap correction (✎ in the suggestion strip) ----

    fun quickFix() {
        if (state.quickFixBusy) return
        if (state.sensitiveField) { state.message = "Исправление недоступно в полях пароля"; return }
        val source = sourceText() ?: run { state.message = "Сначала напишите текст"; return }
        val provider = prefs.aiProvider
        if (prefs.quickFixWithAi && app.ai.isConfigured(provider)) {
            state.quickFixBusy = true
            state.message = "Исправляю через ${provider.label}…"
            lifecycleScope.launch {
                try {
                    val (system, user) = com.rob.aikeyboard.ai.AiPrompts.build(com.rob.aikeyboard.ai.AiAction.Correct, source.text)
                    val fixed = com.rob.aikeyboard.ai.AiPrompts.parseVariants(app.ai.complete(provider, system, user)).first()
                    applyQuickFix(source, fixed)
                } catch (e: Exception) {
                    state.message = e.message ?: "Ошибка AI"
                } finally {
                    state.quickFixBusy = false
                }
            }
        } else {
            applyQuickFix(source, app.quickFix.fix(source.text, state.language))
        }
    }

    private fun applyQuickFix(source: SourceText, fixed: String) {
        if (fixed.isBlank() || fixed == source.text) {
            state.message = "Ошибок не найдено ✓"
            return
        }
        applyResult(source, fixed, append = false)
        state.message = null
        state.quickFixUndo = QuickFixUndo(source.text, fixed)
    }

    fun undoQuickFix() {
        val u = state.quickFixUndo ?: return
        state.quickFixUndo = null
        val ic = currentInputConnection ?: return
        if (ic.getTextBeforeCursor(u.fixed.length, 0)?.toString() == u.fixed) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(u.fixed.length, 0)
            ic.commitText(u.original, 1)
            ic.endBatchEdit()
        }
        updateShift(auto = true)
    }

    // ---- Clipboard ----

    private fun captureClipboard() {
        if (ignoreNextClip) { ignoreNextClip = false; return }
        if (!prefs.clipboardHistory || isPassword) return
        val clip = runCatching { clipboardManager.primaryClip }.getOrNull() ?: return
        val desc = clip.description
        if (desc.extras?.getBoolean(EXTRA_IS_SENSITIVE) == true) return
        for (i in 0 until clip.itemCount) {
            val item = clip.getItemAt(i)
            val uri = item.uri
            val mime = uri?.let { runCatching { contentResolver.getType(it) }.getOrNull() } ?: desc.getMimeType(0)
            if (uri != null && mime != null && mime.startsWith("image/")) {
                app.io.execute {
                    runCatching {
                        contentResolver.openInputStream(uri)?.use { input ->
                            val bytes = input.readBytes()
                            if (bytes.size in 1..MAX_IMAGE_BYTES) app.clipboard.addImage(bytes, mime)
                        }
                    }
                }
            } else {
                val text = item.text?.toString() ?: continue
                if (text.isNotBlank()) app.io.execute { app.clipboard.addText(text.take(MAX_CLIP_CHARS)) }
            }
        }
    }

    fun pasteClip(item: ClipItem) {
        when (item.kind) {
            ClipItem.Kind.TEXT -> commitText(item.value)
            ClipItem.Kind.IMAGE -> insertImage(item)
        }
    }

    fun copyClip(item: ClipItem) {
        if (item.kind == ClipItem.Kind.TEXT) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("ROB", item.value))
            state.message = "Скопировано"
        } else {
            copyImageToClipboard(item)
        }
    }

    private fun imageUri(item: ClipItem) = app.clipboard.exportForSharing(item)?.let {
        FileProvider.getUriForFile(this, "$packageName.fileprovider", it)
    }

    private fun insertImage(item: ClipItem) {
        val info = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) { imageUri(item) } ?: run { state.message = "Изображение недоступно"; return@launch }
            val accepted = EditorInfoCompat.getContentMimeTypes(info).any { ClipDescription.compareMimeTypes(item.mimeType, it) }
            val ok = accepted && InputConnectionCompat.commitContent(
                ic, info,
                InputContentInfoCompat(uri, ClipDescription("image", arrayOf(item.mimeType)), null),
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null,
            )
            if (!ok) {
                copyImageToClipboard(item, uri)
                state.message = "Это приложение не принимает картинки из клавиатуры. Изображение скопировано — вставьте его долгим нажатием."
            }
        }
    }

    private fun copyImageToClipboard(item: ClipItem, knownUri: android.net.Uri? = null) {
        lifecycleScope.launch {
            val uri = knownUri ?: withContext(Dispatchers.IO) { imageUri(item) } ?: return@launch
            ignoreNextClip = true
            clipboardManager.setPrimaryClip(ClipData.newUri(contentResolver, "ROB image", uri))
            if (knownUri == null) state.message = "Изображение скопировано"
        }
    }

    fun pickImage() {
        startActivity(Intent(this, PhotoPickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---- Voice ----

    fun startVoice() {
        state.voice.error = null
        state.voice.partial = ""
        if (!voice.hasPermission()) {
            state.voice.error = "Нужно разрешение на микрофон"
            startActivity(
                Intent(this, PermissionActivity::class.java)
                    .putExtra(PermissionActivity.EXTRA_PERMISSION, android.Manifest.permission.RECORD_AUDIO)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        if (!voice.isAvailable()) {
            state.voice.error = "Распознавание речи недоступно. Установите/включите сервис распознавания (напр. Google) в настройках Android."
            return
        }
        voice.start(state.voice.language, prefs.voicePreferOffline)
    }

    fun stopVoice() {
        if (voice.isListening) voice.stopListening() else voice.stop()
        state.voice.listening = false
    }

    override fun onListening() { state.voice.listening = true }
    override fun onPartial(text: String) { state.voice.partial = text }
    override fun onLevel(rms: Float) { state.voice.level = rms }

    override fun onResult(text: String) {
        state.voice.listening = false
        state.voice.partial = ""
        val ic = currentInputConnection ?: return
        commitComposing()
        val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        var insert = text
        if (needsCaps()) insert = insert.replaceFirstChar(Char::uppercaseChar)
        if (before.isNotEmpty() && !before.last().isWhitespace()) insert = " $insert"
        ic.commitText(insert, 1)
        state.voice.lastInserted = insert
        updateShift(auto = true)
    }

    override fun onError(message: String) {
        state.voice.listening = false
        state.voice.error = message
    }

    /** Removes the last dictated text if it is still right before the cursor. */
    fun undoVoice() {
        val inserted = state.voice.lastInserted ?: return
        val ic = currentInputConnection ?: return
        if (ic.getTextBeforeCursor(inserted.length, 0)?.toString() == inserted) ic.deleteSurroundingText(inserted.length, 0)
        state.voice.lastInserted = null
    }

    /** Replaces the last dictated text with [replacement] (e.g. its translation). */
    fun replaceVoice(replacement: String) {
        val inserted = state.voice.lastInserted ?: return
        val ic = currentInputConnection ?: return
        if (ic.getTextBeforeCursor(inserted.length, 0)?.toString() == inserted) {
            ic.deleteSurroundingText(inserted.length, 0)
            val lead = if (inserted.startsWith(" ")) " " else ""
            ic.commitText(lead + replacement, 1)
            state.voice.lastInserted = lead + replacement
        }
    }

    // ---- Feedback ----

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= 31) (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") (getSystemService(VIBRATOR_SERVICE) as Vibrator)
    }
    private val audio by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    private fun feedback(key: Key?) {
        val v = prefs.vibration
        if (v != Vibration.OFF) {
            runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(v.millis, v.amplitude)) }
        }
        if (prefs.soundOnKeypress) {
            val fx = when (key?.type) {
                KeyType.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio.playSoundEffect(fx, prefs.soundVolume)
        }
    }

    companion object {
        private const val WORD_SEPARATORS = " .,!?;:\n"
        private const val MAX_CLIP_CHARS = 20_000
        private const val MAX_IMAGE_BYTES = 15 * 1024 * 1024
        private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

        private val PASSWORD_VARIATIONS = setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )
        private val TECHNICAL_VARIATIONS = setOf(
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_FILTER,
        )
    }
}
