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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: IncomeEntry)

    @Update
    suspend fun update(entry: IncomeEntry)

    @Delete
    suspend fun delete(entry: IncomeEntry)
}
