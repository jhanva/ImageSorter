package com.smartfolder.domain.repository

import com.smartfolder.domain.model.Embedding

interface EmbeddingRepository {
    suspend fun getByImageId(imageId: Long): Embedding?
    suspend fun getByImageIds(imageIds: List<Long>): List<Embedding>
    suspend fun getByFolderAndModel(folderId: Long, modelName: String): List<Embedding>
    suspend fun insert(embedding: Embedding): Long
    suspend fun insertAll(embeddings: List<Embedding>)
    suspend fun delete(embedding: Embedding)
    suspend fun deleteByFolder(folderId: Long)
    suspend fun deleteByOtherModel(modelName: String)
    suspend fun countByFolderAndModel(folderId: Long, modelName: String): Int
}
