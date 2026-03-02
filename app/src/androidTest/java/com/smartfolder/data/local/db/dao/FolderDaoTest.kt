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
            role = "REFERENCE",
            imageCount = 10
        )
        val id = folderDao.insert(folder)
        val retrieved = folderDao.getById(id)

        assertNotNull(retrieved)
        assertEquals("Test Folder", retrieved?.displayName)
        assertEquals("REFERENCE", retrieved?.role)
        assertEquals(10, retrieved?.imageCount)
    }

    @Test
    fun observeAllReturnsInsertedFolders() = runTest {
        folderDao.insert(FolderEntity(uri = "uri1", displayName = "A", role = "REFERENCE"))
        folderDao.insert(FolderEntity(uri = "uri2", displayName = "B", role = "UNSORTED"))

        val folders = folderDao.observeAll().first()
        assertEquals(2, folders.size)
    }

    @Test
    fun getByRoleFiltersCorrectly() = runTest {
        folderDao.insert(FolderEntity(uri = "uri1", displayName = "Ref", role = "REFERENCE"))
        folderDao.insert(FolderEntity(uri = "uri2", displayName = "Unsorted", role = "UNSORTED"))

        val refFolders = folderDao.getByRole("REFERENCE")
        assertEquals(1, refFolders.size)
        assertEquals("Ref", refFolders[0].displayName)

        val unsortedFolders = folderDao.getByRole("UNSORTED")
        assertEquals(1, unsortedFolders.size)
        assertEquals("Unsorted", unsortedFolders[0].displayName)
    }

    @Test
    fun getByUriReturnsCorrectFolder() = runTest {
        folderDao.insert(FolderEntity(uri = "content://test/unique", displayName = "Unique", role = "REFERENCE"))

        val found = folderDao.getByUri("content://test/unique")
        assertNotNull(found)
        assertEquals("Unique", found?.displayName)

        val notFound = folderDao.getByUri("content://nonexistent")
        assertNull(notFound)
    }

    @Test
    fun updateModifiesFolder() = runTest {
        val id = folderDao.insert(FolderEntity(uri = "uri", displayName = "Old", role = "REFERENCE"))
        val folder = folderDao.getById(id)!!
        folderDao.update(folder.copy(displayName = "New", imageCount = 20))

        val updated = folderDao.getById(id)
        assertEquals("New", updated?.displayName)
        assertEquals(20, updated?.imageCount)
    }

    @Test
    fun deleteRemovesFolder() = runTest {
        val id = folderDao.insert(FolderEntity(uri = "uri", displayName = "ToDelete", role = "REFERENCE"))
        val folder = folderDao.getById(id)!!
        folderDao.delete(folder)

        assertNull(folderDao.getById(id))
    }

    @Test
    fun deleteAllClearsTable() = runTest {
        folderDao.insert(FolderEntity(uri = "uri1", displayName = "A", role = "REFERENCE"))
        folderDao.insert(FolderEntity(uri = "uri2", displayName = "B", role = "UNSORTED"))

        folderDao.deleteAll()
        val all = folderDao.observeAll().first()
        assertEquals(0, all.size)
    }
}
