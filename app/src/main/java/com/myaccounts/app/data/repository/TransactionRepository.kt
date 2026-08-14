package com.myaccounts.app.data.repository

import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.dao.TransactionDao
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao
) : TransactionRepositoryContract {

    override suspend fun addTransaction(
        transaction: TransactionEntity
    ): Long {

        val transactionId =
            transactionDao.insertTransaction(
                transaction
            )

        val newBalance =
            transactionDao.getBalance(
                transaction.accountId
            )

        transactionDao.updateCurrencyBalance(
            accountId = transaction.accountId,
            balanceMinor = newBalance
        )

        return transactionId
    }

    override suspend fun updateTransaction(
        transaction: TransactionEntity
    ) {

        transactionDao.updateTransaction(
            transaction
        )

        val newBalance =
            transactionDao.getBalance(
                transaction.accountId
            )

        transactionDao.updateCurrencyBalance(
            accountId = transaction.accountId,
            balanceMinor = newBalance
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

        transactionDao.deleteTransaction(
            transaction
        )

        val newBalance =
            transactionDao.getBalance(
                transaction.accountId
            )

        transactionDao.updateCurrencyBalance(
            accountId = transaction.accountId,
            balanceMinor = newBalance
        )
    }

    override suspend fun deleteTransactionById(
        transactionId: Long
    ) {

        val transaction =
            transactionDao.getTransaction(
                transactionId
            )

        transactionDao.deleteTransactionById(
            transactionId
        )

        if (transaction != null) {

            val newBalance =
                transactionDao.getBalance(
                    transaction.accountId
                )

            transactionDao.updateCurrencyBalance(
                accountId = transaction.accountId,
                balanceMinor = newBalance
            )
        }
    }
}
