package de.spardirekt.recipeveo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var copied by remember { mutableStateOf(false) }
    fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("VEO", text))
        copied = true
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(pack.dish, style = MaterialTheme.typography.headlineSmall)
        Block("Рецепт", pack.recipe.asText())
        Block("Промпт Veo 3.1 · 8 секунд", pack.veoPrompt)
        Block("Негативный промпт", pack.negativePrompt)
        Block("Озвучка", pack.voiceover)
        Block("Название для TikTok", pack.tiktokTitle)
        Block("Хештеги", pack.hashtags.joinToString(" "))
        Button(
            onClick = { copy(pack.veoPrompt) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (copied) "Промпт скопирован — вставьте в Gemini" else "Скопировать промпт")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Другое блюдо")
        }
    }
}

@Composable
private fun Block(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body)
    }
}
