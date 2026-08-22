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

    @Query("UPDATE transactions SET isArchived = 1, archivedWithPerson = 0 WHERE id = :transactionId")
    suspend fun archiveTransaction(transactionId: Long)

    @Query("UPDATE transactions SET isArchived = 0, archivedWithPerson = 0 WHERE id = :transactionId")
    suspend fun restoreTransaction(transactionId: Long)

    @Query("UPDATE transactions SET isArchived = 0, archivedWithPerson = 0, accountId = :accountId WHERE id = :transactionId")
    suspend fun restoreTransactionToAccount(transactionId: Long, accountId: Long)

    @Query("SELECT * FROM transactions WHERE isArchived = 1 AND archivedWithPerson = 0 ORDER BY transactionDate DESC, id DESC")
    fun observeArchivedTransactions(): Flow<List<TransactionEntity>>

    @Query("""
        SELECT t.* FROM transactions t
        INNER JOIN currency_accounts ca ON ca.id = t.accountId
        WHERE ca.personId = :personId
          AND t.isArchived = 1
          AND t.archivedWithPerson = 1
        ORDER BY t.transactionDate DESC, t.id DESC
    """)
    fun observeArchivedTransactionsForPerson(personId: Long): Flow<List<TransactionEntity>>

    @Query("""
        SELECT t.id AS transactionId, t.accountId AS accountId, p.name AS personName,
               ca.currencyCode AS currencyCode, t.type AS type, t.amountMinor AS amountMinor,
               t.description AS description, t.transactionDate AS transactionDate
        FROM transactions t
        INNER JOIN currency_accounts ca ON ca.id = t.accountId
        INNER JOIN people p ON p.id = ca.personId
        WHERE t.isArchived = 1
          AND t.archivedWithPerson = 0

        UNION ALL

        SELECT s.transactionId AS transactionId, s.accountId AS accountId, s.personName AS personName,
               s.currencyCode AS currencyCode, s.type AS type, s.amountMinor AS amountMinor,
               s.description AS description, s.transactionDate AS transactionDate
        FROM archived_transaction_snapshots s
        WHERE s.archivedWithPerson = 0
          AND NOT EXISTS (
            SELECT 1 FROM transactions t WHERE t.id = s.transactionId
        )

        ORDER BY transactionDate DESC, transactionId DESC
    """)
    fun observeArchivedTransactionRows(): Flow<List<ArchivedTransactionRow>>

    @Query("""
        INSERT OR REPLACE INTO archived_transaction_snapshots (
            transactionId, accountId, personId, personName, personPhone, personAddress, personNotes,
            currencyCode, type, amountMinor, description, transactionDate, createdAt, archivedWithPerson, archivedAt
        )
        SELECT t.id, t.accountId, p.id, p.name, p.phone, p.address, p.notes,
               ca.currencyCode, t.type, t.amountMinor, t.description, t.transactionDate, t.createdAt,
               t.archivedWithPerson, :archivedAt
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
            currencyCode, type, amountMinor, description, transactionDate, createdAt, archivedWithPerson, archivedAt
        )
        SELECT t.id, t.accountId, p.id, p.name, p.phone, p.address, p.notes,
               ca.currencyCode, t.type, t.amountMinor, t.description, t.transactionDate, t.createdAt,
               1, :archivedAt
        FROM transactions t
        INNER JOIN currency_accounts ca ON ca.id = t.accountId
        INNER JOIN people p ON p.id = ca.personId
        WHERE ca.personId = :personId
    """)
    suspend fun snapshotAllTransactionsForPerson(personId: Long, archivedAt: Long = System.currentTimeMillis())

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

    @Query("UPDATE transactions SET isArchived = 1, archivedWithPerson = 1 WHERE accountId IN (SELECT id FROM currency_accounts WHERE personId = :personId)")
    suspend fun archiveAllTransactionsForPerson(personId: Long)

    @Query("UPDATE people SET isActive = 0 WHERE id = :personId")
    suspend fun archivePerson(personId: Long)

    @Query("UPDATE people SET isActive = 1 WHERE id = :personId")
    suspend fun restorePersonById(personId: Long)

    @Query("UPDATE transactions SET isArchived = 0, archivedWithPerson = 0 WHERE accountId IN (SELECT id FROM currency_accounts WHERE personId = :personId) AND archivedWithPerson = 1")
    suspend fun restoreAllTransactionsForPerson(personId: Long)

    @Query("SELECT * FROM transactions WHERE accountId IN (SELECT id FROM currency_accounts WHERE personId = :personId) AND archivedWithPerson = 1 ORDER BY id")
    suspend fun getArchivedTransactionsForPerson(personId: Long): List<TransactionEntity>

    @Query("DELETE FROM archived_transaction_snapshots WHERE personId = :personId AND archivedWithPerson = 1")
    suspend fun deletePersonArchiveSnapshots(personId: Long)

    @Query("DELETE FROM archived_transaction_attachment_snapshots WHERE transactionId IN (SELECT transactionId FROM archived_transaction_snapshots WHERE personId = :personId AND archivedWithPerson = 1)")
    suspend fun deletePersonArchiveAttachmentSnapshots(personId: Long)

    @Query("SELECT * FROM archived_transaction_snapshots WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getArchivedSnapshot(transactionId: Long): ArchivedTransactionSnapshotEntity?

    @Query("SELECT * FROM archived_transaction_attachment_snapshots WHERE transactionId = :transactionId ORDER BY attachmentId")
    suspend fun getArchivedAttachmentSnapshots(transactionId: Long): List<ArchivedTransactionAttachmentSnapshotEntity>

    @Query("SELECT * FROM people WHERE id = :personId LIMIT 1")
    suspend fun getPersonById(personId: Long): PersonEntity?

    @Query("SELECT * FROM people WHERE name = :name AND isActive = 1 ORDER BY id ASC LIMIT 1")
    suspend fun getActivePersonByName(name: String): PersonEntity?

    @Query("SELECT * FROM currency_accounts WHERE id = :accountId LIMIT 1")
    suspend fun getCurrencyAccountById(accountId: Long): CurrencyAccountEntity?

    @Query("SELECT * FROM currency_accounts WHERE personId = :personId AND currencyCode = :currencyCode LIMIT 1")
    suspend fun getCurrencyAccountForPerson(personId: Long, currencyCode: String): CurrencyAccountEntity?

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
    suspend fun archivePersonAndUpdateBalances(personId: Long) {
        val person = getPersonById(personId) ?: return
        if (!person.isActive) return
        snapshotAllTransactionsForPerson(personId)
        snapshotAllAttachmentsForPerson(personId)
        archiveAllTransactionsForPerson(personId)
        archivePerson(personId)
    }

    @Transaction
    suspend fun restorePersonAndUpdateBalances(personId: Long) {
        val person = getPersonById(personId) ?: return
        val existingActivePerson = getActivePersonByName(person.name)
        if (existingActivePerson != null && existingActivePerson.id != personId) {
            val archivedTransactions = getArchivedTransactionsForPerson(personId)
            archivedTransactions.forEach { transaction ->
                val sourceAccount = getCurrencyAccountById(transaction.accountId) ?: return@forEach
                val targetAccount = getCurrencyAccountForPerson(existingActivePerson.id, sourceAccount.currencyCode)
                    ?: insertCurrencyAccount(
                        CurrencyAccountEntity(
                            personId = existingActivePerson.id,
                            currencyCode = sourceAccount.currencyCode,
                            balanceMinor = 0L,
                            createdAt = sourceAccount.createdAt,
                            updatedAt = sourceAccount.updatedAt
                        )
                    ).let { insertedId -> getCurrencyAccountById(insertedId)!! }
                restoreTransactionToAccount(transaction.id, targetAccount.id)
                deleteArchivedSnapshot(transaction.id)
                deleteArchivedAttachmentSnapshots(transaction.id)
                recalculateBalance(targetAccount.id)
            }
            // The archived duplicate has been consumed into the existing account.
            // Deleting it avoids leaving a second archived copy that could be restored again.
            deletePersonArchiveAttachmentSnapshots(personId)
            deletePersonArchiveSnapshots(personId)
            return
        }

        restorePersonById(personId)
        restoreAllTransactionsForPerson(personId)
        deletePersonArchiveAttachmentSnapshots(personId)
        deletePersonArchiveSnapshots(personId)
        getAccountsForPerson(personId).forEach { recalculateBalance(it.id) }
    }

    @Query("SELECT * FROM currency_accounts WHERE personId = :personId ORDER BY id")
    suspend fun getAccountsForPerson(personId: Long): List<CurrencyAccountEntity>

    @Transaction
    suspend fun restoreTransactionAndUpdateBalance(transactionId: Long) {
        val transaction = getTransaction(transactionId)
        if (transaction != null && transaction.isArchived) {
            val oldAccount = getCurrencyAccountById(transaction.accountId)
            val oldPerson = oldAccount?.let { getPersonById(it.personId) }
            val targetPerson = when {
                oldPerson?.isActive == true -> oldPerson
                oldPerson != null -> getActivePersonByName(oldPerson.name) ?: run {
                    restorePersonById(oldPerson.id)
                    oldPerson.copy(isActive = true)
                }
                else -> null
            }

            if (targetPerson != null && oldAccount != null) {
                val targetAccount = getCurrencyAccountForPerson(targetPerson.id, oldAccount.currencyCode)
                    ?: insertCurrencyAccount(
                        CurrencyAccountEntity(
                            personId = targetPerson.id,
                            currencyCode = oldAccount.currencyCode,
                            balanceMinor = 0L,
                            createdAt = oldAccount.createdAt,
                            updatedAt = oldAccount.updatedAt
                        )
                    ).let { insertedId -> getCurrencyAccountById(insertedId)!! }
                restoreTransactionToAccount(transactionId, targetAccount.id)
                deleteArchivedSnapshot(transactionId)
                deleteArchivedAttachmentSnapshots(transactionId)
                recalculateBalance(targetAccount.id)
                if (oldAccount.id != targetAccount.id) recalculateBalance(oldAccount.id)
            } else {
                restoreTransaction(transactionId)
                deleteArchivedSnapshot(transactionId)
                deleteArchivedAttachmentSnapshots(transactionId)
                recalculateBalance(transaction.accountId)
            }
            return
        }

        val snapshot = getArchivedSnapshot(transactionId) ?: return
        val oldPerson = getPersonById(snapshot.personId)
        val targetPerson = getActivePersonByName(snapshot.personName) ?: when {
            oldPerson != null -> {
                if (!oldPerson.isActive) restorePersonById(oldPerson.id)
                oldPerson.copy(isActive = true)
            }
            else -> {
                val insertedId = insertPerson(
                    PersonEntity(
                        id = snapshot.personId,
                        name = snapshot.personName,
                        phone = snapshot.personPhone,
                        address = snapshot.personAddress,
                        notes = snapshot.personNotes,
                        createdAt = snapshot.createdAt,
                        isActive = true
                    )
                )
                PersonEntity(
                    id = if (insertedId != 0L) insertedId else snapshot.personId,
                    name = snapshot.personName,
                    phone = snapshot.personPhone,
                    address = snapshot.personAddress,
                    notes = snapshot.personNotes,
                    createdAt = snapshot.createdAt,
                    isActive = true
                )
            }
        }

        val targetAccount = getCurrencyAccountForPerson(targetPerson.id, snapshot.currencyCode)
            ?: insertCurrencyAccount(
                CurrencyAccountEntity(
                    personId = targetPerson.id,
                    currencyCode = snapshot.currencyCode,
                    balanceMinor = 0L,
                    createdAt = snapshot.createdAt,
                    updatedAt = snapshot.createdAt
                )
            ).let { insertedId -> getCurrencyAccountById(insertedId)!! }

        insertTransaction(
            TransactionEntity(
                id = snapshot.transactionId,
                accountId = targetAccount.id,
                type = enumValueOf(snapshot.type),
                amountMinor = snapshot.amountMinor,
                description = snapshot.description,
                transactionDate = snapshot.transactionDate,
                createdAt = snapshot.createdAt,
                isArchived = false,
                archivedWithPerson = false
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
        recalculateBalance(targetAccount.id)
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
