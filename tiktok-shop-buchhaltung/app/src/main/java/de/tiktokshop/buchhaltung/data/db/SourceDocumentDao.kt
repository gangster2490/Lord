package de.tiktokshop.buchhaltung.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.tiktokshop.buchhaltung.data.model.SourceDocument

@Dao
interface SourceDocumentDao {
    @Query("SELECT * FROM source_documents WHERE hash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): SourceDocument?

    @Query("SELECT * FROM source_documents ORDER BY importDate DESC")
    suspend fun getAll(): List<SourceDocument>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: SourceDocument)
}
