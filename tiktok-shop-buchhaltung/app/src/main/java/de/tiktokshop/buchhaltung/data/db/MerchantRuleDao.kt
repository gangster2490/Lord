package de.tiktokshop.buchhaltung.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.tiktokshop.buchhaltung.data.model.MerchantRule

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :merchantKey AND entryTypeName = :entryTypeName LIMIT 1")
    suspend fun find(merchantKey: String, entryTypeName: String): MerchantRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRule)
}
