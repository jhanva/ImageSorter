package com.smartfolder.domain.repository

import com.smartfolder.domain.model.Embedding

interface EmbeddingRepository {
    suspend fun getByImageIds(imageIds: List<Long>): List<Embedding>
    suspend fun getByFolderAndModel(folderId: Long, modelName: String): List<Embedding>
    suspend fun insert(embedding: Embedding): Long
    suspend fun delete(embedding: Embedding)
    suspend fun deleteByFolder(folderId: Long)
    suspend fun countByFolderAndModel(folderId: Long, modelName: String): Int
}
