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
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        )
        device.waitForIdle()
        assertTrue("Gateway not visible", device.wait(Until.hasObject(By.text("العُهَد")), 15_000))
    }

    @After
    fun tearDown() = runBlocking { clearData() }

    @Test
    fun custodyGatewayDetailAndOrganizationOperationWorkEndToEnd() = runBlocking {
        click(By.text("العُهَد"), "Custody gateway")
        click(By.text(custodyName), "Custody card")
        waitForDetailScreen()

        clickAction("استلام من الجهة", "Receive from organization")
        waitForOperationDialog()
        setField("المبلغ للعملية", "1000")
        clickSaveOperation()
        val custody = waitForCustody()
        assertTrue("Owner balance was not shown after receipt", device.wait(Until.hasObject(By.text("عليه 1000")), 10_000))
        assertEquals(CustodyTransactionType.RECEIVED_FROM_ORG, waitForTransactions(custody.id, 1).single().type)

        clickAction("مرتجع للجهة / تصفية", "Return to organization")
        waitForOperationDialog()
        setField("المبلغ للعملية", "1000")
        clickSaveOperation()
        assertTrue("Owner balance did not return to zero", device.wait(Until.hasObject(By.text("متوازن 0")), 10_000))
        val transactions = waitForTransactions(custody.id, 2)
        assertEquals(2, transactions.size)
        assertTrue(transactions.any { it.type == CustodyTransactionType.RETURNED_TO_ORG })
        assertEquals(0L, db.custodyDao().getOwnerAccount(custody.id, "YER")!!.balanceMinor)
    }

    @Test
    fun custodyPersonAndPersonOperationWorkEndToEndIncludingEditDeleteAndCurrencyChange() = runBlocking {
        click(By.text("العُهَد"), "Custody gateway")
        click(By.text(custodyName), "Custody card")
        waitForDetailScreen()

        clickAction("إضافة شخص", "Add custody person")
        waitForPersonDialog()
        setField("الاسم", "اختبار شخص واجهة")
        setField("الهاتف", "777000000")
        setField("العنوان", "صنعاء")
        setField("الملاحظات", "اختبار")
        clickSavePerson()

        val custody = waitForCustody()
        val person = waitForPerson(custody.id, "اختبار شخص واجهة")
        click(By.text("اختبار شخص واجهة"), "Custody person")
        assertTrue("Person screen did not open", device.wait(Until.hasObject(By.text("صرف للشخص")), 10_000))

        clickAction("إضافة عملية", "Add person operation")
        waitForOperationDialog()
        setField("المبلغ للعملية", "250")
        setField("بيان العملية", "صرف واجهة")
        clickSaveOperation()
        assertTrue("Person balance was not shown", device.wait(Until.hasObject(By.text("عليه 250")), 10_000))

        val first = waitForTransactions(custody.id, 1).single()
        assertEquals(person.id, first.personId)
        assertEquals(CustodyTransactionType.PAID_TO_PERSON, first.type)
        assertEquals("YER", first.currencyCode)
        assertEquals(25000L, first.amountMinor)

        clickAction("تعديل", "Edit person operation")
        waitForOperationDialog()
        setField("المبلغ للعملية", "300")
        clickSaveOperation()
        var updated = waitForTransactions(custody.id, 1).single()
        assertEquals(30000L, updated.amountMinor)
        assertEquals(CustodyTransactionType.PAID_TO_PERSON, updated.type)

        clickAction("تعديل", "Edit operation for currency and type")
        waitForOperationDialog()
        click(By.text("SAR"), "Change operation currency to SAR")
        click(By.text("مرتجع من الشخص"), "Change operation type to returned from person")
        clickSaveOperation()

        updated = waitForTransactions(custody.id, 1).single()
        assertEquals("SAR", updated.currencyCode)
        assertEquals(CustodyTransactionType.RETURNED_FROM_PERSON, updated.type)
        assertEquals(30000L, updated.amountMinor)
        assertEquals(0L, db.custodyDao().getPersonAccount(custody.id, person.id, "YER")!!.balanceMinor)
        assertEquals(-30000L, db.custodyDao().getOwnerAccount(custody.id, "SAR")!!.balanceMinor)
        assertEquals(-30000L, db.custodyDao().getPersonAccount(custody.id, person.id, "SAR")!!.balanceMinor)

        clickAction("حذف", "Delete person operation")
        waitForText("حذف العملية")
        click(By.text("حذف"), "Confirm delete")
        assertTrue("Transaction was not deleted", waitForTransactions(custody.id, 0).isEmpty())
        assertEquals(0L, db.custodyDao().getOwnerAccount(custody.id, "SAR")!!.balanceMinor)
        assertEquals(0L, db.custodyDao().getPersonAccount(custody.id, person.id, "SAR")!!.balanceMinor)
    }

    private fun waitForDetailScreen() {
        assertTrue("Custody detail screen not visible", device.wait(Until.hasObject(By.desc("شاشة تفاصيل العهدة")), 10_000))
    }

    private fun waitForPersonDialog() {
        assertTrue("Person dialog title not visible", device.wait(Until.hasObject(By.desc("حوار إضافة شخص")), 10_000))
        assertTrue("Person name field not visible", device.wait(Until.hasObject(By.desc("الاسم")), 5_000))
        assertTrue("Person phone field not visible", device.wait(Until.hasObject(By.desc("الهاتف")), 5_000))
    }

    private fun clickSavePerson() {
        hideKeyboardIfEditing()
        click(By.desc("حفظ الشخص"), "Save person")
        device.waitForIdle()
        assertTrue("Person dialog did not close", device.wait(Until.gone(By.desc("حوار إضافة شخص")), 10_000))
    }

    private fun waitForOperationDialog() {
        val visible = device.wait(Until.hasObject(By.text("إضافة عملية")), 10_000) ||
            device.wait(Until.hasObject(By.text("تعديل العملية")), 10_000)
        assertTrue("Operation dialog not visible", visible)
        assertTrue("Operation amount field not visible", device.wait(Until.hasObject(By.text("المبلغ")), 5_000))
    }

    private fun setField(description: String, value: String) {
        val directField = device.wait(
            Until.findObject(By.clazz("android.widget.EditText").desc(description)),
            5_000
        )
        if (directField != null) {
            directField.click()
            device.waitForIdle()
            directField.text = value
            device.waitForIdle()
            return
        }

        val semanticField = device.wait(Until.findObject(By.desc(description)), 2_000)
        if (semanticField != null) {
            semanticField.click()
        } else {
            val label = visibleLabelFor(description)
            val labelNode = device.wait(Until.findObject(By.text(label)), 10_000)
                ?: error("Field '$description' not found")
            clickNodeOrClickableAncestor(labelNode, "Field $description")
        }
        device.waitForIdle()
        val focused = device.wait(Until.findObject(By.focused(true)), 3_000)
        val actualField = if (focused?.className == "android.widget.EditText") {
            focused
        } else {
            device.wait(Until.findObject(By.clazz("android.widget.EditText")), 3_000)
        }
        if (actualField == null) error("Editable control for '$description' not found")
        actualField.text = value
        device.waitForIdle()
    }

    private fun visibleLabelFor(description: String): String = when (description) {
        "المبلغ للعملية" -> "المبلغ"
        "بيان العملية" -> "التفاصيل"
        else -> description
    }

    private fun clickSaveOperation() {
        hideKeyboardIfEditing()
        click(By.text("حفظ"), "Save operation")
        device.waitForIdle()
        assertTrue(
            "Operation dialog did not close",
            device.wait(Until.gone(By.text("إضافة عملية")), 5_000) ||
                device.wait(Until.gone(By.text("تعديل العملية")), 10_000)
        )
    }

    private fun hideKeyboardIfEditing() {
        val focused = device.findObject(By.focused(true))
        if (focused?.className == "android.widget.EditText") {
            device.pressBack()
            device.waitForIdle()
        }
    }

    private fun clickAction(text: String, label: String) {
        val byText = device.wait(Until.findObject(By.text(text)), 5_000)
        if (byText != null) {
            clickNodeOrClickableAncestor(byText, label)
            return
        }
        val byDesc = device.wait(Until.findObject(By.desc(text)), 5_000)
        if (byDesc != null) {
            clickNodeOrClickableAncestor(byDesc, label)
            return
        }
        error("$label not found")
    }

    private fun click(selector: androidx.test.uiautomator.BySelector, label: String) {
        val target = device.wait(Until.findObject(selector), 10_000) ?: error("$label not found")
        clickNodeOrClickableAncestor(target, label)
    }

    private fun clickNodeOrClickableAncestor(start: UiObject2, label: String) {
        var current: UiObject2? = start
        repeat(8) {
            val node = current ?: return@repeat
            if (node.isClickable) {
                node.click()
                device.waitForIdle()
                return
            }
            current = runCatching { node.parent }.getOrNull()
        }
        error("$label clickable ancestor not found")
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
            if (transactions.size >= minimum || (minimum == 0 && transactions.isEmpty())) return transactions
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
