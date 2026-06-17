package com.smartfolder.data.local.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartfolder.data.local.db.AppDatabase
import com.smartfolder.data.local.db.entities.FolderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var folderDao: FolderDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        folderDao = database.folderDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveFolder() = runTest {
        val folder = FolderEntity(
            uri = "content://test/folder",
            displayName = "Test Folder",
            role = "DESTINATION",
            imageCount = 10
        )
        val id = folderDao.insert(folder)
        val retrieved = folderDao.getById(id)

        assertNotNull(retrieved)
        assertEquals("Test Folder", retrieved?.displayName)
        assertEquals("DESTINATION", retrieved?.role)
        assertEquals(10, retrieved?.imageCount)
    }

    @Test
    fun observeAllReturnsInsertedFolders() = runTest {
        folderDao.insert(FolderEntity(uri = "uri1", displayName = "A", role = "DESTINATION"))
        folderDao.insert(FolderEntity(uri = "uri2", displayName = "B", role = "SOURCE"))

        val folders = folderDao.observeAll().first()
        assertEquals(2, folders.size)
    }

    @Test
    fun getByRoleFiltersCorrectly() = runTest {
        folderDao.insert(FolderEntity(uri = "uri1", displayName = "Dest", role = "DESTINATION"))
        folderDao.insert(FolderEntity(uri = "uri2", displayName = "Source", role = "SOURCE"))

        val destinationFolders = folderDao.getByRole("DESTINATION")
        assertEquals(1, destinationFolders.size)
        assertEquals("Dest", destinationFolders[0].displayName)

        val sourceFolders = folderDao.getByRole("SOURCE")
        assertEquals(1, sourceFolders.size)
        assertEquals("Source", sourceFolders[0].displayName)
    }

    @Test
    fun getByUriReturnsCorrectFolder() = runTest {
        folderDao.insert(FolderEntity(uri = "content://test/unique", displayName = "Unique", role = "DESTINATION"))

        val found = folderDao.getByUri("content://test/unique")
        assertNotNull(found)
        assertEquals("Unique", found?.displayName)

        val notFound = folderDao.getByUri("content://nonexistent")
        assertNull(notFound)
    }

    @Test
    fun updateModifiesFolder() = runTest {
        val id = folderDao.insert(FolderEntity(uri = "uri", displayName = "Old", role = "DESTINATION"))
        val folder = folderDao.getById(id)!!
        folderDao.update(folder.copy(displayName = "New", imageCount = 20))

        val updated = folderDao.getById(id)
        assertEquals("New", updated?.displayName)
        assertEquals(20, updated?.imageCount)
    }

    @Test
    fun deleteRemovesFolder() = runTest {
        val id = folderDao.insert(FolderEntity(uri = "uri", displayName = "ToDelete", role = "DESTINATION"))
        val folder = folderDao.getById(id)!!
        folderDao.delete(folder)

        assertNull(folderDao.getById(id))
    }

    @Test
    fun deleteAllClearsTable() = runTest {
        folderDao.insert(FolderEntity(uri = "uri1", displayName = "A", role = "DESTINATION"))
        folderDao.insert(FolderEntity(uri = "uri2", displayName = "B", role = "SOURCE"))

        folderDao.deleteAll()
        val all = folderDao.observeAll().first()
        assertEquals(0, all.size)
    }
}
