package de.spardirekt.ugcclean.ui.result

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.spardirekt.ugcclean.R
import de.spardirekt.ugcclean.model.ProjectRecord
import de.spardirekt.ugcclean.ui.theme.Accent
import de.spardirekt.ugcclean.ui.theme.Background
import de.spardirekt.ugcclean.ui.theme.Surface
import de.spardirekt.ugcclean.ui.theme.TextMid
import de.spardirekt.ugcclean.ui.theme.TextPrimary

@Composable
fun ResultScreen(
    project: ProjectRecord,
    onCopied: (String) -> Unit,
    onNewProject: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf<String?>(null) }
    fun copy(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        copied = label
        onCopied(context.getString(R.string.copied))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.result_title), color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { copy("package", project.videoPackage()) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background),
        ) {
            Text(stringResource(R.string.copy_package), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        ResultCard(stringResource(R.string.details), project.copyPack?.details.orEmpty()) {
            copy("details", project.copyPack?.details.orEmpty())
        }
        ResultCard(stringResource(R.string.veo_prompt), project.veoPrompt) {
            copy("prompt", project.veoPrompt)
        }
        ResultCard(stringResource(R.string.caption), project.copyPack?.caption.orEmpty()) {
            copy("caption", project.copyPack?.caption.orEmpty())
        }
        ResultCard(stringResource(R.string.hashtags), project.copyPack?.hashtags.orEmpty().joinToString(" ")) {
            copy("hashtags", project.copyPack?.hashtags.orEmpty().joinToString(" "))
        }
        if (copied != null) {
            Text(stringResource(R.string.copied), color = Accent, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onNewProject) { Text(stringResource(R.string.new_project), color = Accent) }
            TextButton(onClick = onBack) { Text("OK", color = TextMid) }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun ResultCard(title: String, body: String, onCopy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(title, color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onCopy) { Text(stringResource(R.string.copy), color = TextMid) }
        }
        Text(
            body.ifBlank { "—" },
            color = TextPrimary,
            fontSize = 14.sp,
            fontFamily = if (title.contains("PROMPT")) FontFamily.Monospace else FontFamily.Default,
            lineHeight = 20.sp,
        )
    }
}
