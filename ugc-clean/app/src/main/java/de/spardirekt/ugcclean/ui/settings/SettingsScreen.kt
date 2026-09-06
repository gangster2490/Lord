package de.spardirekt.ugcclean.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.spardirekt.ugcclean.R
import de.spardirekt.ugcclean.net.AiProviderId
import de.spardirekt.ugcclean.ui.theme.Accent
import de.spardirekt.ugcclean.ui.theme.Background
import de.spardirekt.ugcclean.ui.theme.Hairline
import de.spardirekt.ugcclean.ui.theme.Surface
import de.spardirekt.ugcclean.ui.theme.TextMid
import de.spardirekt.ugcclean.ui.theme.TextPrimary

@Composable
fun SettingsScreen(
    provider: AiProviderId,
    keyDraft: String,
    masked: Boolean,
    onProvider: (AiProviderId) -> Unit,
    onKeyChange: (String) -> Unit,
    onToggleMask: () -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.settings_title), color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.provider), color = TextMid, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderChip(stringResource(R.string.provider_openai), provider == AiProviderId.OPENAI) {
                onProvider(AiProviderId.OPENAI)
            }
            ProviderChip(stringResource(R.string.provider_gemini), provider == AiProviderId.GEMINI) {
                onProvider(AiProviderId.GEMINI)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.provider_hint), color = TextMid, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Text(
            if (provider == AiProviderId.GEMINI) stringResource(R.string.api_key_gemini) else stringResource(R.string.api_key_openai),
            color = TextMid,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = keyDraft,
            onValueChange = onKeyChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    if (provider == AiProviderId.GEMINI) {
                        stringResource(R.string.api_key_hint_gemini)
                    } else {
                        stringResource(R.string.api_key_hint_openai)
                    },
                    color = TextMid,
                )
            },
            visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = onToggleMask) {
                    Icon(
                        if (masked) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        tint = TextMid,
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Hairline,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Accent,
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface,
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.demo_hint), color = TextMid, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background),
        ) { Text(stringResource(R.string.save_key), fontWeight = FontWeight.Bold) }
        TextButton(onClick = onTest) { Text(stringResource(R.string.test_key), color = Accent) }
        TextButton(onClick = onRemove) { Text(stringResource(R.string.remove_key), color = TextMid) }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun ProviderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Accent.copy(alpha = 0.16f) else Surface
    val border = if (selected) Accent else Hairline
    val color = if (selected) Accent else TextMid
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, color = color, fontWeight = FontWeight.Medium)
    }
}
