package de.spardirekt.veoprompt.ultra.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(UltraColors.pearl)
            .statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header-$projectId") {
            Text("Результат", fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = UltraColors.textOnLight)
            Text("Ready for Gemini", color = UltraColors.violet, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text("VEO 3.1", color = UltraColors.textMuted, fontSize = 12.sp)
        }
        item(key = "prompt-$projectId") {
            NavyCard {
                Text("VEO PROMPT", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
}
