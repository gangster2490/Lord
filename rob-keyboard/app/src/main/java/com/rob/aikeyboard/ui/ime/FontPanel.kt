package com.rob.aikeyboard.ui.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rob.aikeyboard.ime.Panel
import com.rob.aikeyboard.ime.RobKeyboardService
import com.rob.aikeyboard.text.FontStyles

private enum class FontTab(val label: String) { FAVORITES("⭐ Избранное"), MAIN("Основные"), EXTRA("Extra") }

/** Choose a decorative Unicode font; favourites and extra looks have their own tabs. */
@Composable
fun FontPanel(service: RobKeyboardService, height: Dp) {
    val state = service.state
    var tab by remember { mutableStateOf(if (state.fontFavorites.isEmpty()) FontTab.MAIN else FontTab.FAVORITES) }
    Column(Modifier.fillMaxWidth().height(height).background(ImeColors.board)) {
        PanelHeader("Шрифты", onClose = { service.togglePanel(Panel.FONT) }) {
            Chip("Рус → лат.", state.translit) { service.setTranslit(!state.translit) }
        }
        Text(
            if (state.translit) "Русские буквы печатаются латиницей (привет → privet), шрифт применяется к ним. Подсказки в этом режиме выключены."
            else "Буквы набираются выбранным шрифтом. Латиница и цифры; русские буквы не меняются — включите «Рус → лат.». Подсказки выключены.",
            color = ImeColors.textDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            FontTab.entries.forEach { t -> Chip(t.label, tab == t) { tab = t } }
        }
        val styles = when (tab) {
            FontTab.FAVORITES -> state.fontFavorites.mapNotNull { FontStyles.byId(it) }
            FontTab.MAIN -> FontStyles.all
            FontTab.EXTRA -> FontStyles.extra
        }
        if (styles.isEmpty()) {
            Text("Нажмите ☆ у шрифта, чтобы добавить его в избранное.", color = ImeColors.textDim, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
            items(styles, key = { it.id }) { style ->
                val selected = state.fontStyle?.id == style.id
                val favorite = style.id in state.fontFavorites
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
                        .background(ImeColors.keyBg)
                        .border(if (selected) 2.dp else 1.dp, if (selected) ImeColors.cyan else ImeColors.cyanDim, RoundedCornerShape(10.dp))
                        .clickable { service.setFontStyle(style) }
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(style.label, color = ImeColors.textDim, fontSize = 12.sp, modifier = Modifier.weight(0.8f))
                    Text(style.sample, color = ImeColors.text, fontSize = 18.sp, modifier = Modifier.weight(1.4f))
                    Text(
                        if (favorite) "⭐" else "☆", color = ImeColors.cyan, fontSize = 20.sp,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { service.toggleFontFavorite(style.id) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(6.dp)) {
            SmallButton("Выключить шрифт и транслит", enabled = state.fontStyle != null || state.translit) { service.clearFontMode() }
        }
    }
}
