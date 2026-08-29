package com.myaccounts.app.custody

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.data.custody.CustodyPersonEntity
import com.myaccounts.app.data.custody.CustodyRepository
import com.myaccounts.app.data.custody.CustodyTransactionType
import com.myaccounts.app.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustodyOperationsDatabaseTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val db get() = AppDatabase.getInstance(context)
    private lateinit var externalId: String
    private var custodyId = 0L
    private var personId = 0L

    @Before
    fun setUp() {
        runBlocking {
            externalId = "TEST-CUSTODY-OPS-${System.nanoTime()}"
            val repo = CustodyRepository(db, context)
            custodyId = repo.createCustody(CustodyEntity(name = "اختبار العهدة", organizationName = "اختبار الجهة", externalId = externalId))
            personId = repo.addPerson(custodyId, CustodyPersonEntity(custodyId = custodyId, name = "اختبار الشخص"))
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            db.custodyDao().getCustodyByExternalId(externalId)?.let {
                db.custodyDao().deleteTransactions(it.id)
                db.custodyDao().deleteAccounts(it.id)
                db.custodyDao().deletePersons(it.id)
                db.custodyDao().deleteCustody(it.id)
            }
        }
    }

    @Test
    fun fullOperationCycleKeepsDirectionsAndReachesZero() = runBlocking {
        val repo = CustodyRepository(db, context)
        repo.addTransaction(custodyId, "YER", CustodyTransactionType.RECEIVED_FROM_ORG, null, 1000000L, "عهدة", 10000L)
        repo.addTransaction(custodyId, "YER", CustodyTransactionType.PAID_TO_PERSON, personId, 400000L, "صرف", 11000L)
        repo.addTransaction(custodyId, "YER", CustodyTransactionType.RETURNED_FROM_PERSON, personId, 50000L, "مرتجع", 12000L)
        repo.addTransaction(custodyId, "YER", CustodyTransactionType.RETURNED_TO_ORG, null, 650000L, "تصفية", 13000L)
        val owner = db.custodyDao().getOwnerAccount(custodyId, "YER")!!
        val person = db.custodyDao().getPersonAccount(custodyId, personId, "YER")!!
        assertEquals(0L, db.custodyDao().observeBalance(owner.id).first())
        assertEquals(350000L, person.balanceMinor)
        assertEquals(0L, owner.balanceMinor)
    }

    @Test
    fun allFourOperationsHaveExpectedOwnerAndPersonDirections() = runBlocking {
        val repo = CustodyRepository(db, context)
        repo.addTransaction(custodyId, "SAR", CustodyTransactionType.RECEIVED_FROM_ORG, null, 100000L, "1", 10000L)
        repo.addTransaction(custodyId, "SAR", CustodyTransactionType.PAID_TO_PERSON, personId, 30000L, "2", 11000L)
        repo.addTransaction(custodyId, "SAR", CustodyTransactionType.RETURNED_FROM_PERSON, personId, 5000L, "3", 12000L)
        repo.addTransaction(custodyId, "SAR", CustodyTransactionType.RETURNED_TO_ORG, null, 10000L, "4", 13000L)
        val owner = db.custodyDao().getOwnerAccount(custodyId, "SAR")!!
        val person = db.custodyDao().getPersonAccount(custodyId, personId, "SAR")!!
        assertEquals(65000L, owner.balanceMinor)
        assertEquals(25000L, person.balanceMinor)
        assertEquals(65000L, db.custodyDao().observeBalance(owner.id).first())
    }

    @Test
    fun updateReversesOldMovementAndAppliesNewMovement() = runBlocking {
        val repo = CustodyRepository(db, context)
        val transactionId = repo.addTransaction(custodyId, "YER", CustodyTransactionType.PAID_TO_PERSON, personId, 400000L, "قديم", 10000L)
        repo.updateTransaction(transactionId, "SAR", CustodyTransactionType.PAID_TO_PERSON, personId, 250000L, "جديد", 11000L)
        val oldYeerOwner = db.custodyDao().getOwnerAccount(custodyId, "YER")!!
        val newSarOwner = db.custodyDao().getOwnerAccount(custodyId, "SAR")!!
        val yeerPerson = db.custodyDao().getPersonAccount(custodyId, personId, "YER")!!
        val sarPerson = db.custodyDao().getPersonAccount(custodyId, personId, "SAR")!!
        assertEquals(0L, oldYeerOwner.balanceMinor)
        assertEquals(0L, yeerPerson.balanceMinor)
        assertEquals(-250000L, newSarOwner.balanceMinor)
        assertEquals(250000L, sarPerson.balanceMinor)
    }

    @Test
    fun deleteReversesBothOwnerAndPersonBalances() = runBlocking {
        val repo = CustodyRepository(db, context)
        val transactionId = repo.addTransaction(custodyId, "USD", CustodyTransactionType.PAID_TO_PERSON, personId, 125000L, "حذف", 10000L)
        repo.deleteTransaction(transactionId)
        val owner = db.custodyDao().getOwnerAccount(custodyId, "USD")!!
        val person = db.custodyDao().getPersonAccount(custodyId, personId, "USD")!!
        assertEquals(0L, owner.balanceMinor)
        assertEquals(0L, person.balanceMinor)
    }

    @Test
    fun custodyAndPersonEachHaveThreeCurrencyAccounts() = runBlocking {
        val accounts = db.custodyDao().getAllAccounts(custodyId)
        assertEquals(6, accounts.size)
        assertEquals(3, accounts.count { it.holderType == "OWNER" })
        assertEquals(3, accounts.count { it.holderType == "PERSON" && it.personId == personId })
    }
}
