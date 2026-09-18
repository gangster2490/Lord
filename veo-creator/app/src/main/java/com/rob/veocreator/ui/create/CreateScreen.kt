package com.rob.veocreator.ui.create

import android.content.ClipData
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.rob.veocreator.data.model.AspectRatio
import com.rob.veocreator.data.model.Duration
import com.rob.veocreator.data.model.GenerationState
import com.rob.veocreator.data.model.ImageRole
import com.rob.veocreator.data.model.ModelCapabilities
import com.rob.veocreator.data.model.Resolution
import com.rob.veocreator.data.model.SelectedImage
import com.rob.veocreator.data.model.ModelChoice
import com.rob.veocreator.data.model.VideoMode
import com.rob.veocreator.ui.components.FullscreenVideoDialog
import com.rob.veocreator.ui.components.VideoPlayerView
import com.rob.veocreator.ui.history.shareVideo
import com.rob.veocreator.ui.theme.VeoCard
import com.rob.veocreator.ui.theme.VeoTextSecondary
import com.rob.veocreator.ui.theme.VeoYellow
import com.rob.veocreator.util.MediaUtils
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun CreateScreen(
    onOpenSettings: () -> Unit,
    viewModel: CreateViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var showFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshApiKeyState() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris -> if (uris.isNotEmpty()) viewModel.addImages(uris) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) viewModel.addImages(uris) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Create", style = MaterialTheme.typography.headlineSmall)

        if (!state.hasApiKey) {
            ApiKeyRequiredBanner(onOpenSettings)
        }

        ModeSelector(state.mode, viewModel::setMode)

        if (state.mode == VideoMode.IMAGE_TO_VIDEO) {
            ImagePickerSection(
                images = state.images,
                isAnalyzing = state.isAnalyzing,
                onPickFromGallery = {
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onPickFromFiles = { filePickerLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
                onRemove = viewModel::removeImage,
                onSetPrimary = viewModel::setPrimaryImage,
                onAnalyze = viewModel::analyzeImages
            )

            state.analysisSuggestion?.let { suggestion ->
                SuggestionCard(
                    suggestion = suggestion,
                    onInsert = viewModel::insertSuggestion,
                    onDismiss = viewModel::dismissSuggestion
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Use multiple images for product consistency", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Off: one starting image animates. On: up to 3 photos are sent as Veo " +
                            "reference images instead (no starting frame, locked to 8s).",
                        style = MaterialTheme.typography.labelMedium,
                        color = VeoTextSecondary
                    )
                }
                Switch(checked = state.useMultipleImages, onCheckedChange = viewModel::setUseMultipleImages)
            }
        }

        PromptSection(
            prompt = state.prompt,
            onPromptChange = viewModel::setPrompt,
            onPaste = { clipboard.getText()?.text?.let { viewModel.setPrompt(it) } },
            onClear = viewModel::clearPrompt
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Text overlays in video", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Off by default. Extracted spec text is never shown on screen unless enabled.",
                    style = MaterialTheme.typography.labelMedium,
                    color = VeoTextSecondary
                )
            }
            Switch(checked = state.enableTextOverlays, onCheckedChange = viewModel::setTextOverlaysEnabled)
        }

        SectionLabel("Model")
        ChipRow(ModelChoice.entries.toList(), state.modelChoice, { it.label }, viewModel::setModelChoice)

        SectionLabel("Aspect Ratio")
        ChipRow(AspectRatio.entries.toList(), state.aspectRatio, { it.label }, viewModel::setAspectRatio)

        SectionLabel("Resolution")
        val allowedRes = state.modelChoice.allowedResolutions()
        ChipRow(
            Resolution.entries.toList(),
            state.resolution,
            { it.label },
            viewModel::setResolution,
            enabledPredicate = { it in allowedRes }
        )

        SectionLabel("Duration")
        val allowedDur = ModelCapabilities.allowedDurations(state.resolution, state.usesReferenceImages)
        ChipRow(
            Duration.entries.toList(),
            state.duration,
            { it.label },
            viewModel::setDuration,
            enabledPredicate = { it in allowedDur }
        )
        if (state.usesReferenceImages) {
            Text(
                "Locked to 8s: using ${state.referenceImageCount} reference image(s) for product consistency.",
                style = MaterialTheme.typography.labelMedium,
                color = VeoTextSecondary
            )
        }

        CostEstimateCard(state)

        Button(
            onClick = viewModel::generate,
            enabled = state.canGenerate,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VeoYellow,
                contentColor = Color.Black,
                disabledContainerColor = VeoYellow.copy(alpha = 0.35f)
            )
        ) {
            Text("Generate", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        GenerationStatusSection(
            state = state.generationState,
            elapsedSeconds = state.elapsedSeconds,
            onCancel = viewModel::cancelGeneration,
            onReset = viewModel::resetToIdle
        )

        val completedPath = (state.generationState as? GenerationState.Completed)?.videoFilePath
        if (completedPath != null) {
            val file = File(completedPath)
            if (file.exists()) {
                val uri = Uri.fromFile(file)
                Card(colors = CardDefaults.cardColors(containerColor = VeoCard), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        VideoPlayerView(uri = uri, modifier = Modifier.fillMaxWidth().height(360.dp))
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { showFullscreen = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Fullscreen, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Fullscreen")
                            }
                            OutlinedButton(
                                onClick = {
                                    MediaUtils.saveVideoToMovies(context, file)
                                    savedMessage = "Video saved successfully"
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Save")
                            }
                            OutlinedButton(onClick = { shareVideo(context, completedPath) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Share, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Share")
                            }
                        }
                        savedMessage?.let {
                            LaunchedEffect(it) { delay(2500); savedMessage = null }
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (showFullscreen) {
                    FullscreenVideoDialog(uri = uri, onDismiss = { showFullscreen = false })
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ApiKeyRequiredBanner(onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Gemini API key required", fontWeight = FontWeight.SemiBold)
                Text("Add your key to start generating videos.", style = MaterialTheme.typography.labelMedium, color = VeoTextSecondary)
            }
            TextButton(onClick = onOpenSettings) { Text("Open Settings") }
        }
    }
}

@Composable
private fun ModeSelector(mode: VideoMode, onSelect: (VideoMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(4.dp)
    ) {
        VideoMode.entries.forEach { m ->
            val selected = m == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(m) }
                    .background(if (selected) VeoYellow else Color.Transparent, RoundedCornerShape(10.dp))
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (m == VideoMode.TEXT_TO_VIDEO) "Text to Video" else "Image to Video",
                    color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ImagePickerSection(
    images: List<SelectedImage>,
    isAnalyzing: Boolean,
    onPickFromGallery: () -> Unit,
    onPickFromFiles: () -> Unit,
    onRemove: (String) -> Unit,
    onSetPrimary: (String) -> Unit,
    onAnalyze: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Images", modifier = Modifier.weight(1f))
            if (images.isNotEmpty()) {
                TextButton(onClick = onAnalyze, enabled = !isAnalyzing) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (isAnalyzing) "Analyzing..." else "Analyze & Enhance Prompt")
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(images, key = { it.id }) { image ->
                ImageThumbnail(image, onRemove, onSetPrimary)
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                            .clickable { onPickFromGallery() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add image")
                    }
                    TextButton(onClick = onPickFromFiles) { Text("Files", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

@Composable
private fun ImageThumbnail(
    image: SelectedImage,
    onRemove: (String) -> Unit,
    onSetPrimary: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = if (image.isPrimary) 2.dp else 0.dp,
                    color = if (image.isPrimary) VeoYellow else Color.Transparent,
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable { onSetPrimary(image.id) }
        ) {
            AsyncImage(
                model = image.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            if (image.isPrimary) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Primary",
                    tint = VeoYellow,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(18.dp)
                )
            }
            IconButton(
                onClick = { onRemove(image.id) },
                modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White)
            }
        }
        if (image.role != ImageRole.UNANALYZED) {
            Text(
                image.role.label,
                style = MaterialTheme.typography.labelMedium,
                color = VeoTextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SuggestionCard(suggestion: String, onInsert: () -> Unit, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = VeoCard), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("AI suggestion from your images", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(suggestion, style = MaterialTheme.typography.bodyMedium, color = VeoTextSecondary)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onInsert, colors = ButtonDefaults.buttonColors(containerColor = VeoYellow, contentColor = Color.Black)) {
                    Text("Insert into prompt")
                }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun CostEstimateCard(state: CreateUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = VeoCard), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("Model: ${state.effectiveModel.displayName}", style = MaterialTheme.typography.titleMedium)
            Text(
                "${state.resolution.label} · ${state.duration.label}",
                style = MaterialTheme.typography.labelMedium,
                color = VeoTextSecondary
            )
            Spacer(Modifier.height(6.dp))
            val cost = state.estimatedCostUsd
            Text(
                "Estimated API cost: " + (cost?.let { "$" + "%.2f".format(it) } ?: "n/a"),
                style = MaterialTheme.typography.bodyLarge,
                color = VeoYellow
            )
            if (state.modelChoice == ModelChoice.AUTO_CHEAPEST) {
                Spacer(Modifier.height(4.dp))
                Text(
                    state.costExplanation ?: "Cheapest compatible model selected automatically",
                    style = MaterialTheme.typography.labelMedium,
                    color = VeoTextSecondary
                )
            }
        }
    }
}

@Composable
private fun PromptSection(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Prompt", modifier = Modifier.weight(1f))
            TextButton(onClick = onPaste) {
                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Paste")
            }
            TextButton(onClick = onClear) { Text("Clear") }
        }
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            placeholder = { Text("Describe how the image should animate...") },
            modifier = Modifier.fillMaxWidth().height(140.dp),
            keyboardOptions = KeyboardOptions.Default
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

@Composable
private fun <T> ChipRow(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    enabledPredicate: (T) -> Boolean = { true }
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            val isEnabled = enabledPredicate(value)
            FilterChip(
                selected = value == selected,
                onClick = { if (isEnabled) onSelect(value) },
                enabled = isEnabled,
                label = { Text(label(value)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = VeoYellow,
                    selectedLabelColor = Color.Black
                )
            )
        }
    }
}

@Composable
private fun GenerationStatusSection(
    state: GenerationState,
    elapsedSeconds: Int,
    onCancel: () -> Unit,
    onReset: () -> Unit
) {
    when (state) {
        is GenerationState.Idle, is GenerationState.Cancelled -> Unit
        is GenerationState.Uploading, is GenerationState.Submitting,
        is GenerationState.Generating, is GenerationState.Downloading -> {
            Card(colors = CardDefaults.cardColors(containerColor = VeoCard), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = VeoYellow)
                    Spacer(Modifier.height(10.dp))
                    Text(statusLabel(state), style = MaterialTheme.typography.titleMedium)
                    Text("Elapsed: ${elapsedSeconds}s", style = MaterialTheme.typography.labelMedium, color = VeoTextSecondary)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }
        is GenerationState.Error -> {
            var detailsExpanded by remember(state) { mutableStateOf(false) }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Error", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    if (!state.technicalDetails.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                            Text(if (detailsExpanded) "Hide technical details" else "Technical details")
                        }
                        if (detailsExpanded) {
                            Text(
                                state.technicalDetails,
                                style = MaterialTheme.typography.labelMedium,
                                color = VeoTextSecondary
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onReset) { Text("Dismiss") }
                }
            }
        }
        is GenerationState.Completed -> Unit
    }

    if (state is GenerationState.Cancelled) {
        Text("Generation cancelled.", color = VeoTextSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun statusLabel(state: GenerationState): String = when (state) {
    is GenerationState.Uploading -> "Preparing images..."
    is GenerationState.Submitting -> "Sending request to Veo..."
    is GenerationState.Generating -> "Generating video..."
    is GenerationState.Downloading -> "Downloading video..."
    else -> ""
}
