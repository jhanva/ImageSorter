package com.smartfolder.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartfolder.data.local.db.entities.SuggestionEntity

@Dao
interface SuggestionDao {
    @Query("SELECT * FROM suggestions ORDER BY score DESC")
    suspend fun getAll(): List<SuggestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(suggestions: List<SuggestionEntity>)

    @Query("DELETE FROM suggestions")
    suspend fun deleteAll()
}
