package com.rob.aikeyboard.ui.ime

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rob.aikeyboard.ime.Panel
import com.rob.aikeyboard.ime.RobKeyboardService
import com.rob.aikeyboard.launch.AiApp
import com.rob.aikeyboard.launch.AiAppLauncher
import kotlinx.coroutines.delay

/** Toolbar and panel colours; follow the selected design (reads are observed by Compose). */
object ImeColors {
    private val t get() = com.rob.aikeyboard.keyboard.KeyboardTheme.current
    val board get() = Color(t.backgroundTop)
    val bar get() = Color(t.bar)
    /** Colour for text on accent-filled chips and buttons. */
    val key get() = Color(t.onAccent)
    /** Background of cards and fields (same as the keys). */
    val keyBg get() = Color(t.key)
    val chip get() = Color(t.chip)
    val cyan get() = Color(t.accent)
    val cyanDim get() = Color(t.outlineDim)
    val text get() = Color(t.text)
    val textDim get() = Color(t.textDim)
    val danger = Color(0xFFFF5A5A)
}

@Composable
fun ImeRoot(service: RobKeyboardService) {
    val state = service.state
    val density = LocalDensity.current
    val panelHeight: Dp = with(density) { state.keyboardHeightPx.toDp() }.coerceAtLeast(220.dp)

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(3500)
            state.message = null
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(ImeColors.board)
            .navigationBarsPadding(),
    ) {
        Toolbar(service)
        if (state.showAppsRow) AppsRow(service)
        if (state.assist != null && state.panel == Panel.KEYBOARD) AssistRow(service)
        when {
            state.missingApp != null -> MissingAppBar(service, state.missingApp!!)
            state.clearedText != null -> StripRow {
                Text(
                    "Текст удалён", color = ImeColors.text, fontSize = 14.sp,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
                SmallButton("Отменить", primary = true) { service.undoClearAll() }
                SmallButton("✕") { state.clearedText = null }
            }
            state.showClearAll -> StripRow {
                Text(
                    "Держите ⌫ — стирается по словам", color = ImeColors.textDim, fontSize = 13.sp,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                SmallButton("✕ Удалить всё", primary = true) { service.clearAll() }
            }
            state.newScreenshot != null && state.panel == Panel.KEYBOARD -> StripRow {
                Text(
                    "📷 Новый скриншот", color = ImeColors.cyan, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
                SmallButton("Вставить", primary = true) { service.insertNewScreenshot() }
                SmallButton("✕") { state.newScreenshot = null }
            }
            state.quickFixUndo != null -> StripRow {
                Text(
                    "✓ Текст исправлен", color = ImeColors.cyan, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
                SmallButton("Отменить") { service.undoQuickFix() }
                SmallButton("OK", primary = true) { state.quickFixUndo = null }
            }
            state.message != null -> MessageBar(state.message!!)
            state.panel == Panel.KEYBOARD || state.panel == Panel.VOICE -> SuggestionStrip(service)
        }
        when (state.panel) {
            Panel.KEYBOARD -> if (state.hardKeyboard && !state.showSoftKeys) HardKeyboardRow(service) else {
                if (state.hardKeyboard) HardKeyboardRow(service)
                KeyboardHost(service)
            }
            Panel.VOICE -> {
                VoicePanel(service)
                KeyboardHost(service)
            }
            Panel.EMOJI -> EmojiPanel(service, panelHeight)
            Panel.CLIPBOARD -> ClipboardPanel(service, panelHeight)
            Panel.AI -> AiWritingPanel(service, panelHeight)
            Panel.TRANSLATE -> TranslatePanel(service, panelHeight)
            Panel.FONT -> FontPanel(service, panelHeight)
        }
    }
}

/** Slim row shown instead of the keys while a physical keyboard is attached. */
@Composable
private fun HardKeyboardRow(service: RobKeyboardService) {
    val state = service.state
    StripRow {
        Text(
            "⌨ ${state.language.displayName} · Strg+Пробел — язык · Strg+Shift+V — буфер",
            color = ImeColors.textDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 10.dp).clickable { service.onSpaceSwipe(1) },
        )
        SmallButton(if (state.showSoftKeys) "Скрыть клавиши" else "⌨ Клавиши") { service.toggleSoftKeys() }
    }
}

/** Hosts the single [com.rob.aikeyboard.keyboard.KeyboardView] instance owned by the service. */
@Composable
fun KeyboardHost(service: RobKeyboardService) {
    AndroidView(
        factory = {
            (service.keyboardView.parent as? android.view.ViewGroup)?.removeView(service.keyboardView)
            service.keyboardView
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Toolbar(service: RobKeyboardService) {
    val state = service.state
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(ImeColors.bar),
    ) {
        val wide = maxWidth >= 520.dp
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (wide) Arrangement.SpaceEvenly else Arrangement.Start,
        ) {
            if (wide) {
                AiApp.entries.forEach { app -> TextTool(app.label) { service.openApp(app) } }
            } else {
                IconTool(Icons.Outlined.Apps, "ChatGPT, Claude, Gemini", state.showAppsRow) {
                    state.showAppsRow = !state.showAppsRow
                }
            }
            IconTool(Icons.Outlined.Translate, "Перевод", state.panel == Panel.TRANSLATE) { service.togglePanel(Panel.TRANSLATE) }
            IconTool(Icons.Outlined.EditNote, "AI Writing", state.panel == Panel.AI) { service.togglePanel(Panel.AI) }
            AutoEmojiToggle(state.autoEmoji) { service.toggleAutoEmoji() }
            FontToggle(state.fontStyle != null) {
                if (state.fontStyle != null) service.setFontStyle(null) else service.togglePanel(Panel.FONT)
            }
            IconTool(Icons.Outlined.EmojiEmotions, "Emoji", state.panel == Panel.EMOJI) { service.togglePanel(Panel.EMOJI) }
            IconTool(Icons.Outlined.Mic, "Микрофон", state.panel == Panel.VOICE) { service.togglePanel(Panel.VOICE) }
            IconTool(Icons.Outlined.ContentPaste, "Буфер обмена", state.panel == Panel.CLIPBOARD) { service.togglePanel(Panel.CLIPBOARD) }
            IconTool(Icons.Outlined.Settings, "Настройки", false) { service.openSettings() }
        }
    }
}

@Composable
private fun IconTool(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) ImeColors.cyanDim else Color.Transparent)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = ImeColors.cyan, modifier = Modifier.size(25.dp))
    }
}

@Composable
private fun TextTool(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, ImeColors.cyanDim, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ImeColors.cyan, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FontToggle(on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) ImeColors.cyan else Color.Transparent)
            .border(1.dp, if (on) ImeColors.cyan else ImeColors.cyanDim, RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Шрифт для TikTok", onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (on) "𝓐 ON" else "𝓐 OFF",
            color = if (on) ImeColors.key else ImeColors.cyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AutoEmojiToggle(on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) ImeColors.cyan else Color.Transparent)
            .border(1.dp, if (on) ImeColors.cyan else ImeColors.cyanDim, RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Auto Emoji", onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (on) "✨ ON" else "✨ OFF",
            color = if (on) ImeColors.key else ImeColors.cyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** "✨" row: AI-corrected message and continuations, each applied with one tap. */
@Composable
private fun AssistRow(service: RobKeyboardService) {
    val assist = service.state.assist ?: return
    Row(
        Modifier.fillMaxWidth().background(ImeColors.bar).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✨", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 4.dp))
        assist.fixed?.let { fixed ->
            AssistChip("✓ ${fixed.take(60)}${if (fixed.length > 60) "…" else ""}", highlighted = true) { service.applyAssistFix() }
        }
        assist.next.forEach { n -> AssistChip("+ ${n.take(50)}", highlighted = false) { service.applyAssistNext(n) } }
        Icon(
            Icons.Outlined.Close, "Скрыть", tint = ImeColors.textDim,
            modifier = Modifier.size(34.dp).clip(CircleShape).clickable { service.state.assist = null }.padding(7.dp),
        )
    }
}

@Composable
private fun AssistChip(label: String, highlighted: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 3.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (highlighted) ImeColors.cyan else ImeColors.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (highlighted) ImeColors.key else ImeColors.text, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun AppsRow(service: RobKeyboardService) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ImeColors.bar)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        AiApp.entries.forEach { app -> TextTool(app.label) { service.openApp(app) } }
    }
}

@Composable
private fun MissingAppBar(service: RobKeyboardService, app: AiApp) {
    StripRow {
        Text(
            "${app.label} не установлен",
            color = ImeColors.text,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SmallButton("Сайт") { AiAppLauncher.openWebsite(service, app); service.state.missingApp = null }
        SmallButton("Google Play") { AiAppLauncher.openStore(service, app); service.state.missingApp = null }
        Icon(
            Icons.Outlined.Close, "Закрыть", tint = ImeColors.textDim,
            modifier = Modifier.size(36.dp).clickable { service.state.missingApp = null }.padding(8.dp),
        )
    }
}

@Composable
private fun MessageBar(message: String) {
    StripRow {
        Text(
            message, color = ImeColors.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun StripRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SuggestionStrip(service: RobKeyboardService) {
    val state = service.state
    val items = state.suggestions
    StripRow {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(1.5.dp, ImeColors.cyan, CircleShape)
                .clickable(onClickLabel = "Скрыть клавиатуру") { service.hideKeyboard() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.KeyboardArrowDown, "Скрыть клавиатуру", tint = ImeColors.cyan)
        }
        Spacer(Modifier.width(4.dp))
        // Middle slot holds the best guess, as on the reference design.
        val slots = when (items.size) {
            0 -> listOf(null, null, null)
            1 -> listOf(null, items[0], null)
            2 -> listOf(items[0], items[1], null)
            else -> items.take(3)
        }
        slots.forEachIndexed { i, word ->
            val isAuto = word != null && items.indexOf(word) == state.autoCorrectIndex
            Box(
                Modifier
                    .weight(if (i == 1) 1.3f else 1f)
                    .fillMaxHeight()
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ImeColors.chip)
                    .border(BorderStroke(1.dp, if (isAuto) ImeColors.cyan else ImeColors.cyanDim), RoundedCornerShape(8.dp))
                    .clickable(enabled = word != null) { word?.let(service::pickSuggestion) },
                contentAlignment = Alignment.Center,
            ) {
                if (word != null) {
                    Text(
                        word,
                        color = if (i == 1) ImeColors.text else ImeColors.textDim,
                        fontSize = if (i == 1) 19.sp else 17.sp,
                        fontWeight = if (i == 1) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        // Tap: fix the whole message at once. Long press: full AI Writing panel.
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (state.quickFixBusy) ImeColors.cyanDim else ImeColors.chip)
                .combinedClickable(
                    onClickLabel = "Исправить текст",
                    onLongClickLabel = "AI Writing",
                    onClick = { service.quickFix() },
                    onLongClick = { service.togglePanel(Panel.AI) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.EditNote, "Исправить текст", tint = ImeColors.cyan)
        }
    }
}

// ---- Small shared widgets for the panels ----

@Composable
fun SmallButton(label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 3.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (primary && enabled) ImeColors.cyan else ImeColors.chip)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = when {
                !enabled -> ImeColors.textDim
                primary -> ImeColors.key
                else -> ImeColors.text
            },
            fontSize = 14.sp,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 3.dp, vertical = 3.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) ImeColors.cyan else Color.Transparent)
            .border(1.dp, if (selected) ImeColors.cyan else ImeColors.cyanDim, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) ImeColors.key else ImeColors.cyan, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
fun PanelHeader(title: String, onClose: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = ImeColors.cyan, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing()
        Icon(
            Icons.Outlined.Close, "Закрыть", tint = ImeColors.textDim,
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose).padding(9.dp),
        )
    }
}

/** Read-only box showing the text of an [com.rob.aikeyboard.ime.InternalField] with a cursor. */
@Composable
fun InternalFieldBox(text: String, hint: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ImeColors.keyBg)
            .border(1.dp, ImeColors.cyan, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            if (text.isEmpty()) hint else "$text▏",
            color = if (text.isEmpty()) ImeColors.textDim else ImeColors.text,
            fontSize = 15.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
