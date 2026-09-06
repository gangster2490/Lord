package de.spardirekt.ugcclean.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import de.spardirekt.ugcclean.model.ProjectRecord
import de.spardirekt.ugcclean.model.ProjectStatus
import de.spardirekt.ugcclean.ui.theme.Accent
import de.spardirekt.ugcclean.ui.theme.Background
import de.spardirekt.ugcclean.ui.theme.Danger
import de.spardirekt.ugcclean.ui.theme.Surface
import de.spardirekt.ugcclean.ui.theme.TextMid
import de.spardirekt.ugcclean.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    projects: List<ProjectRecord>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.tab_history), color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        if (projects.isEmpty()) {
            Text(stringResource(R.string.history_empty), color = TextMid)
        } else {
            LazyColumn {
                items(projects, key = { it.id }) { project ->
                    HistoryRow(project, onOpen = { onOpen(project.id) }, onDelete = { onDelete(project.id) })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(project: ProjectRecord, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = project.thumbnailUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Background),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                project.plan?.productName?.ifBlank { null } ?: project.status.name,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                buildString {
                    append(statusLabel(project.status))
                    if (project.aiProvider.isNotBlank()) {
                        append(" · ")
                        append(project.aiProvider)
                    }
                    append(" · ")
                    append(formatTime(project.updatedAt))
                },
                color = if (project.status == ProjectStatus.ERROR) Danger else TextMid,
                fontSize = 12.sp,
            )
        }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = TextMid) }
    }
}

private fun statusLabel(status: ProjectStatus): String = when (status) {
    ProjectStatus.READY -> "READY"
    ProjectStatus.RUNNING -> "START"
    ProjectStatus.ERROR -> "ERROR"
    ProjectStatus.DRAFT -> "DRAFT"
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date(ms))
