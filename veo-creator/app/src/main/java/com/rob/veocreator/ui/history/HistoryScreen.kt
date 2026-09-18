package com.rob.veocreator.ui.history

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rob.veocreator.data.db.HistoryEntity
import com.rob.veocreator.ui.components.FullscreenVideoDialog
import com.rob.veocreator.ui.theme.VeoCard
import com.rob.veocreator.ui.theme.VeoTextSecondary
import com.rob.veocreator.ui.theme.VeoYellow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val items by viewModel.items.collectAsState()
    var playingUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("History", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No videos yet. Generated videos will appear here.", color = VeoTextSecondary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.id }) { entity ->
                    HistoryRow(
                        entity = entity,
                        onOpen = {
                            val file = File(entity.filePath)
                            if (file.exists()) playingUri = Uri.fromFile(file)
                        },
                        onDelete = { viewModel.delete(entity) },
                        onShare = { shareVideo(context, entity.filePath) }
                    )
                }
            }
        }
    }

    playingUri?.let { uri ->
        FullscreenVideoDialog(uri = uri, onDismiss = { playingUri = null })
    }
}

@Composable
private fun HistoryRow(
    entity: HistoryEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VeoCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable { onOpen() }
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Movie, contentDescription = null, tint = VeoYellow)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    entity.prompt.ifBlank { "(no prompt)" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${entity.model} · ${entity.aspectRatio} · ${entity.durationSeconds}s · ${entity.resolution}",
                    style = MaterialTheme.typography.labelMedium,
                    color = VeoTextSecondary
                )
                Text(
                    formatDate(entity.createdAtMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = VeoTextSecondary
                )
            }

            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Share")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date(millis))

fun shareVideo(context: android.content.Context, filePath: String) {
    val file = File(filePath)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share video"))
}
