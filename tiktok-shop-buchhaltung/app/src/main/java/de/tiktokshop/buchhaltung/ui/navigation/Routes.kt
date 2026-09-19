package de.tiktokshop.buchhaltung.ui.navigation

object Routes {
    const val DASHBOARD = "dashboard"
    const val INCOME_CAPTURE = "income_capture"
    const val EXPENSE_CAPTURE = "expense_capture"
    const val REVIEW = "review"
    const val EXPORT = "export"
    const val BACKUP = "backup"
    const val IMPORT_ENTRY = "import_entry"
    const val IMPORT_EXCEL = "import_excel"
    const val IMPORT_HISTORY = "import_history"

    const val BUCHUNGEN_PATTERN = "buchungen/{filter}"
    fun buchungen(filter: String = "ALLE") = "buchungen/$filter"

    /** Nested Nav-Graph, damit Scan- und Review-Screen dieselbe ScanViewModel-Instanz teilen. */
    const val SCAN_GRAPH = "scan_graph"
    const val SCAN = "scan"
    const val SCAN_REVIEW = "scan_review"

    const val INCOME_DETAIL_PATTERN = "income_detail/{id}"
    fun incomeDetail(id: String) = "income_detail/$id"

    const val EXPENSE_DETAIL_PATTERN = "expense_detail/{id}"
    fun expenseDetail(id: String) = "expense_detail/$id"

    const val FROZEN_BALANCE_CAPTURE = "frozen_balance_capture"

    const val FROZEN_BALANCE_LIST_PATTERN = "frozen_balance_list/{status}"
    fun frozenBalanceList(status: String = "ALL") = "frozen_balance_list/$status"

    const val FROZEN_BALANCE_DETAIL_PATTERN = "frozen_balance_detail/{id}"
    fun frozenBalanceDetail(id: String) = "frozen_balance_detail/$id"
}
