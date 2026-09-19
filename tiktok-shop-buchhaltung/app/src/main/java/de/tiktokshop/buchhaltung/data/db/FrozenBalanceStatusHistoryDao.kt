package de.tiktokshop.buchhaltung.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceStatusHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface FrozenBalanceStatusHistoryDao {
    @Query("SELECT * FROM frozen_balance_status_history WHERE frozenBalanceEntryId = :frozenBalanceEntryId ORDER BY changedAt ASC")
    fun observeForEntry(frozenBalanceEntryId: String): Flow<List<FrozenBalanceStatusHistory>>

    @Query("SELECT * FROM frozen_balance_status_history")
    suspend fun getAll(): List<FrozenBalanceStatusHistory>

    @Insert
    suspend fun insert(history: FrozenBalanceStatusHistory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: FrozenBalanceStatusHistory)
}
