package com.smartfolder.domain.usecase

import android.net.Uri
import com.smartfolder.data.saf.SafManager
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.repository.FolderRepository
import javax.inject.Inject

class SelectFolderUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val safManager: SafManager
) {
    suspend operator fun invoke(uri: Uri, role: FolderRole): Folder {
        safManager.takePersistablePermission(uri)
        val displayName = safManager.getFolderDisplayName(uri)
        val imageFiles = safManager.listImageFiles(uri, recursive = true)

        val existing = folderRepository.getByUri(uri.toString())
        if (existing != null) {
            val updated = existing.copy(
                role = role,
                displayName = displayName,
                imageCount = imageFiles.size
            )
            folderRepository.update(updated)
            return updated
        }

        val folder = Folder(
            uri = uri,
            displayName = displayName,
            role = role,
            imageCount = imageFiles.size
        )
        val id = folderRepository.insert(folder)
        return folder.copy(id = id)
    }
}
