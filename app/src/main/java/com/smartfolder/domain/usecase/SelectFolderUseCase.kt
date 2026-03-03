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
        val imageCount = try {
            safManager.listImageFiles(uri, recursive = true).size
        } catch (_: Exception) {
            // Folder selection should still succeed even if initial listing fails.
            0
        }

        val existing = folderRepository.getByUri(uri.toString())
        val foldersWithSameRole = folderRepository.getByRole(role)

        // Keep a single folder per role to avoid ambiguous selection downstream.
        val staleSameRoleFolders = foldersWithSameRole.filter { it.id != existing?.id }
        staleSameRoleFolders.forEach { folderRepository.delete(it) }

        if (existing != null) {
            val updated = existing.copy(
                role = role,
                displayName = displayName,
                imageCount = imageCount
            )
            folderRepository.update(updated)
            return updated
        }

        val folder = Folder(
            uri = uri,
            displayName = displayName,
            role = role,
            imageCount = imageCount
        )
        val id = folderRepository.insert(folder)
        return folder.copy(id = id)
    }
}
