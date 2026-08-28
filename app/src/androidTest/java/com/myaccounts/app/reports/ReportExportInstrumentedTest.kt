package com.myaccounts.app.reports

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myaccounts.app.data.reports.CurrencyReportPersonRow
import com.myaccounts.app.data.reports.CurrencyReportSummary
import com.myaccounts.app.util.GeneralReportsExcelExporter
import com.myaccounts.app.util.GeneralReportsPdfExporter
import com.myaccounts.app.util.ReportShareUtil
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportExportInstrumentedTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val summary = CurrencyReportSummary("YER", 12500L, 5000L, 7500L, 2)
    private val people = listOf(CurrencyReportPersonRow(1L, "اختبار التقرير", 12500L, 5000L, 7500L, 2))

    @Test
    fun pdfExportCreatesARealDownloadFileAndReturnsSuccess() {
        val result = GeneralReportsPdfExporter.exportPeopleReport(context, "YER", summary, people, null, null)
        assertTrue("PDF exporter returned failure: ${result.exceptionOrNull()}", result.isSuccess)
        assertLatestDownloadFileExists("MyAccounts_تقرير_الأشخاص", ".pdf")
    }

    @Test
    fun excelExportCreatesARealDownloadFileAndReturnsSuccess() {
        val result = GeneralReportsExcelExporter.exportPeopleReport(context, "YER", summary, people, null, null)
        assertTrue("Excel exporter returned failure: ${result.exceptionOrNull()}", result.isSuccess)
        assertLatestDownloadFileExists("MyAccounts_تقرير_الأشخاص", ".xlsx")
    }

    @Test
    fun shareActionGeneratesTemporaryPdfWithoutPriorExport() = runBlocking {
        val beforeIds = downloadIds("MyAccounts_تقرير_الأشخاص", ".pdf")
        val share = ReportShareUtil.shareGeneratedReport(
            context = context,
            fileNamePrefix = "MyAccounts_تقرير_الأشخاص",
            mimeType = "application/pdf",
            launchChooser = false
        ) {
            GeneralReportsPdfExporter.exportPeopleReport(context, "YER", summary, people, null, null)
        }
        assertTrue("Direct share generation failed: ${share.exceptionOrNull()}", share.isSuccess)
        assertEquals("Direct share must not leave a new Downloads export behind", beforeIds, downloadIds("MyAccounts_تقرير_الأشخاص", ".pdf"))
        val temp = java.io.File(context.cacheDir, "report_share").listFiles()?.filter { it.isFile && it.name.endsWith(".pdf", true) }?.maxByOrNull { it.lastModified() }
        assertTrue("Temporary shared PDF was not created", temp?.isFile == true)
        assertTrue("Temporary shared PDF is empty", (temp?.length() ?: 0L) > 0L)
    }

    private fun downloadIds(prefix: String, extension: String): Set<Long> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptySet()
        val resolver = context.contentResolver
        val ids = mutableSetOf<Long>()
        val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/MyAccounts%")
        resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                if (name.startsWith(prefix) && name.endsWith(extension, true)) ids += cursor.getLong(idIndex)
            }
        }
        return ids
    }

    private fun assertLatestDownloadFileExists(prefix: String, extension: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.SIZE)
            val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/MyAccounts%")
            var latestUri: Uri? = null
            var latestName: String? = null
            var latestSize = -1L
            resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, "${MediaStore.Downloads.DATE_ADDED} DESC")?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex) ?: continue
                    if (!name.startsWith(prefix) || !name.endsWith(extension, true)) continue
                    latestUri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(idIndex).toString())
                    latestName = name
                    latestSize = cursor.getLong(sizeIndex)
                    break
                }
            }
            assertTrue("No exported $extension file found in Downloads/MyAccounts", latestUri != null)
            assertEquals(MediaStore.Downloads.EXTERNAL_CONTENT_URI.scheme, latestUri?.scheme)
            assertTrue("Exported file is empty: $latestName", latestSize > 0L)
        } else {
            val directory = java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MyAccounts")
            val file = directory.listFiles()?.filter { it.isFile && it.name.startsWith(prefix) && it.name.endsWith(extension, true) }?.maxByOrNull { it.lastModified() }
            assertTrue("No exported $extension file found: $prefix", file?.isFile == true)
            assertTrue("Exported file is empty: $file", file?.length() ?: 0L > 0L)
        }
    }
}
