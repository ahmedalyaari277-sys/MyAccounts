package com.myaccounts.app.custody

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiObject2
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
    private val device get() = UiDevice.getInstance(instrumentation)
    private val db get() = AppDatabase.getInstance(context)
    private lateinit var externalId: String
    private lateinit var custodyName: String

    @Before
    fun setUp() {
        runBlocking {
            val token = UUID.randomUUID().toString()
            externalId = "UI-CUSTODY-$token"
            custodyName = "اختبار واجهة العهدة $token"
            CustodyRepository(db, context).createCustody(CustodyEntity(name = custodyName, organizationName = "اختبار الجهة $token", externalId = externalId))
        }
        instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        device.waitForIdle()
        assertTrue("Gateway not visible", device.wait(Until.hasObject(By.text("العُهَد")), 15_000))
    }

    @After
    fun tearDown() {
        runBlocking { db.custodyDao().getCustodyByExternalId(externalId)?.let { db.custodyDao().deleteTransactions(it.id); db.custodyDao().deleteAccounts(it.id); db.custodyDao().deletePersons(it.id); db.custodyDao().deleteCustody(it.id) } }
    }

    @Test
    fun custodyGatewayDetailAndOrganizationOperationWorkEndToEnd() = runBlocking {
        openCustodyDetail(); clickAction("استلام من الجهة"); waitForOperationDialog(false); setField("المبلغ للعملية", "1000"); clickSaveOperation(); assertTrue(device.wait(Until.hasObject(By.text("عليه 1000")), 10_000))
        clickAction("مرتجع للجهة / تصفية"); waitForOperationDialog(false); setField("المبلغ للعملية", "1000"); clickSaveOperation(); assertTrue(device.wait(Until.hasObject(By.text("متوازن 0")), 10_000))
        val custody = db.custodyDao().getCustodyByExternalId(externalId)!!; val tx = db.custodyDao().getAllTransactions(custody.id, false); assertEquals(2, tx.size); assertTrue(tx.any { it.type == CustodyTransactionType.RETURNED_TO_ORG }); assertEquals(0L, db.custodyDao().getOwnerAccount(custody.id, "YER")!!.balanceMinor)
    }

    @Test
    fun custodyPersonAndPersonOperationWorkEndToEndIncludingEditDeleteAndCurrencyChange() = runBlocking {
        openCustodyDetail(); clickAction("إضافة شخص"); waitForPersonDialog(); setField("الاسم", "اختبار شخص واجهة"); setField("الهاتف", "777000000"); setField("العنوان", "صنعاء"); setField("الملاحظات", "اختبار")
        hideKeyboard()
        clickClickable(first(By.desc("حفظ الشخص"), By.text("حفظ")) ?: error("Save person not found"), "Save person")
        assertTrue("Person did not appear in the custody UI after save", device.wait(Until.hasObject(By.text("اختبار شخص واجهة")), 10_000))
        assertTrue("Person dialog did not close", device.wait(Until.gone(By.desc("حوار إضافة شخص")), 10_000))
        val custody = db.custodyDao().getCustodyByExternalId(externalId)!!; val person = waitForPerson(custody.id, "اختبار شخص واجهة"); clickClickable(By.text("اختبار شخص واجهة"), "Custody person"); assertTrue(device.wait(Until.hasObject(By.text("صرف للشخص")), 10_000))
        clickAction("إضافة عملية"); waitForOperationDialog(false); setField("المبلغ للعملية", "250"); setField("بيان العملية", "صرف واجهة"); clickSaveOperation(); assertTrue(device.wait(Until.hasObject(By.text("عليه 250")), 10_000))
        var tx = db.custodyDao().getAllTransactions(custody.id, false); assertEquals(1, tx.size); assertEquals(person.id, tx.single().personId); assertEquals(CustodyTransactionType.PAID_TO_PERSON, tx.single().type); assertEquals("YER", tx.single().currencyCode); assertEquals(25000L, tx.single().amountMinor)
        clickAction("تعديل"); waitForOperationDialog(true); setField("المبلغ للعملية", "300"); clickSaveOperation(); tx = db.custodyDao().getAllTransactions(custody.id, false); assertEquals(30000L, tx.single().amountMinor)
        clickAction("تعديل"); waitForOperationDialog(true); clickClickable(By.text("SAR"), "Change operation currency"); clickClickable(By.text("مرتجع من الشخص"), "Change operation type"); clickSaveOperation(); tx = db.custodyDao().getAllTransactions(custody.id, false); assertEquals(1, tx.size); assertEquals("SAR", tx.single().currencyCode); assertEquals(CustodyTransactionType.RETURNED_FROM_PERSON, tx.single().type); assertEquals(30000L, tx.single().amountMinor); assertEquals(0L, db.custodyDao().getPersonAccount(custody.id, person.id, "YER")!!.balanceMinor); assertEquals(30000L, db.custodyDao().getOwnerAccount(custody.id, "SAR")!!.balanceMinor); assertEquals(-30000L, db.custodyDao().getPersonAccount(custody.id, person.id, "SAR")!!.balanceMinor)
        clickAction("تعديل"); waitForOperationDialog(true); clickClickable(By.text("مرتجع للجهة / تصفية"), "Change back to organization return"); clickSaveOperation(); tx = db.custodyDao().getAllTransactions(custody.id, false); assertEquals(CustodyTransactionType.RETURNED_TO_ORG, tx.single().type); assertEquals(0L, db.custodyDao().getOwnerAccount(custody.id, "SAR")!!.balanceMinor); assertEquals(0L, db.custodyDao().getPersonAccount(custody.id, person.id, "SAR")!!.balanceMinor)
        clickAction("حذف"); waitForText("حذف العملية"); clickClickable(By.text("حذف"), "Confirm delete"); assertTrue(waitForTransactions(custody.id).isEmpty())
    }

    private fun openCustodyDetail() { clickClickable(By.text("العُهَد"), "Custody gateway"); clickClickable(By.text(custodyName), "Custody card"); assertTrue("Custody detail not visible", device.wait(Until.hasObject(By.desc("شاشة تفاصيل العهدة")), 10_000)) }
    private fun waitForPersonDialog() { assertTrue(device.wait(Until.hasObject(By.desc("حوار إضافة شخص")), 10_000)); assertTrue(device.wait(Until.hasObject(By.desc("الاسم")), 5_000)); assertTrue(device.wait(Until.hasObject(By.desc("الهاتف")), 5_000)) }
    private fun waitForOperationDialog(editing: Boolean) { val selector = if (editing) By.text("تعديل العملية") else By.text("إضافة عملية"); assertTrue("Operation dialog not visible", device.wait(Until.hasObject(selector), 10_000)) }
    private fun setField(description: String, value: String) { val selector = By.desc(description); val target = device.wait(Until.findObject(selector), 3_000) ?: device.wait(Until.findObject(By.text(description)), 3_000) ?: error("Field '$description' not found"); clickClickable(target, "Field $description"); val focused = device.wait(Until.findObject(By.focused(true)), 3_000); val field = if (focused?.className == "android.widget.EditText") focused else device.findObjects(By.clazz("android.widget.EditText")).lastOrNull() ?: error("Editable field '$description' not found"); field.text = value; device.waitForIdle() }
    private fun clickSaveOperation() { hideKeyboard(); clickClickable(By.text("حفظ"), "Save operation"); assertTrue("Operation dialog did not close", device.wait(Until.gone(By.text("إضافة عملية")), 5_000) || device.wait(Until.gone(By.text("تعديل العملية")), 10_000)) }
    private fun hideKeyboard() { if (device.findObject(By.focused(true))?.className == "android.widget.EditText") { device.pressBack(); device.waitForIdle() } }
    private fun clickAction(text: String) { clickClickable(first(By.text(text), By.desc(text)) ?: error("Action '$text' not found"), text) }
    private fun waitForText(text: String) { assertTrue("Text '$text' not found", device.wait(Until.hasObject(By.text(text)), 10_000)) }
    private fun first(vararg selectors: BySelector): UiObject2? = selectors.firstNotNullOfOrNull { device.findObject(it) }
    private fun clickClickable(selector: BySelector, label: String) { clickClickable(device.wait(Until.findObject(selector), 10_000) ?: error("$label not found"), label) }
    private fun clickClickable(start: UiObject2, label: String) { var node: UiObject2? = start; repeat(8) { val current = node ?: return@repeat; if (current.isClickable) { current.click(); device.waitForIdle(); return }; node = runCatching { current.parent }.getOrNull() }; error("$label clickable ancestor not found") }
    private suspend fun waitForPerson(custodyId: Long, name: String): com.myaccounts.app.data.custody.CustodyPersonEntity { val until = System.currentTimeMillis() + 10_000L; while (System.currentTimeMillis() < until) { db.custodyDao().getAllPersons(custodyId).firstOrNull { it.name == name }?.let { return it }; Thread.sleep(100) }; return db.custodyDao().getAllPersons(custodyId).firstOrNull { it.name == name }.also { assertNotNull("Person was not persisted", it) }!! }
    private suspend fun waitForTransactions(custodyId: Long): List<com.myaccounts.app.data.custody.CustodyTransactionEntity> { val until = System.currentTimeMillis() + 10_000L; while (System.currentTimeMillis() < until) { val tx = db.custodyDao().getAllTransactions(custodyId, false); if (tx.isEmpty()) return tx; Thread.sleep(100) }; return db.custodyDao().getAllTransactions(custodyId, false) }
}
