package de.spardirekt.recipeveo.ui.result

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.recipeveo.StudioViewModel
import de.spardirekt.recipeveo.domain.RecipeCompiler
import de.spardirekt.recipeveo.ui.components.EmptyHint
import de.spardirekt.recipeveo.ui.components.PrimaryButton
import de.spardirekt.recipeveo.ui.components.StudioCard
import kotlinx.coroutines.launch

@Composable
fun ResultScreen(vm: StudioViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val openedId by vm.openedId.collectAsStateWithLifecycle()
    val recipe = openedId?.let { state.recipe(it) }
    val compiled = recipe?.compiled
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun copy(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        scope.launch { snackbar.showSnackbar("Скопировано: $label") }
    }

    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                }
                Text("Промпт", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { padding ->
        if (compiled == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.Center) {
                EmptyHint("Промпт ещё не собран", "Откройте рецепт и нажмите «Собрать промпт».")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(compiled.title, style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(4.dp))
                Text(compiled.hashtags.joinToString("  "), color = MaterialTheme.colorScheme.primary)
            }
            item {
                CopyBlock("VEO 3.1", compiled.veoPrompt, fontMono = true) { copy("VEO", compiled.veoPrompt) }
            }
            if (compiled.voiceover.isNotBlank()) {
                item { CopyBlock("Voiceover", compiled.voiceover) { copy("Voiceover", compiled.voiceover) } }
            }
            item { CopyBlock("Title", compiled.title) { copy("Title", compiled.title) } }
            item { CopyBlock("Hashtags", compiled.hashtags.joinToString(" ")) { copy("Hashtags", compiled.hashtags.joinToString(" ")) } }
            item {
                PrimaryButton("Копировать пакет", onClick = {
                    copy("Package", RecipeCompiler.sharePackage(compiled))
                })
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, RecipeCompiler.sharePackage(compiled))
                            putExtra(Intent.EXTRA_SUBJECT, compiled.title)
                        }
                        context.startActivity(Intent.createChooser(send, compiled.title))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(Modifier.padding(6.dp))
                    Text("Поделиться")
                }
            }
        }
    }
}

@Composable
private fun CopyBlock(label: String, body: String, fontMono: Boolean = false, onCopy: () -> Unit) {
    StudioCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Копировать")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = if (fontMono) {
                MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyLarge
            },
        )
    }
}
