package de.spardirekt.veoprompt.ultra.ui.generation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.veoprompt.ultra.VeoPromptUltraApp
import de.spardirekt.veoprompt.ultra.model.GenerationStage
import de.spardirekt.veoprompt.ultra.model.ProjectStatus
import de.spardirekt.veoprompt.ultra.ui.components.GradientButton
import de.spardirekt.veoprompt.ultra.ui.components.NavyCard
import de.spardirekt.veoprompt.ultra.ui.components.SecondaryButton
import de.spardirekt.veoprompt.ultra.ui.create.CreateViewModel
import de.spardirekt.veoprompt.ultra.ui.create.GenerationProgress
import de.spardirekt.veoprompt.ultra.ui.theme.UltraColors

@Composable
fun GenerationScreen(
    projectId: String,
    createViewModel: CreateViewModel,
    onOpenResult: (String) -> Unit,
    onBack: () -> Unit
) {
    val state by createViewModel.state.collectAsStateWithLifecycle()
    val manager = VeoPromptUltraApp.instance.generationManager
    val stage by manager.stage.collectAsStateWithLifecycle()
    val running by manager.isRunning.collectAsStateWithLifecycle()
    var showDetail by remember { mutableStateOf(false) }
    var openedResult by remember(projectId) { mutableStateOf(false) }

    LaunchedEffect(projectId) {
        createViewModel.openProject(projectId)
    }
    fun openOnce(id: String) {
        if (openedResult || id.isBlank()) return
        openedResult = true
        createViewModel.consumeNavigation()
        onOpenResult(id)
    }
    LaunchedEffect(state.navigateToResultId) {
        val id = state.navigateToResultId ?: return@LaunchedEffect
        openOnce(id)
    }
    LaunchedEffect(state.project?.status, state.project?.id) {
        val p = state.project ?: return@LaunchedEffect
        if (p.id == projectId && p.status == ProjectStatus.Ready.name && p.veoPrompt.isNotBlank()) {
            openOnce(p.id)
        }
    }

    val failed = stage == GenerationStage.FAILED || state.project?.status == ProjectStatus.Failed.name
    val active = GenerationProgress.activeIndex(stage.name)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UltraColors.pearl)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Генерация", fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = UltraColors.textOnLight)
        Text("VEO 3.1 · точность важнее эффектов", color = UltraColors.textMuted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        NavyCard {
            GenerationProgress.labels.forEachIndexed { index, label ->
                val done = index < active || (stage == GenerationStage.DONE && index <= active)
                val current = index == active && running && !failed
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    failed && index == active -> UltraColors.danger
                                    done || current -> UltraColors.violet
                                    else -> Color.White.copy(alpha = 0.12f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (current) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text(
                                if (done) "✓" else "${index + 1}",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(
                        label,
                        color = if (done || current) UltraColors.textOnNavy else UltraColors.textOnNavyMuted,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (failed) {
            NavyCard {
                Text("Не удалось завершить генерацию.", color = UltraColors.textOnNavy, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Фото и выполненные этапы сохранены.", color = UltraColors.textOnNavyMuted, fontSize = 14.sp)
                if (showDetail && state.errorDetail.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(state.errorDetail, color = UltraColors.textOnNavyMuted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            GradientButton("Продолжить", onClick = { createViewModel.continueGeneration() })
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Подробнее", onClick = { showDetail = !showDetail })
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Назад", onClick = onBack)
        }
    }
}
