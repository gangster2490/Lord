package de.tiktokshop.buchhaltung.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.tiktokshop.buchhaltung.ui.backup.BackupScreen
import de.tiktokshop.buchhaltung.ui.dashboard.DashboardScreen
import de.tiktokshop.buchhaltung.ui.detail.ExpenseDetailScreen
import de.tiktokshop.buchhaltung.ui.detail.IncomeDetailScreen
import de.tiktokshop.buchhaltung.ui.expense.ExpenseCaptureScreen
import de.tiktokshop.buchhaltung.ui.export.ExportScreen
import de.tiktokshop.buchhaltung.ui.income.IncomeCaptureScreen
import de.tiktokshop.buchhaltung.ui.review.ReviewScreen

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
    }
}
