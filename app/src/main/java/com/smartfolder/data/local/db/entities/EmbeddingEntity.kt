package com.smartfolder.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "embeddings",
    foreignKeys = [
        ForeignKey(
            entity = ImageEntity::class,
            parentColumns = ["id"],
            childColumns = ["imageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("imageId", unique = true)]
)
data class EmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageId: Long,
    val vectorBlob: ByteArray,
    val modelName: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EmbeddingEntity
        return id == other.id && imageId == other.imageId && vectorBlob.contentEquals(other.vectorBlob) && modelName == other.modelName
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + imageId.hashCode()
        result = 31 * result + vectorBlob.contentHashCode()
        result = 31 * result + modelName.hashCode()
        return result
    }
}
