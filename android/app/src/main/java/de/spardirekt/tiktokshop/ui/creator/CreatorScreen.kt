package de.spardirekt.tiktokshop.ui.creator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.spardirekt.tiktokshop.data.CreatorOptions
import de.spardirekt.tiktokshop.data.ImageSlot
import de.spardirekt.tiktokshop.ui.components.AccentCard
import de.spardirekt.tiktokshop.ui.components.CopyChip
import de.spardirekt.tiktokshop.ui.components.ErrorBanner
import de.spardirekt.tiktokshop.ui.components.Hint
import de.spardirekt.tiktokshop.ui.components.PrimaryButton
import de.spardirekt.tiktokshop.ui.components.SectionLabel
import de.spardirekt.tiktokshop.ui.components.ShopCard
import de.spardirekt.tiktokshop.ui.components.ShopDropdown
import de.spardirekt.tiktokshop.ui.components.ShopTextField
import de.spardirekt.tiktokshop.ui.theme.AccentAmber
import de.spardirekt.tiktokshop.ui.theme.AccentCyan
import de.spardirekt.tiktokshop.ui.theme.AccentOrange
import de.spardirekt.tiktokshop.ui.theme.AccentRed
import de.spardirekt.tiktokshop.ui.theme.Bg3
import de.spardirekt.tiktokshop.ui.theme.Border
import de.spardirekt.tiktokshop.ui.theme.Danger
import de.spardirekt.tiktokshop.ui.theme.NeonGreen
import de.spardirekt.tiktokshop.ui.theme.NeonGreenDim
import de.spardirekt.tiktokshop.ui.theme.TextDim
import de.spardirekt.tiktokshop.ui.theme.TextMid
import de.spardirekt.tiktokshop.ui.theme.TextPrimary

@Composable
fun CreatorScreen(viewModel: CreatorViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scroll = rememberScrollState()

    LaunchedEffect(state.result) {
        if (state.result != null) {
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ShopCard {
            SectionLabel("🔑 Anthropic API Key")
            ShopTextField(
                value = state.apiKey,
                onValueChange = viewModel::onApiKeyChange,
                placeholder = "sk-ant-api03-...",
                password = true,
            )
            Hint("Der Key bleibt nur auf diesem Gerät — er wird an den konfigurierten Proxy weitergeleitet.")
        }

        state.slots.forEach { slot ->
            ImageSlotCard(
                slot = slot,
                onPicked = { viewModel.onImagePicked(slot.index, it) },
                onRemoved = { viewModel.onImageRemoved(slot.index) },
            )
        }

        ShopCard {
            SectionLabel("🎨 Einstellungen")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShopDropdown(
                    label = "Video-Stil",
                    value = state.videoStyle,
                    options = CreatorOptions.videoStyles,
                    onSelect = viewModel::onStyleChange,
                    modifier = Modifier.weight(1f),
                )
                ShopDropdown(
                    label = "Ton",
                    value = state.tone,
                    options = CreatorOptions.tones,
                    onSelect = viewModel::onToneChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        ShopCard {
            SectionLabel("🔗 Proxy URL")
            ShopTextField(
                value = state.proxyUrl,
                onValueChange = viewModel::onProxyChange,
                placeholder = "http://10.0.2.2:3001",
            )
            Hint("CORS-Proxy erforderlich. Emulator: 10.0.2.2 zeigt auf localhost des Hosts. Gerät: LAN-IP des Proxy.")
        }

        PrimaryButton(
            text = if (state.loading) "Analysiere Produkt..." else "⚡ Content generieren",
            onClick = viewModel::generate,
            loading = state.loading,
            enabled = !state.loading,
        )

        state.error?.let { ErrorBanner(it) }

        state.result?.let { result ->
            ResultsSection(
                result = result,
                copiedKey = state.copiedKey,
                onCopy = { key ->
                    val text = viewModel.copyText(key, viewModel.textFor(key, result))
                    copyToClipboard(context, text)
                },
            )
        }

        Text(
            "TikTok Shop Creator · SparDirekt DE · Kein Preis · Keine Rabatte · TikTok-safe",
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ImageSlotCard(
    slot: ImageSlot,
    onPicked: (Uri) -> Unit,
    onRemoved: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri)
    }
    ShopCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(slot.title)
            Badge(if (slot.required) "Pflichtfeld" else "Optional", if (slot.required) Danger else TextMid)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(2.dp, if (slot.image != null) NeonGreen else Border, RoundedCornerShape(14.dp))
                .background(if (slot.image != null) Bg3 else NeonGreenDim.copy(alpha = 0.15f))
                .clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            contentAlignment = Alignment.Center,
        ) {
            val image = slot.image
            if (image != null) {
                AsyncImage(
                    model = Uri.parse(image.uriString),
                    contentDescription = slot.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.AddPhotoAlternate,
                        contentDescription = null,
                        tint = TextDim,
                        modifier = Modifier.size(44.dp),
                    )
                    Text("Foto hier auswählen", color = TextMid, fontWeight = FontWeight.SemiBold)
                    Text(slot.subtitle, color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp))
                }
            }
        }
        if (slot.image != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(slot.image.displayName, color = TextMid, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onRemoved,
                    colors = ButtonDefaults.textButtonColors(contentColor = Danger),
                ) { Text("✕ Entfernen", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun ResultsSection(
    result: de.spardirekt.tiktokshop.data.GeneratedContent,
    copiedKey: String?,
    onCopy: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Ergebnisse", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            CopyChip("Alles kopieren", copiedKey == "all") { onCopy("all") }
        }

        ResultCard("📋 Produktdaten", AccentCyan, copiedKey == "facts", { onCopy("facts") }) {
            FactsBlock(result.productFacts)
        }
        ResultCard("✏️ TikTok Titel", NeonGreen, copiedKey == "title", { onCopy("title") }) {
            Text(result.title.ifBlank { "—" }, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        }
        ResultCard("🪝 5 Hook Ideas", AccentAmber, copiedKey == "hooks", { onCopy("hooks") }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                result.hooks.forEachIndexed { i, hook ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(AccentAmber.copy(alpha = 0.14f))
                                .border(1.dp, AccentAmber.copy(alpha = 0.4f), RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${i + 1}", color = AccentAmber, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text(hook, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        ResultCard("#️⃣ Hashtags", NeonGreen, copiedKey == "hashtags", { onCopy("hashtags") }) {
            HashtagWrap(result.hashtags)
        }
        ResultCard("🖼️ Banner Text", AccentOrange, copiedKey == "banner", { onCopy("banner") }) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                result.bannerText.forEach { line ->
                    Text(
                        line,
                        color = de.spardirekt.tiktokshop.ui.theme.BannerLine,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AccentOrange.copy(alpha = 0.06f))
                            .border(width = 0.dp, color = androidx.compose.ui.graphics.Color.Transparent)
                            .padding(start = 10.dp, top = 5.dp, bottom = 5.dp, end = 11.dp),
                    )
                }
            }
        }
        ResultCard("🎨 Banner Prompt", androidx.compose.ui.graphics.Color(0xFFA78BFA), copiedKey == "bannerPrompt", { onCopy("bannerPrompt") }) {
            MonoBlock(result.bannerPrompt)
        }
        ResultCard("🎙️ Voice Script", AccentOrange, copiedKey == "voice", { onCopy("voice") }) {
            TimedText(result.voiceoverText, Regex("""^(\d+s)\s*[–-]\s*(.*)"""))
        }
        ResultCard("🎵 Music Suggestion", androidx.compose.ui.graphics.Color(0xFFF472B6), copiedKey == "music", { onCopy("music") }) {
            Text(result.musicSuggestion.ifBlank { "—" }, color = TextPrimary)
        }
        ResultCard("🔊 Sound Effects", androidx.compose.ui.graphics.Color(0xFFA3E635), copiedKey == "sfx", { onCopy("sfx") }) {
            TimedText(result.soundEffects, Regex("""^(\d+s)\s*[–-]\s*(.*)"""))
        }
        ResultCard(
            title = "🎥 Veo 3.1 Prompt",
            accent = androidx.compose.ui.graphics.Color(0xFF818CF8),
            copied = copiedKey == "veo",
            onCopy = { onCopy("veo") },
            extraLabel = "Veo Komplett",
            extraCopied = copiedKey == "veoKomplett",
            onExtra = { onCopy("veoKomplett") },
        ) {
            MonoBlock(result.veoPrompt)
        }
        ResultCard("🔴 TikTok Live Script", AccentRed, copiedKey == "live", { onCopy("live") }) {
            TimedText(result.liveScript, Regex("""^(\d+:\d+)\s*\|(.*)"""))
        }
        ResultCard(
            title = "📦 Master Copy Block",
            accent = NeonGreen,
            copied = copiedKey == "master",
            onCopy = { onCopy("master") },
            extraLabel = "Alles für Veo",
            extraCopied = copiedKey == "master",
        ) {
            MonoBlock(de.spardirekt.tiktokshop.data.ResultFormatter.buildMasterText(result))
        }
    }
}

@Composable
private fun ResultCard(
    title: String,
    accent: androidx.compose.ui.graphics.Color,
    copied: Boolean,
    onCopy: () -> Unit,
    extraLabel: String? = null,
    extraCopied: Boolean = false,
    onExtra: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AccentCard(accent = accent) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
            Row {
                if (extraLabel != null && onExtra != null) {
                    CopyChip(extraLabel, extraCopied, onExtra)
                }
                CopyChip("Kopieren", copied, onCopy)
            }
        }
        content()
    }
}

@Composable
private fun FactsBlock(facts: de.spardirekt.tiktokshop.data.ProductFacts) {
    val rows = listOf(
        "Produktname" to facts.name,
        "Maße" to facts.dimensions,
        "Kapazität" to facts.capacity,
        "Material" to facts.material,
        "Gewicht" to facts.weight,
        "Farbe" to facts.color,
        "Lieferumfang" to facts.includedItems.joinToString(", ").ifBlank { "Nicht erkennbar" },
        "Features" to facts.keyFeatures.joinToString(", ").ifBlank { "Nicht erkennbar" },
        "Warnhinweise" to facts.warnings.joinToString(", ").ifBlank { "Nicht erkennbar" },
        "Anwendung" to facts.useCases.joinToString(", ").ifBlank { "Nicht erkennbar" },
    )
    Column {
        rows.forEach { (k, v) ->
            Row(Modifier.padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(k.uppercase(), color = TextMid, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.size(width = 110.dp, height = 18.dp))
                Text(v, color = if (v == "Nicht erkennbar") TextDim else TextPrimary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun MonoBlock(text: String) {
    Text(
        text = text.ifBlank { "—" },
        color = NeonGreen,
        fontSize = 12.sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f))
            .padding(10.dp),
    )
}

@Composable
private fun TimedText(text: String, regex: Regex) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (text.isBlank()) {
            Text("—", color = TextDim)
        } else {
            text.lineSequence().forEach { line ->
                val match = regex.find(line)
                if (match != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            match.groupValues[1],
                            color = NeonGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(NeonGreen.copy(alpha = 0.13f))
                                .padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                        Text(match.groupValues[2].trim(), color = TextPrimary)
                    }
                } else {
                    Text(line, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun HashtagWrap(tags: List<String>) {
    // Simple wrapping via rows of 3
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        tags.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { tag ->
                    Text(
                        tag,
                        color = NeonGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(NeonGreenDim)
                            .border(1.dp, NeonGreen.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 11.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("TikTok Shop Creator", text))
}
