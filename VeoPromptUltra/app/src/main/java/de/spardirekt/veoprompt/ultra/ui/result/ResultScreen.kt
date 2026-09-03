package de.spardirekt.veoprompt.ultra.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import de.spardirekt.veoprompt.ultra.ui.components.GradientButton
import de.spardirekt.veoprompt.ultra.ui.components.NavyCard
import de.spardirekt.veoprompt.ultra.ui.components.PearlCard
import de.spardirekt.veoprompt.ultra.ui.components.SecondaryButton
import de.spardirekt.veoprompt.ultra.ui.theme.UltraColors

@Composable
fun ResultScreen(
    projectId: String,
    viewModel: ResultViewModel,
    onBackToCreate: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(projectId) {
        viewModel.load(projectId)
    }

    LaunchedEffect(state.copyNotice) {
        if (state.copyNotice == null) return@LaunchedEffect
        delay(2200)
        viewModel.consumeCopyNotice()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UltraColors.pearl)
    ) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header-$projectId") {
            Text("Результат", fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = UltraColors.textOnLight)
            Text(
                when {
                    state.geminiVerdict.equals("BLOCKED", true) -> "Not ready for Gemini"
                    state.geminiVerdict.equals("SANITIZED", true) -> "Sanitized for Gemini"
                    else -> "Ready for Gemini"
                },
                color = if (state.geminiSubmissionSafe) UltraColors.violet else UltraColors.danger,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Text("VEO 3.1", color = UltraColors.textMuted, fontSize = 12.sp)
        }
        item(key = "prompt-$projectId") {
            NavyCard {
                Text("VEO PROMPT · Gemini copy", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (state.expanded) state.veoPrompt else state.preview(),
                    color = UltraColors.textOnNavy,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = if (state.expanded) "Свернуть" else "Показать полностью",
                    onClick = viewModel::toggleExpanded
                )
            }
        }
        item(key = "copy-$projectId") {
            GradientButton("Копировать VEO Prompt", onClick = viewModel::copyVeoPrompt)
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Поделиться", onClick = viewModel::share)
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Копировать пакет", onClick = viewModel::copyPackage)
        }
        item(key = "vo-$projectId") {
            PearlCard {
                Text("Озвучка", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(state.voiceover.ifBlank { "—" }, color = UltraColors.textOnLight)
            }
        }
        item(key = "title-$projectId") {
            PearlCard {
                Text("Название", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(state.title.ifBlank { "—" })
            }
        }
        item(key = "tags-$projectId") {
            PearlCard {
                Text("5 Хэштегов", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(state.hashtags.joinToString("  "))
            }
        }
        item(key = "audit-$projectId") {
            PearlCard {
                Text("Safety Audit", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "TikTok Shop Content Quality & Compliance",
                    color = UltraColors.violet,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text("Риск: ${state.safetyRisk}", color = UltraColors.textMuted, fontSize = 13.sp)
                if (state.safetyPolicyVersion.isNotBlank()) {
                    Text("Policy ${state.safetyPolicyVersion}", color = UltraColors.textMuted, fontSize = 12.sp)
                }
                state.safetyItems.forEach { item ->
                    Text("• $item", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
                if (state.safetyItems.isEmpty()) {
                    Text("Замечаний нет", color = UltraColors.textMuted, fontSize = 13.sp)
                }
            }
        }
        item(key = "aigc-$projectId") {
            PearlCard {
                Text("AIGC Compliance", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "TikTok Shop AIGC Hard Rules",
                    color = UltraColors.violet,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                if (state.aigcPolicyVersion.isNotBlank()) {
                    Text("Policy ${state.aigcPolicyVersion}", color = UltraColors.textMuted, fontSize = 12.sp)
                }
                val verdict = state.aigcVerdict.ifBlank { "—" }
                Text(
                    if (state.aigcShopPublishSafe) "Вердикт: $verdict" else "Вердикт: $verdict · не публиковать",
                    color = if (state.aigcShopPublishSafe) UltraColors.textOnLight else UltraColors.danger,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 6.dp)
                )
                state.aigcChecklist.forEach { row ->
                    Text("• $row", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
                if (state.aigcPublishSteps.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Публикация", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    state.aigcPublishSteps.forEachIndexed { index, step ->
                        Text("${index + 1}. $step", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Копировать AIGC чеклист", onClick = viewModel::copyAigcChecklist)
            }
        }
        item(key = "gemini-$projectId") {
            PearlCard {
                Text("Gemini / VEO Sanitizer", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Google Gemini / Veo submission safety",
                    color = UltraColors.violet,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                if (state.geminiPolicyVersion.isNotBlank()) {
                    Text("Policy ${state.geminiPolicyVersion}", color = UltraColors.textMuted, fontSize = 12.sp)
                }
                val verdict = state.geminiVerdict.ifBlank { "—" }
                Text(
                    if (state.geminiSubmissionSafe) "Вердикт: $verdict" else "Вердикт: $verdict · не вставлять в Gemini",
                    color = if (state.geminiSubmissionSafe) UltraColors.textOnLight else UltraColors.danger,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 6.dp)
                )
                state.geminiChecklist.forEach { row ->
                    Text("• $row", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
                if (state.geminiPublishSteps.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Вставка в Gemini / VEO", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    state.geminiPublishSteps.forEachIndexed { index, step ->
                        Text("${index + 1}. $step", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Копировать Gemini чеклист", onClick = viewModel::copyGeminiChecklist)
            }
        }
        item(key = "summary-$projectId") {
            PearlCard {
                Text("Project Summary", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Язык: ${state.language}", fontSize = 13.sp)
                Text("Creative: ${state.creativeMode}", fontSize = 13.sp)
                Text("Mode: ${state.appMode}", fontSize = 13.sp)
                if (state.productIdentity.isNotBlank()) {
                    Text(state.productIdentity, fontSize = 13.sp, color = UltraColors.textMuted)
                }
            }
        }
        if (state.appMode == "Advanced" && state.visualSignature.isNotEmpty()) {
            item(key = "signature-$projectId") {
                PearlCard {
                    Text("Visual Signature", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    state.visualSignature.forEach { detail ->
                        Text("• $detail", fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        item(key = "back-$projectId") {
            SecondaryButton("Новый проект", onClick = onBackToCreate)
        }
    }
        state.copyNotice?.let { notice ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(UltraColors.midnight.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(notice, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
