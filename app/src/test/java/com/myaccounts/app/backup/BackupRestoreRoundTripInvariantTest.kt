package com.myaccounts.app.backup

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.myaccounts.app.data.local.AppDatabase
import com.myaccounts.app.data.local.PersonEntity
import com.myaccounts.app.data.local.TransactionAttachmentEntity
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.TransactionType
import com.myaccounts.app.data.local.dao.LedgerDao
import com.myaccounts.app.data.local.dao.TransactionDao
import com.myaccounts.app.data.repository.LedgerRepository
import com.myaccounts.app.util.DatabaseBackupManager
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupRestoreRoundTripInvariantTest {
    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase
    private lateinit var sourceLedger: LedgerDao
    private lateinit var sourceTransactions: TransactionDao
    private lateinit var targetTransactions: TransactionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sourceLedger = source.ledgerDao()
        sourceTransactions = source.transactionDao()
        targetTransactions = target.transactionDao()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    @Test
    fun archiveAwareBackupRoundTripPreservesArchiveOwnershipAndSharedAttachmentSnapshots() = runBlocking {
        val personId = sourceLedger.insertPersonWithCurrencyAccounts(
            PersonEntity(name = "اختبار النسخ", phone = "777", address = "صنعاء"),
            listOf("YER", "SAR", "USD")
        )
        val accountId = requireNotNull(sourceLedger.getCurrencyAccount(personId, "YER")).id

        val individuallyArchived = insertTransaction(accountId, 15000L, "عملية مؤرشفة منفردة")
        val individuallyArchivedAttachment = sourceTransactions.insertTransactionAttachment(
            TransactionAttachmentEntity(
                transactionId = individuallyArchived,
                fileName = "individual.pdf",
                mimeType = "application/pdf",
                relativePath = "attachments/individual.pdf",
                sizeBytes = 12L,
                createdAt = 10L
            )
        )
        sourceTransactions.archiveTransactionAndUpdateBalance(individuallyArchived)

        val archivedWithPerson = insertTransaction(accountId, 25000L, "عملية مع الحساب")
        val archivedWithPersonAttachment = sourceTransactions.insertTransactionAttachment(
            TransactionAttachmentEntity(
                transactionId = archivedWithPerson,
                fileName = "person.pdf",
                mimeType = "application/pdf",
                relativePath = "attachments/person.pdf",
                sizeBytes = 24L,
                createdAt = 20L
            )
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        LedgerRepository(
            dao = sourceLedger,
            transactionDao = sourceTransactions,
            transactionAttachmentDao = source.transactionAttachmentDao(),
            database = source,
            context = context
        ).deletePerson(personId)

        val backup = invokeBuildBackupJson(source.openHelper.readableDatabase)
        invokeValidateBackup(backup)

        val liveAttachments = backup.getJSONArray("attachments")
        val archivedAttachmentSnapshots = backup.getJSONArray("archivedTransactionAttachmentSnapshots")
        assertEquals(2, liveAttachments.length())
        assertEquals(2, archivedAttachmentSnapshots.length())
        assertEquals(individuallyArchivedAttachment, liveAttachments.getJSONObject(0).getLong("id"))
        assertEquals(archivedWithPersonAttachment, archivedAttachmentSnapshots.getJSONObject(1).getLong("attachmentId"))

        target.runInTransaction {
            invokeRestoreIntoDatabase(target.openHelper.writableDatabase, backup)
        }

        assertEquals(1, countRows(target.openHelper.readableDatabase, "people"))
        assertEquals(3, countRows(target.openHelper.readableDatabase, "currency_accounts"))
        assertEquals(2, countRows(target.openHelper.readableDatabase, "transactions"))
        assertEquals(2, countRows(target.openHelper.readableDatabase, "transaction_attachments"))
        assertEquals(2, countRows(target.openHelper.readableDatabase, "archived_transaction_snapshots"))
        assertEquals(2, countRows(target.openHelper.readableDatabase, "archived_transaction_attachment_snapshots"))

        val restoredPerson = targetTransactions.getPersonById(personId)
        assertNotNull(restoredPerson)
        assertFalse(restoredPerson!!.isActive)

        val restoredIndividual = targetTransactions.getTransaction(individuallyArchived)
        val restoredWithPerson = targetTransactions.getTransaction(archivedWithPerson)
        assertTrue(restoredIndividual!!.isArchived)
        assertFalse(restoredIndividual.archivedWithPerson)
        assertTrue(restoredWithPerson!!.isArchived)
        assertTrue(restoredWithPerson.archivedWithPerson)
        assertEquals(0L, targetTransactions.getBalance(accountId))

        targetTransactions.restoreTransactionAndUpdateBalance(individuallyArchived)
        assertTrue(targetTransactions.getPersonById(personId)!!.isActive)
        assertFalse(targetTransactions.getTransaction(individuallyArchived)!!.isArchived)
        assertTrue(targetTransactions.getTransaction(archivedWithPerson)!!.isArchived)
        assertTrue(targetTransactions.getTransaction(archivedWithPerson)!!.archivedWithPerson)
        assertEquals(15000L, targetTransactions.getBalance(accountId))
        assertNull(targetTransactions.getArchivedSnapshot(individuallyArchived))
        assertNotNull(targetTransactions.getArchivedSnapshot(archivedWithPerson))
    }

    @Test
    fun legacyV3RestoreDefaultsArchiveOwnershipToFalseWithoutNullConstraintFailure() = runBlocking {
        val backup = JSONObject()
            .put("backupType", "myaccounts_full_backup")
            .put("formatVersion", 3)
            .put("createdAt", 1L)
            .put("people", JSONArray().put(
                JSONObject()
                    .put("id", 1L)
                    .put("name", "نسخة قديمة")
                    .put("phone", "")
                    .put("address", "")
                    .put("notes", "")
                    .put("createdAt", 1L)
                    .put("isActive", false)
            ))
            .put("currencyAccounts", JSONArray().put(
                JSONObject()
                    .put("id", 10L)
                    .put("personId", 1L)
                    .put("currencyCode", "YER")
                    .put("balanceMinor", 0L)
                    .put("createdAt", 1L)
                    .put("updatedAt", 1L)
            ))
            .put("transactions", JSONArray().put(
                JSONObject()
                    .put("id", 100L)
                    .put("accountId", 10L)
                    .put("type", "RECEIVABLE")
                    .put("amountMinor", 5000L)
                    .put("description", "قديم")
                    .put("transactionDate", 1L)
                    .put("createdAt", 1L)
                    .put("isArchived", true)
            ))
            .put("attachments", JSONArray())

        invokeValidateBackup(backup)
        target.runInTransaction {
            invokeRestoreIntoDatabase(target.openHelper.writableDatabase, backup)
        }

        val restored = targetTransactions.getTransaction(100L)
        assertNotNull(restored)
        assertTrue(restored!!.isArchived)
        assertFalse(restored.archivedWithPerson)
        assertFalse(targetTransactions.getPersonById(1L)!!.isActive)
    }

    @Test
    fun archiveSnapshotCannotReferenceMissingTransaction() = runBlocking {
        val personId = sourceLedger.insertPersonWithCurrencyAccounts(
            PersonEntity(name = "اختبار تحقق النسخة", phone = "", address = ""),
            listOf("YER")
        )
        val accountId = requireNotNull(sourceLedger.getCurrencyAccount(personId, "YER")).id
        val backup = invokeBuildBackupJson(source.openHelper.readableDatabase)
        backup.getJSONArray("archivedTransactionSnapshots").put(
            JSONObject()
                .put("transactionId", 999L)
                .put("accountId", accountId)
                .put("personId", personId)
                .put("personName", "اختبار تحقق النسخة")
                .put("personPhone", "")
                .put("personAddress", "")
                .put("personNotes", "")
                .put("currencyCode", "YER")
                .put("type", "RECEIVABLE")
                .put("amountMinor", 100L)
                .put("description", "يتيم")
                .put("transactionDate", 1L)
                .put("createdAt", 1L)
                .put("archivedWithPerson", false)
                .put("archivedAt", 1L)
        )

        val error = runCatching { invokeValidateBackup(backup) }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().contains("نسخة أرشيف العملية مرتبطة بعملية غير موجودة"))
    }

    private suspend fun insertTransaction(accountId: Long, amount: Long, description: String): Long =
        sourceTransactions.insertTransactionAndUpdateBalance(
            TransactionEntity(
                accountId = accountId,
                type = TransactionType.RECEIVABLE,
                amountMinor = amount,
                description = description,
                transactionDate = 1L,
                createdAt = 1L
            )
        )

    private fun countRows(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun invokeBuildBackupJson(db: SupportSQLiteDatabase): JSONObject =
        invokePrivate("buildConsistentBackupJson", db) as JSONObject

    private fun invokeValidateBackup(backup: JSONObject) {
        invokePrivate("validateBackup", backup)
    }

    private fun invokeRestoreIntoDatabase(db: SupportSQLiteDatabase, backup: JSONObject) {
        invokePrivate("restoreIntoDatabase", db, backup)
    }

    private fun invokePrivate(name: String, vararg args: Any): Any? {
        val method = DatabaseBackupManager::class.java.declaredMethods.firstOrNull { candidate ->
            candidate.name == name &&
                candidate.parameterTypes.size == args.size &&
                candidate.parameterTypes.withIndex().all { (index, expected) -> expected.isInstance(args[index]) }
        } ?: error("Private method not found: $name")
        method.isAccessible = true
        return try {
            method.invoke(DatabaseBackupManager, *args)
        } catch (error: java.lang.reflect.InvocationTargetException) {
            throw (error.targetException ?: error)
        }
    }
}
