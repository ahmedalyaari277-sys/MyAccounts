package com.myaccounts.app.custody

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.myaccounts.app.MainActivity
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.data.custody.CustodyRepository
import com.myaccounts.app.data.custody.CustodyTransactionType
import com.myaccounts.app.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CustodyOperationsUiInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)
    private val db get() = AppDatabase.getInstance(context)

    private lateinit var externalId: String
    private lateinit var custodyName: String

    @Before
    fun setUp() = runBlocking {
        val id = UUID.randomUUID().toString()
        externalId = "UI-CUSTODY-$id"
        custodyName = "اختبار واجهة العهدة $id"
        clearData()
        CustodyRepository(db, context).createCustody(
            CustodyEntity(name = custodyName, organizationName = "اختبار الجهة $id", externalId = externalId)
        )
        instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        device.waitForIdle()
        assertTrue("Gateway not visible", device.wait(Until.hasObject(By.text("العُهَد")), 15_000))
    }

    @After
    fun tearDown() = runBlocking { clearData() }

    @Test
    fun custodyGatewayDetailAndOrganizationOperationWorkEndToEnd() {
        click(By.text("العُهَد"), "Custody gateway")
        click(By.text(custodyName), "Custody card")
        click(By.text("استلام من الجهة"), "Receive from organization")
        waitForSaveOperation()
        setField("المبلغ للعملية", "1000")
        clickSaveOperation()

        val custody = waitForCustody()
        val transactions = waitForTransactions(custody.id, 1)
        assertEquals(1, transactions.size)
        assertEquals(CustodyTransactionType.RECEIVED_FROM_ORG, transactions.single().type)
        assertEquals(100000L, transactions.single().amountMinor)
    }

    @Test
    fun custodyPersonAndPersonOperationWorkEndToEnd() {
        click(By.text("العُهَد"), "Custody gateway")
        click(By.text(custodyName), "Custody card")
        click(By.text("إضافة شخص"), "Add custody person")
        waitForText("إضافة شخص")

        setField("الاسم", "اختبار شخص واجهة")
        setField("الهاتف", "777000000")
        setField("العنوان", "صنعاء")
        setField("الملاحظات", "اختبار")
        clickSavePerson()

        val custody = waitForCustody()
        val person = waitForPerson(custody.id, "اختبار شخص واجهة")
        click(By.text("اختبار شخص واجهة"), "Custody person")
        assertTrue("Person screen did not open", device.wait(Until.hasObject(By.text("صرف للشخص")), 10_000))

        click(By.text("+"), "Add person operation")
        waitForSaveOperation()
        setField("المبلغ للعملية", "250")
        setField("بيان العملية", "صرف واجهة")
        clickSaveOperation()

        val transactions = waitForTransactions(custody.id, 1)
        assertEquals(1, transactions.size)
        assertEquals(person.id, transactions.single().personId)
        assertEquals(CustodyTransactionType.PAID_TO_PERSON, transactions.single().type)
        assertEquals(25000L, transactions.single().amountMinor)
    }

    private fun clickSavePerson() {
        val save = device.wait(Until.findObject(By.text("حفظ")), 3_000)
        if (save != null) {
            save.click()
            device.waitForIdle()
            return
        }
        device.swipe(540, 1500, 540, 650, 20)
        val afterScroll = device.wait(Until.findObject(By.text("حفظ")), 5_000) ?: error("Save person not found")
        afterScroll.click()
        device.waitForIdle()
    }

    private fun waitForSaveOperation() {
        assertTrue("Operation save control not found", device.wait(Until.hasObject(By.desc("حفظ العملية")), 10_000))
    }

    private fun setField(description: String, value: String) {
        val field = device.wait(Until.findObject(By.desc(description)), 10_000) ?: error("Field '$description' not found")
        field.text = value
        device.waitForIdle()
    }

    private fun clickSaveOperation() {
        val save = device.wait(Until.findObject(By.desc("حفظ العملية")), 10_000) ?: error("Save operation not found")
        save.click()
        device.waitForIdle()
    }

    private fun click(selector: androidx.test.uiautomator.BySelector, label: String) {
        val target = device.wait(Until.findObject(selector), 10_000) ?: error("$label not found")
        target.click()
        device.waitForIdle()
    }

    private fun waitForText(text: String) {
        assertTrue("Text '$text' not found", device.wait(Until.hasObject(By.text(text)), 10_000))
    }

    private fun waitForCustody(): CustodyEntity {
        val deadline = System.currentTimeMillis() + 10_000L
        while (System.currentTimeMillis() < deadline) {
            runBlocking { db.custodyDao().getCustodyByExternalId(externalId) }?.let { return it }
            Thread.sleep(100)
        }
        return runBlocking { db.custodyDao().getCustodyByExternalId(externalId) }.also {
            assertNotNull("Test custody was not persisted", it)
        }!!
    }

    private fun waitForPerson(custodyId: Long, name: String): com.myaccounts.app.data.custody.CustodyPersonEntity {
        val deadline = System.currentTimeMillis() + 10_000L
        while (System.currentTimeMillis() < deadline) {
            runBlocking { db.custodyDao().getAllPersons(custodyId).firstOrNull { it.name == name } }?.let { return it }
            Thread.sleep(100)
        }
        return runBlocking { db.custodyDao().getAllPersons(custodyId).firstOrNull { it.name == name } }.also {
            assertNotNull("Test person was not persisted", it)
        }!!
    }

    private fun waitForTransactions(custodyId: Long, minimum: Int): List<com.myaccounts.app.data.custody.CustodyTransactionEntity> {
        val deadline = System.currentTimeMillis() + 10_000L
        while (System.currentTimeMillis() < deadline) {
            val transactions = runBlocking { db.custodyDao().getAllTransactions(custodyId, false) }
            if (transactions.size >= minimum) return transactions
            Thread.sleep(100)
        }
        return runBlocking { db.custodyDao().getAllTransactions(custodyId, false) }
    }

    private suspend fun clearData() {
        if (!::externalId.isInitialized) return
        db.custodyDao().getCustodyByExternalId(externalId)?.let {
            db.custodyDao().deleteTransactions(it.id)
            db.custodyDao().deleteAccounts(it.id)
            db.custodyDao().deletePersons(it.id)
            db.custodyDao().deleteCustody(it.id)
        }
    }
}
