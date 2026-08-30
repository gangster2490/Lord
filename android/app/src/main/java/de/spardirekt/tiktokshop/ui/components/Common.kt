package de.spardirekt.tiktokshop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.spardirekt.tiktokshop.ui.theme.Bg2
import de.spardirekt.tiktokshop.ui.theme.Bg3
import de.spardirekt.tiktokshop.ui.theme.Border
import de.spardirekt.tiktokshop.ui.theme.NeonGreen
import de.spardirekt.tiktokshop.ui.theme.TextDim
import de.spardirekt.tiktokshop.ui.theme.TextMid
import de.spardirekt.tiktokshop.ui.theme.TextPrimary

@Composable
fun ShopCard(
    modifier: Modifier = Modifier,
    accent: Color = NeonGreen,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Bg2)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .border(Dp.Hairline, Color.Transparent, RoundedCornerShape(14.dp))
            .padding(top = 2.dp)
            .background(Bg2)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
    // Accent bar is drawn as a top border via overlay
    Box {}
}

@Composable
fun AccentCard(
    modifier: Modifier = Modifier,
    accent: Color = NeonGreen,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Bg2)
            .border(1.dp, Border, RoundedCornerShape(14.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(accent)
                .padding(vertical = 1.dp),
        )
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = TextMid,
        letterSpacing = 1.sp,
    )
}

@Composable
fun Hint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = TextDim)
}

@Composable
fun ShopTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    password: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = TextDim, fontFamily = FontFamily.Monospace) },
        singleLine = singleLine,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
        trailingIcon = {
            when {
                trailing != null -> trailing()
                password -> IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (visible) "Key verbergen" else "Key anzeigen",
                        tint = TextMid,
                    )
                }
            }
        },
        colors = shopFieldColors(),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            color = TextPrimary,
        ),
        shape = RoundedCornerShape(9.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, color = TextMid)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                colors = shopFieldColors(),
                shape = RoundedCornerShape(9.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun shopFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonGreen,
    unfocusedBorderColor = Border,
    focusedContainerColor = Bg3,
    unfocusedContainerColor = Bg3,
    cursorColor = NeonGreen,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
)

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = NeonGreen,
            contentColor = Color.Black,
            disabledContainerColor = NeonGreen.copy(alpha = 0.35f),
            disabledContentColor = Color.Black.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(13.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(end = 8.dp),
                color = Color.Black,
                strokeWidth = 2.dp,
            )
        }
        Text(text, fontWeight = FontWeight.Black, fontSize = 16.sp)
    }
}

@Composable
fun CopyChip(
    label: String,
    copied: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(
            text = if (copied) "✓ Kopiert" else label,
            color = if (copied) NeonGreen else TextMid,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun ErrorBanner(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0x17EF4444))
            .border(1.dp, Color(0x4DEF4444), RoundedCornerShape(9.dp))
            .padding(14.dp),
    ) {
        Text("⚠ $message", color = Color(0xFFFCA5A5), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ResultHeader(
    title: String,
    copied: Boolean,
    onCopy: () -> Unit,
    extra: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        Row {
            extra()
            CopyChip(label = "Kopieren", copied = copied, onClick = onCopy)
        }
    }
}
