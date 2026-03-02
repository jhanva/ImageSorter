package com.smartfolder.domain.repository

interface TransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}
