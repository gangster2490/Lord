package de.spardirekt.recipeveo.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.recipeveo.StudioViewModel
import de.spardirekt.recipeveo.domain.Recipe
import de.spardirekt.recipeveo.domain.RecipeKind
import de.spardirekt.recipeveo.domain.ShotBeat
import de.spardirekt.recipeveo.domain.VisualStyle
import de.spardirekt.recipeveo.domain.VoiceLang
import de.spardirekt.recipeveo.domain.label
import de.spardirekt.recipeveo.ui.components.ChoiceChip
import de.spardirekt.recipeveo.ui.components.EmptyHint
import de.spardirekt.recipeveo.ui.components.PrimaryButton
import de.spardirekt.recipeveo.ui.components.SectionLabel
import de.spardirekt.recipeveo.ui.components.StudioCard
import de.spardirekt.recipeveo.ui.components.StudioField

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(vm: StudioViewModel, onOpenResult: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val openedId by vm.openedId.collectAsStateWithLifecycle()
    val recipe = openedId?.let { state.recipe(it) }

    if (recipe == null) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
            EmptyHint("Нет открытого рецепта", "Вернитесь в студию и выберите карточку — или создайте новую.")
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Новый рецепт", onClick = vm::createAndOpen)
        }
        return
    }

    var title by remember(recipe.id) { mutableStateOf(recipe.title) }
    var subject by remember(recipe.id) { mutableStateOf(recipe.subject) }
    var setting by remember(recipe.id) { mutableStateOf(recipe.setting) }
    var wish by remember(recipe.id) { mutableStateOf(recipe.wish) }
    var onScreen by remember(recipe.id) { mutableStateOf(recipe.onScreenText) }
    var lock by remember(recipe.id) { mutableStateOf(recipe.lockNotes.joinToString("\n")) }
    var kind by remember(recipe.id) { mutableStateOf(recipe.kind) }
    var style by remember(recipe.id) { mutableStateOf(recipe.style) }
    var voice by remember(recipe.id) { mutableStateOf(recipe.voice) }
    var beats by remember(recipe.id) { mutableStateOf(recipe.beats) }

    fun snapshot(): Recipe = recipe.copy(
        title = title,
        subject = subject,
        setting = setting,
        wish = wish,
        onScreenText = onScreen,
        lockNotes = lock.lines().map { it.trim() }.filter { it.isNotEmpty() },
        kind = kind,
        style = style,
        voice = voice,
        beats = beats,
        updatedAt = vm.clock.nowMillis(),
    )

    LaunchedEffect(title, subject, setting, wish, onScreen, lock, kind, style, voice, beats) {
        vm.save(snapshot())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Рецепт", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(4.dp))
            Text("Четыре блока по 2 секунды. Veo получит готовый 8s промпт.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { StudioField(title, { title = it }, "Название") }
        item { StudioField(subject, { subject = it }, "Что в кадре") }
        item {
            SectionLabel("Категория")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecipeKind.entries.forEach { ChoiceChip(it.label(), kind == it) { kind = it } }
            }
        }
        item {
            SectionLabel("Стиль")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VisualStyle.entries.forEach { ChoiceChip(it.label(), style == it) { style = it } }
            }
        }
        item { StudioField(setting, { setting = it }, "Площадка / свет", singleLine = false, minLines = 2) }
        item { StudioField(lock, { lock = it }, "Product lock — по строке", singleLine = false, minLines = 3) }
        item {
            SectionLabel("Shot sequence")
            beats.forEachIndexed { index, beat ->
                StudioCard(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        "${beat.role.label()}  ·  ${beat.startSec.toInt()}.0–${beat.endSec.toInt()}.0s",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    StudioField(beat.action, { value ->
                        beats = beats.toMutableList().also { list ->
                            list[index] = beat.copy(action = value)
                        }
                    }, "Действие в кадре")
                }
            }
        }
        item {
            SectionLabel("Голос")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoiceLang.entries.forEach { ChoiceChip(it.label(), voice == it) { voice = it } }
            }
        }
        item { StudioField(onScreen, { onScreen = it }, "Текст на экране") }
        item { StudioField(wish, { wish = it }, "Пожелание", singleLine = false, minLines = 2) }
        item {
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Собрать промпт", onClick = {
                vm.saveAndCompile(snapshot())
                onOpenResult()
            }, enabled = title.isNotBlank() || subject.isNotBlank())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { vm.delete(recipe.id) }) {
                    Text("Удалить рецепт", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
