package com.smartfolder.data.local.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartfolder.data.local.db.AppDatabase
import com.smartfolder.data.local.db.entities.EmbeddingEntity
import com.smartfolder.data.local.db.entities.FolderEntity
import com.smartfolder.data.local.db.entities.ImageEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class EmbeddingDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var embeddingDao: EmbeddingDao
    private lateinit var imageDao: ImageDao
    private lateinit var folderDao: FolderDao
    private var folderId: Long = 0
    private var imageId1: Long = 0
    private var imageId2: Long = 0

    @Before
    fun setup() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        folderDao = database.folderDao()
        imageDao = database.imageDao()
        embeddingDao = database.embeddingDao()

        folderId = folderDao.insert(
            FolderEntity(uri = "content://folder", displayName = "Test", role = "REFERENCE")
        )
        imageId1 = imageDao.insert(
            ImageEntity(folderId = folderId, uri = "img1", displayName = "img1.jpg",
                contentHash = "100_200", sizeBytes = 100, lastModified = 200)
        )
        imageId2 = imageDao.insert(
            ImageEntity(folderId = folderId, uri = "img2", displayName = "img2.jpg",
                contentHash = "300_400", sizeBytes = 300, lastModified = 400)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createVectorBlob(vararg values: Float): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(values)
        return buffer.array()
    }

    @Test
    fun insertAndRetrieveEmbedding() = runTest {
        val blob = createVectorBlob(1f, 0f, 0f)
        val id = embeddingDao.insert(
            EmbeddingEntity(imageId = imageId1, vectorBlob = blob, modelName = "fast")
        )

        val embedding = embeddingDao.getByImageId(imageId1)
        assertNotNull(embedding)
        assertEquals(imageId1, embedding?.imageId)
        assertEquals("fast", embedding?.modelName)
    }

    @Test
    fun getByImageIdsReturnsBatch() = runTest {
        embeddingDao.insert(
            EmbeddingEntity(imageId = imageId1, vectorBlob = createVectorBlob(1f), modelName = "fast")
        )
        embeddingDao.insert(
            EmbeddingEntity(imageId = imageId2, vectorBlob = createVectorBlob(0f), modelName = "fast")
        )

        val results = embeddingDao.getByImageIds(listOf(imageId1, imageId2))
        assertEquals(2, results.size)
    }

    @Test
    fun getByFolderAndModelFilters() = runTest {
        embeddingDao.insert(
            EmbeddingEntity(imageId = imageId1, vectorBlob = createVectorBlob(1f), modelName = "fast")
        )
        embeddingDao.insert(
            EmbeddingEntity(imageId = imageId2, vectorBlob = createVectorBlob(0f), modelName = "precise")
        )

        val fastResults = embeddingDao.getByFolderAndModel(folderId, "fast")
        assertEquals(1, fastResults.size)
        assertEquals(imageId1, fastResults[0].imageId)

        val preciseResults = embeddingDao.getByFolderAndModel(folderId, "precise")
        assertEquals(1, preciseResults.size)
        assertEquals(imageId2, preciseResults[0].imageId)
    }

    @Test
    fun countByFolderAndModel() = runTest {
        embeddingDao.insert(
            EmbeddingEntity(imageId = imageId1, vectorBlob = createVectorBlob(1f), modelName = "fast")
        )
        embeddingDao.insert(
            EmbeddingEntity(imageId = imageId2, vectorBlob = createVectorBlob(0f), modelName = "fast")
        )

        val count = embeddingDao.countByFolderAndModel(folderId, "fast")
        assertEquals(2, count)

        val otherCount = embeddingDao.countByFolderAndModel(folderId, "precise")
        assertEquals(0, otherCount)
    }

    @Test
    fun deleteByFolderRemovesAll() = runTest {
        embeddingDao.insert(
            EmbeddingEntity(imageId = imageId1, vectorBlob = createVectorBlob(1f), modelName = "fast")
        )
        embeddingDao.insert(
            EmbeddingEntity(imageId = imageId2, vectorBlob = createVectorBlob(0f), modelName = "fast")
        )

        embeddingDao.deleteByFolder(folderId)

        assertNull(embeddingDao.getByImageId(imageId1))
        assertNull(embeddingDao.getByImageId(imageId2))
    }

    @Test
    fun deleteByOtherModelKeepsCurrentModel() = runTest {
        embeddingDao.insert(
            EmbeddingEntity(imageId = imageId1, vectorBlob = createVectorBlob(1f), modelName = "fast")
        )
        embeddingDao.insert(
            EmbeddingEntity(imageId = imageId2, vectorBlob = createVectorBlob(0f), modelName = "precise")
        )

        embeddingDao.deleteByOtherModel("fast")

        assertNotNull(embeddingDao.getByImageId(imageId1))
        assertNull(embeddingDao.getByImageId(imageId2))
    }

    @Test
    fun cascadeDeleteOnImageRemoval() = runTest {
        embeddingDao.insert(
            EmbeddingEntity(imageId = imageId1, vectorBlob = createVectorBlob(1f), modelName = "fast")
        )

        val image = imageDao.getById(imageId1)!!
        imageDao.delete(image)

        assertNull(embeddingDao.getByImageId(imageId1))
    }
}
