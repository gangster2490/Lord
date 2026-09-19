package de.tiktokshop.buchhaltung.domain

import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceEntry
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceStatus

/**
 * Summen für die separaten Auszahlungsstatus-Einträge ([FrozenBalanceEntry]) im Dashboard-
 * Auszahlungsstatus-Block. WICHTIG: Diese Beträge sind bereits Teil der angezeigten Einnahmen
 * ([de.tiktokshop.buchhaltung.data.model.IncomeEntry]) - sie fließen NIE in Einnahmen, Ausgaben
 * oder das Vorläufige Ergebnis ein. [DashboardCalculator] nimmt bewusst keine
 * [FrozenBalanceEntry]-Liste entgegen und bleibt davon komplett unberührt - diese Klasse ist
 * die einzige Quelle für Eingefroren/Verfügbar/Ausgezahlt im Auszahlungsstatus-Block.
 */
data class FrozenBalanceSummary(
    val frozenCents: Cents,
    val availableCents: Cents,
    val paidOutCents: Cents,
)

object FrozenBalanceCalculator {

    /** REVERSED-Einträge (Rücknahme) zählen in keiner der drei Summen mit. */
    fun calculate(entries: List<FrozenBalanceEntry>): FrozenBalanceSummary {
        fun sumFor(status: FrozenBalanceStatus) = entries.filter { it.status == status }.sumOf { it.amountCents }
        return FrozenBalanceSummary(
            frozenCents = sumFor(FrozenBalanceStatus.FROZEN),
            availableCents = sumFor(FrozenBalanceStatus.AVAILABLE),
            paidOutCents = sumFor(FrozenBalanceStatus.PAID_OUT),
        )
    }

    /**
     * "Verfügbarer Überschuss": earnedTotal - frozenAmount - expensesTotal. Eigener, zusätzlicher
     * Kennwert neben dem "Vorläufigen Ergebnis" (earnedTotal - expensesTotal) - zeigt, was nach
     * Abzug sowohl der Ausgaben als auch des aktuell eingefrorenen Betrags tatsächlich verfügbar
     * wäre. Ersetzt das Vorläufige Ergebnis nicht, ist nur ein zusätzlicher Blick auf dieselben
     * Zahlen.
     */
    fun calculateAvailableSurplusCents(earnedTotalCents: Cents, frozenCents: Cents, expensesCents: Cents): Cents =
        earnedTotalCents - frozenCents - expensesCents
}
