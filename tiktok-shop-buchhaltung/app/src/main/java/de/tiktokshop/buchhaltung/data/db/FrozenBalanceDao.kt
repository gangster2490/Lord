package de.tiktokshop.buchhaltung.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface FrozenBalanceDao {
    @Query("SELECT * FROM frozen_balance_entries ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<FrozenBalanceEntry>>

    @Query("SELECT * FROM frozen_balance_entries WHERE id = :id")
    suspend fun getById(id: String): FrozenBalanceEntry?

    @Query("SELECT * FROM frozen_balance_entries")
    suspend fun getAll(): List<FrozenBalanceEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: FrozenBalanceEntry)

    @Update
    suspend fun update(entry: FrozenBalanceEntry)

    @Delete
    suspend fun delete(entry: FrozenBalanceEntry)
}
