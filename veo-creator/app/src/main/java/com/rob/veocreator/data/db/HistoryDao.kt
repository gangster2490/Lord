package com.rob.veocreator.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entity: HistoryEntity): Long

    @Query("SELECT * FROM history ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Delete
    suspend fun delete(entity: HistoryEntity)

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int

    @Query("DELETE FROM history WHERE id IN (SELECT id FROM history ORDER BY createdAtMillis ASC LIMIT :n)")
    suspend fun trimOldest(n: Int)
}
