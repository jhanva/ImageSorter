package com.smartfolder.domain.usecase

import com.smartfolder.data.media.MediaStoreFolderProvider
import com.smartfolder.data.saf.SafFileOps
import com.smartfolder.data.storage.DirectFileOps
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Every image folder on the device, offered as a destination. Destinations are
 * no longer picked and stored one by one: the triage grid shows them all and
 * promotes the ones in use, so the folder being sorted and the staging trash
 * are the only ones taken out.
 */
class ListDestinationFoldersUseCase @Inject constructor(
    private val mediaStoreFolderProvider: MediaStoreFolderProvider,
    private val directFileOps: DirectFileOps
) {
    suspend operator fun invoke(source: Folder): List<Folder> = withContext(Dispatchers.IO) {
        val sourcePath = directFileOps.resolveFile(source.uri)?.absolutePath?.trimEnd('/')

        mediaStoreFolderProvider.getImageFolders()
            .asSequence()
            .filter { it.absolutePath.isNotBlank() }
            .map { option -> option to File(option.absolutePath) }
            .filterNot { (_, dir) -> dir.absolutePath.trimEnd('/') == sourcePath }
            .filterNot { (_, dir) -> SafFileOps.isTrashFolderName(dir.name) }
            .map { (option, dir) ->
                Folder(
                    uri = directFileOps.uriFor(dir),
                    displayName = option.displayName,
                    role = FolderRole.DESTINATION,
                    imageCount = option.imageCount
                )
            }
            .distinctBy { it.uri.toString() }
            .toList()
    }
}
