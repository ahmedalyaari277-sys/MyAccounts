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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArchiveTypeSeparationTest {
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
    fun archivingAccountDoesNotConvertPreviouslyIndividuallyArchivedTransactionIntoAccountArchive() = runBlocking {
        val personId = ledger.insertPersonWithCurrencyAccounts(
            PersonEntity(name = "فصل الأرشيف"),
            listOf("YER")
        )
        val accountId = requireNotNull(ledger.getCurrencyAccount(personId, "YER")).id

        val individuallyArchived = transactions.insertTransactionAndUpdateBalance(
            TransactionEntity(
                accountId = accountId,
                type = TransactionType.RECEIVABLE,
                amountMinor = 10000,
                transactionDate = System.currentTimeMillis()
            )
        )
        transactions.archiveTransactionAndUpdateBalance(individuallyArchived)

        val liveTransaction = transactions.insertTransactionAndUpdateBalance(
            TransactionEntity(
                accountId = accountId,
                type = TransactionType.RECEIVABLE,
                amountMinor = 20000,
                transactionDate = System.currentTimeMillis()
            )
        )

        repository.deletePerson(personId)

        assertTrue(transactions.getTransaction(individuallyArchived)!!.isArchived)
        assertFalse(transactions.getTransaction(individuallyArchived)!!.archivedWithPerson)
        assertTrue(transactions.getTransaction(liveTransaction)!!.isArchived)
        assertTrue(transactions.getTransaction(liveTransaction)!!.archivedWithPerson)
        assertFalse(transactions.getArchivedSnapshot(individuallyArchived)!!.archivedWithPerson)
        assertTrue(transactions.getArchivedSnapshot(liveTransaction)!!.archivedWithPerson)

        repository.restorePerson(personId)

        assertTrue(transactions.getTransaction(individuallyArchived)!!.isArchived)
        assertFalse(transactions.getTransaction(individuallyArchived)!!.archivedWithPerson)
        assertFalse(transactions.getTransaction(liveTransaction)!!.isArchived)
        assertFalse(transactions.getTransaction(liveTransaction)!!.archivedWithPerson)
    }
}
