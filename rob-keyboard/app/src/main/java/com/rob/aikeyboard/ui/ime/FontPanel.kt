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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rob.aikeyboard.ime.Panel
import com.rob.aikeyboard.ime.RobKeyboardService
import com.rob.aikeyboard.text.FontStyles

/** Choose a decorative Unicode font for TikTok names, bios and comments. */
@Composable
fun FontPanel(service: RobKeyboardService, height: Dp) {
    val state = service.state
    Column(Modifier.fillMaxWidth().height(height).background(ImeColors.board)) {
        PanelHeader("Шрифт для TikTok", onClose = { service.togglePanel(Panel.FONT) })
        Text(
            "Буквы набираются выбранным шрифтом. Латиница и цифры; русские буквы не меняются (кроме зачёркнутого и подчёркнутого). Подсказки в этом режиме выключены.",
            color = ImeColors.textDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
            items(FontStyles.all, key = { it.id }) { style ->
                val selected = state.fontStyle?.id == style.id
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
                        .background(ImeColors.keyBg)
                        .border(if (selected) 2.dp else 1.dp, if (selected) ImeColors.cyan else ImeColors.cyanDim, RoundedCornerShape(10.dp))
                        .clickable { service.setFontStyle(style) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(style.label, color = ImeColors.textDim, fontSize = 12.sp, modifier = Modifier.weight(0.8f))
                    Text(style.sample, color = ImeColors.text, fontSize = 18.sp, modifier = Modifier.weight(1.4f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(6.dp)) {
            SmallButton("Выключить шрифт", enabled = state.fontStyle != null) { service.setFontStyle(null) }
        }
    }
}
