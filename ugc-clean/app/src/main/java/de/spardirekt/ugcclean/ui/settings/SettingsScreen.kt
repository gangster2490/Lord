package de.spardirekt.ugcclean.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.spardirekt.ugcclean.BuildConfig
import de.spardirekt.ugcclean.R
import de.spardirekt.ugcclean.net.AiProviderId
import de.spardirekt.ugcclean.ui.ProviderKeyUi
import de.spardirekt.ugcclean.ui.theme.Accent
import de.spardirekt.ugcclean.ui.theme.Background
import de.spardirekt.ugcclean.ui.theme.Hairline
import de.spardirekt.ugcclean.ui.theme.Surface
import de.spardirekt.ugcclean.ui.theme.TextMid
import de.spardirekt.ugcclean.ui.theme.TextPrimary

@Composable
fun SettingsScreen(
    provider: AiProviderId,
    openai: ProviderKeyUi,
    gemini: ProviderKeyUi,
    claude: ProviderKeyUi,
    onProvider: (AiProviderId) -> Unit,
    onKeyChange: (AiProviderId, String) -> Unit,
    onToggleMask: (AiProviderId) -> Unit,
    onSave: (AiProviderId) -> Unit,
    onTest: (AiProviderId) -> Unit,
    onRemove: (AiProviderId) -> Unit,
) {
    val rows = listOf(
        ProviderRowSpec(AiProviderId.OPENAI, R.string.provider_openai, R.string.api_key_hint_openai, openai),
        ProviderRowSpec(AiProviderId.GEMINI, R.string.provider_gemini, R.string.api_key_hint_gemini, gemini),
        ProviderRowSpec(AiProviderId.CLAUDE, R.string.provider_claude, R.string.api_key_hint_claude, claude),
    )
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                ProviderRow(
                    label = stringResource(row.title),
                    status = if (row.ui.saved) {
                        stringResource(R.string.provider_status_saved)
                    } else {
                        stringResource(R.string.provider_status_missing)
                    },
                    selected = provider == row.id,
                    onClick = { onProvider(row.id) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.provider_hint), color = TextMid, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.provider_fallback_hint), color = TextMid, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        rows.sortedByDescending { it.id == provider }.forEach { row ->
            ProviderKeyCard(
                title = stringResource(row.title),
                status = if (row.ui.saved) {
                    stringResource(R.string.provider_status_saved)
                } else {
                    stringResource(R.string.provider_status_missing)
                },
                hint = stringResource(row.hint),
                ui = row.ui,
                onKeyChange = { onKeyChange(row.id, it) },
                onToggleMask = { onToggleMask(row.id) },
                onSave = { onSave(row.id) },
                onTest = { onTest(row.id) },
                onRemove = { onRemove(row.id) },
            )
        }
        Text(stringResource(R.string.demo_hint), color = TextMid, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.settings_build, BuildConfig.VERSION_NAME, BuildConfig.APPLICATION_ID),
            color = TextMid,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(80.dp))
    }
}

private data class ProviderRowSpec(
    val id: AiProviderId,
    val title: Int,
    val hint: Int,
    val ui: ProviderKeyUi,
)

@Composable
private fun ProviderKeyCard(
    title: String,
    status: String,
    hint: String,
    ui: ProviderKeyUi,
    onKeyChange: (String) -> Unit,
    onToggleMask: () -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(14.dp),
    ) {
        Text(title, color = Accent, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(status, color = TextMid, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = ui.draft,
            onValueChange = onKeyChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint, color = TextMid) },
            visualTransformation = if (ui.masked) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = onToggleMask) {
                    Icon(
                        if (ui.masked) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
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
                focusedContainerColor = Background,
                unfocusedContainerColor = Background,
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background),
        ) { Text(stringResource(R.string.save_key), fontWeight = FontWeight.Bold) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onTest, shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.test_key), color = Accent)
            }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.remove_key), color = TextMid) }
        }
    }
}

@Composable
private fun ProviderRow(
    label: String,
    status: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) Accent.copy(alpha = 0.16f) else Surface
    val border = if (selected) Accent else Hairline
    val color = if (selected) Accent else TextPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = color, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(status, color = TextMid, fontSize = 12.sp)
    }
}
