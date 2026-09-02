package de.spardirekt.svoe.domain

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object MoneyFormat {
    fun format(amountMinor: Long, currencyCode: String, withSign: Boolean = false): String {
        val amount = amountMinor / 100.0
        val signed = if (withSign && amountMinor > 0) amount else if (withSign) amount else amount
        val formatted = formatter(currencyCode).format(kotlin.math.abs(signed))
        return when {
            withSign && amountMinor > 0 -> "+$formatted"
            withSign && amountMinor < 0 -> "−$formatted"
            else -> formatted
        }
    }

    fun formatExpense(amountMinor: Long, currencyCode: String): String =
        "−${formatter(currencyCode).format(amountMinor / 100.0)}"

    fun formatIncome(amountMinor: Long, currencyCode: String): String =
        "+${formatter(currencyCode).format(amountMinor / 100.0)}"

    fun categoryLabel(category: SpendCategory): String = when (category) {
        SpendCategory.FOOD -> "Еда"
        SpendCategory.CAFE -> "Кафе"
        SpendCategory.TRANSPORT -> "Транспорт"
        SpendCategory.HOME -> "Дом"
        SpendCategory.HEALTH -> "Здоровье"
        SpendCategory.SHOPPING -> "Покупки"
        SpendCategory.SUBSCRIPTIONS -> "Подписки"
        SpendCategory.OTHER -> "Другое"
    }

    fun currencyLabel(code: String): String = when (code) {
        "RUB" -> "₽ Рубль"
        "USD" -> "$ Доллар"
        else -> "€ Евро"
    }

    private fun formatter(currencyCode: String): NumberFormat {
        val locale = when (currencyCode) {
            "RUB" -> Locale("ru", "RU")
            "USD" -> Locale.US
            else -> Locale.GERMANY
        }
        return NumberFormat.getCurrencyInstance(locale).apply {
            currency = runCatching { Currency.getInstance(currencyCode) }.getOrElse {
                Currency.getInstance("EUR")
            }
            maximumFractionDigits = 2
            minimumFractionDigits = if (currencyCode == "RUB") 0 else 2
        }
    }
}
