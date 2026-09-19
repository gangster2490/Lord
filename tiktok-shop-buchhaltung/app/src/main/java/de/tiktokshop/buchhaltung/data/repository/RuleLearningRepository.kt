package de.tiktokshop.buchhaltung.data.repository

import de.tiktokshop.buchhaltung.data.db.MerchantRuleDao
import de.tiktokshop.buchhaltung.data.model.EntryType
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import de.tiktokshop.buchhaltung.data.model.MerchantRule

/**
 * Merkt sich vom Nutzer bestätigte Händler-Zuordnungen ("ChatGPT Plus -> AI-Dienste -> 100%
 * geschäftlich") und schlägt sie beim nächsten Scan automatisch vor. Rein lokal (Room),
 * keine Cloud-Synchronisation.
 */
class RuleLearningRepository(private val dao: MerchantRuleDao) {

    suspend fun suggestFor(merchant: String?, entryType: EntryType): MerchantRule? {
        if (merchant.isNullOrBlank()) return null
        return dao.find(normalize(merchant), entryType.name)
    }

    suspend fun learnExpense(merchant: String?, category: ExpenseCategory?, businessUsePercent: Int) {
        if (merchant.isNullOrBlank() || category == null) return
        dao.upsert(
            MerchantRule(
                merchantKey = normalize(merchant),
                entryTypeName = EntryType.EXPENSE.name,
                categoryName = category.name,
                incomeType = null,
                businessUsePercent = businessUsePercent,
            ),
        )
    }

    suspend fun learnIncome(merchant: String?, incomeType: String?, businessUsePercent: Int) {
        if (merchant.isNullOrBlank() || incomeType.isNullOrBlank()) return
        dao.upsert(
            MerchantRule(
                merchantKey = normalize(merchant),
                entryTypeName = EntryType.INCOME.name,
                categoryName = null,
                incomeType = incomeType,
                businessUsePercent = businessUsePercent,
            ),
        )
    }

    private fun normalize(merchant: String) = merchant.trim().lowercase()
}
