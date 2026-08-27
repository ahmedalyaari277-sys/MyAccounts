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
        instrumentation.startActivitySync(Intent(context, MainActivity::class.java))
        device.waitForIdle()
    }

    @After
    fun tearDown() {
        clearTestData()
    }

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
        assertEquals(1, countAccountsForPerson(testPersonExternalId))
        assertEquals(123450L, balanceForPerson(testPersonExternalId, "YER"))
    }

    private fun saveDocumentThroughSystemPicker(fileName: String) {
        waitForDocumentsUi()
        val filename = findFilenameField()
        filename.clear()
        filename.text = fileName
        clickPickerAction("Save")
        device.wait(Until.gone(By.text("Save")), 5_000)
        device.waitForIdle()
        assertTrue("System picker did not close after saving Excel", waitForTextOptional("النسخ الاحتياطي والمزامنة", 5_000))
    }

    private fun openExportedDocumentThroughSystemPicker(fileName: String) {
        waitForDocumentsUi()
        val file = device.wait(Until.findObject(By.text(fileName)), 10_000)
            ?: findDocumentByDescription(fileName)
        assertNotNull("Exported Excel file was not visible in the system picker", file)
        file!!.click()
        device.waitForIdle()
    }

    private fun waitForDocumentsUi() {
        val packageName = "com.google.android.documentsui"
        device.wait(Until.hasObject(By.pkg(packageName)), 10_000)
        assertTrue("Android System File Picker did not open", device.hasObject(By.pkg(packageName)))
    }

    private fun findFilenameField(): UiObject2 {
        val candidates = listOf(
            By.res("com.google.android.documentsui:id/filename"),
            By.res("com.android.documentsui:id/filename")
        )
        candidates.forEach { selector ->
            device.wait(Until.hasObject(selector), 5_000)
            device.findObject(selector)?.let { return it }
        }
        error("System picker filename field was not found")
    }

    private fun clickPickerAction(label: String) {
        val exact = device.findObject(By.text(label))
            ?: device.findObject(By.textContains(label))
            ?: error("System picker action '$label' was not found")
        exact.click()
    }

    private fun findDocumentByDescription(fileName: String): UiObject2? =
        device.findObject(By.descContains(fileName))

    private fun clickByText(text: String) {
        val object2 = device.wait(Until.findObject(By.text(text)), 10_000)
            ?: error("Application text '$text' was not found")
        object2.click()
        device.waitForIdle()
    }

    private fun clickByDescription(description: String) {
        val object2 = device.wait(Until.findObject(By.desc(description)), 10_000)
            ?: error("Application content description '$description' was not found")
        object2.click()
        device.waitForIdle()
    }

    private fun waitForText(text: String) {
        assertTrue("Application text '$text' was not found", waitForTextOptional(text, 10_000))
    }

    private fun waitForTextOptional(text: String, timeoutMs: Long): Boolean =
        device.wait(Until.hasObject(By.text(text)), timeoutMs)

    private fun clearTestData() {
        val db = database.openHelper.writableDatabase
        db.execSQL("DELETE FROM transaction_attachments")
        db.execSQL("DELETE FROM transactions WHERE externalId IN (?, ?)", arrayOf(testTransactionExternalId, "T-M04-UI-ARCHIVED"))
        db.execSQL("DELETE FROM currency_accounts WHERE personId IN (SELECT id FROM people WHERE externalId IN (?, ?))", arrayOf(testPersonExternalId, archivedPersonExternalId))
        db.execSQL("DELETE FROM people WHERE externalId IN (?, ?)", arrayOf(testPersonExternalId, archivedPersonExternalId))
    }

    private fun seedTestData() {
        val db = database.openHelper.writableDatabase
        db.execSQL(
            "INSERT INTO people (id,name,phone,address,notes,createdAt,isActive,archivedAt,externalId) VALUES (?,?,?,?,?,?,?,?,?)",
            arrayOf(970001L, "اختبار واجهة Excel", "777000701", "صنعاء", "M04 UI", 2000L, 1, null, testPersonExternalId)
        )
        db.execSQL(
            "INSERT INTO currency_accounts (id,personId,currencyCode,balanceMinor,createdAt,updatedAt) VALUES (?,?,?,?,?,?)",
            arrayOf(980001L, 970001L, "YER", 123450L, 2001L, 2002L)
        )
        db.execSQL(
            "INSERT INTO transactions (id,accountId,type,amountMinor,description,transactionDate,createdAt,externalId) VALUES (?,?,?,?,?,?,?,?)",
            arrayOf(990001L, 980001L, "RECEIVABLE", 123450L, "عملية اختبار واجهة Excel", 2003L, 2004L, testTransactionExternalId)
        )
        db.execSQL(
            "INSERT INTO people (id,name,phone,address,notes,createdAt,isActive,archivedAt,externalId) VALUES (?,?,?,?,?,?,?,?,?)",
            arrayOf(970002L, "مؤرشف لا يجب تصديره", "777000702", "تعز", "Archived", 2005L, 0, 2006L, archivedPersonExternalId)
        )
    }

    private fun deleteActiveTestData() {
        val db = database.openHelper.writableDatabase
        db.execSQL("DELETE FROM transaction_attachments")
        db.execSQL("DELETE FROM transactions WHERE externalId=?", arrayOf(testTransactionExternalId))
        db.execSQL("DELETE FROM currency_accounts WHERE personId IN (SELECT id FROM people WHERE externalId=?)", arrayOf(testPersonExternalId))
        db.execSQL("DELETE FROM people WHERE externalId=? AND isActive=1", arrayOf(testPersonExternalId))
    }

    private fun countPeopleByExternalId(id: String): Int = database.openHelper.writableDatabase
        .query("SELECT COUNT(*) FROM people WHERE externalId=?", arrayOf(id)).use { c -> c.moveToFirst(); c.getInt(0) }

    private fun countTransactionsByExternalId(id: String): Int = database.openHelper.writableDatabase
        .query("SELECT COUNT(*) FROM transactions WHERE externalId=?", arrayOf(id)).use { c -> c.moveToFirst(); c.getInt(0) }

    private fun countAccountsForPerson(externalId: String): Int = database.openHelper.writableDatabase
        .query("SELECT COUNT(*) FROM currency_accounts WHERE personId=(SELECT id FROM people WHERE externalId=?)", arrayOf(externalId)).use { c -> c.moveToFirst(); c.getInt(0) }

    private fun balanceForPerson(externalId: String, currency: String): Long = database.openHelper.writableDatabase
        .query("SELECT balanceMinor FROM currency_accounts WHERE personId=(SELECT id FROM people WHERE externalId=?) AND currencyCode=?", arrayOf(externalId, currency)).use { c -> assertTrue(c.moveToFirst()); c.getLong(0) }
}
