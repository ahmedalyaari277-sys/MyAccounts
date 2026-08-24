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
class PermanentDeleteRestoreTest {
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
    fun permanentlyDeletingPersonKeepsOnlyIndividuallyArchivedTransactionRestorable() = runBlocking {
        val personId = ledger.insertPersonWithCurrencyAccounts(
            PersonEntity(name = "حذف دائم"),
            listOf("YER")
        )
        val accountId = requireNotNull(ledger.getCurrencyAccount(personId, "YER")).id

        val individuallyArchived = transactions.insertTransactionAndUpdateBalance(
            TransactionEntity(
                accountId = accountId,
                type = TransactionType.RECEIVABLE,
                amountMinor = 10000,
                description = "مؤرشفة منفردًا",
                transactionDate = System.currentTimeMillis()
            )
        )
        transactions.archiveTransactionAndUpdateBalance(individuallyArchived)

        val archivedWithPerson = transactions.insertTransactionAndUpdateBalance(
            TransactionEntity(
                accountId = accountId,
                type = TransactionType.PAYABLE,
                amountMinor = 3000,
                description = "تحذف مع الحساب",
                transactionDate = System.currentTimeMillis()
            )
        )
        repository.deletePerson(personId)

        repository.permanentlyDeletePerson(personId)

        assertNull(transactions.getPersonById(personId))
        assertNull(transactions.getTransaction(archivedWithPerson))
        assertNull(transactions.getTransaction(individuallyArchived))

        val snapshot = transactions.getArchivedSnapshot(individuallyArchived)
        assertNotNull(snapshot)
        assertFalse(snapshot!!.archivedWithPerson)
        assertNull(transactions.getArchivedSnapshot(archivedWithPerson))

        transactions.restoreTransactionAndUpdateBalance(individuallyArchived)

        val restoredPerson = transactions.getPersonById(personId)
        assertNotNull(restoredPerson)
        assertTrue(restoredPerson!!.isActive)

        val restoredAccount = requireNotNull(ledger.getCurrencyAccount(personId, "YER"))
        val restoredTransaction = transactions.getTransaction(individuallyArchived)
        assertNotNull(restoredTransaction)
        assertEquals(restoredAccount.id, restoredTransaction!!.accountId)
        assertFalse(restoredTransaction.isArchived)
        assertEquals(10000L, transactions.getBalance(restoredAccount.id))
        assertNull(transactions.getArchivedSnapshot(individuallyArchived))
    }
}
