package com.myaccounts.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myaccounts.app.data.local.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(
        onConflict = OnConflictStrategy.ABORT
    )
    suspend fun insertTransaction(
        transaction: TransactionEntity
    ): Long

    @Query(
        """
        SELECT *
        FROM transactions
        WHERE accountId = :accountId
        ORDER BY transactionDate DESC, id DESC
        """
    )
    fun observeTransactions(
        accountId: Long
    ): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT *
        FROM transactions
        WHERE accountId = :accountId
        ORDER BY transactionDate DESC, id DESC
        """
    )
    suspend fun getTransactions(
        accountId: Long
    ): List<TransactionEntity>

    @Query(
        """
        SELECT *
        FROM transactions
        WHERE id = :transactionId
        LIMIT 1
        """
    )
    suspend fun getTransaction(
        transactionId: Long
    ): TransactionEntity?

    @Delete
    suspend fun deleteTransaction(
        transaction: TransactionEntity
    )

    @Query(
        """
        DELETE FROM transactions
        WHERE id = :transactionId
        """
    )
    suspend fun deleteTransactionById(
        transactionId: Long
    )
}
