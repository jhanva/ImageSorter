package com.smartfolder.data.repository

import android.util.Half
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

    override suspend fun delete(embedding: Embedding) {
        embeddingDao.delete(embedding.toEntity())
    }

    override suspend fun deleteByFolder(folderId: Long) {
        embeddingDao.deleteByFolder(folderId)
    }

    override suspend fun countByFolderAndModel(folderId: Long, modelName: String): Int {
        return embeddingDao.countByFolderAndModel(folderId, modelName)
    }

    companion object {
        private const val SQLITE_BIND_LIMIT = 900
        // Tag byte for the half-precision (float16) blob layout. Old float32 blobs have
        // even byte length (size % 4 == 0); new float16 blobs have odd byte length
        // (1-byte tag + 2 bytes per element), so the size parity is enough to discriminate.
        private const val FLOAT16_TAG: Byte = 0xF1.toByte()
    }

    private fun EmbeddingEntity.toDomain(): Embedding = Embedding(
        id = id,
        imageId = imageId,
        vector = decodeVector(vectorBlob),
        modelName = modelName,
        createdAt = createdAt
    )

    private fun Embedding.toEntity(): EmbeddingEntity = EmbeddingEntity(
        id = id,
        imageId = imageId,
        vectorBlob = encodeVectorFloat16(vector),
        modelName = modelName,
        createdAt = createdAt
    )

    private fun encodeVectorFloat16(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(1 + floats.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(FLOAT16_TAG)
        for (f in floats) {
            buffer.putShort(Half.toHalf(f))
        }
        return buffer.array()
    }

    private fun decodeVector(blob: ByteArray): FloatArray {
        if (blob.isEmpty()) return FloatArray(0)
        return if (blob.size and 1 == 1) {
            // Float16 layout: 1-byte tag + 2 bytes per element
            val count = (blob.size - 1) / 2
            val buffer = ByteBuffer.wrap(blob, 1, blob.size - 1).order(ByteOrder.LITTLE_ENDIAN)
            FloatArray(count) { Half.toFloat(buffer.short) }
        } else {
            // Legacy float32 layout: 4 bytes per element
            val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
            FloatArray(blob.size / 4).also { buffer.asFloatBuffer().get(it) }
        }
    }
}
