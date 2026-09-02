package de.spardirekt.recipeveo.ui.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.spardirekt.recipeveo.R
import de.spardirekt.recipeveo.StudioViewModel
import de.spardirekt.recipeveo.domain.CreativeMode
import de.spardirekt.recipeveo.domain.PhotoRef
import de.spardirekt.recipeveo.domain.ProjectStatus
import de.spardirekt.recipeveo.domain.SeedProjects
import de.spardirekt.recipeveo.domain.StudioRules
import de.spardirekt.recipeveo.domain.VoiceLang
import de.spardirekt.recipeveo.domain.label
import de.spardirekt.recipeveo.ui.components.ChoiceChip
import de.spardirekt.recipeveo.ui.components.PrimaryButton
import de.spardirekt.recipeveo.ui.components.SectionLabel
import de.spardirekt.recipeveo.ui.components.StudioCard
import de.spardirekt.recipeveo.ui.components.StudioField

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateScreen(vm: StudioViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val project = state.active()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(StudioRules.MAX_PHOTOS),
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        vm.addPhotos(uris.map { it.toString() })
    }

    if (project == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val generating = project.status == ProjectStatus.Generating

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Recipe VEO Studio", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Фото товара → точный 8-секундный промпт для Veo 3.1. Ролик собирает Veo, не это приложение.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = vm::newProject) { Text("Новый проект") }
        }
        item {
            StudioCard {
                Text("Ваши фото товара", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Можно фото упаковки и скриншоты описания. До ${StudioRules.MAX_PHOTOS} кадров.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                PhotoGrid(
                    photos = project.photos,
                    onAdd = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRemove = vm::removePhoto,
                )
                if (project.photos.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = vm::addDemoPhoto) {
                        Text("Добавить демо-фото Velvet Gold")
                    }
                }
            }
        }
        item {
            StudioField(project.wish, vm::setWish, "Пожелание (необязательно)", singleLine = false, minLines = 2)
        }
        item {
            SectionLabel("Голос")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoiceLang.entries.forEach { ChoiceChip(it.label(), project.voice == it) { vm.setVoice(it) } }
            }
        }
        item {
            SectionLabel("Режим")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CreativeMode.entries.forEach { ChoiceChip(it.label(), project.creative == it) { vm.setCreative(it) } }
            }
        }
        item {
            StudioCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("TikTok Shop", style = MaterialTheme.typography.titleMedium)
                        Text("CTA и хэштеги под витрину", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = project.tiktokShop, onCheckedChange = vm::setTiktok)
                }
            }
        }
        item {
            val enabled = StudioRules.canGenerate(project)
            PrimaryButton(
                text = if (generating) project.stage.label().ifBlank { "Сборка…" } else "Собрать промпт",
                onClick = vm::generate,
                enabled = enabled,
            )
            if (generating) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(project.stage.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (project.status == ProjectStatus.Error && project.error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(project.error, color = MaterialTheme.colorScheme.error)
            }
            if (!enabled && !generating) {
                Spacer(Modifier.height(8.dp))
                Text("Добавьте хотя бы одно фото.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PhotoGrid(
    photos: List<PhotoRef>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val cells = photos + listOf(null)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { photo ->
                    if (photo == null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(onClick = onAdd),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Добавить фото")
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp)),
                        ) {
                            val model: Any = if (photo.uri == SeedProjects.DEMO_PHOTO) {
                                R.drawable.demo_product
                            } else {
                                photo.uri
                            }
                            AsyncImage(
                                model = model,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            IconButton(
                                onClick = { onRemove(photo.id) },
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Убрать")
                            }
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}
