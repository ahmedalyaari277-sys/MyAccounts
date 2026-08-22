package com.myaccounts.app.archive

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myaccounts.app.data.local.AppDatabase
import com.myaccounts.app.data.local.PersonEntity
import com.myaccounts.app.data.local.dao.LedgerDao
import com.myaccounts.app.data.local.dao.TransactionDao
import com.myaccounts.app.data.repository.LedgerRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountNameUniquenessInvariantTest {
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
    fun activeNamesAreUniqueAfterTrimAndCaseNormalization() = runBlocking {
        val firstId = repository.insertPerson(PersonEntity(name = "  Ahmed  "))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.insertPerson(PersonEntity(name = "ahmed"))
            }
        }

        assertEquals(firstId, ledger.findActivePersonIdByName("Ahmed"))
    }

    @Test
    fun archivedNameCanBeReusedForANewActiveAccount() = runBlocking {
        val archivedId = repository.insertPerson(PersonEntity(name = "علي"))
        repository.deletePerson(archivedId)

        val activeId = repository.insertPerson(PersonEntity(name = " علي "))

        assertNotEquals(archivedId, activeId)
        assertEquals(activeId, ledger.findActivePersonIdByName("علي"))
    }
}
