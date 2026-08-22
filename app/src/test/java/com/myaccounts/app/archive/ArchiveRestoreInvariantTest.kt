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
class ArchiveRestoreInvariantTest {
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
        repository = LedgerRepository(ledger, transactions, database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun restoringOneTransactionFromArchivedAccountDoesNotRestoreTheOthers() = runBlocking {
        val personId = createPerson("أحمد")
        val accountId = accountId(personId, "YER")
        val first = insertTransaction(accountId, 10000)
        val second = insertTransaction(accountId, 20000)

        repository.deletePerson(personId)
        transactions.restoreTransactionAndUpdateBalance(first)

        assertTrue(transactions.getPersonById(personId)?.isActive == true)
        assertTrue(transactions.getTransaction(first)?.isArchived == false)
        assertTrue(transactions.getTransaction(second)?.isArchived == true)
        assertTrue(transactions.getTransaction(second)?.archivedWithPerson == true)
    }

    @Test
    fun restoringArchivedAccountAfterOneTransactionWasRestoredRestoresOnlyRemainingTransactions() = runBlocking {
        val personId = createPerson("محمد")
        val accountId = accountId(personId, "YER")
        val first = insertTransaction(accountId, 10000)
        val second = insertTransaction(accountId, 20000)

        repository.deletePerson(personId)
        transactions.restoreTransactionAndUpdateBalance(first)
        repository.restorePerson(personId)

        assertTrue(transactions.getPersonById(personId)?.isActive == true)
        assertTrue(transactions.getTransaction(first)?.isArchived == false)
        assertTrue(transactions.getTransaction(second)?.isArchived == false)
        assertEquals(30000L, transactions.getBalance(accountId))
    }

    @Test
    fun restoringIndividuallyArchivedTransactionAfterPermanentPersonDeletionRecreatesOnlyThatTransactionAndPerson() = runBlocking {
        val personId = createPerson("سعيد")
        val accountId = accountId(personId, "YER")
        val archivedTransactionId = insertTransaction(accountId, 15000)
        val liveTransactionId = insertTransaction(accountId, 5000)

        transactions.archiveTransactionAndUpdateBalance(archivedTransactionId)
        repository.permanentlyDeletePerson(personId)

        assertNull(transactions.getPersonById(personId))
        assertNotNull(transactions.getArchivedSnapshot(archivedTransactionId))
        assertNull(transactions.getTransaction(liveTransactionId))

        transactions.restoreTransactionAndUpdateBalance(archivedTransactionId)

        val restoredPerson = transactions.getPersonById(personId)
        assertNotNull(restoredPerson)
        assertTrue(restoredPerson?.isActive == true)
        val restored = transactions.getTransaction(archivedTransactionId)
        assertNotNull(restored)
        assertFalse(restored!!.isArchived)
        assertNull(transactions.getTransaction(liveTransactionId))
        assertNull(transactions.getArchivedSnapshot(archivedTransactionId))
    }

    @Test
    fun restoringArchivedAccountIntoExistingSameNameAccountMergesTransactionsWithoutCreatingDuplicatePerson() = runBlocking {
        val archivedPersonId = createPerson("علي")
        val archivedAccountId = accountId(archivedPersonId, "YER")
        val archivedTransactionId = insertTransaction(archivedAccountId, 25000)
        repository.deletePerson(archivedPersonId)

        val existingPersonId = createPerson("علي")
        val existingAccountId = accountId(existingPersonId, "YER")
        val existingTransactionId = insertTransaction(existingAccountId, 7000)

        repository.restorePerson(archivedPersonId)

        assertNotNull(transactions.getPersonById(existingPersonId))
        assertNull(transactions.getPersonById(archivedPersonId))
        assertTrue(transactions.getTransaction(archivedTransactionId)?.isArchived == false)
        assertTrue(transactions.getTransaction(existingTransactionId)?.isArchived == false)
        assertEquals(existingAccountId, transactions.getTransaction(archivedTransactionId)?.accountId)
        assertEquals(32000L, transactions.getBalance(existingAccountId))
    }

    @Test
    fun restoringMultipleArchivedTransactionsIntoExistingSameNameAccountDoesNotDuplicateThem() = runBlocking {
        val archivedPersonId = createPerson("حسن")
        val archivedAccountId = accountId(archivedPersonId, "USD")
        val first = insertTransaction(archivedAccountId, 10000)
        val second = insertTransaction(archivedAccountId, 20000)
        repository.deletePerson(archivedPersonId)

        val existingPersonId = createPerson("حسن")
        val existingAccountId = accountId(existingPersonId, "USD")

        repository.restorePerson(archivedPersonId)
        repository.restorePerson(archivedPersonId)

        assertNull(transactions.getPersonById(archivedPersonId))
        assertEquals(existingAccountId, transactions.getTransaction(first)?.accountId)
        assertEquals(existingAccountId, transactions.getTransaction(second)?.accountId)
        assertFalse(transactions.getTransaction(first)!!.isArchived)
        assertFalse(transactions.getTransaction(second)!!.isArchived)
        assertEquals(30000L, transactions.getBalance(existingAccountId))
    }

    private suspend fun createPerson(name: String): Long =
        ledger.insertPersonWithCurrencyAccounts(PersonEntity(name = name), listOf("YER", "SAR", "USD"))

    private suspend fun accountId(personId: Long, currency: String): Long =
        requireNotNull(ledger.getCurrencyAccount(personId, currency)).id

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
