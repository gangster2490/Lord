package de.spardirekt.tiktokshop.ui.veo

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.spardirekt.tiktokshop.ui.components.ErrorBanner
import de.spardirekt.tiktokshop.ui.components.Hint
import de.spardirekt.tiktokshop.ui.components.PrimaryButton
import de.spardirekt.tiktokshop.ui.components.SectionLabel
import de.spardirekt.tiktokshop.ui.components.ShopCard
import de.spardirekt.tiktokshop.ui.components.ShopDropdown
import de.spardirekt.tiktokshop.ui.components.ShopTextField
import de.spardirekt.tiktokshop.ui.theme.Bg2
import de.spardirekt.tiktokshop.ui.theme.Bg3
import de.spardirekt.tiktokshop.ui.theme.Border
import de.spardirekt.tiktokshop.ui.theme.Danger
import de.spardirekt.tiktokshop.ui.theme.NeonGreen
import de.spardirekt.tiktokshop.ui.theme.NeonGreenDim
import de.spardirekt.tiktokshop.ui.theme.TextDim
import de.spardirekt.tiktokshop.ui.theme.TextMid
import de.spardirekt.tiktokshop.ui.theme.TextPrimary

@Composable
fun VeoCleanerScreen(viewModel: VeoViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.toast) {
        val toast = state.toast
        if (toast != null) {
            snackbar.showSnackbar(toast)
            viewModel.consumeToast()
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (state.page) {
            VeoPage.Home -> VeoHome(
                hasKey = state.apiKey.isNotBlank(),
                onNew = viewModel::startNewProduct,
                onHistory = { viewModel.show(VeoPage.History) },
                onSettings = { viewModel.show(VeoPage.Settings) },
            )
            VeoPage.Upload -> VeoUpload(state, viewModel)
            VeoPage.Result -> VeoResult(state, viewModel, context)
            VeoPage.History -> VeoHistory(state, viewModel)
            VeoPage.Settings -> VeoSettings(state, viewModel)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun VeoHome(
    hasKey: Boolean,
    onNew: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
            Text(
                "⚙️ API Key",
                color = TextMid,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Bg3)
                    .border(1.dp, Border, RoundedCornerShape(10.dp))
                    .clickable(onClick = onSettings)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(NeonGreenDim)
                    .border(2.dp, NeonGreen, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("🧹", fontSize = 38.sp) }
            Text("VEO Product", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
            Text("Photo Cleaner", style = MaterialTheme.typography.headlineLarge, color = NeonGreen)
            Text(
                "Lade Produktfotos hoch → erhalte ein sauberes 9:16 VEO-Referenzbild",
                color = TextDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!hasKey) {
                Text(
                    "⚠️ Kein OpenAI API Key gesetzt — hier tippen",
                    color = Danger,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Danger.copy(alpha = 0.1f))
                        .border(1.dp, Danger.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onSettings)
                        .padding(10.dp),
                )
            }
            PrimaryButton("📸 Neues Produkt", onNew)
            SecondaryAction("🕒 Verlauf", onHistory)
            SecondaryAction("⚙️ Einstellungen", onSettings)
        }
    }
}

@Composable
private fun SecondaryAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Bg3)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun VeoHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück", tint = NeonGreen)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    }
}

@Composable
private fun VeoUpload(state: VeoUiState, viewModel: VeoViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        if (uris.isNotEmpty()) viewModel.addPhotos(uris)
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VeoHeader("Neues Produkt") { viewModel.show(VeoPage.Home) }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Bg2)
                .border(2.dp, NeonGreen.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📁", fontSize = 36.sp)
                Text("Fotos hochladen", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("Bis zu 9 Fotos · JPG, PNG, WebP", color = TextDim, fontSize = 12.sp)
            }
        }
        if (state.photos.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.photos.chunked(3).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { col, photo ->
                            val index = rowIndex * 3 + col
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp)),
                            ) {
                                AsyncImage(
                                    model = Uri.parse(photo.uriString),
                                    contentDescription = photo.displayName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Danger.copy(alpha = 0.9f))
                                        .clickable { viewModel.removePhoto(index) },
                                    contentAlignment = Alignment.Center,
                                ) { Text("✕", color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp) }
                            }
                        }
                        repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
            Text("${state.photos.size}/9 Fotos geladen", color = TextDim, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (state.loading) StatusBar(state.status ?: "Verarbeite...")
        state.error?.let { ErrorBanner(it) }
        state.dna?.let { dna ->
            ShopCard {
                SectionLabel("⚡ Product DNA")
                DnaRow("Produkt", dna.name)
                DnaRow("Kategorie", dna.category)
                DnaRow("Form", dna.shape)
                DnaRow("Material", dna.material)
                DnaRow("Farbe", dna.color)
                DnaRow("Details", dna.details)
                DnaRow("Nicht ändern", dna.doNotChange)
            }
        }
        SecondaryAction("🔍 Produkt analysieren") { viewModel.analyze() }
        PrimaryButton(
            text = "✨ 9:16 VEO Foto erstellen",
            onClick = viewModel::generateImage,
            enabled = state.photos.isNotEmpty() && !state.loading,
            loading = state.loading,
        )
    }
}

@Composable
private fun DnaRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercase(), color = TextDim, fontSize = 11.sp, modifier = Modifier.size(width = 90.dp, height = 16.dp))
        Text(value.ifBlank { "–" }, color = TextPrimary, fontSize = 13.sp)
    }
}

@Composable
private fun StatusBar(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Bg2)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(color = NeonGreen, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        Text(text, color = TextMid, fontSize = 13.sp)
    }
}

@Composable
private fun VeoResult(state: VeoUiState, viewModel: VeoViewModel, context: Context) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VeoHeader("VEO Ergebnis") { viewModel.show(VeoPage.Upload) }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Bg2)
                .border(1.dp, Border, RoundedCornerShape(16.dp)),
        ) {
            AsyncImage(
                model = state.resultUrl,
                contentDescription = "Generated product image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(androidx.compose.ui.graphics.Color.White),
                contentScale = ContentScale.Fit,
            )
            Text(
                "9:16 · VEO READY",
                color = androidx.compose.ui.graphics.Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(NeonGreen)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        ShopCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SectionLabel("VEO PROMPT")
                Text(
                    "Kopieren",
                    color = NeonGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        copy(context, state.prompt)
                        viewModel.apply { /* toast via snackbar from screen if needed */ }
                    },
                )
            }
            Text(state.prompt, color = TextDim, fontSize = 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { SecondaryAction("⬇️ Download") { viewModel.downloadResult() } }
            Box(Modifier.weight(1f)) {
                SecondaryAction("📋 Prompt") {
                    copy(context, state.prompt)
                }
            }
        }
        PrimaryButton(text = "🔄 Neue Generation", onClick = { viewModel.show(VeoPage.Upload) })
        SecondaryAction("🏠 Home") { viewModel.show(VeoPage.Home) }
    }
}

@Composable
private fun VeoHistory(state: VeoUiState, viewModel: VeoViewModel) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VeoHeader("Verlauf") { viewModel.show(VeoPage.Home) }
        if (state.history.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🕒", fontSize = 40.sp)
                Text("Noch keine Generierungen gespeichert.", color = TextDim)
            }
        } else {
            state.history.forEach { entry ->
                ShopCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(entry.productName, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(entry.date, color = TextDim, fontSize = 11.sp)
                    }
                    if (entry.resultUrl.isNotBlank()) {
                        AsyncImage(
                            model = entry.resultUrl,
                            contentDescription = entry.productName,
                            modifier = Modifier
                                .size(width = 44.dp, height = 66.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.5.dp, NeonGreen, RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (entry.resultUrl.isNotBlank()) {
                            MiniBtn("🖼️ Anzeigen") { viewModel.loadHistoryResult(entry) }
                        }
                        MiniBtn("📋 Prompt") { copy(context, entry.prompt) }
                        MiniBtn("🔄 Neu") { viewModel.loadHistoryProduct(entry) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniBtn(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = TextPrimary,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Bg3)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun VeoSettings(state: VeoUiState, viewModel: VeoViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VeoHeader("Einstellungen") { viewModel.show(VeoPage.Home) }
        SectionLabel("OpenAI API Key")
        ShopTextField(
            value = state.apiKey,
            onValueChange = viewModel::onApiKeyChange,
            placeholder = "sk-...",
            password = true,
        )
        Hint("Dein Key wird nur lokal auf dem Gerät gespeichert.")
        state.keyTest?.let {
            Text(it, color = if (state.keyTestOk == true) NeonGreen else Danger, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { SecondaryAction("🔌 Key testen", viewModel::testKey) }
            Box(Modifier.weight(1f)) { PrimaryButton(text = "💾 Speichern", onClick = viewModel::saveSettings) }
        }
        ShopDropdown(
            label = "Analyse-Modell",
            value = state.analysisModel,
            options = listOf("gpt-4o", "gpt-4o-mini"),
            onSelect = viewModel::onAnalysisModel,
        )
        ShopDropdown(
            label = "Bildgenerierung",
            value = state.imageModel,
            options = listOf("dall-e-3", "dall-e-2"),
            onSelect = viewModel::onImageModel,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Danger)
                .clickable(onClick = viewModel::clearHistory)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("🗑️ Verlauf löschen", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("VEO Prompt", text))
}
