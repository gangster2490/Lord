package com.rob.aikeyboard.ui.ime

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rob.aikeyboard.RobApp
import com.rob.aikeyboard.clipboard.ClipItem
import com.rob.aikeyboard.ime.Panel
import com.rob.aikeyboard.ime.RobKeyboardService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ClipboardPanel(service: RobKeyboardService, height: Dp) {
    val app = RobApp.instance
    val store = app.clipboard
    val state = service.state
    var images by remember { mutableStateOf(false) }
    val revision = state.clipboardRevision
    val kind = if (images) ClipItem.Kind.IMAGE else ClipItem.Kind.TEXT
    val items = remember(revision, kind) { store.history.all(kind) }

    Column(Modifier.fillMaxWidth().height(height).background(ImeColors.board)) {
        PanelHeader("Буфер обмена", onClose = { service.togglePanel(Panel.CLIPBOARD) }) {
            Chip("Текст", !images) { images = false }
            Chip("Картинки", images) { images = true }
        }
        if (!app.prefs.clipboardHistory) {
            Text(
                "История буфера выключена в настройках. Закреплённые элементы доступны ниже.",
                color = ImeColors.textDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (images) {
                ImageGrid(service, items)
            } else if (items.isEmpty()) {
                Text(
                    "Скопируйте текст в любом приложении — он появится здесь.\nХранятся 10 последних элементов; 📌 закреплённые — до удаления.",
                    color = ImeColors.textDim, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    items(items, key = { it.id }) { item -> TextClipRow(service, item) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Не закреплённые: ${items.count { !it.pinned }}/10",
                color = ImeColors.textDim, fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
            SmallButton("Очистить историю") { app.io.execute { store.clearUnpinned(kind) } }
        }
    }
}

@Composable
private fun TextClipRow(service: RobKeyboardService, item: ClipItem) {
    val store = RobApp.instance.clipboard
    val io = RobApp.instance.io
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ImeColors.keyBg)
            .border(1.dp, if (item.pinned) ImeColors.cyan else ImeColors.cyanDim, RoundedCornerShape(10.dp))
            .clickable(onClickLabel = "Вставить") { service.pasteClip(item) }
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.value, color = ImeColors.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
        RowIcon(if (item.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, if (item.pinned) "Открепить" else "Закрепить") {
            io.execute { store.setPinned(item.id, !item.pinned) }
        }
        RowIcon(Icons.Outlined.ContentCopy, "Копировать") { service.copyClip(item) }
        RowIcon(Icons.Outlined.Delete, "Удалить") { io.execute { store.remove(item.id) } }
    }
}

@Composable
private fun RowIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Icon(
        icon, label, tint = ImeColors.cyan,
        modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick).padding(10.dp),
    )
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ImageGrid(service: RobKeyboardService, items: List<ClipItem>) {
    val store = RobApp.instance.clipboard
    val io = RobApp.instance.io
    LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
        item {
            Box(
                Modifier.padding(4.dp).aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                    .border(1.dp, ImeColors.cyanDim, RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = "Выбрать изображение") { service.pickImage() },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null, tint = ImeColors.cyan)
                    Text("Скриншот /\nфото", color = ImeColors.textDim, fontSize = 11.sp)
                }
            }
        }
        items(items, key = { it.id }) { item ->
            var bitmap by remember(item.id) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(item.id) { bitmap = withContext(Dispatchers.IO) { thumbnail(item) } }
            Box(
                Modifier.padding(4.dp).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(ImeColors.keyBg)
                    .border(1.dp, if (item.pinned) ImeColors.cyan else ImeColors.cyanDim, RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = "Вставить") { service.pasteClip(item) },
            ) {
                bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Row(Modifier.align(Alignment.TopEnd).background(ImeColors.board.copy(alpha = 0.75f), RoundedCornerShape(bottomStart = 10.dp))) {
                    RowIcon(if (item.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, if (item.pinned) "Открепить" else "Закрепить") {
                        io.execute { store.setPinned(item.id, !item.pinned) }
                    }
                    RowIcon(Icons.Outlined.Delete, "Удалить из истории") { io.execute { store.remove(item.id) } }
                }
            }
        }
    }
    if (items.isEmpty()) {
        Text(
            "Скопированные картинки и выбранные скриншоты появятся здесь. Оригиналы в галерее не удаляются.",
            color = ImeColors.textDim, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        )
    }
}

private fun thumbnail(item: ClipItem): ImageBitmap? {
    val bytes = RobApp.instance.clipboard.imageBytes(item) ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 256) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}
