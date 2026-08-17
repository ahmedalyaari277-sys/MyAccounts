package com.myaccounts.app.data.repository

import com.myaccounts.app.data.local.TransactionAttachmentEntity
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.dao.TransactionAttachmentDao
import com.myaccounts.app.data.local.dao.TransactionDao
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val attachmentDao: TransactionAttachmentDao
) : TransactionRepositoryContract {

    override suspend fun addTransaction(
        transaction: TransactionEntity
    ): Long {
        return transactionDao.insertTransactionAndUpdateBalance(
            transaction
        )
    }

    override suspend fun updateTransaction(
        transaction: TransactionEntity
    ) {
        transactionDao.updateTransactionAndUpdateBalance(
            transaction
        )
    }

    override fun observeTransactions(
        accountId: Long
    ): Flow<List<TransactionEntity>> {
        return transactionDao.observeTransactions(
            accountId
        )
    }

    override suspend fun getTransactions(
        accountId: Long
    ): List<TransactionEntity> {
        return transactionDao.getTransactions(
            accountId
        )
    }

    override suspend fun getTransaction(
        transactionId: Long
    ): TransactionEntity? {
        return transactionDao.getTransaction(
            transactionId
        )
    }

    override fun observeBalance(
        accountId: Long
    ): Flow<Long> {
        return transactionDao.observeBalance(
            accountId
        )
    }

    override suspend fun getBalance(
        accountId: Long
    ): Long {
        return transactionDao.getBalance(
            accountId
        )
    }

    override suspend fun deleteTransaction(
        transaction: TransactionEntity
    ) {
        transactionDao.deleteTransactionAndUpdateBalance(
            transaction
        )
    }

    override suspend fun deleteTransactionById(
        transactionId: Long
    ) {
        transactionDao.deleteTransactionByIdAndUpdateBalance(
            transactionId
        )
    }

    override fun observeAttachments(
        transactionId: Long
    ): Flow<List<TransactionAttachmentEntity>> {
        return attachmentDao.observeAttachments(transactionId)
    }

    override fun observeAttachmentCount(
        transactionId: Long
    ): Flow<Int> {
        return attachmentDao.observeAttachmentCount(transactionId)
    }

    override suspend fun getAttachments(
        transactionId: Long
    ): List<TransactionAttachmentEntity> {
        return attachmentDao.getAttachments(transactionId)
    }

    override suspend fun addAttachments(
        attachments: List<TransactionAttachmentEntity>
    ) {
        if (attachments.isNotEmpty()) {
            attachmentDao.insertAttachments(attachments)
        }
    }

    override suspend fun deleteAttachment(
        attachment: TransactionAttachmentEntity
    ) {
        attachmentDao.deleteAttachment(attachment)
    }
}
