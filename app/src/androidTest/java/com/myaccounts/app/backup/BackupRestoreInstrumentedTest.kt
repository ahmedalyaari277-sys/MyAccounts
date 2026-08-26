package com.myaccounts.app.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val database: AppDatabase
        get() = AppDatabase.getInstance(context)

    private var backupUri: Uri? = null

    @After
    fun cleanup() {
        backupUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        database.openHelper.writableDatabase.execSQL("DELETE FROM transaction_attachments")
        database.openHelper.writableDatabase.execSQL("DELETE FROM transactions")
        database.openHelper.writableDatabase.execSQL("DELETE FROM currency_accounts")
        database.openHelper.writableDatabase.execSQL("DELETE FROM people")
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

        db.query("SELECT name,phone,address,notes,isActive,archivedAt FROM people WHERE id=910001").use { c ->
            assertTrue("Restored person not found", c.moveToFirst())
            assertEquals("اختبار الاستعادة", c.getString(0))
            assertEquals("0500000000", c.getString(1))
            assertEquals("عنوان الاختبار", c.getString(2))
            assertEquals("ملاحظة الاختبار", c.getString(3))
            assertEquals(1, c.getInt(4))
            assertTrue("archivedAt should remain NULL", c.isNull(5))
        }

        db.query("SELECT currencyCode,balanceMinor FROM currency_accounts WHERE id=920001").use { c ->
            assertTrue("Restored currency account not found", c.moveToFirst())
            assertEquals("YER", c.getString(0))
            assertEquals(125000L, c.getLong(1))
        }

        db.query("SELECT accountId,type,amountMinor,description,transactionDate,createdAt FROM transactions WHERE id=930001").use { c ->
            assertTrue("Restored transaction not found", c.moveToFirst())
            assertEquals(920001L, c.getLong(0))
            assertEquals("RECEIVABLE", c.getString(1))
            assertEquals(125000L, c.getLong(2))
            assertEquals("عملية أصلية", c.getString(3))
            assertEquals(1003L, c.getLong(4))
            assertEquals(1004L, c.getLong(5))
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
}
