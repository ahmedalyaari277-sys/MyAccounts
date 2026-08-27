package com.myaccounts.app.data.repository

import androidx.room.withTransaction
import com.myaccounts.app.data.local.AppDatabase
import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.TransactionAttachmentEntity
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.dao.TransactionAttachmentDao
import com.myaccounts.app.data.local.dao.TransactionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val attachmentDao: TransactionAttachmentDao,
    private val database: AppDatabase
) : TransactionRepositoryContract {
    override suspend fun addTransaction(transaction: TransactionEntity): Long = withContext(Dispatchers.IO) {
        transactionDao.insertTransactionAndUpdateBalance(transaction)
    }

    override suspend fun updateTransaction(transaction: TransactionEntity) = transactionDao.updateTransactionAndUpdateBalance(transaction)

    override suspend fun updateTransactionWithAttachments(
        transaction: TransactionEntity,
        newAttachments: List<TransactionAttachmentEntity>,
        deletedAttachments: List<TransactionAttachmentEntity>
    ) {
        database.withTransaction {
            transactionDao.updateTransactionAndUpdateBalance(transaction)
            deletedAttachments.forEach { attachmentDao.deleteAttachment(it) }
            if (newAttachments.isNotEmpty()) attachmentDao.insertAttachments(newAttachments)
        }
    }

    override suspend fun getCurrencyAccountById(accountId: Long): CurrencyAccountEntity? = transactionDao.getCurrencyAccountById(accountId)

    override suspend fun getCurrencyAccount(personId: Long, currencyCode: String): CurrencyAccountEntity? =
        transactionDao.getCurrencyAccountForPerson(personId, currencyCode)

    override suspend fun getPersonNameForAccount(accountId: Long): String? =
        transactionDao.getPersonNameForAccount(accountId)

    override fun observeTransactions(accountId: Long): Flow<List<TransactionEntity>> = transactionDao.observeTransactions(accountId)
    override suspend fun getTransactions(accountId: Long): List<TransactionEntity> = transactionDao.getTransactions(accountId)
    override suspend fun getTransaction(transactionId: Long): TransactionEntity? = transactionDao.getTransaction(transactionId)
    override fun observeBalance(accountId: Long): Flow<Long> = transactionDao.observeBalance(accountId)
    override suspend fun getBalance(accountId: Long): Long = transactionDao.getBalance(accountId)
    override suspend fun deleteTransaction(transaction: TransactionEntity) = transactionDao.deleteTransactionAndUpdateBalance(transaction)
    override suspend fun deleteTransactionById(transactionId: Long) = transactionDao.deleteTransactionByIdAndUpdateBalance(transactionId)
    override fun observeAttachments(transactionId: Long): Flow<List<TransactionAttachmentEntity>> = attachmentDao.observeAttachments(transactionId)
    override fun observeAttachmentCount(transactionId: Long): Flow<Int> = attachmentDao.observeAttachmentCount(transactionId)
    override suspend fun getAttachments(transactionId: Long): List<TransactionAttachmentEntity> = attachmentDao.getAttachments(transactionId)
    override suspend fun addAttachments(attachments: List<TransactionAttachmentEntity>) { if (attachments.isNotEmpty()) attachmentDao.insertAttachments(attachments) }
    override suspend fun deleteAttachment(attachment: TransactionAttachmentEntity) = attachmentDao.deleteAttachment(attachment)
}
