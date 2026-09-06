package de.spardirekt.ugcclean.ui.result

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import de.spardirekt.ugcclean.R
import de.spardirekt.ugcclean.model.ProjectRecord
import de.spardirekt.ugcclean.ui.theme.Accent
import de.spardirekt.ugcclean.ui.theme.Background
import de.spardirekt.ugcclean.ui.theme.Hairline
import de.spardirekt.ugcclean.ui.theme.Surface
import de.spardirekt.ugcclean.ui.theme.TextMid
import de.spardirekt.ugcclean.ui.theme.TextPrimary

@Composable
fun ResultScreen(
    project: ProjectRecord,
    onCopied: (String) -> Unit,
    onSave: () -> Unit,
    onNewProject: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }
    var previewUri by remember { mutableStateOf<String?>(null) }
    fun copy(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        copied = label
        onCopied(context.getString(R.string.copied))
    }

    Box(Modifier.fillMaxSize().background(Background)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.result_title), color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.veo_references), color = Accent, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            project.firstFrameUri()?.let { uri ->
                ReferenceThumb(uri, stringResource(R.string.first_frame), emphasized = true) { previewUri = uri }
            }
            project.supportUris().forEachIndexed { index, uri ->
                ReferenceThumb(uri, stringResource(R.string.support_n, index + 1), emphasized = false) { previewUri = uri }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.first_frame_hint), color = TextMid, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        PrimaryCopy(stringResource(R.string.copy_package)) { copy("package", project.videoPackage()) }
        Spacer(Modifier.height(8.dp))
        SecondaryCopy(stringResource(R.string.copy_details)) { copy("details", project.copyPack?.details.orEmpty()) }
        SecondaryCopy(stringResource(R.string.copy_prompt)) { copy("prompt", project.veoPrompt) }
        SecondaryCopy(stringResource(R.string.copy_caption)) { copy("caption", project.copyPack?.caption.orEmpty()) }
        SecondaryCopy(stringResource(R.string.copy_hashtags)) {
            copy("hashtags", project.copyPack?.hashtags.orEmpty().joinToString(" "))
        }
        SecondaryCopy(stringResource(R.string.copy_all)) { copy("all", project.copyAll()) }
        SecondaryCopy(stringResource(R.string.save_project), onSave)
        Spacer(Modifier.height(16.dp))
        ResultCard(stringResource(R.string.details), project.copyPack?.details.orEmpty())
        ResultCard(stringResource(R.string.veo_prompt), project.veoPrompt, mono = true)
        ResultCard(stringResource(R.string.caption), project.copyPack?.caption.orEmpty())
        ResultCard(stringResource(R.string.hashtags), project.copyPack?.hashtags.orEmpty().joinToString(" "))
        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(stringResource(R.string.advanced_details), color = TextMid)
        }
        if (showAdvanced) {
            ResultCard(stringResource(R.string.advanced_details), project.advancedDetails())
        }
        if (copied != null) {
            Text(stringResource(R.string.copied), color = Accent, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onNewProject) { Text(stringResource(R.string.new_project), color = Accent) }
            TextButton(onClick = onBack) { Text("OK", color = TextMid) }
        }
        Spacer(Modifier.height(80.dp))
    }
    previewUri?.let { uri ->
        Dialog(onDismissRequest = { previewUri = null }) {
            AsyncImage(
                model = uri,
                contentDescription = stringResource(R.string.first_frame),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { previewUri = null },
            )
        }
    }
    }
}

@Composable
private fun PrimaryCopy(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecondaryCopy(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
    ) {
        Text(label, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ReferenceThumb(uri: String, label: String, emphasized: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp).clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = uri,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(if (emphasized) 72.dp else 64.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (emphasized) Modifier.border(2.dp, Accent, RoundedCornerShape(12.dp))
                    else Modifier.border(1.dp, Hairline, RoundedCornerShape(12.dp)),
                ),
        )
        Text(label, color = if (emphasized) Accent else TextMid, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun ResultCard(title: String, body: String, mono: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(14.dp),
    ) {
        Text(title, color = Accent, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            body.ifBlank { "—" },
            color = TextPrimary,
            fontSize = 14.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            lineHeight = 20.sp,
        )
    }
}
