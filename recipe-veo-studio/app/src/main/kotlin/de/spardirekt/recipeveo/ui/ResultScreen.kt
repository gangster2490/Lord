package de.spardirekt.recipeveo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.spardirekt.recipeveo.domain.CulinaryPackage

@Composable
fun ResultScreen(
    pack: CulinaryPackage,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf<String?>(null) }
    fun copy(label: String, text: String) {
        if (text.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        copied = label
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(pack.dish, style = MaterialTheme.typography.headlineSmall)
        if (pack.fromOpenAi) {
            Text("Собрано через OpenAI", style = MaterialTheme.typography.bodySmall)
        }
        CopyBlock("Рецепт", pack.recipe.asText(), copied, "Рецепт") { copy("Рецепт", it) }
        CopyBlock("Промпт Veo 3.1 · 8 секунд", pack.veoPrompt, copied, "Промпт Veo 3.1") { copy("Промпт Veo 3.1", it) }
        CopyBlock("Негативный промпт", pack.negativePrompt, copied, "Негативный промпт") { copy("Негативный промпт", it) }
        CopyBlock("Озвучка", pack.voiceover, copied, "Озвучка") { copy("Озвучка", it) }
        CopyBlock("Название для TikTok", pack.tiktokTitle, copied, "Название для TikTok") { copy("Название для TikTok", it) }
        CopyBlock("Хештеги", pack.hashtags.joinToString(" "), copied, "Хештеги") { copy("Хештеги", it) }
        Button(
            onClick = { copy("Промпт Veo 3.1", pack.geminiPrompt()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (copied == "Промпт Veo 3.1") "Промпт скопирован — вставьте в Gemini"
                else "Скопировать промпт в Gemini",
            )
        }
        OutlinedButton(
            onClick = { copy("Пакет", pack.fullPackage()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (copied == "Пакет") "Весь пакет скопирован" else "Скопировать всё")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Другое блюдо")
        }
    }
}

@Composable
private fun CopyBlock(
    title: String,
    body: String,
    copied: String?,
    copyId: String,
    onCopy: (String) -> Unit,
) {
    if (body.isBlank()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onCopy(body) }) {
                    Text(if (copied == copyId) "Скопировано" else "Копировать")
                }
            }
            Text(body)
        }
    }
}
