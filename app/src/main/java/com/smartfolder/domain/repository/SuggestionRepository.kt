package com.smartfolder.domain.repository

import com.smartfolder.domain.model.ReviewStatus
import com.smartfolder.domain.model.StoredSuggestion

interface SuggestionRepository {
    suspend fun getAll(): List<StoredSuggestion>
    suspend fun insertAll(suggestions: List<StoredSuggestion>)
    suspend fun replaceAll(suggestions: List<StoredSuggestion>)
    suspend fun deleteAll()
    suspend fun setReviewStatus(imageId: Long, status: ReviewStatus, destinationFolderId: Long?)
}
