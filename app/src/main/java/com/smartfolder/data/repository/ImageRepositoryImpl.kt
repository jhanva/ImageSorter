package com.smartfolder.data.repository

import android.net.Uri
import com.smartfolder.data.local.db.dao.ImageDao
import com.smartfolder.data.local.db.entities.ImageEntity
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.repository.ImageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageRepositoryImpl @Inject constructor(
    private val imageDao: ImageDao
) : ImageRepository {

    override fun observeByFolder(folderId: Long): Flow<List<ImageInfo>> {
        return imageDao.observeByFolder(folderId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getByFolder(folderId: Long): List<ImageInfo> {
        return imageDao.getByFolder(folderId).map { it.toDomain() }
    }

    override suspend fun getById(id: Long): ImageInfo? {
        return imageDao.getById(id)?.toDomain()
    }

    override suspend fun getByIds(ids: List<Long>): List<ImageInfo> {
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(SQLITE_BIND_LIMIT).flatMap { chunk ->
            imageDao.getByIds(chunk).map { it.toDomain() }
        }
    }

    override suspend fun getByUri(uri: String): ImageInfo? {
        return imageDao.getByUri(uri)?.toDomain()
    }

    override suspend fun getByUris(uris: List<String>): List<ImageInfo> {
        // SQLite has a limit of ~999 bind variables; chunk to stay safe
        return uris.chunked(SQLITE_BIND_LIMIT).flatMap { chunk ->
            imageDao.getByUris(chunk).map { it.toDomain() }
        }
    }

    companion object {
        private const val SQLITE_BIND_LIMIT = 900
    }

    override suspend fun insert(image: ImageInfo): Long {
        return imageDao.insert(image.toEntity())
    }

    override suspend fun insertAll(images: List<ImageInfo>): List<Long> {
        return imageDao.insertAll(images.map { it.toEntity() })
    }

    override suspend fun update(image: ImageInfo) {
        imageDao.update(image.toEntity())
    }

    override suspend fun delete(image: ImageInfo) {
        imageDao.delete(image.toEntity())
    }

    override suspend fun deleteByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        ids.chunked(SQLITE_BIND_LIMIT).forEach { chunk ->
            imageDao.deleteByIds(chunk)
        }
    }

    override suspend fun deleteByFolder(folderId: Long) {
        imageDao.deleteByFolder(folderId)
    }

    override suspend fun countByFolder(folderId: Long): Int {
        return imageDao.countByFolder(folderId)
    }

    private fun ImageEntity.toDomain(): ImageInfo = ImageInfo(
        id = id,
        folderId = folderId,
        uri = Uri.parse(uri),
        displayName = displayName,
        contentHash = contentHash,
        sizeBytes = sizeBytes,
        lastModified = lastModified
    )

    private fun ImageInfo.toEntity(): ImageEntity = ImageEntity(
        id = id,
        folderId = folderId,
        uri = uri.toString(),
        displayName = displayName,
        contentHash = contentHash,
        sizeBytes = sizeBytes,
        lastModified = lastModified
    )
}
