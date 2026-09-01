package de.spardirekt.agents.pro.ui.create

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.dp
import de.spardirekt.agents.pro.model.GenerationStage
import de.spardirekt.agents.pro.ui.components.GradientPillButton
import de.spardirekt.agents.pro.ui.components.NavyCard
import de.spardirekt.agents.pro.ui.components.OutlinedActionButton
import de.spardirekt.agents.pro.ui.components.ProgressRail
import de.spardirekt.agents.pro.ui.theme.LocalVppType
import de.spardirekt.agents.pro.ui.theme.VppColors
import de.spardirekt.agents.pro.ui.theme.VppShapes

@Composable
fun GenerationProgressCard(stage: GenerationStage) {
    NavyCard {
        Text(
            "Ход генерации",
            style = LocalVppType.current.cardTitle.copy(color = VppColors.textLight)
        )
        Spacer(Modifier.height(16.dp))
        ProgressRail(
            stages = GenerationProgress.labels,
            activeIndex = GenerationProgress.activeIndex(stage),
            completedThrough = GenerationProgress.completedThrough(stage)
        )
    }
}

@Composable
fun GenerationErrorCard(
    message: String,
    detail: String,
    showDetail: Boolean,
    onContinue: () -> Unit,
    onDetails: () -> Unit
) {
    val type = LocalVppType.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VppShapes.cardShape)
            .background(VppColors.errorCard)
            .padding(22.dp)
    ) {
        Text("Не удалось создать промпт.", style = type.cardTitle.copy(color = VppColors.textLight))
        Spacer(Modifier.height(6.dp))
        Text(
            "Фото и уже выполненные этапы сохранены.",
            style = type.secondary.copy(color = VppColors.textMuted)
        )
        Spacer(Modifier.height(8.dp))
        Text(message, style = type.body.copy(color = VppColors.error))
        AnimatedVisibility(showDetail && detail.isNotBlank()) {
            Text(
                detail,
                style = type.secondary.copy(color = VppColors.textMuted),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientPillButton(
                text = "Продолжить",
                onClick = onContinue,
                modifier = Modifier.weight(1f)
            )
            OutlinedActionButton(
                text = if (showDetail) "Скрыть детали" else "Подробнее",
                onClick = onDetails,
                height = 44.dp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
