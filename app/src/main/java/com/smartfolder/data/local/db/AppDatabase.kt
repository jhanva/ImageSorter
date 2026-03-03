package com.smartfolder.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.smartfolder.data.local.db.converters.Converters
import com.smartfolder.data.local.db.dao.DecisionDao
import com.smartfolder.data.local.db.dao.EmbeddingDao
import com.smartfolder.data.local.db.dao.FolderDao
import com.smartfolder.data.local.db.dao.ImageDao
import com.smartfolder.data.local.db.dao.SuggestionDao
import com.smartfolder.data.local.db.entities.DecisionEntity
import com.smartfolder.data.local.db.entities.EmbeddingEntity
import com.smartfolder.data.local.db.entities.FolderEntity
import com.smartfolder.data.local.db.entities.ImageEntity
import com.smartfolder.data.local.db.entities.SuggestionEntity

@Database(
    entities = [
        FolderEntity::class,
        ImageEntity::class,
        EmbeddingEntity::class,
        DecisionEntity::class,
        SuggestionEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun imageDao(): ImageDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun decisionDao(): DecisionDao
    abstract fun suggestionDao(): SuggestionDao
}
