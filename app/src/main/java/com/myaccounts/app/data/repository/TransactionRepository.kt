package com.myaccounts.app.data.repository

import com.myaccounts.app.data.local.TransactionAttachmentEntity
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.dao.ArchivedTransactionRow
import com.myaccounts.app.data.local.dao.TransactionAttachmentDao
import com.myaccounts.app.data.local.dao.TransactionDao
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val attachmentDao: TransactionAttachmentDao
) : TransactionRepositoryContract {
    override suspend fun addTransaction(transaction: TransactionEntity): Long = transactionDao.insertTransactionAndUpdateBalance(transaction)
    override suspend fun updateTransaction(transaction: TransactionEntity) = transactionDao.updateTransactionAndUpdateBalance(transaction)
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

    override suspend fun restoreTransaction(transactionId: Long) {
        // If the transaction still exists, the person/account may have been soft-deleted
        // while the transaction was archived. Reactivate the owner before restoring the
        // transaction so it becomes visible again in account statements and reports.
        val transaction = transactionDao.getTransaction(transactionId)
        if (transaction != null && transaction.isArchived) {
            val account = transactionDao.getCurrencyAccountById(transaction.accountId)
            if (account != null) {
                val person = transactionDao.getPersonById(account.personId)
                if (person != null && !person.isActive) {
                    transactionDao.restorePersonById(person.id)
                }
            }
        }
        transactionDao.restoreTransactionAndUpdateBalance(transactionId)
    }

    override fun observeAttachments(transactionId: Long): Flow<List<TransactionAttachmentEntity>> = attachmentDao.observeAttachments(transactionId)
    override fun observeAttachmentCount(transactionId: Long): Flow<Int> = attachmentDao.observeAttachmentCount(transactionId)
    override suspend fun getAttachments(transactionId: Long): List<TransactionAttachmentEntity> = attachmentDao.getAttachments(transactionId)
    override suspend fun addAttachments(attachments: List<TransactionAttachmentEntity>) { if (attachments.isNotEmpty()) attachmentDao.insertAttachments(attachments) }
    override suspend fun deleteAttachment(attachment: TransactionAttachmentEntity) = attachmentDao.deleteAttachment(attachment)
}
