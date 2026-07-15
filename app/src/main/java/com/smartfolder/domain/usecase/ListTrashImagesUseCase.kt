package com.smartfolder.domain.usecase

import com.smartfolder.data.saf.SafFileOps
import com.smartfolder.data.saf.SafManager
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.ImageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ListTrashImagesUseCase @Inject constructor(
    private val safFileOps: SafFileOps,
    private val safManager: SafManager
) {
    suspend operator fun invoke(sourceFolder: Folder): List<ImageInfo> = withContext(Dispatchers.IO) {
        val trashUri = safFileOps.findChildFolder(sourceFolder.uri, SafFileOps.TRASH_FOLDER_NAME)
            ?: return@withContext emptyList()
        safManager.listImageFilesInFolder(sourceFolder.uri, trashUri)
            .sortedByDescending { it.lastModified }
            .mapIndexed { index, file ->
                ImageInfo(
                    id = index.toLong() + 1,
                    folderId = sourceFolder.id,
                    uri = file.uri,
                    displayName = file.displayName,
                    contentHash = "",
                    sizeBytes = file.sizeBytes,
                    lastModified = file.lastModified
                )
            }
    }
}
