package com.smartfolder.domain.repository

import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun observeAll(): Flow<List<Folder>>
    suspend fun getById(id: Long): Folder?
    suspend fun getByRole(role: FolderRole): List<Folder>
    suspend fun getByUri(uri: String): Folder?
    suspend fun insert(folder: Folder): Long
    suspend fun update(folder: Folder)
    suspend fun delete(folder: Folder)
    suspend fun deleteAll()
}
