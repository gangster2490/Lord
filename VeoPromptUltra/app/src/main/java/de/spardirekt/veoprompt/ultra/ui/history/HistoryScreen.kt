package de.spardirekt.veoprompt.ultra.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.spardirekt.veoprompt.ultra.compliance.SevenDayPromotionalRiskAnalyzer
import de.spardirekt.veoprompt.ultra.data.db.ProjectEntity
import de.spardirekt.veoprompt.ultra.model.CreativeMode
import de.spardirekt.veoprompt.ultra.ui.components.PearlCard
import de.spardirekt.veoprompt.ultra.ui.theme.LocalBottomBarInset
import de.spardirekt.veoprompt.ultra.ui.theme.UltraColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenResult: (String) -> Unit,
    onOpenGeneration: (String) -> Unit,
    onOpenCreate: (String) -> Unit
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val risk by viewModel.sevenDayRisk.collectAsStateWithLifecycle()
    val bottom = LocalBottomBarInset.current
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(UltraColors.pearl)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottom + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            Text("История", fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = UltraColors.textOnLight)
            Text("Проекты сохраняются на устройстве", color = UltraColors.textMuted, fontSize = 13.sp)
        }
        item(key = "seven-day-risk") {
            SevenDayRiskCard(risk)
        }
        if (projects.isEmpty()) {
            item(key = "empty") {
                PearlCard {
                    Text("Пока пусто", fontWeight = FontWeight.Medium)
                    Text("Создайте первый VEO Prompt — он появится здесь.", color = UltraColors.textMuted, fontSize = 13.sp)
                }
            }
        }
        items(projects, key = { it.id }) { project ->
            HistoryCard(
                project = project,
                date = fmt.format(Date(project.updatedAt)),
                onOpen = {
                    when (val t = viewModel.openTarget(project)) {
                        is HistoryViewModel.OpenTarget.Result -> onOpenResult(t.id)
                        is HistoryViewModel.OpenTarget.Generation -> onOpenGeneration(t.id)
                        is HistoryViewModel.OpenTarget.Create -> onOpenCreate(t.id)
                    }
                },
                onDelete = { viewModel.delete(project.id) },
                onDuplicate = { viewModel.duplicate(project) { onOpenCreate(it) } },
                onNewVersion = { viewModel.newVersion(project) { onOpenCreate(it) } }
            )
        }
    }
}

@Composable
private fun SevenDayRiskCard(risk: SevenDayPromotionalRiskAnalyzer.Report) {
    val tone = when (risk.riskLevel) {
        "CRITICAL", "HIGH" -> UltraColors.danger
        "MEDIUM" -> UltraColors.warning
        else -> UltraColors.violet
    }
    PearlCard {
        Text("7-дневный риск промо-контента", fontWeight = FontWeight.SemiBold)
        Text(
            SevenDayPromotionalRiskAnalyzer.TITLE,
            color = UltraColors.violet,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text("Policy ${risk.policyVersion}", color = UltraColors.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Уровень: ${risk.riskLevel} · score ${risk.score}/100",
            color = tone,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
        if (risk.projectedRestrictionDays > 0) {
            Text(
                "Оценка ограничения shoppable: ${risk.projectedRestrictionDays} дней",
                color = tone,
                fontSize = 13.sp
            )
        }
        risk.items.forEach { row ->
            Text("• $row", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(risk.recommendation, color = UltraColors.textOnLight, fontSize = 13.sp)
    }
}

@Composable
private fun HistoryCard(
    project: ProjectEntity,
    date: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onNewVersion: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val status = when (project.status) {
        "Ready" -> "Ready"
        "Failed" -> "Failed"
        else -> "Draft"
    }
    val statusColor = when (status) {
        "Ready" -> UltraColors.success
        "Failed" -> UltraColors.danger
        else -> UltraColors.warning
    }
    PearlCard(contentPadding = PaddingValues(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = project.thumbnailUri.ifBlank { null },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(UltraColors.pearlDeep)
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(project.title.ifBlank { "Без названия" }, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(date, color = UltraColors.textMuted, fontSize = 12.sp)
                Text(
                    "${project.voiceLanguage} · ${CreativeMode.fromRaw(project.creativeMode).uiLabel()}",
                    color = UltraColors.textMuted,
                    fontSize = 12.sp
                )
                Text(status, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Меню")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete() })
                    DropdownMenuItem(text = { Text("Дублировать") }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Новая версия") }, onClick = { menu = false; onNewVersion() })
                }
            }
        }
    }
}
