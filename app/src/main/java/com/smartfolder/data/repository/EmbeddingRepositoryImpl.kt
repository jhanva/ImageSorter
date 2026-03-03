package com.smartfolder.data.repository

import com.smartfolder.data.local.db.dao.EmbeddingDao
import com.smartfolder.data.local.db.entities.EmbeddingEntity
import com.smartfolder.domain.model.Embedding
import com.smartfolder.domain.repository.EmbeddingRepository
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmbeddingRepositoryImpl @Inject constructor(
    private val embeddingDao: EmbeddingDao
) : EmbeddingRepository {

    override suspend fun getByImageId(imageId: Long): Embedding? {
        return embeddingDao.getByImageId(imageId)?.toDomain()
    }

    override suspend fun getByImageIds(imageIds: List<Long>): List<Embedding> {
        if (imageIds.isEmpty()) return emptyList()
        return imageIds.chunked(SQLITE_BIND_LIMIT).flatMap { chunk ->
            embeddingDao.getByImageIds(chunk).map { it.toDomain() }
        }
    }

    override suspend fun getByFolderAndModel(folderId: Long, modelName: String): List<Embedding> {
        return embeddingDao.getByFolderAndModel(folderId, modelName).map { it.toDomain() }
    }

    override suspend fun insert(embedding: Embedding): Long {
        return embeddingDao.insert(embedding.toEntity())
    }

    override suspend fun insertAll(embeddings: List<Embedding>) {
        embeddingDao.insertAll(embeddings.map { it.toEntity() })
    }

    override suspend fun delete(embedding: Embedding) {
        embeddingDao.delete(embedding.toEntity())
    }

    override suspend fun deleteByFolder(folderId: Long) {
        embeddingDao.deleteByFolder(folderId)
    }

    override suspend fun deleteByOtherModel(modelName: String) {
        embeddingDao.deleteByOtherModel(modelName)
    }

    override suspend fun countByFolderAndModel(folderId: Long, modelName: String): Int {
        return embeddingDao.countByFolderAndModel(folderId, modelName)
    }

    companion object {
        private const val SQLITE_BIND_LIMIT = 900
    }

    private fun EmbeddingEntity.toDomain(): Embedding = Embedding(
        id = id,
        imageId = imageId,
        vector = toFloatArray(vectorBlob),
        modelName = modelName,
        createdAt = createdAt
    )

    private fun Embedding.toEntity(): EmbeddingEntity = EmbeddingEntity(
        id = id,
        imageId = imageId,
        vectorBlob = toByteArray(vector),
        modelName = modelName,
        createdAt = createdAt
    )

    private fun toFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(floats)
        return floats
    }

    private fun toByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(floats)
        return buffer.array()
    }
}
