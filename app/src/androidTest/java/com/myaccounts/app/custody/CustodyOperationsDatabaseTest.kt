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
import org.junit.Assert.assertTrue
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
    fun setUp() = runBlocking {
        externalId = "TEST-CUSTODY-OPS-${System.nanoTime()}"
        val repo = CustodyRepository(db, context)
        custodyId = repo.createCustody(CustodyEntity(name = "اختبار العهدة", organizationName = "اختبار الجهة", externalId = externalId))
        personId = repo.addPerson(custodyId, CustodyPersonEntity(custodyId = custodyId, name = "اختبار الشخص"))
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
    fun requiredFullFinancialCycleReachesZero() = runBlocking {
        val repo = CustodyRepository(db, context)
        repo.addTransaction(custodyId, "YER", CustodyTransactionType.RECEIVED_FROM_ORG, null, 1_000_000L, "", "استلام", 10_000L)
        repo.addTransaction(custodyId, "YER", CustodyTransactionType.PAID_TO_PERSON, personId, 300_000L, "", "صرف", 11_000L)
        repo.addTransaction(custodyId, "YER", CustodyTransactionType.RETURNED_FROM_PERSON, personId, 50_000L, "", "مرتجع", 12_000L)
        repo.addTransaction(custodyId, "YER", CustodyTransactionType.RETURNED_TO_ORG, null, 750_000L, "", "تصفية", 13_000L)
        val owner = db.custodyDao().getOwnerAccount(custodyId, "YER")!!
        val person = db.custodyDao().getPersonAccount(custodyId, personId, "YER")!!
        assertEquals(0L, owner.balanceMinor)
        assertEquals(250_000L, person.balanceMinor)
        assertEquals(0L, db.custodyDao().observeBalance(owner.id).first())
    }

    @Test
    fun allFourCoreOperationsHaveExpectedOwnerAndPersonDirections() = runBlocking {
        val repo = CustodyRepository(db, context)
        repo.addTransaction(custodyId, "SAR", CustodyTransactionType.RECEIVED_FROM_ORG, null, 100_000L, "", "1", 10_000L)
        repo.addTransaction(custodyId, "SAR", CustodyTransactionType.PAID_TO_PERSON, personId, 30_000L, "", "2", 11_000L)
        repo.addTransaction(custodyId, "SAR", CustodyTransactionType.RETURNED_FROM_PERSON, personId, 5_000L, "", "3", 12_000L)
        repo.addTransaction(custodyId, "SAR", CustodyTransactionType.RETURNED_TO_ORG, null, 10_000L, "", "4", 13_000L)
        val owner = db.custodyDao().getOwnerAccount(custodyId, "SAR")!!
        val person = db.custodyDao().getPersonAccount(custodyId, personId, "SAR")!!
        assertEquals(65_000L, owner.balanceMinor)
        assertEquals(25_000L, person.balanceMinor)
        assertEquals(65_000L, db.custodyDao().observeBalance(owner.id).first())
    }

    @Test
    fun personLoanChangesOwnerCashButCreatesSeparateDebt() = runBlocking {
        val repo = CustodyRepository(db, context)
        repo.addTransaction(custodyId, "YER", CustodyTransactionType.PERSON_LOAN_TO_OWNER, personId, 20_000L, "", "قرض شخصي", 10_000L)
        val owner = db.custodyDao().getOwnerAccount(custodyId, "YER")!!
        val person = db.custodyDao().getPersonAccount(custodyId, personId, "YER")!!
        val tx = db.custodyDao().getAllTransactions(custodyId, false).single()
        assertEquals(20_000L, owner.balanceMinor)
        assertEquals(0L, person.balanceMinor)
        assertEquals(CustodyTransactionType.PERSON_LOAN_TO_OWNER, tx.type)
    }

    @Test
    fun repayingPersonLoanReducesCashAndDebtWithoutChangingPersonCustody() = runBlocking {
        val repo = CustodyRepository(db, context)
        repo.addTransaction(custodyId, "YER", CustodyTransactionType.PERSON_LOAN_TO_OWNER, personId, 20_000L, "", "قرض", 10_000L)
        repo.addTransaction(custodyId, "YER", CustodyTransactionType.OWNER_REPAY_PERSON_LOAN, personId, 5_000L, "", "سداد", 11_000L)
        val owner = db.custodyDao().getOwnerAccount(custodyId, "YER")!!
        val person = db.custodyDao().getPersonAccount(custodyId, personId, "YER")!!
        assertEquals(15_000L, owner.balanceMinor)
        assertEquals(0L, person.balanceMinor)
    }

    @Test
    fun orgLoanAndRepaymentAreStoredWithoutBeingConfusedWithCoreReceiptReturn() = runBlocking {
        val repo = CustodyRepository(db, context)
        repo.addTransaction(custodyId, "USD", CustodyTransactionType.ORG_LOAN_FROM_OWNER, null, 30_000L, "", "تسليف الجهة", 10_000L)
        repo.addTransaction(custodyId, "USD", CustodyTransactionType.ORG_LOAN_REPAYMENT, null, 10_000L, "", "سداد تسليف الجهة", 11_000L)
        val owner = db.custodyDao().getOwnerAccount(custodyId, "USD")!!
        assertEquals(-20_000L, owner.balanceMinor)
        val types = db.custodyDao().getAllTransactions(custodyId, false).map { it.type }
        assertTrue(types.contains(CustodyTransactionType.ORG_LOAN_FROM_OWNER))
        assertTrue(types.contains(CustodyTransactionType.ORG_LOAN_REPAYMENT))
    }

    @Test
    fun updateReversesOldMovementAndAppliesNewMovement() = runBlocking {
        val repo = CustodyRepository(db, context)
        val transactionId = repo.addTransaction(custodyId, "YER", CustodyTransactionType.PAID_TO_PERSON, personId, 400_000L, "", "قديم", 10_000L)
        repo.updateTransaction(transactionId, "SAR", CustodyTransactionType.PAID_TO_PERSON, personId, 250_000L, "", "جديد", 11_000L)
        assertEquals(0L, db.custodyDao().getOwnerAccount(custodyId, "YER")!!.balanceMinor)
        assertEquals(0L, db.custodyDao().getPersonAccount(custodyId, personId, "YER")!!.balanceMinor)
        assertEquals(-250_000L, db.custodyDao().getOwnerAccount(custodyId, "SAR")!!.balanceMinor)
        assertEquals(250_000L, db.custodyDao().getPersonAccount(custodyId, personId, "SAR")!!.balanceMinor)
    }

    @Test
    fun deleteReversesBothOwnerAndPersonBalances() = runBlocking {
        val repo = CustodyRepository(db, context)
        val transactionId = repo.addTransaction(custodyId, "USD", CustodyTransactionType.PAID_TO_PERSON, personId, 125_000L, "", "حذف", 10_000L)
        repo.deleteTransaction(transactionId)
        assertEquals(0L, db.custodyDao().getOwnerAccount(custodyId, "USD")!!.balanceMinor)
        assertEquals(0L, db.custodyDao().getPersonAccount(custodyId, personId, "USD")!!.balanceMinor)
    }

    @Test
    fun custodyAndPersonEachHaveThreeCurrencyAccounts() = runBlocking {
        val accounts = db.custodyDao().getAllAccounts(custodyId)
        assertEquals(6, accounts.size)
        assertEquals(3, accounts.count { it.holderType == "OWNER" })
        assertEquals(3, accounts.count { it.holderType == "PERSON" && it.personId == personId })
    }

    @Test
    fun closeRequiresAllThreeCurrencyCustodyBalancesToBeSettledAndCanReopen() = runBlocking {
        val repo = CustodyRepository(db, context)
        repo.addTransaction(custodyId, "YER", CustodyTransactionType.RECEIVED_FROM_ORG, null, 100_000L, "", "استلام", 10_000L)
        repo.addTransaction(custodyId, "YER", CustodyTransactionType.PAID_TO_PERSON, personId, 30_000L, "", "صرف", 11_000L)
        repo.addTransaction(custodyId, "YER", CustodyTransactionType.RETURNED_FROM_PERSON, personId, 30_000L, "", "مرتجع", 12_000L)
        repo.addTransaction(custodyId, "YER", CustodyTransactionType.RETURNED_TO_ORG, null, 100_000L, "", "تصفية", 13_000L)
        repo.closeCustody(custodyId, 0L, 0L, 0L, "تسوية كاملة")
        assertTrue(db.custodyDao().getCustody(custodyId)!!.isClosed)
        var failed = false
        try { repo.addTransaction(custodyId, "YER", CustodyTransactionType.RECEIVED_FROM_ORG, null, 1_000L, "", "ممنوع بعد الإغلاق", 14_000L) } catch (_: IllegalArgumentException) { failed = true }
        assertTrue("Closed custody accepted a new transaction", failed)
        repo.reopenCustody(custodyId)
        assertTrue(!db.custodyDao().getCustody(custodyId)!!.isClosed)
        repo.addTransaction(custodyId, "SAR", CustodyTransactionType.RECEIVED_FROM_ORG, null, 2_000L, "", "بعد إعادة الفتح", 15_000L)
        assertEquals(2_000L, db.custodyDao().getOwnerAccount(custodyId, "SAR")!!.balanceMinor)
    }
}
