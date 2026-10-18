package com.rob.aikeyboard.ui.ime

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rob.aikeyboard.RobApp
import com.rob.aikeyboard.emoji.EmojiGroup
import com.rob.aikeyboard.emoji.EmojiItem
import com.rob.aikeyboard.ime.InternalField
import com.rob.aikeyboard.ime.RobKeyboardService

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmojiPanel(service: RobKeyboardService, height: Dp) {
    val app = RobApp.instance
    val catalog = app.emojiCatalog
    val state = service.state
    val search = state.internalField
    var group by remember { mutableStateOf(if (app.emojiHistory.recent().isEmpty()) EmojiGroup.SMILEYS else EmojiGroup.RECENT) }
    var toneTarget by remember { mutableStateOf<EmojiItem?>(null) }
    var historyVersion by remember { mutableIntStateOf(0) }

    fun send(emoji: String) {
        service.commitEmoji(emoji)
        historyVersion++
    }

    if (catalog == null) {
        Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text("Загрузка Emoji…", color = ImeColors.textDim)
        }
        return
    }

    if (search != null) {
        // Search mode: query box + one row of results above the normal keys.
        val results = remember(search.text) { catalog.search(search.text, 80) }
        Column(Modifier.fillMaxWidth().background(ImeColors.board)) {
            Row(Modifier.fillMaxWidth().padding(8.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                InternalFieldBox(search.text, search.hint, Modifier.weight(1f))
                SmallButton("Готово") { state.internalField = null }
            }
            LazyRow(Modifier.fillMaxWidth().height(52.dp)) {
                items(results, key = { it.emoji }) { item ->
                    EmojiCell(item.emoji, 48.dp, onClick = { send(item.emoji) }, onLongClick = { toneTarget = item })
                }
            }
            if (results.isEmpty() && search.text.isNotEmpty()) {
                Text("Ничего не найдено", color = ImeColors.textDim, modifier = Modifier.padding(12.dp, 0.dp))
            }
            KeyboardHost(service)
        }
        return
    }

    Column(Modifier.fillMaxWidth().height(height).background(ImeColors.board)) {
        // Category tabs.
        Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).combinedClickable(onClick = {
                    state.internalField = InternalField("Поиск: сердце, Herz, heart…")
                }),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Search, "Поиск Emoji", tint = ImeColors.cyan) }
            LazyRow(Modifier.weight(1f)) {
                items(EmojiGroup.entries) { g ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (g == group) ImeColors.cyanDim else Color.Transparent)
                            .combinedClickable(onClickLabel = g.label, onClick = { group = g; toneTarget = null }),
                        contentAlignment = Alignment.Center,
                    ) { Text(g.icon, fontSize = 20.sp) }
                }
            }
        }

        toneTarget?.let { item -> ToneRow(item, onPick = { send(it); toneTarget = null }, onClose = { toneTarget = null }) }

        val emojis: List<String> = remember(group, historyVersion) {
            when (group) {
                EmojiGroup.RECENT -> (app.emojiHistory.recent() + app.emojiHistory.frequent()).distinct()
                EmojiGroup.FAVORITES -> app.emojiHistory.favorites()
                else -> catalog.group(group).map { it.emoji }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (emojis.isEmpty()) {
                Text(
                    if (group == EmojiGroup.FAVORITES) "Долгое нажатие на Emoji → ⭐ добавить в избранное" else "Здесь появятся недавние Emoji",
                    color = ImeColors.textDim, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
            }
            LazyVerticalGrid(GridCells.Adaptive(46.dp), Modifier.fillMaxSize()) {
                items(emojis, key = { it }) { e ->
                    EmojiCell(e, 46.dp, onClick = { send(e) }, onLongClick = { toneTarget = catalog.find(e) ?: EmojiItem(e, "", emptyList(), emptyList()) })
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SmallButton("ABC") { service.togglePanel(com.rob.aikeyboard.ime.Panel.EMOJI) }
            Box(
                Modifier.weight(1f).height(36.dp).padding(horizontal = 8.dp).clip(RoundedCornerShape(8.dp))
                    .background(ImeColors.key).combinedClickable(onClick = { service.commitText(" ") }),
            )
            Box(
                Modifier.size(48.dp, 36.dp).clip(RoundedCornerShape(8.dp)).background(ImeColors.chip)
                    .combinedClickable(onClick = { service.onFunction(com.rob.aikeyboard.keyboard.KeyType.DELETE) }),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Outlined.Backspace, "Удалить", tint = ImeColors.text) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiCell(emoji: String, size: Dp, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 26.sp) }
}

/** Skin tones and the favourite toggle for a long-pressed emoji. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToneRow(item: EmojiItem, onPick: (String) -> Unit, onClose: () -> Unit) {
    val history = RobApp.instance.emojiHistory
    var fav by remember(item) { mutableStateOf(history.isFavorite(item.emoji)) }
    Row(
        Modifier.fillMaxWidth().height(50.dp).background(ImeColors.key).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(Modifier.weight(1f)) {
            items(listOf(item.emoji) + item.skinTones) { e -> EmojiCell(e, 46.dp, onClick = { onPick(e) }, onLongClick = {}) }
        }
        Box(
            Modifier.clip(RoundedCornerShape(16.dp)).background(if (fav) ImeColors.cyan else ImeColors.chip)
                .combinedClickable(onClick = {
                    history.toggleFavorite(item.emoji)
                    fav = !fav
                    RobApp.instance.io.execute { RobApp.instance.saveEmojiHistory() }
                })
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(if (fav) "⭐ В избранном" else "☆ В избранное", color = if (fav) ImeColors.key else ImeColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        SmallButton("✕", onClick = onClose)
    }
}
