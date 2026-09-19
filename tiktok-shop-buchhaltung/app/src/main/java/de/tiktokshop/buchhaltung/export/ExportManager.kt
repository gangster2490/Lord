package de.tiktokshop.buchhaltung.export

import android.content.Context
import androidx.core.content.FileProvider
import android.net.Uri
import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Schreibt CSV/ZIP-Exporte in den App-Cache und liefert teilbare content:// URIs. */
class ExportManager(private val context: Context) {

    private val exportsDir: File
        get() = File(context.externalCacheDir ?: context.cacheDir, "exports").apply { mkdirs() }

    fun exportCsv(incomes: List<IncomeEntry>, expenses: List<ExpenseEntry>, from: LocalDate, to: LocalDate): Uri {
        val csv = ExportFormatter.buildGeneralCsv(incomes, expenses, from, to)
        val file = File(exportsDir, "${ExportFormatter.exportFileBaseName("export", from, to)}.csv")
        file.writeText(csv, Charsets.UTF_8)
        return uriFor(file)
    }

    fun exportTikTokCsv(incomes: List<IncomeEntry>, from: LocalDate, to: LocalDate): Uri {
        val csv = ExportFormatter.buildTikTokCsv(incomes, from, to)
        val file = File(exportsDir, "${ExportFormatter.exportFileBaseName("tiktok", from, to)}.csv")
        file.writeText(csv, Charsets.UTF_8)
        return uriFor(file)
    }

    /** CSV + alle referenzierten Belege im Zeitraum als ZIP (PRODUCT_REQUIREMENTS.md 4.8). */
    fun exportZipWithReceipts(incomes: List<IncomeEntry>, expenses: List<ExpenseEntry>, from: LocalDate, to: LocalDate): Uri {
        val csv = ExportFormatter.buildGeneralCsv(incomes, expenses, from, to)
        val receiptPaths = (incomes.filter { it.date in from..to }.flatMap { it.receiptUris } +
            expenses.filter { it.date in from..to }.flatMap { it.receiptUris }).distinct()

        val zipFile = File(exportsDir, "${ExportFormatter.exportFileBaseName("export", from, to)}.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("export.csv"))
            zip.write(csv.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            receiptPaths.forEach { path ->
                val receiptFile = File(path)
                if (receiptFile.exists()) {
                    zip.putNextEntry(ZipEntry("belege/${receiptFile.name}"))
                    receiptFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return uriFor(zipFile)
    }

    private fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
