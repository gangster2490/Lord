package de.tiktokshop.buchhaltung.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Projektion für die Dublettenprüfung beim Excel-Import - siehe [IncomeDao.findExistingExternalKeys]. */
data class ExternalTransactionKey(val externalTransactionId: String, val externalEarningType: String?)

@Dao
interface IncomeDao {
    @Query("SELECT * FROM income_entries ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<IncomeEntry>>

    @Query("SELECT * FROM income_entries WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getInRange(from: LocalDate, to: LocalDate): List<IncomeEntry>

    @Query("SELECT * FROM income_entries WHERE id = :id")
    suspend fun getById(id: String): IncomeEntry?

    @Query("SELECT * FROM income_entries")
    suspend fun getAll(): List<IncomeEntry>

    /**
     * Für den Excel-Import (§8): liefert (Transaction ID, Earning-Type) bereits gespeicherter
     * Einnahmen, deren Transaction ID unter den übergebenen ist - Vorfilterung per ID, die
     * exakte Duplikatsprüfung (ID + Earning-Type gemeinsam, siehe [IncomeEntry]-Kommentar)
     * erfolgt danach in Kotlin.
     */
    @Query(
        "SELECT externalTransactionId, externalEarningType FROM income_entries " +
            "WHERE externalTransactionId IN (:externalTransactionIds)",
    )
    suspend fun findExistingExternalKeys(externalTransactionIds: List<String>): List<ExternalTransactionKey>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: IncomeEntry)

    @Update
    suspend fun update(entry: IncomeEntry)

    @Delete
    suspend fun delete(entry: IncomeEntry)
}
