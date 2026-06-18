package com.smartfolder.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface DecisionDao {
    @Query("DELETE FROM decisions")
    suspend fun deleteAll()
}
