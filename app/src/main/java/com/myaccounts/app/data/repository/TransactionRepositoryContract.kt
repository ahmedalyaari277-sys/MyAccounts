package com.myaccounts.app.data.repository

import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.TransactionAttachmentEntity
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.dao.ArchivedTransactionRow
import com.myaccounts.app.data.local.dao.RestoreTransactionResult
import kotlinx.coroutines.flow.Flow

interface TransactionRepositoryContract {
    suspend fun addTransaction(transaction: TransactionEntity): Long
    suspend fun updateTransaction(transaction: TransactionEntity)
    suspend fun updateTransactionWithAttachments(
        transaction: TransactionEntity,
        newAttachments: List<TransactionAttachmentEntity>,
        deletedAttachments: List<TransactionAttachmentEntity>
    )
    suspend fun getCurrencyAccountById(accountId: Long): CurrencyAccountEntity?
    suspend fun getCurrencyAccount(personId: Long, currencyCode: String): CurrencyAccountEntity?
    suspend fun getPersonNameForAccount(accountId: Long): String?
    fun observeTransactions(accountId: Long): Flow<List<TransactionEntity>>
    fun observeArchivedTransactions(): Flow<List<TransactionEntity>>
    fun observeArchivedTransactionRows(): Flow<List<ArchivedTransactionRow>>
    suspend fun getTransactions(accountId: Long): List<TransactionEntity>
    suspend fun getTransaction(transactionId: Long): TransactionEntity?
    fun observeBalance(accountId: Long): Flow<Long>
    suspend fun getBalance(accountId: Long): Long
    suspend fun deleteTransaction(transaction: TransactionEntity)
    suspend fun deleteTransactionById(transactionId: Long)
    suspend fun archiveTransaction(transactionId: Long)
    suspend fun restoreTransaction(transactionId: Long): RestoreTransactionResult
    fun observeAttachments(transactionId: Long): Flow<List<TransactionAttachmentEntity>>
    fun observeAttachmentCount(transactionId: Long): Flow<Int>
    suspend fun getAttachments(transactionId: Long): List<TransactionAttachmentEntity>
    suspend fun addAttachments(attachments: List<TransactionAttachmentEntity>)
    suspend fun deleteAttachment(attachment: TransactionAttachmentEntity)
}
