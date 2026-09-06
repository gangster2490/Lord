package de.spardirekt.clipforge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.spardirekt.clipforge.ui.theme.ErrorRed
import de.spardirekt.clipforge.ui.theme.Hairline
import de.spardirekt.clipforge.ui.theme.Surface
import de.spardirekt.clipforge.ui.theme.Surface2
import de.spardirekt.clipforge.ui.theme.TextMid
import de.spardirekt.clipforge.ui.theme.TextPrimary

@Composable
fun ConfirmDestructiveDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface)
                .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                .padding(20.dp)
                .testTag("confirm_dialog"),
        ) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(message, color = TextMid, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogButton("Отмена", Modifier.weight(1f), destructive = false, onClick = onDismiss)
                DialogButton(
                    confirmLabel,
                    Modifier.weight(1f).testTag("confirm_accept"),
                    destructive = true,
                    onClick = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    modifier: Modifier,
    destructive: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (destructive) ErrorRed.copy(alpha = 0.16f) else Surface2)
            .border(1.dp, if (destructive) ErrorRed.copy(alpha = 0.5f) else Hairline, shape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (destructive) ErrorRed else TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}
