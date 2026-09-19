package de.tiktokshop.buchhaltung.ui.navigation

object Routes {
    const val DASHBOARD = "dashboard"
    const val INCOME_CAPTURE = "income_capture"
    const val EXPENSE_CAPTURE = "expense_capture"
    const val REVIEW = "review"
    const val EXPORT = "export"
    const val BACKUP = "backup"

    const val INCOME_DETAIL_PATTERN = "income_detail/{id}"
    fun incomeDetail(id: String) = "income_detail/$id"

    const val EXPENSE_DETAIL_PATTERN = "expense_detail/{id}"
    fun expenseDetail(id: String) = "expense_detail/$id"
}
