package com.smartfolder.domain.usecase

import com.smartfolder.data.saf.SafFileOps
import com.smartfolder.data.saf.SafManager
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.ImageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Lists the staged images of a source folder. Every trash folder under the
 * source tree is read, not just the canonically named one: a provider may have
 * created numbered duplicates, and the images stranded in them have to stay
 * restorable.
 */
class ListTrashImagesUseCase @Inject constructor(
    private val safFileOps: SafFileOps,
    private val safManager: SafManager
) {
    data class TrashListing(
        val items: List<ImageInfo> = emptyList(),
        val folderPaths: List<String> = emptyList()
    )

    suspend operator fun invoke(sourceFolder: Folder): TrashListing = withContext(Dispatchers.IO) {
        val trashFolders = safFileOps.findTrashFolders(sourceFolder.uri)
        if (trashFolders.isEmpty()) return@withContext TrashListing()

        val files = trashFolders.flatMap { folder ->
            safManager.listImageFilesInFolder(sourceFolder.uri, folder.uri)
        }

        TrashListing(
            items = files
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
                },
            folderPaths = trashFolders.map { it.relativePath }.sorted()
        )
    }
}
