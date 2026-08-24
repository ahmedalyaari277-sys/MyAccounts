package com.myaccounts.app.archive

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myaccounts.app.data.local.AppDatabase
import com.myaccounts.app.data.local.PersonEntity
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.TransactionType
import com.myaccounts.app.data.local.dao.LedgerDao
import com.myaccounts.app.data.local.dao.TransactionDao
import com.myaccounts.app.data.repository.LedgerRepository
import kotlinx.coroutines.runBlocking
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
class ArchiveRestoreNameNormalizationTest {
    private lateinit var database: AppDatabase
    private lateinit var ledger: LedgerDao
    private lateinit var transactions: TransactionDao
    private lateinit var repository: LedgerRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ledger = database.ledgerDao()
        transactions = database.transactionDao()
        repository = LedgerRepository(
            dao = ledger,
            transactionDao = transactions,
            transactionAttachmentDao = database.transactionAttachmentDao(),
            database = database,
            context = context
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun restoringArchivedAccountMatchesExistingActiveNameIgnoringTrimAndCase() = runBlocking {
        val archivedPersonId = createPerson("  Ahmed  ")
        val archivedAccountId = accountId(archivedPersonId)
        val archivedTransactionId = insertTransaction(archivedAccountId, 10000L)
        repository.deletePerson(archivedPersonId)

        val existingPersonId = createPerson("ahmed")
        val existingAccountId = accountId(existingPersonId)

        repository.restorePerson(archivedPersonId)

        assertNull(transactions.getPersonById(archivedPersonId))
        assertNotNull(transactions.getPersonById(existingPersonId))
        assertEquals(existingAccountId, transactions.getTransaction(archivedTransactionId)?.accountId)
        assertFalse(transactions.getTransaction(archivedTransactionId)!!.isArchived)
        assertEquals(10000L, transactions.getBalance(existingAccountId))
    }

    @Test
    fun restoringArchivedTransactionMatchesExistingActiveNameIgnoringTrimAndCase() = runBlocking {
        val archivedPersonId = createPerson("  علي  ")
        val archivedAccountId = accountId(archivedPersonId)
        val archivedTransactionId = insertTransaction(archivedAccountId, 15000L)
        transactions.archiveTransactionAndUpdateBalance(archivedTransactionId)
        repository.permanentlyDeletePerson(archivedPersonId)

        val existingPersonId = createPerson("علي")
        val existingAccountId = accountId(existingPersonId)

        transactions.restoreTransactionAndUpdateBalance(archivedTransactionId)

        assertNull(transactions.getPersonById(archivedPersonId))
        assertEquals(existingAccountId, transactions.getTransaction(archivedTransactionId)?.accountId)
        assertFalse(transactions.getTransaction(archivedTransactionId)!!.isArchived)
        assertEquals(15000L, transactions.getBalance(existingAccountId))
        assertTrue(ledger.findActivePersonIdByName("  علي  ") == existingPersonId)
    }

    private suspend fun createPerson(name: String): Long =
        ledger.insertPersonWithCurrencyAccounts(PersonEntity(name = name), listOf("YER", "SAR", "USD"))

    private suspend fun accountId(personId: Long): Long =
        requireNotNull(ledger.getCurrencyAccount(personId, "YER")).id

    private suspend fun insertTransaction(accountId: Long, amount: Long): Long =
        transactions.insertTransactionAndUpdateBalance(
            TransactionEntity(
                accountId = accountId,
                type = TransactionType.RECEIVABLE,
                amountMinor = amount,
                transactionDate = System.currentTimeMillis()
            )
        )
}
