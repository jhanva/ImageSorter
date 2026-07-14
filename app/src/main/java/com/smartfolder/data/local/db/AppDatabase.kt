package com.smartfolder.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.smartfolder.data.local.db.converters.Converters
import com.smartfolder.data.local.db.dao.FolderDao
import com.smartfolder.data.local.db.entities.FolderEntity

@Database(
    entities = [
        FolderEntity::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
}
