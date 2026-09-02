package de.spardirekt.recipeveo.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.spardirekt.recipeveo.domain.Project
import de.spardirekt.recipeveo.domain.StudioRules
import java.io.File

@Composable
fun CreateScreen(
    project: Project,
    onWish: (String) -> Unit,
    onAddPhoto: (Uri) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(StudioRules.MAX_PHOTOS),
    ) { uris -> uris.forEach(onAddPhoto) }

    Column(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Recipe VEO Studio", style = MaterialTheme.typography.headlineSmall)
        Text("Добавьте фото товара и короткое желание. Приложение сразу соберёт 12-секционный промпт на 8 секунд.")
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(project.photos, key = { it.id }) { photo ->
                Box {
                    AsyncImage(
                        model = File(photo.uri),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        "×",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clickable { onRemovePhoto(photo.id) }
                            .padding(6.dp),
                    )
                }
            }
            if (project.photos.size < StudioRules.MAX_PHOTOS) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+ фото")
                    }
                }
            }
        }
        OutlinedTextField(
            value = project.wish,
            onValueChange = { if (it.length <= 42) onWish(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Желание (необязательно, до 42 символов)") },
            singleLine = true,
        )
        Button(
            onClick = onGenerate,
            enabled = StudioRules.canGenerate(project),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Собрать промпт")
        }
    }
}
