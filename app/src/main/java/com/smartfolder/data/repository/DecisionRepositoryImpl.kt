package com.smartfolder.data.repository

import com.smartfolder.data.local.db.dao.DecisionDao
import com.smartfolder.domain.repository.DecisionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DecisionRepositoryImpl @Inject constructor(
    private val decisionDao: DecisionDao
) : DecisionRepository {

    override suspend fun deleteAll() {
        decisionDao.deleteAll()
    }
}
