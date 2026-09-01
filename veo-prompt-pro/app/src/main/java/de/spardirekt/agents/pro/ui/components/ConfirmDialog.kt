package de.spardirekt.agents.pro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.spardirekt.agents.pro.ui.theme.LocalVppType
import de.spardirekt.agents.pro.ui.theme.VppColors
import de.spardirekt.agents.pro.ui.theme.VppShapes

/**
 * Guard in front of the actions that destroy work: deleting a project, wiping
 * the whole history, and removing the stored API key. All three used to run on
 * a single tap with no undo, and the first two also delete the photo files.
 */
@Composable
fun ConfirmDestructiveDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val type = LocalVppType.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(VppShapes.cardShape)
                .background(VppColors.cardNavy)
                .padding(22.dp)
                .testTag(VppTags.CONFIRM_DIALOG)
        ) {
            Text(title, style = type.sectionTitle.copy(color = VppColors.textLight))
            Spacer(Modifier.height(8.dp))
            Text(message, style = type.secondary.copy(color = VppColors.textMuted))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedActionButton(
                    text = "Отмена",
                    onClick = onDismiss,
                    height = 44.dp,
                    modifier = Modifier.weight(1f)
                )
                DestructiveButton(
                    text = confirmLabel,
                    onClick = {
                        onConfirm()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(VppTags.CONFIRM_ACCEPT)
                )
            }
        }
    }
}
