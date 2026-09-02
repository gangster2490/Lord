package de.spardirekt.recipeveo.ui.styles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.recipeveo.StudioViewModel
import de.spardirekt.recipeveo.domain.Copy
import de.spardirekt.recipeveo.domain.ShotStyle
import de.spardirekt.recipeveo.domain.label
import de.spardirekt.recipeveo.ui.components.MetaRow
import de.spardirekt.recipeveo.ui.components.StudioCard

@Composable
fun StylesScreen(
    vm: StudioViewModel,
    onApply: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val current = state.active()?.style

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Стили", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Готовые 8-секундные shot-рецепты. Нажмите — стиль попадёт в текущий проект.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(ShotStyle.entries, key = { it.name }) { style ->
            StudioCard(onClick = {
                vm.applyStyle(style)
                onApply()
            }) {
                MetaRow(if (current == style) "Выбран" else "8.0s", "4 кадра")
                Spacer(Modifier.height(8.dp))
                Text(style.label(), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(Copy.styleBlurb(style), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
