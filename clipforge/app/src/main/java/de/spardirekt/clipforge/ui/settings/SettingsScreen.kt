package de.spardirekt.clipforge.ui.settings

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.spardirekt.clipforge.ui.StudioEvent
import de.spardirekt.clipforge.ui.StudioUiState
import de.spardirekt.clipforge.ui.components.BrandMark
import de.spardirekt.clipforge.ui.components.ErrorBanner
import de.spardirekt.clipforge.ui.components.ForgeCard
import de.spardirekt.clipforge.ui.components.SectionLabel
import de.spardirekt.clipforge.ui.theme.Background
import de.spardirekt.clipforge.ui.theme.BackgroundGlow
import de.spardirekt.clipforge.ui.theme.Cyan
import de.spardirekt.clipforge.ui.theme.Hairline
import de.spardirekt.clipforge.ui.theme.Magenta
import de.spardirekt.clipforge.ui.theme.TextMid
import de.spardirekt.clipforge.ui.theme.TextPrimary

@Composable
fun SettingsScreen(
    state: StudioUiState,
    onEvent: (StudioEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BackgroundGlow, Background)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BrandMark()
        Text(
            text = "Настройки",
            color = TextPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
        )

        ForgeCard {
            SectionLabel("OpenAI API")
            Text(
                text = "Ключ хранится в EncryptedSharedPreferences. Для проверки без сети вставьте sk-demo — ClipForge соберёт полный демо-пакет.",
                color = TextMid,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = { onEvent(StudioEvent.ApiKeyChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-… или sk-demo") },
                singleLine = true,
                visualTransformation = if (state.showApiKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { onEvent(StudioEvent.ToggleApiKeyVisibility) }) {
                        Icon(
                            imageVector = if (state.showApiKey) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = if (state.showApiKey) "Скрыть ключ" else "Показать ключ",
                            tint = TextMid,
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Magenta,
                    unfocusedBorderColor = Hairline,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Magenta,
                    focusedPlaceholderColor = TextMid,
                    unfocusedPlaceholderColor = TextMid,
                ),
                shape = RoundedCornerShape(12.dp),
            )
            Row {
                TextButton(onClick = { onEvent(StudioEvent.SaveApiKey) }) {
                    Text("Сохранить", color = Magenta, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { onEvent(StudioEvent.TestApiKey) }, enabled = !state.isTestingKey) {
                    Text(
                        if (state.isTestingKey) "Проверяю…" else "Проверить",
                        color = Cyan,
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(onClick = { onEvent(StudioEvent.RequestClearApiKey) }) {
                    Text("Удалить", color = TextMid)
                }
            }
            state.settingsMessage?.let {
                Text(it, color = Cyan, fontSize = 13.sp)
            }
            state.error?.let { ErrorBanner(it) { onEvent(StudioEvent.DismissError) } }
        }

        ForgeCard {
            SectionLabel("Архив")
            Text(
                text = "Пакеты хранятся на этом устройстве. Очистка удаляет все записи и превью.",
                color = TextMid,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            TextButton(onClick = { onEvent(StudioEvent.RequestClearArchive) }) {
                Text("Очистить архив", color = Magenta, fontWeight = FontWeight.Bold)
            }
        }

        ForgeCard {
            SectionLabel("Как пользоваться")
            HowLine("1", "Загрузите фото товара — чем больше ракурсов, тем точнее замок продукта.")
            HowLine("2", "Выберите TikTok Shop, Reels или Shorts и хронометраж 8 или 15 секунд.")
            HowLine("3", "ClipForge отдаёт хуки, подпись, раскадровку и готовый промпт Veo 3.1.")
            HowLine("4", "Скопируйте Veo-пакет в Gemini / Veo. Видеофайл приложение не генерирует.")
        }

        ForgeCard {
            SectionLabel("Правила рекламы")
            Text(
                text = "Без цен, скидок и фальшивой срочности. Товар с фото не перерисовывается. Люди в кадре используют продукт. CTA родной для площадки: корзина TikTok Shop, профиль Reels, описание Shorts.",
                color = TextPrimary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
        }
    }
}

@Composable
private fun HowLine(index: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(index, color = Magenta, fontWeight = FontWeight.Black, fontSize = 14.sp)
        Text(
            text,
            color = TextPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
