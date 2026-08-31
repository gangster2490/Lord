package de.spardirekt.tiktokshop.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.spardirekt.tiktokshop.data.model.GenerateResult
import de.spardirekt.tiktokshop.data.model.ImageSlot
import de.spardirekt.tiktokshop.data.model.ProductFacts
import de.spardirekt.tiktokshop.data.model.Tones
import de.spardirekt.tiktokshop.data.model.VideoStyles
import de.spardirekt.tiktokshop.data.model.asPlainText
import de.spardirekt.tiktokshop.data.model.masterCopy
import de.spardirekt.tiktokshop.ui.theme.AccentAmber
import de.spardirekt.tiktokshop.ui.theme.AccentCyan
import de.spardirekt.tiktokshop.ui.theme.AccentEmerald
import de.spardirekt.tiktokshop.ui.theme.AccentIndigo
import de.spardirekt.tiktokshop.ui.theme.AccentLime
import de.spardirekt.tiktokshop.ui.theme.AccentLive
import de.spardirekt.tiktokshop.ui.theme.AccentPink
import de.spardirekt.tiktokshop.ui.theme.AccentViolet
import de.spardirekt.tiktokshop.ui.theme.Background
import de.spardirekt.tiktokshop.ui.theme.ErrorBg
import de.spardirekt.tiktokshop.ui.theme.ErrorBorder
import de.spardirekt.tiktokshop.ui.theme.ErrorRed
import de.spardirekt.tiktokshop.ui.theme.Hairline
import de.spardirekt.tiktokshop.ui.theme.Neon
import de.spardirekt.tiktokshop.ui.theme.NeonBorder
import de.spardirekt.tiktokshop.ui.theme.NeonDim
import de.spardirekt.tiktokshop.ui.theme.Orange
import de.spardirekt.tiktokshop.ui.theme.OrangeSoft
import de.spardirekt.tiktokshop.ui.theme.Surface
import de.spardirekt.tiktokshop.ui.theme.Surface2
import de.spardirekt.tiktokshop.ui.theme.TextDim
import de.spardirekt.tiktokshop.ui.theme.TextMid
import de.spardirekt.tiktokshop.ui.theme.TextPrimary

private val CardShape = RoundedCornerShape(14.dp)
private val FieldShape = RoundedCornerShape(9.dp)

@Composable
fun CreatorScreen(
    state: CreatorUiState,
    onEvent: (CreatorEvent) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.copiedLabel) {
        state.copiedLabel?.let { snackbar.showSnackbar(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 36.dp),
        ) {
            item { AppHeader() }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ApiKeyCard(state, onEvent)
                    ImageSlotCard(
                        index = 0,
                        slot = state.images[0],
                        title = "Bild 1 – Produktbild",
                        required = true,
                        hint = "Produktbild auswählen · JPG PNG WEBP · max 10 MB",
                        onEvent = onEvent,
                    )
                    ImageSlotCard(
                        index = 1,
                        slot = state.images[1],
                        title = "Bild 2 – Produktbild",
                        required = false,
                        hint = "Weitere Produktansicht (Perspektive, Detail, Anwendung).",
                        onEvent = onEvent,
                    )
                    ImageSlotCard(
                        index = 2,
                        slot = state.images[2],
                        title = "Bild 3 – Produktbild",
                        required = false,
                        hint = "Weitere Produktansicht (Verpackung, Lieferumfang, Detail).",
                        onEvent = onEvent,
                    )
                    ImageSlotCard(
                        index = 3,
                        slot = state.images[3],
                        title = "Bild 4 – Beschreibung / Spezifikationen",
                        required = false,
                        hint = "Screenshot der Produktbeschreibung (OCR).",
                        onEvent = onEvent,
                    )
                    SettingsCard(state, onEvent)
                    ProxyCard(state, onEvent)
                    GenerateButton(state, onEvent)
                    AnimatedVisibility(visible = state.error != null) {
                        ErrorBox(state.error.orEmpty())
                    }
                }
            }
            val result = state.result
            if (result != null) {
                item {
                    ResultsHeader(onCopyAll = { onEvent(CreatorEvent.CopyAll) })
                }
                itemsIndexed(resultCards(result, onEvent)) { _, card ->
                    Box(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        card()
                    }
                }
            }
            item {
                Text(
                    text = "TikTok Shop Creator · SparDirekt DE · Kein Preis · Keine Rabatte · TikTok-safe",
                    color = TextDim,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 22.dp),
                )
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(Background.copy(alpha = 0.94f))
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Bolt, contentDescription = null, tint = Neon, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("TikTok Shop Creator", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
        Text(
            text = "SPARDIREKT DE",
            color = Neon,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(NeonDim)
                .border(1.dp, NeonBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CardSurface(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Surface)
            .border(1.dp, Hairline, CardShape)
            .padding(18.dp),
    ) { content() }
}

@Composable
private fun CardLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = TextMid,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.9.sp,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Neon,
    unfocusedBorderColor = Hairline,
    focusedContainerColor = Surface2,
    unfocusedContainerColor = Surface2,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Neon,
    focusedLabelColor = TextMid,
    unfocusedLabelColor = TextMid,
)

@Composable
private fun ApiKeyCard(state: CreatorUiState, onEvent: (CreatorEvent) -> Unit) {
    CardSurface {
        CardLabel("Anthropic API Key")
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = { onEvent(CreatorEvent.ApiKeyChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-ant-api03-…", color = TextDim, fontFamily = FontFamily.Monospace) },
            visualTransformation = if (state.showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            shape = FieldShape,
            colors = fieldColors(),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
            trailingIcon = {
                IconButton(onClick = { onEvent(CreatorEvent.ToggleApiKeyVisibility) }) {
                    Icon(
                        imageVector = if (state.showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (state.showApiKey) "Key verbergen" else "Key anzeigen",
                        tint = TextMid,
                    )
                }
            },
        )
        Text(
            "Dein Key bleibt nur auf dem Gerät – er wird nie gespeichert oder weitergegeben.",
            color = TextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ImageSlotCard(
    index: Int,
    slot: ImageSlot,
    title: String,
    required: Boolean,
    hint: String,
    onEvent: (CreatorEvent) -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val name = runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
                }
            }.getOrNull()
            onEvent(CreatorEvent.ImagePicked(index, uri, name))
        }
    }

    CardSurface {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Text(
                text = title.uppercase(),
                color = TextMid,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.7.sp,
                modifier = Modifier.weight(1f),
            )
            Badge(if (required) "Pflichtfeld" else "Optional", required)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 170.dp)
                .clip(CardShape)
                .background(Surface2)
                .border(2.dp, if (slot.uri != null) NeonBorder else Hairline, CardShape)
                .clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            contentAlignment = Alignment.Center,
        ) {
            if (slot.uri != null) {
                AsyncImage(
                    model = slot.uri,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 290.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                    Icon(Icons.Outlined.Image, contentDescription = null, tint = TextDim, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Tippen zum Auswählen", color = TextMid, fontWeight = FontWeight.SemiBold)
                    Text(hint, color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        if (slot.uri != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    slot.fileName ?: "Bild ausgewählt",
                    color = TextMid,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                TextButton(onClick = { onEvent(CreatorEvent.ImageRemoved(index)) }) {
                    Text("Entfernen", color = ErrorRed, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, required: Boolean) {
    val color = if (required) ErrorRed else TextMid
    val bg = if (required) ErrorBg else Color(0x1A8A8F98)
    val border = if (required) ErrorBorder else Color(0x388A8F98)
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun SettingsCard(state: CreatorUiState, onEvent: (CreatorEvent) -> Unit) {
    CardSurface {
        CardLabel("Einstellungen")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectField("Video-Stil", state.videoStyle, VideoStyles.all) {
                onEvent(CreatorEvent.VideoStyleChanged(it))
            }
            SelectField("Ton", state.tone, Tones.all) {
                onEvent(CreatorEvent.ToneChanged(it))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectField(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label.uppercase(), color = TextMid, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(5.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                shape = FieldShape,
                colors = fieldColors(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProxyCard(state: CreatorUiState, onEvent: (CreatorEvent) -> Unit) {
    CardSurface {
        CardLabel("Proxy URL")
        OutlinedTextField(
            value = state.proxyUrl,
            onValueChange = { onEvent(CreatorEvent.ProxyUrlChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://your-proxy.onrender.com", color = TextDim) },
            singleLine = true,
            shape = FieldShape,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Text(
            "CORS-Proxy erforderlich. Emulator: http://10.0.2.2:3001 — Gerät: LAN-IP des Rechners.",
            color = TextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun GenerateButton(state: CreatorUiState, onEvent: (CreatorEvent) -> Unit) {
    Button(
        onClick = { onEvent(CreatorEvent.Generate) },
        enabled = !state.isGenerating,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Neon,
            contentColor = Color.Black,
            disabledContainerColor = Neon.copy(alpha = 0.4f),
            disabledContentColor = Color.Black.copy(alpha = 0.6f),
        ),
    ) {
        if (state.isGenerating) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.Black,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text("Analysiere Produkt…", fontWeight = FontWeight.Black, fontSize = 16.sp)
        } else {
            Icon(Icons.Outlined.Bolt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Content generieren", fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ErrorBox(message: String) {
    Text(
        text = "Fehler: $message",
        color = Color(0xFFFCA5A5),
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .background(ErrorBg)
            .border(1.dp, ErrorBorder, FieldShape)
            .padding(13.dp),
    )
}

@Composable
private fun ResultsHeader(onCopyAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Ergebnisse", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        TextButton(onClick = onCopyAll) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = TextMid, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("ALLES KOPIEREN", color = TextMid, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
        }
    }
}

private fun resultCards(
    result: GenerateResult,
    onEvent: (CreatorEvent) -> Unit,
): List<@Composable () -> Unit> = listOf(
    { ResultCard("Produktdaten", AccentCyan, result.productFacts.asPlainText(), onEvent) { FactsBody(result.productFacts) } },
    { ResultCard("TikTok Titel", Neon, result.title, onEvent) { Text(result.title, color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) } },
    { ResultCard("5 Hook Ideas", AccentAmber, result.hooks.mapIndexed { i, h -> "${i + 1}. $h" }.joinToString("\n"), onEvent) { HooksBody(result.hooks) } },
    { ResultCard("Hashtags", AccentEmerald, result.hashtags.joinToString(" "), onEvent) { TagsBody(result.hashtags) } },
    { ResultCard("Banner Text", Orange, result.bannerText.joinToString("\n"), onEvent) { BannerBody(result.bannerText) } },
    { ResultCard("Banner Prompt", AccentViolet, result.bannerPrompt, onEvent) { MonoBody(result.bannerPrompt) } },
    { ResultCard("Voice Script", Orange, result.voiceoverText, onEvent) { TimedBody(result.voiceoverText) } },
    { ResultCard("Music Suggestion", AccentPink, result.musicSuggestion, onEvent) { Text(result.musicSuggestion, color = TextPrimary) } },
    { ResultCard("Sound Effects", AccentLime, result.soundEffects, onEvent) { TimedBody(result.soundEffects) } },
    {
        ResultCard(
            title = "Veo 3.1 Prompt",
            accent = AccentIndigo,
            copyText = result.veoPrompt,
            onEvent = onEvent,
            extraAction = "Veo Komplett" to { onEvent(CreatorEvent.CopyVeoKomplett) },
        ) { MonoBody(result.veoPrompt) }
    },
    { ResultCard("TikTok Live Script", AccentLive, result.liveScript, onEvent) { TimedBody(result.liveScript) } },
    {
        ResultCard(
            title = "Master Copy Block",
            accent = Neon,
            copyText = result.masterCopy(),
            onEvent = onEvent,
            extraAction = "Alles für Veo kopieren" to { onEvent(CreatorEvent.CopyMaster) },
            extraFilled = true,
        ) { MonoBody(result.masterCopy()) }
    },
)

@Composable
private fun ResultCard(
    title: String,
    accent: Color,
    copyText: String,
    onEvent: (CreatorEvent) -> Unit,
    extraAction: Pair<String, () -> Unit>? = null,
    extraFilled: Boolean = false,
    body: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Surface)
            .border(1.dp, Hairline, CardShape),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(accent),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            CopyChip("Kopieren") { onEvent(CreatorEvent.Copy(copyText, "Kopiert")) }
            if (extraAction != null) {
                Spacer(Modifier.width(6.dp))
                CopyChip(extraAction.first, filled = extraFilled, onClick = extraAction.second)
            }
        }
        Box(Modifier.padding(15.dp)) { body() }
    }
}

@Composable
private fun CopyChip(label: String, filled: Boolean = false, onClick: () -> Unit) {
    Text(
        text = label.uppercase(),
        color = if (filled) Color.Black else TextDim,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.5.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (filled) Neon else Color.Transparent)
            .border(1.dp, if (filled) Neon else Hairline, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun FactsBody(facts: ProductFacts) {
    val rows = listOf(
        "Produktname" to facts.name,
        "Maße" to facts.dimensions,
        "Kapazität" to facts.capacity,
        "Material" to facts.material,
        "Gewicht" to facts.weight,
        "Farbe" to facts.color,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (k, v) -> FactRow(k, v) }
        TagFactRow("Lieferumfang", facts.includedItems)
        TagFactRow("Features", facts.keyFeatures)
        TagFactRow("Warnhinweise", facts.warnings)
        TagFactRow("Anwendung", facts.useCases)
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label.uppercase(),
            color = TextMid,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.width(110.dp),
        )
        Text(
            value.ifBlank { "Nicht erkennbar" },
            color = if (value.isBlank() || value == "Nicht erkennbar") TextDim else TextPrimary,
            fontSize = 13.sp,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagFactRow(label: String, values: List<String>) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label.uppercase(),
            color = TextMid,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.width(110.dp),
        )
        if (values.isEmpty()) {
            Text("Nicht erkennbar", color = TextDim, fontSize = 13.sp)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                values.forEach { Tag(it, TextPrimary, Surface2, Hairline) }
            }
        }
    }
}

@Composable
private fun HooksBody(hooks: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        hooks.forEachIndexed { i, hook ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0x24FBBF24))
                        .border(1.dp, Color(0x66FBBF24), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", color = AccentAmber, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(10.dp))
                Text(hook, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsBody(tags: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        tags.forEach { Tag(it, Neon, NeonDim, NeonBorder) }
    }
}

@Composable
private fun Tag(text: String, color: Color, bg: Color, border: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(horizontal = 11.dp, vertical = 3.dp),
    )
}

@Composable
private fun BannerBody(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        lines.forEach { line ->
            Text(
                line,
                color = Color(0xFFFDBA74),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                    .background(OrangeSoft)
                    .padding(start = 3.dp)
                    .padding(horizontal = 11.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun MonoBody(text: String) {
    Text(
        text = text.ifBlank { "—" },
        color = Neon,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x4D000000))
            .padding(8.dp),
    )
}

@Composable
private fun TimedBody(text: String) {
    val lines = text.split('\n').filter { it.isNotBlank() }
    if (lines.isEmpty()) {
        Text("—", color = TextDim)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            val timed = Regex("""^(\d+s|\d+:\d+)\s*[–\-|]\s*(.*)$""").find(line)
            if (timed != null) {
                Row {
                    Text(
                        timed.groupValues[1],
                        color = Neon,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NeonDim)
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(timed.groupValues[2].trim(), color = TextPrimary, fontSize = 14.sp)
                }
            } else {
                Text(line, color = TextPrimary, fontSize = 14.sp)
            }
        }
    }
}
