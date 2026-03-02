package com.smartfolder.data.local.db.dao

import androidx.room.*
import com.smartfolder.data.local.db.entities.ImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageDao {
    @Query("SELECT * FROM images WHERE folderId = :folderId")
    fun observeByFolder(folderId: Long): Flow<List<ImageEntity>>

    @Query("SELECT * FROM images WHERE folderId = :folderId")
    suspend fun getByFolder(folderId: Long): List<ImageEntity>

    @Query("SELECT * FROM images WHERE id = :id")
    suspend fun getById(id: Long): ImageEntity?

    @Query("SELECT * FROM images WHERE uri = :uri")
    suspend fun getByUri(uri: String): ImageEntity?

    @Query("SELECT * FROM images WHERE uri IN (:uris)")
    suspend fun getByUris(uris: List<String>): List<ImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: ImageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<ImageEntity>): List<Long>

    @Update
    suspend fun update(image: ImageEntity)

    @Delete
    suspend fun delete(image: ImageEntity)

    @Query("DELETE FROM images WHERE folderId = :folderId")
    suspend fun deleteByFolder(folderId: Long)

    @Query("SELECT COUNT(*) FROM images WHERE folderId = :folderId")
    suspend fun countByFolder(folderId: Long): Int
}
