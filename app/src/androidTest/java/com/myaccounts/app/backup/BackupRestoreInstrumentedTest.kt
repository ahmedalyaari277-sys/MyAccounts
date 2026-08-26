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
import androidx.test.uiautomator.Until
import com.myaccounts.app.MainActivity
import com.myaccounts.app.data.local.AppDatabase
import com.myaccounts.app.util.DatabaseBackupManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreInstrumentedTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context: Context
        get() = instrumentation.targetContext

    private val database: AppDatabase
        get() = AppDatabase.getInstance(context)

    private var backupUri: Uri? = null
    private var device: UiDevice? = null

    @After
    fun cleanup() {
        backupUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        database.openHelper.writableDatabase.execSQL("DELETE FROM transaction_attachments")
        database.openHelper.writableDatabase.execSQL("DELETE FROM transactions")
        database.openHelper.writableDatabase.execSQL("DELETE FROM currency_accounts")
        database.openHelper.writableDatabase.execSQL("DELETE FROM people")
        device?.pressBack()
    }

    @Test
    fun backupAndRestoreRoundTripRestoresPeopleAccountsTransactionsAndBalances() = runBlocking {
        val db = database.openHelper.writableDatabase
        db.execSQL("DELETE FROM transaction_attachments")
        db.execSQL("DELETE FROM transactions")
        db.execSQL("DELETE FROM currency_accounts")
        db.execSQL("DELETE FROM people")

        db.execSQL(
            "INSERT INTO people (id,name,phone,address,notes,createdAt,isActive,archivedAt) VALUES (?,?,?,?,?,?,?,?)",
            arrayOf(910001L, "اختبار الاستعادة", "0500000000", "عنوان الاختبار", "ملاحظة الاختبار", 1000L, 1, null)
        )
        db.execSQL(
            "INSERT INTO currency_accounts (id,personId,currencyCode,balanceMinor,createdAt,updatedAt) VALUES (?,?,?,?,?,?)",
            arrayOf(920001L, 910001L, "YER", 125000L, 1001L, 1002L)
        )
        db.execSQL(
            "INSERT INTO transactions (id,accountId,type,amountMinor,description,transactionDate,createdAt) VALUES (?,?,?,?,?,?,?)",
            arrayOf(930001L, 920001L, "RECEIVABLE", 125000L, "عملية أصلية", 1003L, 1004L)
        )

        backupUri = createBackupUri("m03_restore_${System.currentTimeMillis()}.myaccounts")
        val createResult = DatabaseBackupManager.createBackup(context, backupUri!!)
        assertTrue("Backup creation failed: ${createResult.exceptionOrNull()}", createResult.isSuccess)
        publishBackupUri(backupUri!!)
        assertTrue("Backup file is empty", backupSize(backupUri!!) > 0L)

        db.execSQL("UPDATE people SET name=? WHERE id=?", arrayOf("بيانات معدلة", 910001L))
        db.execSQL("UPDATE currency_accounts SET balanceMinor=? WHERE id=?", arrayOf(999L, 920001L))
        db.execSQL("UPDATE transactions SET description=? WHERE id=?", arrayOf("بيانات معدلة", 930001L))

        val restoreResult = DatabaseBackupManager.restoreBackup(context, backupUri!!)
        assertTrue("Restore failed: ${restoreResult.exceptionOrNull()}", restoreResult.isSuccess)

        assertRestoredData(db)
    }

    @Test
    fun restoreBackupThroughRealPhoneUiAndSystemFilePicker() = runBlocking {
        val db = database.openHelper.writableDatabase
        db.execSQL("DELETE FROM transaction_attachments")
        db.execSQL("DELETE FROM transactions")
        db.execSQL("DELETE FROM currency_accounts")
        db.execSQL("DELETE FROM people")

        db.execSQL(
            "INSERT INTO people (id,name,phone,address,notes,createdAt,isActive,archivedAt) VALUES (?,?,?,?,?,?,?,?)",
            arrayOf(910101L, "اختبار الهاتف", "0511111111", "عنوان الهاتف", "ملاحظة الهاتف", 2000L, 1, null)
        )
        db.execSQL(
            "INSERT INTO currency_accounts (id,personId,currencyCode,balanceMinor,createdAt,updatedAt) VALUES (?,?,?,?,?,?)",
            arrayOf(920101L, 910101L, "YER", 275000L, 2001L, 2002L)
        )
        db.execSQL(
            "INSERT INTO transactions (id,accountId,type,amountMinor,description,transactionDate,createdAt) VALUES (?,?,?,?,?,?,?)",
            arrayOf(930101L, 920101L, "RECEIVABLE", 275000L, "عملية الهاتف الأصلية", 2003L, 2004L)
        )

        backupUri = createBackupUri("m03_phone_restore_${System.currentTimeMillis()}.myaccounts")
        val createResult = DatabaseBackupManager.createBackup(context, backupUri!!)
        assertTrue("Backup creation failed: ${createResult.exceptionOrNull()}", createResult.isSuccess)
        publishBackupUri(backupUri!!)
        assertTrue("Backup file is empty", backupSize(backupUri!!) > 0L)
        val backupFileName = queryDisplayName(backupUri!!)

        db.execSQL("UPDATE people SET name=? WHERE id=?", arrayOf("بيانات الهاتف المعدلة", 910101L))
        db.execSQL("UPDATE currency_accounts SET balanceMinor=? WHERE id=?", arrayOf(999L, 920101L))
        db.execSQL("UPDATE transactions SET description=? WHERE id=?", arrayOf("بيانات الهاتف المعدلة", 930101L))

        device = UiDevice.getInstance(instrumentation)
        val ui = device!!
        ui.pressHome()
        instrumentation.targetContext.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        assertTrue("MyAccounts did not become visible", ui.wait(Until.hasObject(By.pkg(context.packageName)), 15_000))

        clickFresh(ui, By.desc("النسخ الاحتياطي والاستعادة"), "Backup/restore button")

        assertTrue(
            "Backup restore screen did not open",
            ui.wait(Until.hasObject(By.text("استعادة نسخة احتياطية")), 10_000)
        )

        clickFresh(ui, By.text("استعادة نسخة احتياطية"), "Restore backup button")

        assertTrue(
            "Android system file picker did not open",
            ui.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 10_000)
        )
        selectBackupFromDocumentsUi(ui, backupFileName)

        assertTrue(
            "Restore confirmation dialog did not appear",
            ui.wait(Until.hasObject(By.text("تأكيد الاستعادة")), 10_000)
        )
        clickFresh(ui, By.text("استعادة"), "Restore confirmation button")

        assertTrue(
            "Restore success dialog did not appear",
            ui.wait(Until.hasObject(By.textContains("تمت استعادة النسخة الاحتياطية")), 15_000)
        )

        assertRestoredData(
            db,
            personId = 910101L,
            accountId = 920101L,
            transactionId = 930101L,
            expectedPersonName = "اختبار الهاتف",
            expectedBalance = 275000L,
            expectedDescription = "عملية الهاتف الأصلية"
        )
    }

    private fun assertRestoredData(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        personId: Long = 910001L,
        accountId: Long = 920001L,
        transactionId: Long = 930001L,
        expectedPersonName: String = "اختبار الاستعادة",
        expectedBalance: Long = 125000L,
        expectedDescription: String = "عملية أصلية"
    ) {
        db.query("SELECT name,phone,address,notes,isActive,archivedAt FROM people WHERE id=$personId").use { c ->
            assertTrue("Restored person not found", c.moveToFirst())
            assertEquals(expectedPersonName, c.getString(0))
            assertEquals(if (personId == 910001L) "0500000000" else "0511111111", c.getString(1))
            assertEquals(if (personId == 910001L) "عنوان الاختبار" else "عنوان الهاتف", c.getString(2))
            assertEquals(if (personId == 910001L) "ملاحظة الاختبار" else "ملاحظة الهاتف", c.getString(3))
            assertEquals(1, c.getInt(4))
            assertTrue("archivedAt should remain NULL", c.isNull(5))
        }

        db.query("SELECT currencyCode,balanceMinor FROM currency_accounts WHERE id=$accountId").use { c ->
            assertTrue("Restored currency account not found", c.moveToFirst())
            assertEquals("YER", c.getString(0))
            assertEquals(expectedBalance, c.getLong(1))
        }

        db.query("SELECT accountId,type,amountMinor,description,transactionDate,createdAt FROM transactions WHERE id=$transactionId").use { c ->
            assertTrue("Restored transaction not found", c.moveToFirst())
            assertEquals(accountId, c.getLong(0))
            assertEquals("RECEIVABLE", c.getString(1))
            assertEquals(expectedBalance, c.getLong(2))
            assertEquals(expectedDescription, c.getString(3))
            assertEquals(if (transactionId == 930001L) 1003L else 2003L, c.getLong(4))
            assertEquals(if (transactionId == 930001L) 1004L else 2004L, c.getLong(5))
        }
    }

    private fun createBackupUri(fileName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MyAccounts")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create MediaStore backup URI")
    }

    private fun publishBackupUri(uri: Uri) {
        context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null
        )
    }

    private fun backupSize(uri: Uri): Long {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.Downloads.SIZE),
            null,
            null,
            null
        )?.use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return 0L
    }

    private fun queryDisplayName(uri: Uri): String {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.Downloads.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        error("Could not read backup display name")
    }

    private fun selectBackupFromDocumentsUi(ui: UiDevice, fileName: String) {
        ui.waitForIdle()

        val rootsSelector = firstMatchingSelector(
            ui,
            By.desc("Show roots"),
            By.desc("Show roots drawer"),
            By.res("com.google.android.documentsui:id/toolbar_nav_button")
        )
        if (rootsSelector != null) {
            clickFresh(ui, rootsSelector, "DocumentsUI roots button")
            ui.waitForIdle()
        }

        val downloadsSelector = firstMatchingSelector(
            ui,
            By.text("Downloads"),
            By.textContains("Downloads")
        )
        if (downloadsSelector != null) {
            clickFresh(ui, downloadsSelector, "Downloads root")
            waitForDocumentsUiToSettle(ui)
        }

        val folderSelector = firstMatchingSelector(
            ui,
            By.text("MyAccounts"),
            By.textContains("MyAccounts")
        )
        if (folderSelector != null) {
            clickFresh(ui, folderSelector, "MyAccounts folder")
            waitForDocumentsUiToSettle(ui)
        }

        var fileSelector = firstMatchingSelector(
            ui,
            By.text(fileName),
            By.textContains(fileName)
        )

        if (fileSelector == null) {
            val searchSelector = firstMatchingSelector(
                ui,
                By.desc("Search"),
                By.res("com.google.android.documentsui:id/option_menu_search")
            )
            if (searchSelector != null) {
                clickFresh(ui, searchSelector, "DocumentsUI search button")
                waitForDocumentsUiToSettle(ui)

                val searchField = ui.wait(
                    Until.findObject(By.res("com.google.android.documentsui:id/toolbar_search")),
                    3_000
                ) ?: ui.findObject(By.clazz("android.widget.EditText"))

                if (searchField != null) {
                    searchField.text = fileName
                    waitForDocumentsUiToSettle(ui)
                    fileSelector = firstMatchingSelector(
                        ui,
                        By.text(fileName),
                        By.textContains(fileName)
                    )
                }
            }
        }

        val selector = fileSelector ?: error(
            "Backup file '$fileName' was not selectable in Android DocumentsUI"
        )
        // Re-query immediately before the click. A DocumentsUI refresh can invalidate
        // a previously returned UiObject2 and cause StaleObjectException.
        clickFresh(ui, selector, "Backup file '$fileName'")
    }

    private fun firstMatchingSelector(ui: UiDevice, vararg selectors: BySelector): BySelector? {
        for (selector in selectors) {
            if (ui.findObject(selector) != null) return selector
        }
        return null
    }

    private fun clickFresh(ui: UiDevice, selector: BySelector, description: String) {
        val object2 = ui.wait(Until.findObject(selector), 7_000)
            ?: error("$description was not found")
        object2.click()
        ui.waitForIdle()
    }

    private fun waitForDocumentsUiToSettle(ui: UiDevice) {
        ui.waitForIdle()
        Thread.sleep(300)
        ui.waitForIdle()
    }
}
