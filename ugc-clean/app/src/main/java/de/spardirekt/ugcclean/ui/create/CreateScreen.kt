package de.spardirekt.ugcclean.ui.create

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.spardirekt.ugcclean.R
import de.spardirekt.ugcclean.gen.StartGate
import de.spardirekt.ugcclean.model.PipelineStage
import de.spardirekt.ugcclean.model.SpeechLanguage
import de.spardirekt.ugcclean.ui.theme.Accent
import de.spardirekt.ugcclean.ui.theme.Background
import de.spardirekt.ugcclean.ui.theme.Danger
import de.spardirekt.ugcclean.ui.theme.Hairline
import de.spardirekt.ugcclean.ui.theme.Surface
import de.spardirekt.ugcclean.ui.theme.Surface2
import de.spardirekt.ugcclean.ui.theme.TextDim
import de.spardirekt.ugcclean.ui.theme.TextMid
import de.spardirekt.ugcclean.ui.theme.TextPrimary

@Composable
fun CreateScreen(
    photos: List<String>,
    language: SpeechLanguage,
    canStart: Boolean,
    running: Boolean,
    stage: PipelineStage?,
    percent: Int,
    error: String?,
    onAddPhotos: (List<String>) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onClear: () -> Unit,
    onLanguage: (SpeechLanguage) -> Unit,
    onStart: () -> Unit,
    onRetry: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(StartGate.MAX_PHOTOS),
    ) { uris ->
        onAddPhotos(uris.map { it.toString() })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.create_title), color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.create_subtitle), color = TextMid, fontSize = 15.sp)
        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.photo_count, photos.size),
                color = TextMid,
                modifier = Modifier.weight(1f),
            )
            if (photos.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.clear_all), color = TextMid)
                }
            }
        }

        val tileCount = photos.size + if (photos.size < StartGate.MAX_PHOTOS) 1 else 0
        val rows = ((tileCount + 2) / 3).coerceAtLeast(1)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .height((rows * 118).dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false,
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            itemsIndexed(photos, key = { _, uri -> uri }) { index, uri ->
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(14.dp)),
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = if (index == 0) stringResource(R.string.first_frame) else null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (index == 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Accent.copy(alpha = 0.92f))
                                .padding(vertical = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.first_frame),
                                color = Background,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Background.copy(alpha = 0.8f))
                            .clickable { onRemovePhoto(uri) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.remove), tint = TextPrimary, modifier = Modifier.size(14.dp))
                    }
                }
            }
            if (photos.size < StartGate.MAX_PHOTOS) {
                item {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Surface)
                            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                            .clickable {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Add, contentDescription = null, tint = Accent)
                            Text(stringResource(R.string.add_photos), color = TextMid, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        if (photos.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.first_frame_hint), color = TextMid, fontSize = 12.sp)
        }
        if (photos.size < StartGate.MIN_PHOTOS) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.need_three), color = TextDim, fontSize = 13.sp)
        }

        Spacer(Modifier.height(22.dp))
        Text(stringResource(R.string.language), color = TextMid, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LangChip("Deutsch", language == SpeechLanguage.DE) { onLanguage(SpeechLanguage.DE) }
            LangChip("Русский", language == SpeechLanguage.RU) { onLanguage(SpeechLanguage.RU) }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onStart,
            enabled = canStart && !running,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Background,
                disabledContainerColor = Surface2,
                disabledContentColor = TextDim,
            ),
        ) {
            if (running) {
                CircularProgressIndicator(color = Background, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(10.dp))
                Text(stringResource(R.string.starting), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            } else {
                Text(stringResource(R.string.start), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }

        if (running) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.background), color = TextMid, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (percent.coerceIn(0, 100)) / 100f },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(99.dp)),
                color = Accent,
                trackColor = Surface2,
            )
            Spacer(Modifier.height(8.dp))
            Text(stageLabel(stage), color = TextPrimary, fontSize = 14.sp)
        }

        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(error, color = Danger, fontSize = 14.sp)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry), color = Accent) }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun LangChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Accent.copy(alpha = 0.16f) else Surface
    val border = if (selected) Accent else Hairline
    val color = if (selected) Accent else TextMid
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun stageLabel(stage: PipelineStage?): String = when (stage) {
    PipelineStage.PHOTOS -> stringResource(R.string.stage_photos)
    PipelineStage.ANALYZE -> stringResource(R.string.stage_analyze)
    PipelineStage.PROMPT -> stringResource(R.string.stage_prompt)
    PipelineStage.COPY -> stringResource(R.string.stage_copy)
    PipelineStage.DONE -> stringResource(R.string.result_title)
    null -> stringResource(R.string.starting)
}
