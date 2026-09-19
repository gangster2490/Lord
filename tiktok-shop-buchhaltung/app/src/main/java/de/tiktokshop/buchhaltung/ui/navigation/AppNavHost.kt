package de.tiktokshop.buchhaltung.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceStatus
import de.tiktokshop.buchhaltung.ui.backup.BackupScreen
import de.tiktokshop.buchhaltung.ui.buchungen.BuchungFilter
import de.tiktokshop.buchhaltung.ui.buchungen.BuchungenScreen
import de.tiktokshop.buchhaltung.ui.common.rememberApp
import de.tiktokshop.buchhaltung.ui.dashboard.DashboardScreen
import de.tiktokshop.buchhaltung.ui.detail.ExpenseDetailScreen
import de.tiktokshop.buchhaltung.ui.detail.IncomeDetailScreen
import de.tiktokshop.buchhaltung.ui.expense.ExpenseCaptureScreen
import de.tiktokshop.buchhaltung.ui.export.ExportScreen
import de.tiktokshop.buchhaltung.ui.frozen.FrozenBalanceCaptureScreen
import de.tiktokshop.buchhaltung.ui.frozen.FrozenBalanceDetailScreen
import de.tiktokshop.buchhaltung.ui.frozen.FrozenBalanceListScreen
import de.tiktokshop.buchhaltung.ui.importer.ImportEntryScreen
import de.tiktokshop.buchhaltung.ui.importer.ImportHistoryScreen
import de.tiktokshop.buchhaltung.ui.importer.UniversalImportScreen
import de.tiktokshop.buchhaltung.ui.income.IncomeCaptureScreen
import de.tiktokshop.buchhaltung.ui.review.ReviewScreen
import de.tiktokshop.buchhaltung.ui.scan.ScanScreen
import de.tiktokshop.buchhaltung.ui.scan.ScanViewModel
import de.tiktokshop.buchhaltung.ui.scan.TransactionReviewScreen

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onEinnahmeErfassen = { navController.navigate(Routes.INCOME_CAPTURE) },
                onAusgabeErfassen = { navController.navigate(Routes.EXPENSE_CAPTURE) },
                onBelegePruefen = { navController.navigate(Routes.REVIEW) },
                onExport = { navController.navigate(Routes.EXPORT) },
                onBackup = { navController.navigate(Routes.BACKUP) },
                onImportEntry = { navController.navigate(Routes.IMPORT_ENTRY) },
                onImportHistory = { navController.navigate(Routes.IMPORT_HISTORY) },
                onBuchungen = { filter -> navController.navigate(Routes.buchungen(filter.name)) },
                onFrozenBalanceErfassen = { navController.navigate(Routes.FROZEN_BALANCE_CAPTURE) },
                onFrozenBalanceList = { status -> navController.navigate(Routes.frozenBalanceList(status.name)) },
            )
        }
        composable(Routes.IMPORT_ENTRY) {
            ImportEntryScreen(
                onExcelCsv = { navController.navigate(Routes.IMPORT_EXCEL) },
                onFotoScreenshot = { navController.navigate(Routes.SCAN_GRAPH) },
                onKamera = { navController.navigate(Routes.SCAN_GRAPH) },
                onManuellEinnahme = { navController.navigate(Routes.INCOME_CAPTURE) },
                onManuellAusgabe = { navController.navigate(Routes.EXPENSE_CAPTURE) },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Routes.INCOME_CAPTURE) {
            IncomeCaptureScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Routes.EXPENSE_CAPTURE) {
            ExpenseCaptureScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Routes.REVIEW) {
            ReviewScreen(
                onOpenIncome = { id -> navController.navigate(Routes.incomeDetail(id)) },
                onOpenExpense = { id -> navController.navigate(Routes.expenseDetail(id)) },
            )
        }
        composable(Routes.INCOME_DETAIL_PATTERN) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id").orEmpty()
            IncomeDetailScreen(
                incomeId = id,
                onDeleted = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.EXPENSE_DETAIL_PATTERN) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id").orEmpty()
            ExpenseDetailScreen(
                expenseId = id,
                onDeleted = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.EXPORT) { ExportScreen() }
        composable(Routes.BACKUP) { BackupScreen() }
        composable(Routes.IMPORT_EXCEL) {
            UniversalImportScreen(
                onDone = { navController.popBackStack(Routes.DASHBOARD, inclusive = false) },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Routes.IMPORT_HISTORY) { ImportHistoryScreen() }

        composable(Routes.FROZEN_BALANCE_CAPTURE) {
            FrozenBalanceCaptureScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Routes.FROZEN_BALANCE_LIST_PATTERN) { backStackEntry ->
            val statusName = backStackEntry.arguments?.getString("status").orEmpty()
            val status = runCatching { FrozenBalanceStatus.valueOf(statusName) }.getOrNull()
            FrozenBalanceListScreen(
                initialStatus = status,
                onOpenEntry = { id -> navController.navigate(Routes.frozenBalanceDetail(id)) },
                onCapture = { navController.navigate(Routes.FROZEN_BALANCE_CAPTURE) },
            )
        }
        composable(Routes.FROZEN_BALANCE_DETAIL_PATTERN) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id").orEmpty()
            FrozenBalanceDetailScreen(
                entryId = id,
                onDeleted = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.BUCHUNGEN_PATTERN) { backStackEntry ->
            val filterName = backStackEntry.arguments?.getString("filter").orEmpty()
            val filter = runCatching { BuchungFilter.valueOf(filterName) }.getOrDefault(BuchungFilter.ALLE)
            BuchungenScreen(
                initialFilter = filter,
                onOpenIncome = { id -> navController.navigate(Routes.incomeDetail(id)) },
                onOpenExpense = { id -> navController.navigate(Routes.expenseDetail(id)) },
            )
        }

        navigation(startDestination = Routes.SCAN, route = Routes.SCAN_GRAPH) {
            composable(Routes.SCAN) { backStackEntry ->
                val app = rememberApp()
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(Routes.SCAN_GRAPH) }
                val viewModel: ScanViewModel = viewModel(
                    viewModelStoreOwner = parentEntry,
                    factory = viewModelFactory {
                        initializer {
                            ScanViewModel(app.multiTransactionExtractor, app.repository, app.receiptStorage, app.ruleLearningRepository)
                        }
                    },
                )
                ScanScreen(
                    viewModel = viewModel,
                    onRecognized = { navController.navigate(Routes.SCAN_REVIEW) },
                    onCancel = { navController.popBackStack(Routes.DASHBOARD, inclusive = false) },
                )
            }
            composable(Routes.SCAN_REVIEW) { backStackEntry ->
                val app = rememberApp()
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(Routes.SCAN_GRAPH) }
                val viewModel: ScanViewModel = viewModel(
                    viewModelStoreOwner = parentEntry,
                    factory = viewModelFactory {
                        initializer {
                            ScanViewModel(app.multiTransactionExtractor, app.repository, app.receiptStorage, app.ruleLearningRepository)
                        }
                    },
                )
                TransactionReviewScreen(
                    viewModel = viewModel,
                    onSaved = { navController.popBackStack(Routes.DASHBOARD, inclusive = false) },
                    onCancel = { navController.popBackStack(Routes.DASHBOARD, inclusive = false) },
                )
            }
        }
    }
}
