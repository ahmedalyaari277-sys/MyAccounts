package com.myaccounts.app.data.repository

import androidx.room.withTransaction
import com.myaccounts.app.data.local.AppDatabase
import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.TransactionAttachmentEntity
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.dao.ArchivedTransactionRow
import com.myaccounts.app.data.local.dao.TransactionAttachmentDao
import com.myaccounts.app.data.local.dao.TransactionDao
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val attachmentDao: TransactionAttachmentDao,
    private val database: AppDatabase
) : TransactionRepositoryContract {
    override suspend fun addTransaction(transaction: TransactionEntity): Long = transactionDao.insertTransactionAndUpdateBalance(transaction)

    override suspend fun updateTransaction(transaction: TransactionEntity) = transactionDao.updateTransactionAndUpdateBalance(transaction)

    override suspend fun updateTransactionWithAttachments(
        transaction: TransactionEntity,
        newAttachments: List<TransactionAttachmentEntity>,
        deletedAttachments: List<TransactionAttachmentEntity>
    ) {
        database.withTransaction {
            transactionDao.updateTransactionAndUpdateBalance(transaction)
            if (deletedAttachments.isNotEmpty()) {
                deletedAttachments.forEach { attachmentDao.deleteAttachment(it) }
            }
            if (newAttachments.isNotEmpty()) {
                attachmentDao.insertAttachments(newAttachments)
            }
        }
    }

    override suspend fun getCurrencyAccountById(accountId: Long): CurrencyAccountEntity? =
        transactionDao.getCurrencyAccountById(accountId)

    override suspend fun getCurrencyAccount(personId: Long, currencyCode: String): CurrencyAccountEntity? =
        transactionDao.getCurrencyAccountForPerson(personId, currencyCode)

    override fun observeTransactions(accountId: Long): Flow<List<TransactionEntity>> = transactionDao.observeTransactions(accountId)
    override fun observeArchivedTransactions(): Flow<List<TransactionEntity>> = transactionDao.observeArchivedTransactions()
    override fun observeArchivedTransactionRows(): Flow<List<ArchivedTransactionRow>> = transactionDao.observeArchivedTransactionRows()
    override suspend fun getTransactions(accountId: Long): List<TransactionEntity> = transactionDao.getTransactions(accountId)
    override suspend fun getTransaction(transactionId: Long): TransactionEntity? = transactionDao.getTransaction(transactionId)
    override fun observeBalance(accountId: Long): Flow<Long> = transactionDao.observeBalance(accountId)
    override suspend fun getBalance(accountId: Long): Long = transactionDao.getBalance(accountId)
    override suspend fun deleteTransaction(transaction: TransactionEntity) = transactionDao.deleteTransactionAndUpdateBalance(transaction)
    override suspend fun deleteTransactionById(transactionId: Long) = transactionDao.deleteTransactionByIdAndUpdateBalance(transactionId)
    override suspend fun archiveTransaction(transactionId: Long) = transactionDao.archiveTransactionAndUpdateBalance(transactionId)
    override suspend fun restoreTransaction(transactionId: Long) = transactionDao.restoreTransactionAndUpdateBalance(transactionId)
    override fun observeAttachments(transactionId: Long): Flow<List<TransactionAttachmentEntity>> = attachmentDao.observeAttachments(transactionId)
    override fun observeAttachmentCount(transactionId: Long): Flow<Int> = attachmentDao.observeAttachmentCount(transactionId)
    override suspend fun getAttachments(transactionId: Long): List<TransactionAttachmentEntity> = attachmentDao.getAttachments(transactionId)
    override suspend fun addAttachments(attachments: List<TransactionAttachmentEntity>) { if (attachments.isNotEmpty()) attachmentDao.insertAttachments(attachments) }
    override suspend fun deleteAttachment(attachment: TransactionAttachmentEntity) = attachmentDao.deleteAttachment(attachment)
}
