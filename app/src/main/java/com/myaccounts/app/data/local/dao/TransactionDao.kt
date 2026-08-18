package com.myaccounts.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.myaccounts.app.data.local.TransactionEntity
import kotlinx.coroutines.flow.Flow

data class ArchivedTransactionRow(
    val transactionId: Long,
    val accountId: Long,
    val personName: String,
    val currencyCode: String,
    val type: String,
    val amountMinor: Long,
    val description: String,
    val transactionDate: Long
)

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND isArchived = 0 ORDER BY transactionDate DESC, id DESC")
    fun observeTransactions(accountId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND isArchived = 0 ORDER BY transactionDate DESC, id DESC")
    suspend fun getTransactions(accountId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getTransaction(transactionId: Long): TransactionEntity?

    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'RECEIVABLE' THEN amountMinor WHEN type = 'PAYABLE' THEN -amountMinor ELSE 0 END),0) FROM transactions WHERE accountId = :accountId AND isArchived = 0")
    fun observeBalance(accountId: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'RECEIVABLE' THEN amountMinor WHEN type = 'PAYABLE' THEN -amountMinor ELSE 0 END),0) FROM transactions WHERE accountId = :accountId AND isArchived = 0")
    suspend fun getBalance(accountId: Long): Long

    @Query("UPDATE currency_accounts SET balanceMinor = :balanceMinor WHERE id = :accountId")
    suspend fun updateCurrencyBalance(accountId: Long, balanceMinor: Long)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransactionById(transactionId: Long)

    @Query("UPDATE transactions SET isArchived = 1 WHERE id = :transactionId")
    suspend fun archiveTransaction(transactionId: Long)

    @Query("UPDATE transactions SET isArchived = 0 WHERE id = :transactionId")
    suspend fun restoreTransaction(transactionId: Long)

    @Query("SELECT * FROM transactions WHERE isArchived = 1 ORDER BY transactionDate DESC, id DESC")
    fun observeArchivedTransactions(): Flow<List<TransactionEntity>>

    @Query("""
        SELECT t.id AS transactionId, t.accountId AS accountId, p.name AS personName,
               ca.currencyCode AS currencyCode, t.type AS type, t.amountMinor AS amountMinor,
               t.description AS description, t.transactionDate AS transactionDate
        FROM transactions t
        INNER JOIN currency_accounts ca ON ca.id = t.accountId
        INNER JOIN people p ON p.id = ca.personId
        WHERE t.isArchived = 1
        ORDER BY t.transactionDate DESC, t.id DESC
    """)
    fun observeArchivedTransactionRows(): Flow<List<ArchivedTransactionRow>>

    @Transaction
    suspend fun insertTransactionAndUpdateBalance(transaction: TransactionEntity): Long {
        val transactionId = insertTransaction(transaction)
        recalculateBalance(transaction.accountId)
        return transactionId
    }

    @Transaction
    suspend fun updateTransactionAndUpdateBalance(transaction: TransactionEntity) {
        val previousTransaction = getTransaction(transaction.id)
        updateTransaction(transaction)
        previousTransaction?.let { if (it.accountId != transaction.accountId) recalculateBalance(it.accountId) }
        recalculateBalance(transaction.accountId)
    }

    @Transaction
    suspend fun archiveTransactionAndUpdateBalance(transactionId: Long) {
        val transaction = getTransaction(transactionId)
        if (transaction != null && !transaction.isArchived) {
            archiveTransaction(transactionId)
            recalculateBalance(transaction.accountId)
        }
    }

    @Transaction
    suspend fun restoreTransactionAndUpdateBalance(transactionId: Long) {
        val transaction = getTransaction(transactionId)
        if (transaction != null && transaction.isArchived) {
            restoreTransaction(transactionId)
            recalculateBalance(transaction.accountId)
        }
    }

    @Transaction
    suspend fun deleteTransactionAndUpdateBalance(transaction: TransactionEntity) {
        deleteTransaction(transaction)
        recalculateBalance(transaction.accountId)
    }

    @Transaction
    suspend fun deleteTransactionByIdAndUpdateBalance(transactionId: Long) {
        val transaction = getTransaction(transactionId)
        deleteTransactionById(transactionId)
        transaction?.let { recalculateBalance(it.accountId) }
    }

    private suspend fun recalculateBalance(accountId: Long) {
        updateCurrencyBalance(accountId, getBalance(accountId))
    }
}
