package com.smartfolder.domain.usecase

import android.net.Uri
import android.provider.DocumentsContract
import com.smartfolder.data.media.MediaStoreFolderProvider
import com.smartfolder.data.saf.SafManager
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.repository.FolderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SelectFolderUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val safManager: SafManager,
    private val mediaStoreFolderProvider: MediaStoreFolderProvider
) {
    suspend operator fun invoke(uri: Uri, role: FolderRole): Folder = withContext(Dispatchers.IO) {
        safManager.takePersistablePermission(uri)
        val displayName = safManager.getFolderDisplayName(uri)
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        val imageCount = if (documentId != null) {
            runCatching {
                mediaStoreFolderProvider.getImageCountForDocumentId(documentId)
            }.getOrDefault(0)
        } else {
            0
        }

        val existing = folderRepository.getByUri(uri.toString())
        if (existing != null) {
            val updated = existing.copy(
                role = role,
                displayName = displayName,
                imageCount = imageCount
            )
            folderRepository.update(updated)
            return@withContext updated
        }

        val folder = Folder(
            uri = uri,
            displayName = displayName,
            role = role,
            imageCount = imageCount
        )
        val id = folderRepository.insert(folder)
        folder.copy(id = id)
    }
}
