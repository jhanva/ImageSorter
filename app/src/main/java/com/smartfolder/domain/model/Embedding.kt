package com.smartfolder.domain.model

data class Embedding(
    val id: Long = 0,
    val imageId: Long,
    val vector: FloatArray,
    val modelName: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Embedding
        return id == other.id && imageId == other.imageId && vector.contentEquals(other.vector) && modelName == other.modelName
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + imageId.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + modelName.hashCode()
        return result
    }
}
