package com.smartfolder.domain.repository

import com.smartfolder.domain.model.ImageInfo

interface ImageRepository {
    suspend fun getByFolder(folderId: Long): List<ImageInfo>
    suspend fun getByIds(ids: List<Long>): List<ImageInfo>
    suspend fun getByUri(uri: String): ImageInfo?
    suspend fun getByUris(uris: List<String>): List<ImageInfo>
    suspend fun insert(image: ImageInfo): Long
    suspend fun insertAll(images: List<ImageInfo>): List<Long>
    suspend fun update(image: ImageInfo)
    suspend fun delete(image: ImageInfo)
    suspend fun deleteByIds(ids: List<Long>)
    suspend fun deleteByFolder(folderId: Long)
    suspend fun countByFolder(folderId: Long): Int
}
