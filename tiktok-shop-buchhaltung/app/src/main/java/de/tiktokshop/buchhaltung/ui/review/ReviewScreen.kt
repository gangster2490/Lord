package de.tiktokshop.buchhaltung.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.tiktokshop.buchhaltung.ui.common.asEuro
import de.tiktokshop.buchhaltung.ui.common.rememberApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(onOpenIncome: (String) -> Unit, onOpenExpense: (String) -> Unit) {
    val app = rememberApp()
    val viewModel: ReviewViewModel = viewModel(
        factory = viewModelFactory { initializer { ReviewViewModel(app.repository) } },
    )
    val items by viewModel.items.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Belege prüfen") }) }) { padding ->
        if (items.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Keine offenen Warnungen.")
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxSize().padding(0.dp),
                    onClick = {
                        when (item) {
                            is ReviewItem.Income -> onOpenIncome(item.id)
                            is ReviewItem.Expense -> onOpenExpense(item.id)
                        }
                    },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        when (item) {
                            is ReviewItem.Income -> Text("${item.entry.date} · ${item.entry.platform} · ${item.entry.amountCents.asEuro()}")
                            is ReviewItem.Expense -> Text("${item.entry.date} · ${item.entry.merchant ?: "?"} · ${item.entry.grossAmountCents.asEuro()}")
                        }
                        item.warnings.forEach { warning -> Text("⚠ $warning") }
                    }
                }
            }
        }
    }
}
