package com.myaccounts.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.text.intl.LocaleList
import com.myaccounts.app.data.reports.CurrencyReportPersonRow
import com.myaccounts.app.data.reports.CurrencyReportSummary
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportPdfExporter {

    fun exportCurrencyReport(
        context: Context,
        summary: CurrencyReportSummary,
        people: List<CurrencyReportPersonRow>
    ): Result<String> {

        return try {
            val document = PdfDocument()

            val pageWidth = 595
            val pageHeight = 842

            val titlePaint = Paint().apply {
                color = Color.BLACK
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }

            val headingPaint = Paint().apply {
                color = Color.BLACK
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }

            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 12f
                typeface = Typeface.DEFAULT
                isAntiAlias = true
            }

            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }

            var pageNumber = 1
            var page = document.startPage(
                PdfDocument.PageInfo.Builder(
                    pageWidth,
                    pageHeight,
                    pageNumber
                ).create()
            )

            var canvas = page.canvas
            var y = 50f

            fun drawHeader() {
                canvas.drawText(
                    "MyAccounts - تقرير الحسابات",
                    40f,
                    y,
                    titlePaint
                )

                y += 30f

                canvas.drawText(
                    "العملة: ${currencyName(summary.currencyCode)}",
                    40f,
                    y,
                    textPaint
                )

                y += 25f

                canvas.drawLine(
                    40f,
                    y,
                    555f,
                    y,
                    linePaint
                )

                y += 25f
            }

            fun newPage() {
                document.finishPage(page)

                pageNumber++

                page = document.startPage(
                    PdfDocument.PageInfo.Builder(
                        pageWidth,
                        pageHeight,
                        pageNumber
                    ).create()
                )

                canvas = page.canvas
                y = 50f

                drawHeader()
            }

            fun ensureSpace(requiredHeight: Float) {
                if (y + requiredHeight > 790f) {
                    newPage()
                }
            }

            drawHeader()

            canvas.drawText(
                "ملخص التقرير",
                40f,
                y,
                headingPaint
            )

            y += 30f

            canvas.drawText(
                "إجمالي لك: ${formatAmount(summary.totalReceivableMinor)}",
                40f,
                y,
                textPaint
            )

            y += 22f

            canvas.drawText(
                "إجمالي عليك: ${formatAmount(summary.totalPayableMinor)}",
                40f,
                y,
                textPaint
            )

            y += 22f

            canvas.drawText(
                "الرصيد: ${formatAmount(summary.balanceMinor)}",
                40f,
                y,
                textPaint
            )

            y += 22f

            canvas.drawText(
                "عدد العمليات: ${summary.transactionCount}",
                40f,
                y,
                textPaint
            )

            y += 35f

            canvas.drawLine(
                40f,
                y,
                555f,
                y,
                linePaint
            )

            y += 30f

            canvas.drawText(
                "الأشخاص",
                40f,
                y,
                headingPaint
            )

            y += 30f

            if (people.isEmpty()) {

                canvas.drawText(
                    "لا توجد بيانات للأشخاص.",
                    40f,
                    y,
                    textPaint
                )

            } else {

                people.forEach { person ->

                    ensureSpace(95f)

                    canvas.drawText(
                        person.personName,
                        40f,
                        y,
                        headingPaint
                    )

                    y += 22f

                    canvas.drawText(
                        "لك: ${formatAmount(person.totalReceivableMinor)}",
                        55f,
                        y,
                        textPaint
                    )

                    y += 20f

                    canvas.drawText(
                        "عليك: ${formatAmount(person.totalPayableMinor)}",
                        55f,
                        y,
                        textPaint
                    )

                    y += 20f

                    canvas.drawText(
                        "الرصيد: ${formatAmount(person.balanceMinor)}",
                        55f,
                        y,
                        textPaint
                    )

                    y += 20f

                    canvas.drawText(
                        "عدد العمليات: ${person.transactionCount}",
                        55f,
                        y,
                        textPaint
                    )

                    y += 20f

                    canvas.drawLine(
                        40f,
                        y,
                        555f,
                        y,
                        linePaint
                    )

                    y += 20f
                }
            }

            document.finishPage(page)

            val fileName =
                "MyAccounts_Report_${
                    SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.US
                    ).format(Date())
                }.pdf"

            val outputPath: String

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                val values = ContentValues().apply {
                    put(
                        MediaStore.Downloads.DISPLAY_NAME,
                        fileName
                    )

                    put(
                        MediaStore.Downloads.MIME_TYPE,
                        "application/pdf"
                    )

                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS +
                            "/MyAccounts"
                    )
                }

                val resolver =
                    context.contentResolver

                val uri =
                    resolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        values
                    )
                        ?: throw IllegalStateException(
                            "تعذر إنشاء ملف التقرير"
                        )

                resolver.openOutputStream(uri).use { outputStream ->

                    if (outputStream == null) {
                        throw IllegalStateException(
                            "تعذر فتح ملف التقرير"
                        )
                    }

                    document.writeTo(outputStream)
                }

                outputPath =
                    "تم حفظ التقرير في مجلد التنزيلات/MyAccounts"

            } else {

                val directory =
                    File(
                        context.getExternalFilesDir(
                            Environment.DIRECTORY_DOCUMENTS
                        ),
                        "MyAccounts"
                    )

                if (!directory.exists()) {
                    directory.mkdirs()
                }

                val file =
                    File(
                        directory,
                        fileName
                    )

                FileOutputStream(file).use { outputStream ->
                    document.writeTo(outputStream)
                }

                outputPath = file.absolutePath
            }

            document.close()

            Result.success(outputPath)

        } catch (exception: Exception) {

            Result.failure(exception)
        }
    }

    private fun currencyName(
        currencyCode: String
    ): String {
        return when (currencyCode) {
            "YER" -> "الريال اليمني"
            "SAR" -> "الريال السعودي"
            "USD" -> "الدولار الأمريكي"
            else -> currencyCode
        }
    }

    private fun formatAmount(
        amountMinor: Long
    ): String {
        return java.math.BigDecimal(amountMinor)
            .movePointLeft(2)
            .stripTrailingZeros()
            .toPlainString()
    }
}
