package com.myaccounts.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myaccounts.app.data.local.AppDatabase
import com.myaccounts.app.data.local.PersonEntity
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransactionCurrencyTransferTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun updateTransactionAndUpdateBalance_movesTransactionAcrossAllCurrencyDirections() = runBlocking {
        val personId = database.ledgerDao().insertPersonWithCurrencyAccounts(
            PersonEntity(name = "Currency Transfer Test"),
            listOf("YER", "SAR", "USD")
        )
        val accounts = mapOf(
            "YER" to database.ledgerDao().getCurrencyAccount(personId, "YER")!!,
            "SAR" to database.ledgerDao().getCurrencyAccount(personId, "SAR")!!,
            "USD" to database.ledgerDao().getCurrencyAccount(personId, "USD")!!
        )

        val transactionId = database.transactionDao().insertTransactionAndUpdateBalance(
            TransactionEntity(
                accountId = accounts.getValue("YER").id,
                type = TransactionType.RECEIVABLE,
                amountMinor = 100_000L,
                description = "Currency transfer coverage",
                transactionDate = 1L
            )
        )

        fun moveAndAssert(from: String, to: String) = runBlocking {
            val transaction = database.transactionDao().getTransaction(transactionId)!!
            assertEquals(accounts.getValue(from).id, transaction.accountId)

            database.transactionDao().updateTransactionAndUpdateBalance(
                transaction.copy(accountId = accounts.getValue(to).id)
            )

            val moved = database.transactionDao().getTransaction(transactionId)!!
            assertEquals(accounts.getValue(to).id, moved.accountId)
            assertEquals(100_000L, moved.amountMinor)
            assertEquals(TransactionType.RECEIVABLE, moved.type)
            assertEquals("Currency transfer coverage", moved.description)
            assertEquals(1L, moved.transactionDate)
            assertTrue(database.transactionDao().getTransactions(accounts.getValue(from).id).isEmpty())
            assertEquals(1, database.transactionDao().getTransactions(accounts.getValue(to).id).size)
            assertEquals(0L, database.transactionDao().getBalance(accounts.getValue(from).id))
            assertEquals(100_000L, database.transactionDao().getBalance(accounts.getValue(to).id))
        }

        moveAndAssert("YER", "SAR")
        moveAndAssert("SAR", "USD")
        moveAndAssert("USD", "YER")
        moveAndAssert("YER", "USD")
        moveAndAssert("USD", "SAR")
        moveAndAssert("SAR", "YER")

        assertEquals(accounts.getValue("YER").id, database.transactionDao().getTransaction(transactionId)!!.accountId)
        assertEquals(100_000L, database.transactionDao().getBalance(accounts.getValue("YER").id))
        assertEquals(0L, database.transactionDao().getBalance(accounts.getValue("SAR").id))
        assertEquals(0L, database.transactionDao().getBalance(accounts.getValue("USD").id))
    }
}
