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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportExportInstrumentedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val summary = CurrencyReportSummary(
        currencyCode = "YER",
        totalReceivableMinor = 12500L,
        totalPayableMinor = 5000L,
        balanceMinor = 7500L,
        transactionCount = 2
    )

    private val people = listOf(
        CurrencyReportPersonRow(
            personId = 1L,
            personName = "اختبار التقرير",
            totalReceivableMinor = 12500L,
            totalPayableMinor = 5000L,
            balanceMinor = 7500L,
            transactionCount = 2
        )
    )

    @Test
    fun pdfExportCreatesARealDownloadFileAndReturnsSuccess() {
        val result = GeneralReportsPdfExporter.exportPeopleReport(
            context = context,
            currency = "YER",
            summary = summary,
            people = people,
            start = null,
            end = null
        )

        assertTrue("PDF exporter returned failure: ${result.exceptionOrNull()}", result.isSuccess)
        val path = result.getOrNull()
        assertNotNull(path)
        assertDownloadFileExists(path!!, ".pdf")
    }

    @Test
    fun excelExportCreatesARealDownloadFileAndReturnsSuccess() {
        val result = GeneralReportsExcelExporter.exportPeopleReport(
            context = context,
            currency = "YER",
            summary = summary,
            people = people,
            start = null,
            end = null
        )

        assertTrue("Excel exporter returned failure: ${result.exceptionOrNull()}", result.isSuccess)
        val path = result.getOrNull()
        assertNotNull(path)
        assertDownloadFileExists(path!!, ".xlsx")
    }

    @Test
    fun exportedPdfCanBeSharedThroughTheProductionSharePath() {
        val export = GeneralReportsPdfExporter.exportPeopleReport(
            context = context,
            currency = "YER",
            summary = summary,
            people = people,
            start = null,
            end = null
        )
        assertTrue("PDF export failed before share test: ${export.exceptionOrNull()}", export.isSuccess)

        val share = ReportShareUtil.shareLatestReport(
            context = context,
            fileNamePrefix = "MyAccounts_تقرير_الأشخاص",
            mimeType = "application/pdf"
        )
        assertTrue("Share path failed: ${share.exceptionOrNull()}", share.isSuccess)
    }

    private fun assertDownloadFileExists(pathOrUri: String, extension: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = Uri.parse(pathOrUri)
            assertEquals(MediaStore.Downloads.EXTERNAL_CONTENT_URI.scheme, uri.scheme)
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                assertTrue("Exported file URI did not resolve to a MediaStore row", cursor.moveToFirst())
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE))
                assertTrue("Unexpected extension: $name", name.endsWith(extension, ignoreCase = true))
                assertTrue("Exported file is empty: $name", size > 0L)
            } ?: throw AssertionError("Could not query exported file URI")
        } else {
            val expected = java.io.File(pathOrUri)
            assertTrue("Exported legacy file does not exist: $expected", expected.isFile)
            assertTrue("Exported legacy file is empty: $expected", expected.length() > 0L)
        }
    }
}
