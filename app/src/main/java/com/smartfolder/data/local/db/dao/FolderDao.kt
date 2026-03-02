package com.smartfolder.data.local.db.dao

import androidx.room.*
import com.smartfolder.data.local.db.entities.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: Long): FolderEntity?

    @Query("SELECT * FROM folders WHERE role = :role")
    suspend fun getByRole(role: String): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE uri = :uri")
    suspend fun getByUri(uri: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("DELETE FROM folders")
    suspend fun deleteAll()
}
