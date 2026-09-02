package com.myaccounts.app.excel

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.myaccounts.app.MainActivity
import com.myaccounts.app.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExcelImportExportUiInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)
    private val database: AppDatabase get() = AppDatabase.getInstance(context)
    private val testPersonExternalId = "P-M04-UI-001"
    private val testTransactionExternalId = "T-M04-UI-001"
    private val archivedPersonExternalId = "P-M04-UI-ARCHIVED"
    private val exportFileName = "MyAccounts_M04_UI_Test.xlsx"

    @Before
    fun setUp() {
        clearTestData()
        seedTestData()
        val intent = Intent(context, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        instrumentation.startActivitySync(intent)
        device.waitForIdle()
        clickByText("دفتر الحسابات")
    }

    @After
    fun tearDown() = clearTestData()

    @Test
    fun exportThenImportThroughRealUiAndSystemPickerRestoresOnlyActiveData() {
        clickByDescription("النسخ الاحتياطي والاستعادة")
        waitForText("النسخ الاحتياطي والمزامنة")
        clickByText("تصدير البيانات إلى Excel")
        saveDocumentThroughSystemPicker(exportFileName)
        waitForText("تم تصدير البيانات النشطة بنجاح.")
        clickByText("موافق")

        deleteActiveTestData()
        assertEquals(1, countPeopleByExternalId(archivedPersonExternalId))
        assertEquals(0, countPeopleByExternalId(testPersonExternalId))
        assertEquals(0, countTransactionsByExternalId(testTransactionExternalId))

        clickByText("استيراد البيانات من Excel")
        openExportedDocumentThroughSystemPicker(exportFileName)
        waitForText("مراجعة ملف Excel")
        waitForText("الأشخاص: 1")
        waitForText("الحسابات: 1")
        waitForText("العمليات: 1")
        clickByText("استيراد")
        waitForText("تم الاستيراد بنجاح.")
        clickByText("موافق")
        instrumentation.waitForIdleSync()

        assertEquals(1, countPeopleByExternalId(testPersonExternalId))
        assertEquals(1, countPeopleByExternalId(archivedPersonExternalId))
        assertEquals(1, countTransactionsByExternalId(testTransactionExternalId))
        assertEquals(3, countAccountsForPerson(testPersonExternalId))
        assertEquals(1, countAccountsForPersonAndCurrency(testPersonExternalId, "YER"))
        assertEquals(1, countAccountsForPersonAndCurrency(testPersonExternalId, "SAR"))
        assertEquals(1, countAccountsForPersonAndCurrency(testPersonExternalId, "USD"))
        assertEquals(123450L, balanceForPerson(testPersonExternalId, "YER"))
    }

    private fun saveDocumentThroughSystemPicker(fileName: String) {
        waitForDocumentsUi()
        val filename = findFilenameField()
        filename.clear()
        filename.text = fileName
        val action = findPickerSaveAction()
        if (action != null) {
            action.click()
        } else {
            filename.click()
            device.pressEnter()
        }
        device.waitForIdle()
        assertTrue(
            "System picker did not finish saving Excel",
            device.wait(Until.gone(By.pkg("com.google.android.documentsui")), 10_000)
        )
        assertTrue(
            "MyAccounts activity did not resume after saving Excel",
            device.wait(Until.hasObject(By.pkg("com.myaccounts.app")), 10_000)
        )
    }

    private fun openExportedDocumentThroughSystemPicker(fileName: String) {
        waitForDocumentsUi()
        val file = device.wait(Until.findObject(By.text(fileName)), 10_000) ?: findDocumentByDescription(fileName)
        assertNotNull("Exported Excel file was not visible in the system picker", file)
        file!!.click()
        device.waitForIdle()
    }

    private fun waitForDocumentsUi() {
        assertTrue("Android System File Picker did not open", device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 10_000))
    }

    private fun findFilenameField(): UiObject2 {
        val resourceCandidates = listOf(By.res("com.google.android.documentsui:id/filename"), By.res("com.android.documentsui:id/filename"))
        resourceCandidates.forEach { selector -> device.wait(Until.hasObject(selector), 2_000); device.findObject(selector)?.let { return it } }
        val editTexts = device.findObjects(By.clazz("android.widget.EditText"))
        if (editTexts.size == 1) return editTexts.first()
        editTexts.firstOrNull { it.isEnabled && it.isFocusable && it.isClickable }?.let { return it }
        error("System picker filename field was not found; EditText count=${editTexts.size}")
    }

    private fun findPickerSaveAction(): UiObject2? {
        val labels = listOf("Save", "حفظ", "حفظ الملف", "Guardar", "Enregistrer")
        labels.forEach { label ->
            device.findObject(By.text(label))?.let { if (it.isEnabled) return it }
            device.findObject(By.textContains(label))?.let { if (it.isEnabled) return it }
            device.findObject(By.desc(label))?.let { if (it.isEnabled) return it }
            device.findObject(By.descContains(label))?.let { if (it.isEnabled) return it }
        }
        return null
    }

    private fun findDocumentByDescription(fileName: String): UiObject2? = device.findObject(By.descContains(fileName))
    private fun clickByText(text: String) { val object2 = device.wait(Until.findObject(By.text(text)), 10_000) ?: error("Application text '$text' was not found"); clickClickable(object2, "Application text '$text'") }
    private fun clickByDescription(description: String) { val object2 = device.wait(Until.findObject(By.desc(description)), 10_000) ?: device.wait(Until.findObject(By.descContains(description)), 5_000) ?: error("Application content description '$description' was not found"); clickClickable(object2, "Application content description '$description'") }
    private fun waitForText(text: String) = assertTrue("Application text '$text' was not found", waitForTextOptional(text, 15_000))
    private fun waitForTextOptional(text: String, timeoutMs: Long): Boolean = device.wait(Until.hasObject(By.text(text)), timeoutMs) || device.wait(Until.hasObject(By.textContains(text)), timeoutMs)
    private fun clickClickable(start: UiObject2, label: String) {
        var node: UiObject2? = start
        repeat(8) {
            val current = node ?: return@repeat
            if (current.isClickable) { current.click(); device.waitForIdle(); return }
            node = runCatching { current.parent }.getOrNull()
        }
        error("$label clickable ancestor not found")
    }

    private fun clearTestData() {
        val db = database.openHelper.writableDatabase
        db.execSQL("DELETE FROM transaction_attachments")
        db.execSQL("DELETE FROM transactions WHERE externalId IN (?, ?)", arrayOf(testTransactionExternalId, "T-M04-UI-ARCHIVED"))
        db.execSQL("DELETE FROM currency_accounts WHERE personId IN (SELECT id FROM people WHERE externalId IN (?, ?))", arrayOf(testPersonExternalId, archivedPersonExternalId))
        db.execSQL("DELETE FROM people WHERE externalId IN (?, ?)", arrayOf(testPersonExternalId, archivedPersonExternalId))
    }

    private fun seedTestData() {
        val db = database.openHelper.writableDatabase
        db.execSQL("INSERT INTO people (id,name,phone,address,notes,createdAt,isActive,archivedAt,externalId) VALUES (?,?,?,?,?,?,?,?,?)", arrayOf(970001L, "اختبار واجهة Excel", "777000701", "صنعاء", "M04 UI", 2000L, 1, null, testPersonExternalId))
        db.execSQL("INSERT INTO currency_accounts (id,personId,currencyCode,balanceMinor,createdAt,updatedAt) VALUES (?,?,?,?,?,?)", arrayOf(980001L, 970001L, "YER", 123450L, 2001L, 2002L))
        db.execSQL("INSERT INTO transactions (id,accountId,type,amountMinor,description,transactionDate,createdAt,externalId) VALUES (?,?,?,?,?,?,?,?)", arrayOf(990001L, 980001L, "RECEIVABLE", 123450L, "عملية اختبار واجهة Excel", 2003L, 2004L, testTransactionExternalId))
        db.execSQL("INSERT INTO people (id,name,phone,address,notes,createdAt,isActive,archivedAt,externalId) VALUES (?,?,?,?,?,?,?,?,?)", arrayOf(970002L, "مؤرشف لا يجب تصديره", "777000702", "تعز", "Archived", 2005L, 0, 2006L, archivedPersonExternalId))
    }

    private fun deleteActiveTestData() {
        val db = database.openHelper.writableDatabase
        db.execSQL("DELETE FROM transaction_attachments")
        db.execSQL("DELETE FROM transactions WHERE externalId=?", arrayOf(testTransactionExternalId))
        db.execSQL("DELETE FROM currency_accounts WHERE personId IN (SELECT id FROM people WHERE externalId=?)", arrayOf(testPersonExternalId))
        db.execSQL("DELETE FROM people WHERE externalId=? AND isActive=1", arrayOf(testPersonExternalId))
    }

    private fun countPeopleByExternalId(id: String): Int = database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM people WHERE externalId=?", arrayOf(id)).use { c -> c.moveToFirst(); c.getInt(0) }
    private fun countTransactionsByExternalId(id: String): Int = database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM transactions WHERE externalId=?", arrayOf(id)).use { c -> c.moveToFirst(); c.getInt(0) }
    private fun countAccountsForPerson(externalId: String): Int = database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM currency_accounts WHERE personId=(SELECT id FROM people WHERE externalId=?)", arrayOf(externalId)).use { c -> c.moveToFirst(); c.getInt(0) }
    private fun countAccountsForPersonAndCurrency(externalId: String, currency: String): Int = database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM currency_accounts WHERE personId=(SELECT id FROM people WHERE externalId=?) AND currencyCode=?", arrayOf(externalId, currency)).use { c -> c.moveToFirst(); c.getInt(0) }
    private fun balanceForPerson(externalId: String, currency: String): Long = database.openHelper.writableDatabase.query("SELECT balanceMinor FROM currency_accounts WHERE personId=(SELECT id FROM people WHERE externalId=?) AND currencyCode=?", arrayOf(externalId, currency)).use { c -> assertTrue(c.moveToFirst()); c.getLong(0) }
}
