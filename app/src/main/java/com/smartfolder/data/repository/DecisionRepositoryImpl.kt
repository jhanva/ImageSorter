package com.smartfolder.data.repository

import com.smartfolder.data.local.db.dao.DecisionDao
import com.smartfolder.data.local.db.entities.DecisionEntity
import com.smartfolder.domain.model.Decision
import com.smartfolder.domain.repository.DecisionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DecisionRepositoryImpl @Inject constructor(
    private val decisionDao: DecisionDao
) : DecisionRepository {

    override suspend fun getByImageId(imageId: Long): Decision? {
        return decisionDao.getByImageId(imageId)?.toDomain()
    }

    override suspend fun getAccepted(): List<Decision> {
        return decisionDao.getAccepted().map { it.toDomain() }
    }

    override suspend fun insert(decision: Decision): Long {
        return decisionDao.insert(decision.toEntity())
    }

    override suspend fun delete(decision: Decision) {
        decisionDao.delete(decision.toEntity())
    }

    override suspend fun deleteAll() {
        decisionDao.deleteAll()
    }

    private fun DecisionEntity.toDomain(): Decision = Decision(
        id = id,
        imageId = imageId,
        accepted = accepted,
        score = score,
        decidedAt = decidedAt
    )

    private fun Decision.toEntity(): DecisionEntity = DecisionEntity(
        id = id,
        imageId = imageId,
        accepted = accepted,
        score = score,
        decidedAt = decidedAt
    )
}
