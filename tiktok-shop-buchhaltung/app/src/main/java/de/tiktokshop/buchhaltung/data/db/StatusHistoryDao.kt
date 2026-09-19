package de.tiktokshop.buchhaltung.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.tiktokshop.buchhaltung.data.model.StatusHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface StatusHistoryDao {
    @Query("SELECT * FROM status_history WHERE incomeEntryId = :incomeEntryId ORDER BY changedAt ASC")
    fun observeForIncome(incomeEntryId: String): Flow<List<StatusHistory>>

    @Query("SELECT * FROM status_history")
    suspend fun getAll(): List<StatusHistory>

    @Insert
    suspend fun insert(history: StatusHistory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: StatusHistory)
}
