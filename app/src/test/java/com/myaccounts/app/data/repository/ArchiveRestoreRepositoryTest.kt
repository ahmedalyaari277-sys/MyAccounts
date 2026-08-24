package com.myaccounts.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myaccounts.app.data.local.AppDatabase
import com.myaccounts.app.data.local.PersonEntity
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.TransactionType
import kotlinx.coroutines.flow.first
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
class ArchiveRestoreRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: LedgerRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LedgerRepository(
            dao = database.ledgerDao(),
            transactionDao = database.transactionDao(),
            database = database
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun archiveAndRestorePerson_preservesAllCurrenciesTransactionsAndBalances() = runBlocking {
        val personId = database.ledgerDao().insertPersonWithCurrencyAccounts(
            PersonEntity(name = "Archive Test"),
            listOf("YER", "SAR", "USD")
        )
        val yer = database.ledgerDao().getCurrencyAccount(personId, "YER")!!
        val sar = database.ledgerDao().getCurrencyAccount(personId, "SAR")!!
        val usd = database.ledgerDao().getCurrencyAccount(personId, "USD")!!

        database.transactionDao().insertTransactionAndUpdateBalance(
            TransactionEntity(accountId = yer.id, type = TransactionType.RECEIVABLE, amountMinor = 150_000L, transactionDate = 1L)
        )
        database.transactionDao().insertTransactionAndUpdateBalance(
            TransactionEntity(accountId = yer.id, type = TransactionType.PAYABLE, amountMinor = 25_000L, transactionDate = 2L)
        )
        database.transactionDao().insertTransactionAndUpdateBalance(
            TransactionEntity(accountId = sar.id, type = TransactionType.RECEIVABLE, amountMinor = 75_000L, transactionDate = 3L)
        )
        database.transactionDao().insertTransactionAndUpdateBalance(
            TransactionEntity(accountId = usd.id, type = TransactionType.PAYABLE, amountMinor = 10_000L, transactionDate = 4L)
        )

        assertEquals(125_000L, database.transactionDao().getBalance(yer.id))
        assertEquals(75_000L, database.transactionDao().getBalance(sar.id))
        assertEquals(-10_000L, database.transactionDao().getBalance(usd.id))

        repository.deletePerson(personId)

        assertFalse(database.ledgerDao().observeArchivedPersonsWithAccounts().first().isEmpty())
        assertTrue(database.transactionDao().getTransactions(yer.id).isEmpty())
        assertTrue(database.transactionDao().getTransactions(sar.id).isEmpty())
        assertTrue(database.transactionDao().getTransactions(usd.id).isEmpty())
        assertEquals(4, database.transactionDao().getArchivedTransactionsForPerson(personId).size)

        val archivedPersonRows = database.transactionDao().observeArchivedTransactionsForPerson(personId).first()
        assertEquals(4, archivedPersonRows.size)

        repository.restorePerson(personId)

        val restoredPerson = database.ledgerDao().observePersonWithAccounts(personId).first()
        assertNotNull(restoredPerson)
        assertTrue(restoredPerson!!.person.isActive)
        assertEquals(2, database.transactionDao().getTransactions(yer.id).size)
        assertEquals(1, database.transactionDao().getTransactions(sar.id).size)
        assertEquals(1, database.transactionDao().getTransactions(usd.id).size)
        assertEquals(125_000L, database.transactionDao().getBalance(yer.id))
        assertEquals(75_000L, database.transactionDao().getBalance(sar.id))
        assertEquals(-10_000L, database.transactionDao().getBalance(usd.id))
        assertTrue(database.transactionDao().getArchivedTransactionsForPerson(personId).isEmpty())
        assertTrue(database.transactionDao().observeArchivedTransactionRows().first().isEmpty())
    }

    @Test
    fun restoreArchivedPerson_withExistingActiveSameName_mergesTransactionsWithoutDuplicatePerson() = runBlocking {
        val archivedPersonId = repository.insertPerson(PersonEntity(name = "Same Name"))
        val archivedAccount = database.ledgerDao().getCurrencyAccount(archivedPersonId, "YER")!!
        database.transactionDao().insertTransactionAndUpdateBalance(
            TransactionEntity(accountId = archivedAccount.id, type = TransactionType.RECEIVABLE, amountMinor = 40_000L, transactionDate = 1L)
        )
        repository.deletePerson(archivedPersonId)

        val activePersonId = repository.insertPerson(PersonEntity(name = "Same Name"))
        val activeAccount = database.ledgerDao().getCurrencyAccount(activePersonId, "YER")!!
        database.transactionDao().insertTransactionAndUpdateBalance(
            TransactionEntity(accountId = activeAccount.id, type = TransactionType.PAYABLE, amountMinor = 15_000L, transactionDate = 2L)
        )

        repository.restorePerson(archivedPersonId)

        assertNull(database.ledgerDao().observePersonWithAccounts(archivedPersonId).first())
        val activePeople = database.ledgerDao().observePersonsWithAccounts().first().filter { it.person.name == "Same Name" }
        assertEquals(1, activePeople.size)
        assertEquals(25_000L, database.transactionDao().getBalance(activeAccount.id))
        assertEquals(2, database.transactionDao().getTransactions(activeAccount.id).size)
        assertTrue(database.transactionDao().observeArchivedTransactionRows().first().isEmpty())
    }

    @Test
    fun archiveAndRestoreSingleTransaction_preservesItsSnapshotAndBalance() = runBlocking {
        val personId = repository.insertPerson(PersonEntity(name = "Transaction Archive Test"))
        val account = database.ledgerDao().getCurrencyAccount(personId, "YER")!!
        val transactionId = database.transactionDao().insertTransactionAndUpdateBalance(
            TransactionEntity(accountId = account.id, type = TransactionType.RECEIVABLE, amountMinor = 90_000L, transactionDate = 1L)
        )

        database.transactionDao().archiveTransactionAndUpdateBalance(transactionId)

        assertTrue(database.transactionDao().getTransactions(account.id).isEmpty())
        assertEquals(0L, database.transactionDao().getBalance(account.id))
        assertNotNull(database.transactionDao().getArchivedSnapshot(transactionId))
        assertEquals(1, database.transactionDao().observeArchivedTransactionRows().first().size)

        database.transactionDao().restoreTransactionAndUpdateBalance(transactionId)

        assertEquals(1, database.transactionDao().getTransactions(account.id).size)
        assertEquals(90_000L, database.transactionDao().getBalance(account.id))
        assertNull(database.transactionDao().getArchivedSnapshot(transactionId))
        assertTrue(database.transactionDao().observeArchivedTransactionRows().first().isEmpty())
    }

    @Test
    fun permanentDeleteArchivedPerson_removesPersonTransactionsAndArchiveSnapshots() = runBlocking {
        val personId = repository.insertPerson(PersonEntity(name = "Permanent Delete Archive Test"))
        val account = database.ledgerDao().getCurrencyAccount(personId, "USD")!!
        val transactionId = database.transactionDao().insertTransactionAndUpdateBalance(
            TransactionEntity(accountId = account.id, type = TransactionType.RECEIVABLE, amountMinor = 55_000L, transactionDate = 1L)
        )

        repository.deletePerson(personId)
        assertNotNull(database.transactionDao().getArchivedSnapshot(transactionId))

        repository.permanentlyDeletePerson(personId)

        assertNull(database.ledgerDao().observePerson(personId).first())
        assertNull(database.transactionDao().getTransaction(transactionId))
        assertNull(database.transactionDao().getArchivedSnapshot(transactionId))
        assertTrue(database.transactionDao().observeArchivedTransactionRows().first().isEmpty())
    }
}