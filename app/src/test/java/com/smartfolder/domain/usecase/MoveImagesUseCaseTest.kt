package com.smartfolder.domain.usecase

import android.net.Uri
import com.smartfolder.data.saf.MoveResult
import com.smartfolder.data.saf.SafFileOps
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.repository.ImageRepository
import com.smartfolder.domain.repository.TransactionRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class MoveImagesUseCaseTest {

    private lateinit var safFileOps: SafFileOps
    private lateinit var imageRepository: ImageRepository
    private lateinit var transactionRunner: TransactionRunner
    private lateinit var useCase: MoveImagesUseCase

    private val destUri = mock(Uri::class.java)

    @Before
    fun setup() {
        safFileOps = mock(SafFileOps::class.java)
        imageRepository = mock(ImageRepository::class.java)
        transactionRunner = object : TransactionRunner {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
        }
        useCase = MoveImagesUseCase(safFileOps, imageRepository, transactionRunner)
    }

    @Test
    fun `empty list returns zero counts`() = runTest {
        val report = useCase(emptyList(), destUri)
        assertEquals(0, report.moved)
        assertEquals(0, report.copiedOnly)
        assertEquals(0, report.failed)
        assertEquals(0, report.errors.size)
    }

    @Test
    fun `successful move increments moved count and deletes from repo`() = runTest {
        val imageUri = mock(Uri::class.java)
        val newUri = mock(Uri::class.java)
        val image = ImageInfo(1L, 1L, imageUri, "test.jpg", "hash", 1000L, 100L)

        `when`(safFileOps.moveFile(imageUri, destUri, "test.jpg"))
            .thenReturn(MoveResult.Moved(newUri))

        val report = useCase(listOf(image), destUri)

        assertEquals(1, report.moved)
        assertEquals(0, report.copiedOnly)
        assertEquals(0, report.failed)
        verify(imageRepository).delete(image)
    }

    @Test
    fun `copied only result increments copiedOnly count`() = runTest {
        val imageUri = mock(Uri::class.java)
        val newUri = mock(Uri::class.java)
        val image = ImageInfo(1L, 1L, imageUri, "test.jpg", "hash", 1000L, 100L)

        `when`(safFileOps.moveFile(imageUri, destUri, "test.jpg"))
            .thenReturn(MoveResult.CopiedOnly(newUri, "Could not delete"))

        val report = useCase(listOf(image), destUri)

        assertEquals(0, report.moved)
        assertEquals(1, report.copiedOnly)
        assertEquals(0, report.failed)
        assertEquals(listOf("test.jpg: copied only (Could not delete)"), report.errors)
    }

    @Test
    fun `failure increments failed count and adds error`() = runTest {
        val imageUri = mock(Uri::class.java)
        val image = ImageInfo(1L, 1L, imageUri, "test.jpg", "hash", 1000L, 100L)

        `when`(safFileOps.moveFile(imageUri, destUri, "test.jpg"))
            .thenReturn(MoveResult.Failure("Permission denied"))

        val report = useCase(listOf(image), destUri)

        assertEquals(0, report.moved)
        assertEquals(0, report.copiedOnly)
        assertEquals(1, report.failed)
        assertEquals(1, report.errors.size)
        assertEquals("test.jpg: Permission denied", report.errors[0])
    }

    @Test
    fun `mixed results tracked correctly`() = runTest {
        val uri1 = mock(Uri::class.java)
        val uri2 = mock(Uri::class.java)
        val uri3 = mock(Uri::class.java)
        val newUri = mock(Uri::class.java)

        val img1 = ImageInfo(1L, 1L, uri1, "a.jpg", "h1", 100L, 100L)
        val img2 = ImageInfo(2L, 1L, uri2, "b.jpg", "h2", 100L, 100L)
        val img3 = ImageInfo(3L, 1L, uri3, "c.jpg", "h3", 100L, 100L)

        `when`(safFileOps.moveFile(uri1, destUri, "a.jpg"))
            .thenReturn(MoveResult.Moved(newUri))
        `when`(safFileOps.moveFile(uri2, destUri, "b.jpg"))
            .thenReturn(MoveResult.CopiedOnly(newUri, "reason"))
        `when`(safFileOps.moveFile(uri3, destUri, "c.jpg"))
            .thenReturn(MoveResult.Failure("error"))

        val report = useCase(listOf(img1, img2, img3), destUri)

        assertEquals(1, report.moved)
        assertEquals(1, report.copiedOnly)
        assertEquals(1, report.failed)
    }
}
