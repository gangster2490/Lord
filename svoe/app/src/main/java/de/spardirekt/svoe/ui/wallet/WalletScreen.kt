package de.spardirekt.svoe.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.svoe.SvoeViewModel
import de.spardirekt.svoe.domain.Copy
import de.spardirekt.svoe.domain.MoneyFormat
import de.spardirekt.svoe.domain.MoneyKind
import de.spardirekt.svoe.domain.MoneyTx
import de.spardirekt.svoe.domain.SpendCategory
import de.spardirekt.svoe.domain.WalletMath
import de.spardirekt.svoe.domain.epochDayToDate
import de.spardirekt.svoe.ui.components.ChoiceChip
import de.spardirekt.svoe.ui.components.EmptyState
import de.spardirekt.svoe.ui.components.PrimaryButton
import de.spardirekt.svoe.ui.components.SvoeCard
import de.spardirekt.svoe.ui.components.SvoeField
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(vm: SvoeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val today = vm.clock.today()
    val month = YearMonth.from(today)
    val summary = remember(state.txs, month) { WalletMath.monthSummary(state.txs, month) }
    val currency = state.prefs.currencyCode
    var adding by remember { mutableStateOf(false) }
    val monthTxs = state.txs.filter { YearMonth.from(epochDayToDate(it.epochDay)) == month }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { adding = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Добавить")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Деньги", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(4.dp))
                Text(Copy.monthTitle(today), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SummaryTile(Modifier.weight(1f), "Расходы", MoneyFormat.formatExpense(summary.expenseMinor, currency))
                    SummaryTile(Modifier.weight(1f), "Доходы", MoneyFormat.formatIncome(summary.incomeMinor, currency))
                }
            }
            item {
                SvoeCard {
                    Text("Итог месяца", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    val netLabel = if (summary.netMinor >= 0) {
                        MoneyFormat.formatIncome(summary.netMinor, currency)
                    } else {
                        MoneyFormat.formatExpense(-summary.netMinor, currency)
                    }
                    Text(netLabel, style = MaterialTheme.typography.headlineMedium)
                    if (summary.byCategory.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        val max = summary.byCategory.maxOf { it.amountMinor }.coerceAtLeast(1)
                        summary.byCategory.forEach { row ->
                            Text(
                                "${MoneyFormat.categoryLabel(row.category)}  ·  ${MoneyFormat.format(row.amountMinor, currency)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(row.amountMinor.toFloat() / max.toFloat())
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
            if (monthTxs.isEmpty()) {
                item { EmptyState("🪙", "Пока без движений", "Коснитесь плюса, чтобы записать расход или доход.") }
            } else {
                item { Text("Операции", style = MaterialTheme.typography.titleLarge) }
                items(monthTxs, key = { it.id }) { tx ->
                    TxRow(tx, currency, onDelete = { vm.deleteTx(tx.id) })
                }
            }
        }
    }
    if (adding) {
        TxEditorSheet(
            onDismiss = { adding = false },
            onSave = { kind, amount, category, note ->
                vm.addTx(kind, amount, category, note, today)
                adding = false
            },
        )
    }
}

@Composable
private fun SummaryTile(modifier: Modifier, label: String, value: String) {
    SvoeCard(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun TxRow(tx: MoneyTx, currency: String, onDelete: () -> Unit) {
    val amount = if (tx.kind == MoneyKind.INCOME) {
        MoneyFormat.formatIncome(tx.amountMinor, currency)
    } else {
        MoneyFormat.formatExpense(tx.amountMinor, currency)
    }
    SvoeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(amount, style = MaterialTheme.typography.titleMedium)
                val cat = if (tx.kind == MoneyKind.INCOME) "Доход" else MoneyFormat.categoryLabel(tx.category)
                val note = tx.note.ifBlank { null }
                Text(
                    listOfNotNull(cat, note, Copy.shortDate(epochDayToDate(tx.epochDay))).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TxEditorSheet(
    onDismiss: () -> Unit,
    onSave: (MoneyKind, Long, SpendCategory, String) -> Unit,
) {
    var kind by remember { mutableStateOf(MoneyKind.EXPENSE) }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var category by remember { mutableStateOf(SpendCategory.FOOD) }
    val parsed = WalletMath.parseAmountToMinor(amount)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Операция", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Расход", kind == MoneyKind.EXPENSE) { kind = MoneyKind.EXPENSE }
                ChoiceChip("Доход", kind == MoneyKind.INCOME) { kind = MoneyKind.INCOME }
            }
            Spacer(Modifier.height(12.dp))
            SvoeField(
                amount,
                { amount = it },
                "Сумма",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.height(10.dp))
            SvoeField(note, { note = it }, "Комментарий")
            if (kind == MoneyKind.EXPENSE) {
                Spacer(Modifier.height(14.dp))
                Text("Категория", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpendCategory.entries.forEach { cat ->
                        ChoiceChip(MoneyFormat.categoryLabel(cat), category == cat) { category = cat }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                "Записать",
                enabled = parsed != null,
                onClick = { parsed?.let { onSave(kind, it, category, note) } },
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}
