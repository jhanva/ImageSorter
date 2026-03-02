package com.smartfolder.data.local.db.dao

import androidx.room.*
import com.smartfolder.data.local.db.entities.EmbeddingEntity

@Dao
interface EmbeddingDao {
    @Query("SELECT * FROM embeddings WHERE imageId = :imageId")
    suspend fun getByImageId(imageId: Long): EmbeddingEntity?

    @Query("SELECT * FROM embeddings WHERE imageId IN (:imageIds)")
    suspend fun getByImageIds(imageIds: List<Long>): List<EmbeddingEntity>

    @Query("SELECT * FROM embeddings WHERE imageId IN (SELECT id FROM images WHERE folderId = :folderId) AND modelName = :modelName")
    suspend fun getByFolderAndModel(folderId: Long, modelName: String): List<EmbeddingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: EmbeddingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(embeddings: List<EmbeddingEntity>)

    @Delete
    suspend fun delete(embedding: EmbeddingEntity)

    @Query("DELETE FROM embeddings WHERE imageId IN (SELECT id FROM images WHERE folderId = :folderId)")
    suspend fun deleteByFolder(folderId: Long)

    @Query("DELETE FROM embeddings WHERE modelName != :modelName")
    suspend fun deleteByOtherModel(modelName: String)

    @Query("SELECT COUNT(*) FROM embeddings WHERE imageId IN (SELECT id FROM images WHERE folderId = :folderId) AND modelName = :modelName")
    suspend fun countByFolderAndModel(folderId: Long, modelName: String): Int
}
