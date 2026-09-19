package de.tiktokshop.buchhaltung.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expense_entries ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<ExpenseEntry>>

    @Query("SELECT * FROM expense_entries WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getInRange(from: LocalDate, to: LocalDate): List<ExpenseEntry>

    @Query("SELECT * FROM expense_entries WHERE id = :id")
    suspend fun getById(id: String): ExpenseEntry?

    @Query("SELECT * FROM expense_entries")
    suspend fun getAll(): List<ExpenseEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ExpenseEntry)

    @Update
    suspend fun update(entry: ExpenseEntry)

    @Delete
    suspend fun delete(entry: ExpenseEntry)
}
