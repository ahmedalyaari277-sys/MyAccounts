package com.myaccounts.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.myaccounts.app.data.local.ArchivedTransactionAttachmentSnapshotEntity
import com.myaccounts.app.data.local.ArchivedTransactionSnapshotEntity
import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.PersonEntity
import com.myaccounts.app.data.local.TransactionAttachmentEntity
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

enum class RestoreTransactionResult {
    RESTORED,
    ACCOUNT_ARCHIVED,
    ACCOUNT_DELETED,
    ACCOUNT_REPLACED,
    OWNER_DELETED
}

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

        UNION ALL

        SELECT s.transactionId AS transactionId, s.accountId AS accountId, s.personName AS personName,
               s.currencyCode AS currencyCode, s.type AS type, s.amountMinor AS amountMinor,
               s.description AS description, s.transactionDate AS transactionDate
        FROM archived_transaction_snapshots s
        WHERE NOT EXISTS (
            SELECT 1 FROM transactions t WHERE t.id = s.transactionId
        )

        ORDER BY transactionDate DESC, transactionId DESC
    """)
    fun observeArchivedTransactionRows(): Flow<List<ArchivedTransactionRow>>

    @Query("""
        INSERT OR REPLACE INTO archived_transaction_snapshots (
            transactionId, accountId, personId, personName, personPhone, personAddress, personNotes,
            currencyCode, type, amountMinor, description, transactionDate, createdAt, archivedAt
        )
        SELECT t.id, t.accountId, p.id, p.name, p.phone, p.address, p.notes,
               ca.currencyCode, t.type, t.amountMinor, t.description, t.transactionDate, t.createdAt, :archivedAt
        FROM transactions t
        INNER JOIN currency_accounts ca ON ca.id = t.accountId
        INNER JOIN people p ON p.id = ca.personId
        WHERE t.id = :transactionId
    """)
    suspend fun snapshotArchivedTransaction(transactionId: Long, archivedAt: Long = System.currentTimeMillis())

    @Query("""
        INSERT OR REPLACE INTO archived_transaction_attachment_snapshots (
            attachmentId, transactionId, fileName, mimeType, relativePath, sizeBytes, createdAt
        )
        SELECT id, transactionId, fileName, mimeType, relativePath, sizeBytes, createdAt
        FROM transaction_attachments
        WHERE transactionId = :transactionId
    """)
    suspend fun snapshotTransactionAttachments(transactionId: Long)

    @Query("""
        INSERT OR REPLACE INTO archived_transaction_snapshots (
            transactionId, accountId, personId, personName, personPhone, personAddress, personNotes,
            currencyCode, type, amountMinor, description, transactionDate, createdAt, archivedAt
        )
        SELECT t.id, t.accountId, p.id, p.name, p.phone, p.address, p.notes,
               ca.currencyCode, t.type, t.amountMinor, t.description, t.transactionDate, t.createdAt, :archivedAt
        FROM transactions t
        INNER JOIN currency_accounts ca ON ca.id = t.accountId
        INNER JOIN people p ON p.id = ca.personId
        WHERE ca.personId = :personId AND t.isArchived = 1
    """)
    suspend fun snapshotArchivedTransactionsForPerson(personId: Long, archivedAt: Long = System.currentTimeMillis())

    @Query("""
        INSERT OR REPLACE INTO archived_transaction_attachment_snapshots (
            attachmentId, transactionId, fileName, mimeType, relativePath, sizeBytes, createdAt
        )
        SELECT a.id, a.transactionId, a.fileName, a.mimeType, a.relativePath, a.sizeBytes, a.createdAt
        FROM transaction_attachments a
        INNER JOIN transactions t ON t.id = a.transactionId
        INNER JOIN currency_accounts ca ON ca.id = t.accountId
        WHERE ca.personId = :personId AND t.isArchived = 1
    """)
    suspend fun snapshotArchivedAttachmentsForPerson(personId: Long)

    @Query("SELECT * FROM archived_transaction_snapshots WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getArchivedSnapshot(transactionId: Long): ArchivedTransactionSnapshotEntity?

    @Query("SELECT * FROM archived_transaction_attachment_snapshots WHERE transactionId = :transactionId ORDER BY attachmentId")
    suspend fun getArchivedAttachmentSnapshots(transactionId: Long): List<ArchivedTransactionAttachmentSnapshotEntity>

    @Query("SELECT * FROM people WHERE id = :personId LIMIT 1")
    suspend fun getPersonById(personId: Long): PersonEntity?

    @Query("SELECT * FROM currency_accounts WHERE id = :accountId LIMIT 1")
    suspend fun getCurrencyAccountById(accountId: Long): CurrencyAccountEntity?

    @Query("SELECT * FROM currency_accounts WHERE personId = :personId AND currencyCode = :currencyCode LIMIT 1")
    suspend fun getCurrencyAccountForPerson(personId: Long, currencyCode: String): CurrencyAccountEntity?

    @Query("SELECT p.name FROM people p INNER JOIN currency_accounts ca ON ca.personId = p.id WHERE ca.id = :accountId LIMIT 1")
    suspend fun getPersonNameForAccount(accountId: Long): String?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPerson(person: PersonEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCurrencyAccount(account: CurrencyAccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactionAttachment(attachment: TransactionAttachmentEntity): Long

    @Query("DELETE FROM archived_transaction_snapshots WHERE transactionId = :transactionId")
    suspend fun deleteArchivedSnapshot(transactionId: Long)

    @Query("DELETE FROM archived_transaction_attachment_snapshots WHERE transactionId = :transactionId")
    suspend fun deleteArchivedAttachmentSnapshots(transactionId: Long)

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
            snapshotArchivedTransaction(transactionId)
            snapshotTransactionAttachments(transactionId)
            archiveTransaction(transactionId)
            recalculateBalance(transaction.accountId)
        }
    }

    @Transaction
    suspend fun restoreTransactionAndUpdateBalance(transactionId: Long): RestoreTransactionResult {
        val transaction = getTransaction(transactionId)
        if (transaction != null && transaction.isArchived) {
            val account = getCurrencyAccountById(transaction.accountId)
            val person = account?.let { getPersonById(it.personId) }
            if (account == null || person == null) return RestoreTransactionResult.ACCOUNT_DELETED
            if (!person.isActive) return RestoreTransactionResult.ACCOUNT_ARCHIVED

            restoreTransaction(transactionId)
            deleteArchivedSnapshot(transactionId)
            deleteArchivedAttachmentSnapshots(transactionId)
            recalculateBalance(transaction.accountId)
            return RestoreTransactionResult.RESTORED
        }

        val snapshot = getArchivedSnapshot(transactionId) ?: return RestoreTransactionResult.ACCOUNT_DELETED
        val person = getPersonById(snapshot.personId) ?: return RestoreTransactionResult.OWNER_DELETED
        if (!person.isActive) return RestoreTransactionResult.ACCOUNT_ARCHIVED

        val account = getCurrencyAccountById(snapshot.accountId)
        if (account == null) {
            val replacement = getCurrencyAccountForPerson(snapshot.personId, snapshot.currencyCode)
            return if (replacement != null) RestoreTransactionResult.ACCOUNT_REPLACED else RestoreTransactionResult.ACCOUNT_DELETED
        }
        if (account.personId != snapshot.personId || account.currencyCode != snapshot.currencyCode) {
            return RestoreTransactionResult.ACCOUNT_REPLACED
        }

        insertTransaction(
            TransactionEntity(
                id = snapshot.transactionId,
                accountId = snapshot.accountId,
                type = enumValueOf(snapshot.type),
                amountMinor = snapshot.amountMinor,
                description = snapshot.description,
                transactionDate = snapshot.transactionDate,
                createdAt = snapshot.createdAt,
                isArchived = false
            )
        )

        getArchivedAttachmentSnapshots(transactionId).forEach { attachment ->
            insertTransactionAttachment(
                TransactionAttachmentEntity(
                    id = attachment.attachmentId,
                    transactionId = attachment.transactionId,
                    fileName = attachment.fileName,
                    mimeType = attachment.mimeType,
                    relativePath = attachment.relativePath,
                    sizeBytes = attachment.sizeBytes,
                    createdAt = attachment.createdAt
                )
            )
        }

        deleteArchivedSnapshot(transactionId)
        deleteArchivedAttachmentSnapshots(transactionId)
        recalculateBalance(snapshot.accountId)
        return RestoreTransactionResult.RESTORED
    }

    @Transaction
    suspend fun deleteTransactionAndUpdateBalance(transaction: TransactionEntity) {
        deleteArchivedSnapshot(transaction.id)
        deleteArchivedAttachmentSnapshots(transaction.id)
        deleteTransaction(transaction)
        recalculateBalance(transaction.accountId)
    }

    @Transaction
    suspend fun deleteTransactionByIdAndUpdateBalance(transactionId: Long) {
        val transaction = getTransaction(transactionId)
        deleteArchivedSnapshot(transactionId)
        deleteArchivedAttachmentSnapshots(transactionId)
        deleteTransactionById(transactionId)
        transaction?.let { recalculateBalance(it.accountId) }
    }

    private suspend fun recalculateBalance(accountId: Long) {
        updateCurrencyBalance(accountId, getBalance(accountId))
    }
}
