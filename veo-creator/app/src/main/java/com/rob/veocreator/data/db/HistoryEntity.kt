package com.rob.veocreator.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val createdAtMillis: Long,
    val model: String,
    val aspectRatio: String,
    val durationSeconds: Int,
    val resolution: String,
    val mode: String,
    val filePath: String,
    val thumbnailPath: String?
)
