package com.smartfolder.domain.usecase

import android.net.TestUri
import com.smartfolder.data.media.MediaStoreFolderProvider
import com.smartfolder.data.storage.DirectFileOps
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.ImageFolderOption
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ListDestinationFoldersUseCaseTest {

    private lateinit var mediaStoreFolderProvider: MediaStoreFolderProvider
    private lateinit var directFileOps: DirectFileOps
    private lateinit var useCase: ListDestinationFoldersUseCase

    private val source = Folder(
        id = 1L,
        uri = TestUri("content://tree/source"),
        displayName = "Descargas",
        role = FolderRole.SOURCE
    )

    @Before
    fun setup() {
        mediaStoreFolderProvider = mock(MediaStoreFolderProvider::class.java)
        directFileOps = mock(DirectFileOps::class.java)
        useCase = ListDestinationFoldersUseCase(mediaStoreFolderProvider, directFileOps)
        `when`(directFileOps.resolveFile(source.uri))
            .thenReturn(File("/storage/emulated/0/Download"))
    }

    private fun option(name: String, path: String) = ImageFolderOption(
        displayName = name,
        documentId = "primary:$name",
        imageCount = 3,
        absolutePath = path
    )

    /**
     * Stubs the uris first: doing it while building the argument of another
     * stubbing call leaves Mockito with an unfinished stub.
     */
    private fun given(options: List<ImageFolderOption>) {
        options.filter { it.absolutePath.isNotBlank() }.forEach { folder ->
            `when`(directFileOps.uriFor(File(folder.absolutePath)))
                .thenReturn(TestUri("file://${folder.absolutePath}"))
        }
        `when`(mediaStoreFolderProvider.getImageFolders()).thenReturn(options)
    }

    @Test
    fun `offers every image folder except the source and the trash`() = runTest {
        given(
            listOf(
                option("Memes", "/storage/emulated/0/Pictures/Memes"),
                option("Descargas", "/storage/emulated/0/Download"),
                option("ImageSorterTrash", "/storage/emulated/0/Download/ImageSorterTrash"),
                option("ImageSorterTrash (1)", "/storage/emulated/0/Download/ImageSorterTrash (1)"),
                option("Familia", "/storage/emulated/0/Pictures/Familia")
            )
        )

        val destinations = useCase(source)

        assertEquals(listOf("Memes", "Familia"), destinations.map { it.displayName })
    }

    @Test
    fun `skips folders without a resolved path`() = runTest {
        given(
            listOf(
                option("Memes", "/storage/emulated/0/Pictures/Memes"),
                option("Sin ruta", "")
            )
        )

        val destinations = useCase(source)

        assertEquals(listOf("Memes"), destinations.map { it.displayName })
    }
}
