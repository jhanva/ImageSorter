package com.smartfolder.data.local.db.dao

import androidx.room.*
import com.smartfolder.data.local.db.entities.DecisionEntity

@Dao
interface DecisionDao {
    @Query("SELECT * FROM decisions WHERE imageId = :imageId")
    suspend fun getByImageId(imageId: Long): DecisionEntity?

    @Query("SELECT * FROM decisions WHERE accepted = 1")
    suspend fun getAccepted(): List<DecisionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(decision: DecisionEntity): Long

    @Delete
    suspend fun delete(decision: DecisionEntity)

    @Query("DELETE FROM decisions")
    suspend fun deleteAll()
}
