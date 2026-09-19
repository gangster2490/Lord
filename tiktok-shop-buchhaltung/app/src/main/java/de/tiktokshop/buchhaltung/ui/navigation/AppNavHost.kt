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
import de.tiktokshop.buchhaltung.ui.backup.BackupScreen
import de.tiktokshop.buchhaltung.ui.common.rememberApp
import de.tiktokshop.buchhaltung.ui.dashboard.DashboardScreen
import de.tiktokshop.buchhaltung.ui.detail.ExpenseDetailScreen
import de.tiktokshop.buchhaltung.ui.detail.IncomeDetailScreen
import de.tiktokshop.buchhaltung.ui.expense.ExpenseCaptureScreen
import de.tiktokshop.buchhaltung.ui.export.ExportScreen
import de.tiktokshop.buchhaltung.ui.importer.ImportExcelScreen
import de.tiktokshop.buchhaltung.ui.importer.ImportHistoryScreen
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
                onScan = { navController.navigate(Routes.SCAN_GRAPH) },
                onImportExcel = { navController.navigate(Routes.IMPORT_EXCEL) },
                onImportHistory = { navController.navigate(Routes.IMPORT_HISTORY) },
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
            ImportExcelScreen(
                onDone = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Routes.IMPORT_HISTORY) { ImportHistoryScreen() }

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
