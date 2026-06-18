package com.smartfolder.domain.usecase

import android.net.TestUri
import android.net.Uri
import com.smartfolder.data.media.MediaStoreFolderProvider
import com.smartfolder.data.saf.SafManager
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SelectFolderUseCaseTest {

    private lateinit var folderRepository: RecordingFolderRepository
    private lateinit var safManager: SafManager
    private lateinit var mediaStoreFolderProvider: MediaStoreFolderProvider
    private lateinit var useCase: SelectFolderUseCase

    @Before
    fun setup() {
        folderRepository = RecordingFolderRepository()
        safManager = mock(SafManager::class.java)
        mediaStoreFolderProvider = mock(MediaStoreFolderProvider::class.java)
        useCase = SelectFolderUseCase(folderRepository, safManager, mediaStoreFolderProvider)
    }

    @Test
    fun `inserting a new destination folder does not replace existing destination folders`() = runTest {
        val newUri = TestUri("content://new-destination")

        `when`(safManager.getFolderDisplayName(newUri)).thenReturn("New destination")

        val result = useCase(newUri, FolderRole.DESTINATION)

        val inserted = folderRepository.insertedFolders.single()
        assertEquals(newUri, inserted.uri)
        assertEquals("New destination", inserted.displayName)
        assertEquals(FolderRole.DESTINATION, inserted.role)
        assertEquals(2L, result.id)
        assertEquals(FolderRole.DESTINATION, result.role)
        assertEquals("New destination", result.displayName)
        assertNull(folderRepository.updatedFolder)
    }

    @Test
    fun `selecting an existing uri updates the stored folder instead of duplicating it`() = runTest {
        val uri = TestUri("content://existing-folder")
        val existingFolder = Folder(
            id = 4L,
            uri = uri,
            displayName = "Old name",
            role = FolderRole.SOURCE,
            imageCount = 2
        )

        folderRepository.byUri[uri.toString()] = existingFolder
        `when`(safManager.getFolderDisplayName(uri)).thenReturn("Updated name")

        val result = useCase(uri, FolderRole.DESTINATION)

        val updated = requireNotNull(folderRepository.updatedFolder)
        assertEquals(existingFolder.id, updated.id)
        assertEquals(FolderRole.DESTINATION, updated.role)
        assertEquals("Updated name", updated.displayName)
        assertEquals(updated, result)
        assertEquals(emptyList<Folder>(), folderRepository.insertedFolders)
    }

    private class RecordingFolderRepository : FolderRepository {
        val byUri = linkedMapOf<String, Folder>()
        val insertedFolders = mutableListOf<Folder>()
        var updatedFolder: Folder? = null

        override fun observeAll(): Flow<List<Folder>> = emptyFlow()

        override suspend fun getByRole(role: FolderRole): List<Folder> =
            byUri.values.filter { it.role == role }

        override suspend fun getByUri(uri: String): Folder? = byUri[uri]

        override suspend fun insert(folder: Folder): Long {
            val newId = insertedFolders.size.toLong() + 2L
            val inserted = folder.copy(id = newId)
            insertedFolders += inserted
            byUri[inserted.uri.toString()] = inserted
            return newId
        }

        override suspend fun update(folder: Folder) {
            updatedFolder = folder
            byUri[folder.uri.toString()] = folder
        }

        override suspend fun delete(folder: Folder) = Unit

        override suspend fun deleteAll() {
            byUri.clear()
            insertedFolders.clear()
            updatedFolder = null
        }
    }
}
