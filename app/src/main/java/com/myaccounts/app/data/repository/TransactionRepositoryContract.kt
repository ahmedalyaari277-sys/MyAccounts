package com.myaccounts.app.data.repository

import com.myaccounts.app.data.local.TransactionEntity
import kotlinx.coroutines.flow.Flow

interface TransactionRepositoryContract {

    suspend fun addTransaction(
        transaction: TransactionEntity
    ): Long

    suspend fun updateTransaction(
        transaction: TransactionEntity
    )

    fun observeTransactions(
        accountId: Long
    ): Flow<List<TransactionEntity>>

    suspend fun getTransactions(
        accountId: Long
    ): List<TransactionEntity>

    suspend fun getTransaction(
        transactionId: Long
    ): TransactionEntity?

    fun observeBalance(
        accountId: Long
    ): Flow<Long>

    suspend fun getBalance(
        accountId: Long
    ): Long

    suspend fun deleteTransaction(
        transaction: TransactionEntity
    )

    suspend fun deleteTransactionById(
        transactionId: Long
    )
}
