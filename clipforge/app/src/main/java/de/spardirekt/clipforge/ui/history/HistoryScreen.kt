package de.spardirekt.clipforge.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.spardirekt.clipforge.data.model.AdLength
import de.spardirekt.clipforge.data.model.HistoryEntry
import de.spardirekt.clipforge.data.model.Platform
import de.spardirekt.clipforge.ui.StudioEvent
import de.spardirekt.clipforge.ui.StudioUiState
import de.spardirekt.clipforge.ui.components.BrandMark
import de.spardirekt.clipforge.ui.components.ForgeCard
import de.spardirekt.clipforge.ui.result.platformAccent
import de.spardirekt.clipforge.ui.theme.Background
import de.spardirekt.clipforge.ui.theme.BackgroundGlow
import de.spardirekt.clipforge.ui.theme.Hairline
import de.spardirekt.clipforge.ui.theme.Surface2
import de.spardirekt.clipforge.ui.theme.TextMid
import de.spardirekt.clipforge.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    state: StudioUiState,
    onEvent: (StudioEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BackgroundGlow, Background)))
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp),
    ) {
        BrandMark()
        Text(
            text = "Архив роликов",
            color = TextPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        Text(
            text = if (state.history.isEmpty()) {
                "Пока пусто. Соберите первый пакет в Студии — он появится здесь."
            } else {
                "${state.history.size} готовых пакетов. Нажмите, чтобы открыть."
            },
            color = TextMid,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.history, key = { it.id }) { entry ->
                HistoryCard(entry, onEvent)
            }
        }
    }
}

@Composable
private fun HistoryCard(entry: HistoryEntry, onEvent: (StudioEvent) -> Unit) {
    val platform = Platform.fromId(entry.platformId)
    val accent = platformAccent(platform)
    val whenText = SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(entry.createdAt))
    ForgeCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEvent(StudioEvent.OpenHistory(entry.id)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface2)
                    .border(1.dp, Hairline, RoundedCornerShape(10.dp)),
            ) {
                if (entry.thumbnailUri != null) {
                    AsyncImage(
                        model = entry.thumbnailUri,
                        contentDescription = entry.productName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("▶", color = accent)
                    }
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(entry.productName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "${platform.labelRu} · ${AdLength.fromSeconds(entry.lengthSeconds).label} · $whenText",
                    color = TextMid,
                    fontSize = 12.sp,
                )
                Text(
                    entry.ad.cta,
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Удалить из архива",
                tint = TextMid,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onEvent(StudioEvent.RequestDeleteHistory(entry.id)) }
                    .padding(6.dp)
                    .size(18.dp),
            )
        }
    }
}
