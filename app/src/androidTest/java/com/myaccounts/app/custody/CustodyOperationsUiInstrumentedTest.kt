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
import com.myaccounts.app.data.custody.CustodyPersonEntity
import com.myaccounts.app.data.custody.CustodyRepository
import com.myaccounts.app.data.custody.CustodyTransactionType
import com.myaccounts.app.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustodyOperationsUiInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)
    private val db get() = AppDatabase.getInstance(context)
    private val externalId = "UI-CUSTODY-001"

    @Before
    fun setUp() = runBlocking {
        clearData()
        CustodyRepository(db, context).createCustody(
            CustodyEntity(name = "اختبار واجهة العهدة", organizationName = "اختبار الجهة", externalId = externalId)
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
        click(By.text("اختبار واجهة العهدة"), "Custody card")
        click(By.text("استلام من الجهة"), "Receive from organization")
        waitForSaveOperation()
        enterFirstAmountField("1000")
        clickSaveOperation()

        val custody = runBlocking { db.custodyDao().getCustodyByExternalId(externalId)!! }
        val transactions = runBlocking { db.custodyDao().getAllTransactions(custody.id, false) }
        assertEquals(1, transactions.size)
        assertEquals(CustodyTransactionType.RECEIVED_FROM_ORG, transactions.single().type)
        assertEquals(100000L, transactions.single().amountMinor)
    }

    @Test
    fun custodyPersonAndPersonOperationWorkEndToEnd() {
        click(By.text("العُهَد"), "Custody gateway")
        click(By.text("اختبار واجهة العهدة"), "Custody card")
        click(By.text("إضافة شخص"), "Add custody person")
        waitForText("إضافة شخص")
        val fields = waitForEditTexts(4)
        fields[0].text = "اختبار شخص واجهة"
        fields[1].text = "777000000"
        fields[2].text = "صنعاء"
        fields[3].text = "اختبار"
        clickSavePerson()

        assertTrue("Person was not created", device.wait(Until.hasObject(By.text("اختبار شخص واجهة")), 10_000))
        click(By.text("اختبار شخص واجهة"), "Custody person")
        assertTrue("Person screen did not open", device.wait(Until.hasObject(By.text("صرف للشخص")), 10_000))

        click(By.text("+"), "Add person operation")
        waitForSaveOperation()
        val opFields = waitForEditTexts(3)
        opFields[0].text = "250"
        opFields[2].text = "صرف واجهة"
        clickSaveOperation()

        val custody = runBlocking { db.custodyDao().getCustodyByExternalId(externalId)!! }
        val person = runBlocking { db.custodyDao().getAllPersons(custody.id).single() }
        val transactions = runBlocking { db.custodyDao().getAllTransactions(custody.id, false) }
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
        assertTrue(
            "Operation save control not found",
            device.wait(Until.hasObject(By.desc("حفظ العملية")), 10_000)
        )
    }

    private fun enterFirstAmountField(amount: String) {
        val fields = waitForEditTexts(1)
        fields.first().text = amount
        device.waitForIdle()
    }

    private fun clickSaveOperation() {
        val save = device.wait(Until.findObject(By.desc("حفظ العملية")), 10_000) ?: error("Save operation not found")
        save.click()
        device.waitForIdle()
    }

    private fun waitForEditTexts(minimum: Int): List<UiObject2> {
        val deadline = System.currentTimeMillis() + 10_000L
        while (System.currentTimeMillis() < deadline) {
            val objects = device.findObjects(By.clazz("android.widget.EditText"))
            if (objects.size >= minimum) return objects
            device.waitForIdle()
            Thread.sleep(100)
        }
        error("Expected at least $minimum EditTexts, found ${device.findObjects(By.clazz("android.widget.EditText")).size}")
    }

    private fun click(selector: androidx.test.uiautomator.BySelector, label: String) {
        val target = device.wait(Until.findObject(selector), 10_000) ?: error("$label not found")
        target.click()
        device.waitForIdle()
    }

    private fun waitForText(text: String) {
        assertTrue("Text '$text' not found", device.wait(Until.hasObject(By.text(text)), 10_000))
    }

    private suspend fun clearData() {
        db.custodyDao().getCustodyByExternalId(externalId)?.let {
            db.custodyDao().deleteTransactions(it.id)
            db.custodyDao().deleteAccounts(it.id)
            db.custodyDao().deletePersons(it.id)
            db.custodyDao().deleteCustody(it.id)
        }
    }
}
