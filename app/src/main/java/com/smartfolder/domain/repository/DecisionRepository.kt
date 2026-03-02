package com.smartfolder.domain.repository

import com.smartfolder.domain.model.Decision

interface DecisionRepository {
    suspend fun getByImageId(imageId: Long): Decision?
    suspend fun getAccepted(): List<Decision>
    suspend fun insert(decision: Decision): Long
    suspend fun delete(decision: Decision)
    suspend fun deleteAll()
}
