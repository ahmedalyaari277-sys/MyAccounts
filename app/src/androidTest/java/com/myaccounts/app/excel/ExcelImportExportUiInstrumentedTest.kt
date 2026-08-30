package com.myaccounts.app.excel

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.myaccounts.app.MainActivity
import com.myaccounts.app.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExcelImportExportUiInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val db get() = AppDatabase.getInstance(context)
    private val device get() = UiDevice.getInstance(instrumentation)
    private val activeExternalId = "P-M04-UI-001"
    private val archivedExternalId = "P-M04-UI-ARCHIVED"
    private val transactionExternalId = "T-M04-UI-001"
    private var excelUri: Uri? = null

    @Before
    fun setUp() {
        clearData()
        val w = db.openHelper.writableDatabase
        w.execSQL("INSERT INTO people (id,name,phone,address,notes,createdAt,isActive,archivedAt,externalId) VALUES (?,?,?,?,?,?,?,?,?)", arrayOf(970001L, "اختبار واجهة Excel", "777000701", "صنعاء", "M04 UI", 2000L, 1, null, activeExternalId))
        w.execSQL("INSERT INTO currency_accounts (id,personId,currencyCode,balanceMinor,createdAt,updatedAt) VALUES (?,?,?,?,?,?)", arrayOf(980001L, 970001L, "YER", 123450L, 2001L, 2002L))
        w.execSQL("INSERT INTO transactions (id,accountId,type,amountMinor,description,transactionDate,createdAt,externalId) VALUES (?,?,?,?,?,?,?,?)", arrayOf(990001L, 980001L, "RECEIVABLE", 123450L, "عملية اختبار واجهة Excel", 2003L, 2004L, transactionExternalId))
        w.execSQL("INSERT INTO people (id,name,phone,address,notes,createdAt,isActive,archivedAt,externalId) VALUES (?,?,?,?,?,?,?,?,?)", arrayOf(970002L, "مؤرشف لا يجب تصديره", "777000702", "تعز", "Archived", 2005L, 0, 2006L, archivedExternalId))
        instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        device.waitForIdle()
    }

    @After
    fun tearDown() { excelUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }; clearData() }

    @Test
    fun exportThenImportThroughRealUiAndSystemPickerRestoresOnlyActiveData() {
        clickClickable(By.text("دفتر الحسابات"), "Ledger gateway")
        clickClickable(By.desc("النسخ الاحتياطي والاستعادة"), "Backup/restore")
        assertTrue("Backup screen did not open", device.wait(Until.hasObject(By.text("النسخ الاحتياطي والمزامنة")), 10_000))

        clickClickable(By.text("تصدير البيانات إلى Excel"), "Export Excel")
        saveDocumentAs("MyAccounts_M04_UI_Test.xlsx")
        waitForTextContains("تم تصدير البيانات النشطة بنجاح.")
        dismissMessage()

        val w = db.openHelper.writableDatabase
        w.execSQL("DELETE FROM transaction_attachments")
        w.execSQL("DELETE FROM transactions WHERE externalId=?", arrayOf(transactionExternalId))
        w.execSQL("DELETE FROM currency_accounts WHERE personId=(SELECT id FROM people WHERE externalId=?)", arrayOf(activeExternalId))
        w.execSQL("DELETE FROM people WHERE externalId=? AND isActive=1", arrayOf(activeExternalId))
        assertEquals(1, countPeople(archivedExternalId))
        assertEquals(0, countPeople(activeExternalId))

        clickClickable(By.text("استيراد البيانات من Excel"), "Import Excel")
        openDocument("MyAccounts_M04_UI_Test.xlsx")
        waitForTextContains("مراجعة ملف Excel")
        waitForTextContains("الأشخاص: 1")
        waitForTextContains("الحسابات: 1")
        waitForTextContains("العمليات: 1")
        clickClickable(By.text("استيراد"), "Confirm Excel import")
        waitForTextContains("تم الاستيراد بنجاح.")
        dismissMessage()

        assertEquals(1, countPeople(activeExternalId))
        assertEquals(1, countPeople(archivedExternalId))
        assertEquals(1, countTransactions(transactionExternalId))
        assertEquals(3, countAccounts(activeExternalId))
        assertEquals(123450L, balance(activeExternalId, "YER"))
    }

    private fun saveDocumentAs(fileName: String) {
        waitForDocumentsUi()
        val field = waitFor(By.res("com.google.android.documentsui:id/filename"), 2_000)
            ?: waitFor(By.res("com.android.documentsui:id/filename"), 2_000)
            ?: device.findObjects(By.clazz("android.widget.EditText")).firstOrNull()
            ?: error("DocumentsUI filename field was not found")
        field.text = fileName
        val save = firstObject(By.text("Save"), By.text("حفظ"), By.textContains("Save"), By.textContains("حفظ"), By.desc("Save"), By.desc("حفظ"))
        if (save != null) saveClickable(save, "DocumentsUI save") else { field.click(); device.pressEnter() }
        assertTrue("DocumentsUI did not close after save", device.wait(Until.gone(By.pkg("com.google.android.documentsui")), 10_000))
        device.waitForIdle()
    }

    private fun openDocument(fileName: String) {
        waitForDocumentsUi()
        val existing = firstObject(By.text(fileName), By.textContains(fileName), By.descContains(fileName))
        if (existing != null) { saveClickable(existing, "Excel file") ; return }
        firstObject(By.desc("Show roots"), By.desc("Show roots drawer"), By.res("com.google.android.documentsui:id/toolbar_nav_button"))?.let { saveClickable(it, "DocumentsUI roots") }
        firstObject(By.text("Downloads"), By.textContains("Downloads"))?.let { saveClickable(it, "Downloads") }
        firstObject(By.text("MyAccounts"), By.textContains("MyAccounts"))?.let { saveClickable(it, "MyAccounts") }
        val file = firstObject(By.text(fileName), By.textContains(fileName), By.descContains(fileName))
            ?: error("Excel file '$fileName' was not found in DocumentsUI")
        saveClickable(file, "Excel file")
        device.waitForIdle()
    }

    private fun dismissMessage() { firstObject(By.text("موافق"))?.let { saveClickable(it, "Dismiss result") } }
    private fun waitForDocumentsUi() = assertTrue("DocumentsUI did not open", device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 10_000))
    private fun waitForTextContains(text: String) = assertTrue("Application text '$text' was not found", device.wait(Until.hasObject(By.textContains(text)), 15_000))
    private fun clickClickable(selector: BySelector, label: String) { saveClickable(device.wait(Until.findObject(selector), 10_000) ?: error("$label not found"), label) }
    private fun saveClickable(start: UiObject2, label: String) { var node: UiObject2? = start; repeat(8) { val current = node ?: return@repeat; if (current.isClickable) { current.click(); device.waitForIdle(); return }; node = runCatching { current.parent }.getOrNull() }; error("$label clickable ancestor not found") }
    private fun waitFor(selector: BySelector, timeout: Long): UiObject2? = device.wait(Until.findObject(selector), timeout)
    private fun firstObject(vararg selectors: BySelector): UiObject2? = selectors.firstNotNullOfOrNull { device.findObject(it) }

    private fun clearData() {
        val w = db.openHelper.writableDatabase
        w.execSQL("DELETE FROM transaction_attachments")
        w.execSQL("DELETE FROM transactions WHERE externalId IN (?,?)", arrayOf(transactionExternalId, "T-M04-UI-ARCHIVED"))
        w.execSQL("DELETE FROM currency_accounts WHERE personId IN (SELECT id FROM people WHERE externalId IN (?,?))", arrayOf(activeExternalId, archivedExternalId))
        w.execSQL("DELETE FROM people WHERE externalId IN (?,?)", arrayOf(activeExternalId, archivedExternalId))
    }

    private fun countPeople(externalId: String): Int = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM people WHERE externalId=?", arrayOf(externalId)).use { it.moveToFirst(); it.getInt(0) }
    private fun countTransactions(externalId: String): Int = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM transactions WHERE externalId=?", arrayOf(externalId)).use { it.moveToFirst(); it.getInt(0) }
    private fun countAccounts(externalId: String): Int = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM currency_accounts WHERE personId=(SELECT id FROM people WHERE externalId=?)", arrayOf(externalId)).use { it.moveToFirst(); it.getInt(0) }
    private fun balance(externalId: String, currency: String): Long = db.openHelper.writableDatabase.query("SELECT balanceMinor FROM currency_accounts WHERE personId=(SELECT id FROM people WHERE externalId=?) AND currencyCode=?", arrayOf(externalId, currency)).use { assertTrue(it.moveToFirst()); it.getLong(0) }
}
