package de.tiktokshop.buchhaltung.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.tiktokshop.buchhaltung.data.model.ImportBatch
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportBatchDao {
    @Query("SELECT * FROM import_batches ORDER BY importDate DESC")
    fun observeAll(): Flow<List<ImportBatch>>

    @Query("SELECT * FROM import_batches WHERE sourceHash = :sourceHash LIMIT 1")
    suspend fun findBySourceHash(sourceHash: String): ImportBatch?

    @Insert
    suspend fun insert(batch: ImportBatch)
}
