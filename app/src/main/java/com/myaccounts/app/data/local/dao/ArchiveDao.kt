package com.myaccounts.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ArchiveDao {
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
        WHERE ca.personId = :personId AND t.isArchived = 0
    """)
    suspend fun snapshotActiveTransactionsForPerson(personId: Long, archivedAt: Long)

    @Query("""
        INSERT OR REPLACE INTO archived_transaction_attachment_snapshots (
            attachmentId, transactionId, fileName, mimeType, relativePath, sizeBytes, createdAt
        )
        SELECT a.id, a.transactionId, a.fileName, a.mimeType, a.relativePath, a.sizeBytes, a.createdAt
        FROM transaction_attachments a
        INNER JOIN transactions t ON t.id = a.transactionId
        INNER JOIN currency_accounts ca ON ca.id = t.accountId
        WHERE ca.personId = :personId AND t.isArchived = 0
    """)
    suspend fun snapshotActiveAttachmentsForPerson(personId: Long)

    @Query("""
        UPDATE transactions
        SET isArchived = 1
        WHERE accountId IN (SELECT id FROM currency_accounts WHERE personId = :personId)
          AND isArchived = 0
    """)
    suspend fun archiveActiveTransactionsForPerson(personId: Long)

    @Query("""
        UPDATE currency_accounts
        SET balanceMinor = COALESCE((
            SELECT SUM(CASE WHEN t.type = 'RECEIVABLE' THEN t.amountMinor WHEN t.type = 'PAYABLE' THEN -t.amountMinor ELSE 0 END)
            FROM transactions t
            WHERE t.accountId = currency_accounts.id AND t.isArchived = 0
        ), 0),
        updatedAt = :updatedAt
        WHERE personId = :personId
    """)
    suspend fun recalculateBalancesForPerson(personId: Long, updatedAt: Long)

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
        WHERE ca.personId = :personId
    """)
    suspend fun snapshotAllTransactionsForPerson(personId: Long, archivedAt: Long)

    @Query("""
        INSERT OR REPLACE INTO archived_transaction_attachment_snapshots (
            attachmentId, transactionId, fileName, mimeType, relativePath, sizeBytes, createdAt
        )
        SELECT a.id, a.transactionId, a.fileName, a.mimeType, a.relativePath, a.sizeBytes, a.createdAt
        FROM transaction_attachments a
        INNER JOIN transactions t ON t.id = a.transactionId
        INNER JOIN currency_accounts ca ON ca.id = t.accountId
        WHERE ca.personId = :personId
    """)
    suspend fun snapshotAllAttachmentsForPerson(personId: Long)

    @Query("SELECT archivedAt FROM people WHERE id = :personId LIMIT 1")
    suspend fun getPersonArchivedAt(personId: Long): Long?

    @Query("UPDATE people SET isActive = 0, archivedAt = :archivedAt WHERE id = :personId AND isActive = 1")
    suspend fun archivePerson(personId: Long, archivedAt: Long): Int

    @Query("""
        SELECT t.id FROM transactions t
        INNER JOIN currency_accounts ca ON ca.id = t.accountId
        WHERE ca.personId = :personId AND t.isArchived = 1
          AND EXISTS (
              SELECT 1 FROM archived_transaction_snapshots s
              WHERE s.transactionId = t.id AND s.archivedAt = :archivedAt
          )
    """)
    suspend fun getTransactionsArchivedWithPerson(personId: Long, archivedAt: Long): List<Long>

    @Query("UPDATE transactions SET isArchived = 0 WHERE id = :transactionId")
    suspend fun restoreTransaction(transactionId: Long)

    @Query("DELETE FROM archived_transaction_snapshots WHERE transactionId = :transactionId")
    suspend fun deleteTransactionSnapshot(transactionId: Long)

    @Query("DELETE FROM archived_transaction_attachment_snapshots WHERE transactionId = :transactionId")
    suspend fun deleteTransactionAttachmentSnapshots(transactionId: Long)

    @Query("UPDATE people SET isActive = 1, archivedAt = NULL WHERE id = :personId")
    suspend fun restorePerson(personId: Long): Int

    @Query("""
        SELECT t.id FROM transactions t
        INNER JOIN currency_accounts ca ON ca.id = t.accountId
        WHERE ca.personId = :personId
    """)
    suspend fun getPersonTransactionIds(personId: Long): List<Long>

    @Query("SELECT id FROM transactions WHERE isArchived = 1")
    suspend fun getArchivedTransactionIds(): List<Long>

    @Query("SELECT transactionId FROM archived_transaction_snapshots")
    suspend fun getSnapshotTransactionIds(): List<Long>

    @Query("DELETE FROM archived_transaction_attachment_snapshots")
    suspend fun clearAttachmentSnapshots()

    @Query("DELETE FROM archived_transaction_snapshots")
    suspend fun clearTransactionSnapshots()

    @Query("DELETE FROM people WHERE isActive = 0")
    suspend fun clearArchivedPeople()

    @Query("DELETE FROM transactions WHERE isArchived = 1")
    suspend fun clearArchivedTransactions()

    @Transaction
    suspend fun clearArchive(): List<Long> {
        val ids = (getArchivedTransactionIds() + getSnapshotTransactionIds()).distinct()
        clearAttachmentSnapshots()
        clearTransactionSnapshots()
        clearArchivedPeople()
        clearArchivedTransactions()
        return ids
    }
}
