package com.myaccounts.app.excel

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myaccounts.app.data.local.AppDatabase
import com.myaccounts.app.util.ExcelDataManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class ExcelImportExportInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val database: AppDatabase get() = AppDatabase.getInstance(context)
    private var excelUri: Uri? = null

    @After
    fun cleanup() {
        excelUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        val db = database.openHelper.writableDatabase
        db.execSQL("DELETE FROM transaction_attachments")
        db.execSQL("DELETE FROM transactions")
        db.execSQL("DELETE FROM currency_accounts")
        db.execSQL("DELETE FROM people WHERE isActive = 1")
        db.execSQL("DELETE FROM people WHERE isActive = 0")
    }

    @Test
    fun exportImportRoundTripUsesOneSheetAndExcludesArchive() = runBlocking {
        val db = database.openHelper.writableDatabase
        db.execSQL("DELETE FROM transaction_attachments")
        db.execSQL("DELETE FROM transactions")
        db.execSQL("DELETE FROM currency_accounts")
        db.execSQL("DELETE FROM people")

        db.execSQL(
            "INSERT INTO people (id,name,phone,address,notes,createdAt,isActive,archivedAt,externalId) VALUES (?,?,?,?,?,?,?,?,?)",
            arrayOf(940001L, "شخص نشط Excel", "777000001", "صنعاء", "ملاحظة", 1000L, 1, null, "P-EXCEL-001")
        )
        db.execSQL(
            "INSERT INTO currency_accounts (id,personId,currencyCode,balanceMinor,createdAt,updatedAt) VALUES (?,?,?,?,?,?)",
            arrayOf(950001L, 940001L, "YER", 123450L, 1001L, 1002L)
        )
        db.execSQL(
            "INSERT INTO transactions (id,accountId,type,amountMinor,description,transactionDate,createdAt,externalId) VALUES (?,?,?,?,?,?,?,?)",
            arrayOf(960001L, 950001L, "RECEIVABLE", 123450L, "عملية Excel", 1003L, 1004L, "T-EXCEL-001")
        )
        db.execSQL(
            "INSERT INTO people (id,name,phone,address,notes,createdAt,isActive,archivedAt,externalId) VALUES (?,?,?,?,?,?,?,?,?)",
            arrayOf(940002L, "شخص مؤرشف لا يظهر", "777000002", "تعز", "أرشيف", 1005L, 0, 1006L, "P-ARCHIVED-001")
        )

        excelUri = createExcelUri()
        val export = ExcelDataManager.exportActive(context, excelUri!!)
        assertTrue("Export failed: ${export.exceptionOrNull()}", export.isSuccess)
        publishExcelUri(excelUri!!)
        assertTrue("Exported Excel file is empty", fileSize(excelUri!!) > 0L)

        val xmlEntries = readXmlEntries(excelUri!!)
        assertTrue("workbook.xml missing", xmlEntries.containsKey("xl/workbook.xml"))
        assertTrue("sheet1.xml missing", xmlEntries.containsKey("xl/worksheets/sheet1.xml"))
        assertEquals("Workbook must contain exactly one worksheet", 1, "<sheet ".toRegex().findAll(xmlEntries.getValue("xl/workbook.xml")).count())
        assertTrue("Active person missing from Excel", xmlEntries.getValue("xl/worksheets/sheet1.xml").contains("شخص نشط Excel"))
        assertFalse("Archived person leaked into Excel", xmlEntries.getValue("xl/worksheets/sheet1.xml").contains("شخص مؤرشف لا يظهر"))

        val preview = ExcelDataManager.previewImport(context, excelUri!!)
        assertTrue("Preview failed: ${preview.exceptionOrNull()}", preview.isSuccess)
        assertTrue("Preview unexpectedly contains errors: ${preview.getOrNull()?.errors}", preview.getOrNull()?.isValid == true)
        assertEquals(1, preview.getOrNull()?.people)
        assertEquals(1, preview.getOrNull()?.accounts)
        assertEquals(1, preview.getOrNull()?.transactions)

        db.execSQL("DELETE FROM transactions")
        db.execSQL("DELETE FROM currency_accounts")
        db.execSQL("DELETE FROM people WHERE isActive = 1")

        val archivedCountBefore = countPeopleByExternalId("P-ARCHIVED-001")
        assertEquals(1, archivedCountBefore)

        val import = ExcelDataManager.import(context, excelUri!!)
        assertTrue("Import failed: ${import.exceptionOrNull()}", import.isSuccess)
        assertEquals(1, import.getOrNull()?.peopleAdded)
        assertEquals(3, import.getOrNull()?.accountsAdded)
        assertEquals(1, import.getOrNull()?.transactionsAdded)

        assertEquals(1, countPeopleByExternalId("P-EXCEL-001"))
        assertEquals(1, countPeopleByExternalId("P-ARCHIVED-001"))
        assertEquals(1, countTransactionsByExternalId("T-EXCEL-001"))
        db.query("SELECT balanceMinor FROM currency_accounts WHERE personId=(SELECT id FROM people WHERE externalId='P-EXCEL-001') AND currencyCode='YER'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(123450L, c.getLong(0))
        }
    }

    @Test
    fun importingSameFileTwiceDoesNotDuplicateTransactions() = runBlocking {
        val db = database.openHelper.writableDatabase
        db.execSQL("DELETE FROM transaction_attachments")
        db.execSQL("DELETE FROM transactions")
        db.execSQL("DELETE FROM currency_accounts")
        db.execSQL("DELETE FROM people")

        db.execSQL(
            "INSERT INTO people (id,name,phone,address,notes,createdAt,isActive,archivedAt,externalId) VALUES (?,?,?,?,?,?,?,?,?)",
            arrayOf(941001L, "تكرار Excel", "777000010", "صنعاء", "", 1000L, 1, null, "P-EXCEL-DUP")
        )
        db.execSQL(
            "INSERT INTO currency_accounts (id,personId,currencyCode,balanceMinor,createdAt,updatedAt) VALUES (?,?,?,?,?,?)",
            arrayOf(951001L, 941001L, "USD", 50000L, 1001L, 1002L)
        )
        db.execSQL(
            "INSERT INTO transactions (id,accountId,type,amountMinor,description,transactionDate,createdAt,externalId) VALUES (?,?,?,?,?,?,?,?)",
            arrayOf(961001L, 951001L, "PAYABLE", 50000L, "تجربة تكرار", 1003L, 1004L, "T-EXCEL-DUP")
        )

        excelUri = createExcelUri()
        assertTrue(ExcelDataManager.exportActive(context, excelUri!!).isSuccess)
        publishExcelUri(excelUri!!)

        db.execSQL("DELETE FROM transactions")
        db.execSQL("DELETE FROM currency_accounts")
        db.execSQL("DELETE FROM people WHERE isActive = 1")

        val first = ExcelDataManager.import(context, excelUri!!)
        val second = ExcelDataManager.import(context, excelUri!!)
        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        assertEquals(1, countTransactionsByExternalId("T-EXCEL-DUP"))
        assertEquals(1, second.getOrNull()?.skippedDuplicates)
    }

    private fun createExcelUri(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "m04_excel_${System.currentTimeMillis()}.xlsx")
            put(MediaStore.Downloads.MIME_TYPE, ExcelDataManager.MIME_TYPE)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MyAccounts")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create Excel MediaStore URI")
    }

    private fun publishExcelUri(uri: Uri) {
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
    }

    private fun fileSize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(MediaStore.Downloads.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return 0L
    }

    private fun readXmlEntries(uri: Uri): Map<String, String> {
        val result = mutableMapOf<String, String>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.endsWith(".xml")) {
                        result[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                    }
                }
            }
        }
        return result
    }

    private fun countPeopleByExternalId(id: String): Int = database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM people WHERE externalId=?", arrayOf(id)).use { c -> c.moveToFirst(); c.getInt(0) }
    private fun countTransactionsByExternalId(id: String): Int = database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM transactions WHERE externalId=?", arrayOf(id)).use { c -> c.moveToFirst(); c.getInt(0) }
}
