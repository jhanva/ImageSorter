package com.smartfolder.data.repository

import android.net.Uri
import com.smartfolder.data.local.db.dao.FolderDao
import com.smartfolder.data.local.db.entities.FolderEntity
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao
) : FolderRepository {

    override fun observeAll(): Flow<List<Folder>> {
        return folderDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getById(id: Long): Folder? {
        return folderDao.getById(id)?.toDomain()
    }

    override suspend fun getByRole(role: FolderRole): List<Folder> {
        return folderDao.getByRole(role.name).map { it.toDomain() }
    }

    override suspend fun getByUri(uri: String): Folder? {
        return folderDao.getByUri(uri)?.toDomain()
    }

    override suspend fun insert(folder: Folder): Long {
        return folderDao.insert(folder.toEntity())
    }

    override suspend fun update(folder: Folder) {
        folderDao.update(folder.toEntity())
    }

    override suspend fun delete(folder: Folder) {
        folderDao.delete(folder.toEntity())
    }

    override suspend fun deleteAll() {
        folderDao.deleteAll()
    }

    private fun FolderEntity.toDomain(): Folder = Folder(
        id = id,
        uri = Uri.parse(uri),
        displayName = displayName,
        role = FolderRole.valueOf(role),
        imageCount = imageCount,
        indexedCount = indexedCount,
        lastIndexedAt = lastIndexedAt
    )

    private fun Folder.toEntity(): FolderEntity = FolderEntity(
        id = id,
        uri = uri.toString(),
        displayName = displayName,
        role = role.name,
        imageCount = imageCount,
        indexedCount = indexedCount,
        lastIndexedAt = lastIndexedAt
    )
}
