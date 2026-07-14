package com.smartfolder.domain.usecase

import com.smartfolder.data.saf.SafManager
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.ImageInfo
import javax.inject.Inject

/**
 * Lists images through SAF so every uri is a document under the granted tree:
 * moves then work silently, without per-file write-permission dialogs.
 */
class ListSourceImagesUseCase @Inject constructor(
    private val safManager: SafManager
) {
    suspend operator fun invoke(folder: Folder): List<ImageInfo> {
        val files = safManager.listImageFiles(folder.uri, recursive = true)
        return files
            .sortedByDescending { it.lastModified }
            .mapIndexed { index, file ->
                ImageInfo(
                    id = index.toLong() + 1,
                    folderId = folder.id,
                    uri = file.uri,
                    displayName = file.displayName,
                    contentHash = "",
                    sizeBytes = file.sizeBytes,
                    lastModified = file.lastModified
                )
            }
    }
}
