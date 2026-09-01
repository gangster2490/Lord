package de.spardirekt.agents.pro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.spardirekt.agents.pro.ui.theme.LocalVppType
import de.spardirekt.agents.pro.ui.theme.VppColors
import de.spardirekt.agents.pro.ui.theme.VppShapes

/**
 * Single API key prompt. Create and Settings previously carried two copies that
 * had already drifted: only one of them showed the `sk-…` hint.
 */
@Composable
fun ApiKeyDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val type = LocalVppType.current
    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(VppShapes.cardShape)
                .background(VppColors.cardNavy)
                .padding(22.dp)
                .testTag(VppTags.API_KEY_DIALOG)
        ) {
            Text("OpenAI API", style = type.sectionTitle.copy(color = VppColors.textLight))
            Spacer(Modifier.height(8.dp))
            Text(
                "Добавьте API-ключ для анализа фотографий и создания промптов.",
                style = type.secondary.copy(color = VppColors.textMuted)
            )
            Spacer(Modifier.height(14.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                textStyle = type.body.copy(color = VppColors.textLight),
                cursorBrush = SolidColor(VppColors.accentPurple),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(VppColors.cardInset)
                    .padding(14.dp)
                    .testTag(VppTags.API_KEY_FIELD),
                decorationBox = { inner ->
                    Box {
                        if (value.isBlank()) {
                            Text("sk-…", color = VppColors.textMuted, style = type.body)
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientPillButton(
                    text = "Сохранить",
                    onClick = onSave,
                    enabled = value.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(VppTags.API_KEY_SAVE)
                )
                OutlinedActionButton(
                    text = "Отмена",
                    onClick = onCancel,
                    height = 44.dp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
