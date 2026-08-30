package com.myaccounts.app.backup

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
import com.myaccounts.app.util.DatabaseBackupManager
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val db get() = AppDatabase.getInstance(context)
    private val device get() = UiDevice.getInstance(instrumentation)
    private var backupUri: Uri? = null

    @After
    fun cleanup() {
        backupUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        val database = db.openHelper.writableDatabase
        database.execSQL("DELETE FROM transaction_attachments")
        database.execSQL("DELETE FROM transactions")
        database.execSQL("DELETE FROM currency_accounts")
        database.execSQL("DELETE FROM people")
        runCatching { device.pressBack() }
    }

    @Test
    fun backupAndRestoreRoundTripRestoresPeopleAccountsTransactionsAndBalances() = runBlocking {
        seedPersonAndTransaction(910001L, 920001L, 930001L, "اختبار الاستعادة", "عملية أصلية", 125000L)
        val uri = createBackupUri("m03_roundtrip_${System.currentTimeMillis()}.myaccounts")
        backupUri = uri

        assertTrue(DatabaseBackupManager.createBackup(context, uri).isSuccess)
        publish(uri)
        db.openHelper.writableDatabase.execSQL("UPDATE people SET name=? WHERE id=?", arrayOf("بيانات معدلة", 910001L))
        db.openHelper.writableDatabase.execSQL("UPDATE currency_accounts SET balanceMinor=? WHERE id=?", arrayOf(999L, 920001L))
        db.openHelper.writableDatabase.execSQL("UPDATE transactions SET description=? WHERE id=?", arrayOf("بيانات معدلة", 930001L))

        val result = DatabaseBackupManager.restoreBackup(context, uri)
        assertTrue("Restore failed: ${result.exceptionOrNull()}", result.isSuccess)
        assertRestored(910001L, 920001L, 930001L, "اختبار الاستعادة", "عملية أصلية", 125000L)
    }

    @Test
    fun restoreLegacyFormatV3WithoutArchiveOrExternalIds() = runBlocking {
        val database = db.openHelper.writableDatabase
        val legacy = JSONObject()
            .put("backupType", "myaccounts_full_backup")
            .put("formatVersion", 3)
            .put("createdAt", 3000L)
            .put("people", JSONArray().put(JSONObject().apply {
                put("id", 940001L); put("name", "شخص من النسخة القديمة"); put("phone", "0522222222");
                put("address", "عنوان قديم"); put("notes", "ملاحظة قديمة"); put("createdAt", 3001L); put("isActive", true)
            }))
            .put("currencyAccounts", JSONArray().put(JSONObject().apply {
                put("id", 950001L); put("personId", 940001L); put("currencyCode", "YER");
                put("balanceMinor", 375000L); put("createdAt", 3002L); put("updatedAt", 3003L)
            }))
            .put("transactions", JSONArray().put(JSONObject().apply {
                put("id", 960001L); put("accountId", 950001L); put("type", "RECEIVABLE");
                put("amountMinor", 375000L); put("description", "عملية من النسخة القديمة"); put("transactionDate", 3004L); put("createdAt", 3005L)
            }))
            .put("attachments", JSONArray())

        val uri = createBackupUri("m03_legacy_${System.currentTimeMillis()}.myaccounts")
        backupUri = uri
        context.contentResolver.openOutputStream(uri)?.use { it.write(legacy.toString().toByteArray()) } ?: error("Could not write legacy backup")
        publish(uri)

        val result = DatabaseBackupManager.restoreBackup(context, uri)
        assertTrue("Legacy restore failed: ${result.exceptionOrNull()}", result.isSuccess)
        database.query("SELECT name,archivedAt,externalId FROM people WHERE id=940001").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("شخص من النسخة القديمة", c.getString(0)); assertTrue(c.isNull(1)); assertEquals("P-940001", c.getString(2))
        }
        database.query("SELECT balanceMinor FROM currency_accounts WHERE id=950001").use { c -> assertTrue(c.moveToFirst()); assertEquals(375000L, c.getLong(0)) }
        database.query("SELECT amountMinor,description,externalId FROM transactions WHERE id=960001").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(375000L, c.getLong(0)); assertEquals("عملية من النسخة القديمة", c.getString(1)); assertEquals("T-960001", c.getString(2))
        }
    }

    @Test
    fun restoreBackupThroughRealPhoneUiAndSystemFilePicker() = runBlocking {
        seedPersonAndTransaction(910101L, 920101L, 930101L, "اختبار الهاتف", "عملية الهاتف الأصلية", 275000L)
        val uri = createBackupUri("m03_phone_${System.currentTimeMillis()}.myaccounts")
        backupUri = uri
        assertTrue(DatabaseBackupManager.createBackup(context, uri).isSuccess)
        publish(uri)
        val fileName = queryDisplayName(uri)
        db.openHelper.writableDatabase.execSQL("UPDATE people SET name=? WHERE id=?", arrayOf("بيانات الهاتف المعدلة", 910101L))

        instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue("MyAccounts did not become visible", device.wait(Until.hasObject(By.pkg(context.packageName)), 15_000))
        clickClickable(By.text("دفتر الحسابات"), "Ledger gateway")
        clickClickable(By.desc("النسخ الاحتياطي والاستعادة"), "Backup/restore action")
        assertTrue("Backup restore screen did not open", device.wait(Until.hasObject(By.text("النسخ الاحتياطي والمزامنة")), 10_000))
        clickClickable(By.text("استعادة نسخة احتياطية"), "Restore backup action")
        assertTrue("System file picker did not open", device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 10_000))
        selectBackupFile(fileName)
        assertTrue("Restore confirmation did not appear", device.wait(Until.hasObject(By.text("تأكيد الاستعادة")), 10_000))
        clickClickable(By.text("استعادة"), "Restore confirmation")
        assertTrue("Restore success dialog did not appear", device.wait(Until.hasObject(By.textContains("تمت استعادة النسخة الاحتياطية")), 15_000))
        assertRestored(910101L, 920101L, 930101L, "اختبار الهاتف", "عملية الهاتف الأصلية", 275000L)
    }

    private fun seedPersonAndTransaction(personId: Long, accountId: Long, txId: Long, name: String, description: String, balance: Long) {
        val database = db.openHelper.writableDatabase
        database.execSQL("DELETE FROM transaction_attachments")
        database.execSQL("DELETE FROM transactions")
        database.execSQL("DELETE FROM currency_accounts")
        database.execSQL("DELETE FROM people")
        database.execSQL("INSERT INTO people (id,name,phone,address,notes,createdAt,isActive,archivedAt,externalId) VALUES (?,?,?,?,?,?,?,?,?)", arrayOf(personId, name, "0500000000", "العنوان", "ملاحظة", 1000L, 1, null, "P-$personId"))
        database.execSQL("INSERT INTO currency_accounts (id,personId,currencyCode,balanceMinor,createdAt,updatedAt) VALUES (?,?,?,?,?,?)", arrayOf(accountId, personId, "YER", balance, 1001L, 1002L))
        database.execSQL("INSERT INTO transactions (id,accountId,type,amountMinor,description,transactionDate,createdAt,externalId) VALUES (?,?,?,?,?,?,?,?)", arrayOf(txId, accountId, "RECEIVABLE", balance, description, 1003L, 1004L, "T-$txId"))
    }

    private fun assertRestored(personId: Long, accountId: Long, txId: Long, name: String, description: String, amount: Long) {
        db.openHelper.writableDatabase.query("SELECT name,balanceMinor FROM people LEFT JOIN currency_accounts ON people.id=currency_accounts.personId WHERE people.id=?", arrayOf(personId.toString())).use { c ->
            assertTrue(c.moveToFirst()); assertEquals(name, c.getString(0)); assertEquals(amount, c.getLong(1))
        }
        db.openHelper.writableDatabase.query("SELECT accountId,type,amountMinor,description FROM transactions WHERE id=?", arrayOf(txId.toString())).use { c ->
            assertTrue(c.moveToFirst()); assertEquals(accountId, c.getLong(0)); assertEquals("RECEIVABLE", c.getString(1)); assertEquals(amount, c.getLong(2)); assertEquals(description, c.getString(3))
        }
    }

    private fun createBackupUri(fileName: String): Uri {
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName); put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream"); put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MyAccounts"); put(MediaStore.Downloads.IS_PENDING, 1)
        }) ?: error("Could not create backup URI")
    }

    private fun publish(uri: Uri) { context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) }

    private fun queryDisplayName(uri: Uri): String = context.contentResolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { c -> assertTrue(c.moveToFirst()); c.getString(0) } ?: error("Could not query display name")

    private fun selectBackupFile(fileName: String) {
        navigatePickerToMyAccounts()
        val file = first(By.text(fileName), By.textContains(fileName))
        clickClickable(file ?: error("Backup file '$fileName' not found in DocumentsUI"), "Backup file")
    }

    private fun navigatePickerToMyAccounts() {
        first(By.desc("Show roots"), By.desc("Show roots drawer"), By.res("com.google.android.documentsui:id/toolbar_nav_button"))?.let { clickClickable(it, "DocumentsUI roots") }
        first(By.text("Downloads"), By.textContains("Downloads"))?.let { clickClickable(it, "Downloads") }
        first(By.text("MyAccounts"), By.textContains("MyAccounts"))?.let { clickClickable(it, "MyAccounts folder") }
    }

    private fun first(vararg selectors: BySelector): BySelector? { selectors.firstOrNull { device.findObject(it) != null }?.let { return it }; return null }

    private fun clickClickable(selector: BySelector, label: String) {
        val target = device.wait(Until.findObject(selector), 10_000) ?: error("$label not found")
        clickClickable(target, label)
    }

    private fun clickClickable(start: UiObject2, label: String) {
        var current: UiObject2? = start
        repeat(8) {
            val node = current ?: return@repeat
            if (node.isClickable) { node.click(); device.waitForIdle(); return }
            current = runCatching { node.parent }.getOrNull()
        }
        error("$label clickable ancestor not found")
    }
}
