package com.smartfolder.domain.usecase

import android.net.Uri
import android.provider.DocumentsContract
import com.smartfolder.data.media.MediaStoreFolderProvider
import com.smartfolder.data.saf.SafManager
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.repository.FolderRepository
import javax.inject.Inject

class SelectFolderUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val safManager: SafManager,
    private val mediaStoreFolderProvider: MediaStoreFolderProvider
) {
    suspend operator fun invoke(uri: Uri, role: FolderRole): Folder {
        safManager.takePersistablePermission(uri)
        val hasTreeAccess = safManager.hasPersistedPermission(uri)
        if (!hasTreeAccess) {
            throw IllegalStateException(
                "Folder access is not fully granted yet. In Secure Folder, use manual folder grant from the system picker."
            )
        }
        val displayName = safManager.getFolderDisplayName(uri)
        val imageCount = try {
            safManager.listImageFiles(uri, recursive = true).size
        } catch (_: Exception) {
            // Fallback to MediaStore count when SAF listing is not available yet.
            val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            if (documentId != null) {
                mediaStoreFolderProvider.getImageCountForDocumentId(documentId)
            } else {
                0
            }
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
