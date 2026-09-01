package de.spardirekt.agents.pro.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.spardirekt.agents.pro.model.GenerationStage
import de.spardirekt.agents.pro.ui.components.GradientButton
import de.spardirekt.agents.pro.ui.components.VppTags
import de.spardirekt.agents.pro.ui.theme.LocalVppType
import de.spardirekt.agents.pro.ui.theme.VppColors
import de.spardirekt.agents.pro.ui.theme.VppDimens

/**
 * The generate action pinned above the bottom navigation.
 *
 * It used to sit at the end of the scrolling form, below four setting cards, so
 * on a short screen the main action of the app was two swipes away and the live
 * progress was off-screen while the run was in flight.
 */
@Composable
fun CreateActionBar(
    isGenerating: Boolean,
    stage: GenerationStage,
    photoCount: Int,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val type = LocalVppType.current
    val enabled = CreateFormRules.canGenerate(photoCount, isGenerating)
    val hint = CreateFormRules.blockingHint(photoCount, isGenerating)
    val status = if (isGenerating) GenerationProgress.statusLine(stage) else ""

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        VppColors.backgroundLight.copy(alpha = 0f),
                        VppColors.backgroundLight.copy(alpha = 0.92f),
                        VppColors.backgroundLight
                    )
                )
            )
            .padding(horizontal = VppDimens.screenPadding)
            .padding(top = 14.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (status.isNotBlank()) {
            Text(
                status,
                style = type.secondary.copy(color = VppColors.textMutedDark),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(VppTags.GENERATE_STATUS)
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { generationFraction(stage) },
                color = VppColors.accentPurple,
                trackColor = VppColors.outlineSoft,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.height(12.dp))
        }
        GradientButton(
            text = if (isGenerating) "Генерация…" else "✦ Создать VEO Prompt",
            onClick = onGenerate,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(VppTags.GENERATE_BUTTON)
        )
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                hint,
                style = type.secondary.copy(color = VppColors.textMutedDark),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun generationFraction(stage: GenerationStage): Float {
    if (!GenerationProgress.isRunning(stage) && stage != GenerationStage.DONE) return 0f
    val completed = GenerationProgress.activeIndex(stage) + 1
    return completed.toFloat() / GenerationProgress.stepCount
}
