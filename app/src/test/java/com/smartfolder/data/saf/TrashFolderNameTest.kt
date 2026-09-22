package com.smartfolder.data.saf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashFolderNameTest {

    @Test
    fun `recognizes the canonical trash folder`() {
        assertTrue(SafFileOps.isTrashFolderName(SafFileOps.TRASH_FOLDER_NAME))
    }

    @Test
    fun `recognizes numbered duplicates created by the provider`() {
        assertTrue(SafFileOps.isTrashFolderName("ImageSorterTrash (1)"))
        assertTrue(SafFileOps.isTrashFolderName("ImageSorterTrash (12)"))
    }

    @Test
    fun `ignores unrelated folders`() {
        assertFalse(SafFileOps.isTrashFolderName("ImageSorterTrashOld"))
        assertFalse(SafFileOps.isTrashFolderName("Trash"))
        assertFalse(SafFileOps.isTrashFolderName("ImageSorterTrash ()"))
        assertFalse(SafFileOps.isTrashFolderName("Camera"))
    }
}
