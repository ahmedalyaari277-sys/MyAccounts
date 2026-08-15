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
            val right = 555f
            val left = 40f

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }

            val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 12f
                typeface = Typeface.DEFAULT
                textAlign = Paint.Align.RIGHT
            }

            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
                    right,
                    y,
                    titlePaint
                )
                y += 30f

                canvas.drawText(
                    "العملة: ${currencyName(summary.currencyCode)}",
                    right,
                    y,
                    textPaint
                )
                y += 25f

                canvas.drawLine(
                    left,
                    y,
                    right,
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

            fun drawText(text: String, paint: Paint) {
                canvas.drawText(
                    text,
                    right,
                    y,
                    paint
                )
            }

            fun balanceDescription(balanceMinor: Long): String {
                return when {
                    balanceMinor > 0L ->
                        "الرصيد: ${formatAmount(balanceMinor)} (عليه)"

                    balanceMinor < 0L ->
                        "الرصيد: ${formatAmount(-balanceMinor)} (له)"

                    else ->
                        "الرصيد: 0 (متوازن)"
                }
            }

            drawHeader()

            drawText("ملخص التقرير", headingPaint)
            y += 30f

            drawText(
                "إجمالي عليه: ${formatAmount(summary.totalReceivableMinor)}",
                textPaint
            )
            y += 22f

            drawText(
                "إجمالي له: ${formatAmount(summary.totalPayableMinor)}",
                textPaint
            )
            y += 22f

            drawText(
                balanceDescription(summary.balanceMinor),
                textPaint
            )
            y += 22f

            drawText(
                "عدد العمليات: ${summary.transactionCount}",
                textPaint
            )
            y += 35f

            canvas.drawLine(
                left,
                y,
                right,
                y,
                linePaint
            )
            y += 30f

            drawText("الأشخاص", headingPaint)
            y += 30f

            if (people.isEmpty()) {
                drawText("لا توجد بيانات للأشخاص.", textPaint)
            } else {
                people.forEach { person ->
                    ensureSpace(110f)

                    drawText(
                        person.personName,
                        headingPaint
                    )
                    y += 22f

                    drawText(
                        "عليه: ${formatAmount(person.totalReceivableMinor)}",
                        textPaint
                    )
                    y += 20f

                    drawText(
                        "له: ${formatAmount(person.totalPayableMinor)}",
                        textPaint
                    )
                    y += 20f

                    drawText(
                        balanceDescription(person.balanceMinor),
                        textPaint
                    )
                    y += 20f

                    drawText(
                        "عدد العمليات: ${person.transactionCount}",
                        textPaint
                    )
                    y += 20f

                    canvas.drawLine(
                        left,
                        y,
                        right,
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
                        Environment.DIRECTORY_DOWNLOADS + "/MyAccounts"
                    )
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: throw IllegalStateException(
                    "تعذر إنشاء ملف التقرير."
                )

                try {
                    resolver.openOutputStream(uri).use { outputStream ->
                        if (outputStream == null) {
                            throw IllegalStateException(
                                "تعذر فتح ملف التقرير."
                            )
                        }
                        document.writeTo(outputStream)
                    }
                } catch (exception: Exception) {
                    resolver.delete(uri, null, null)
                    throw exception
                }

                Result.success(
                    "تم حفظ التقرير في مجلد التنزيلات/MyAccounts"
                )
            } else {
                val directory = File(
                    context.getExternalFilesDir(
                        Environment.DIRECTORY_DOCUMENTS
                    ),
                    "MyAccounts"
                )

                if (!directory.exists() && !directory.mkdirs()) {
                    throw IllegalStateException(
                        "تعذر إنشاء مجلد حفظ التقرير."
                    )
                }

                val file = File(directory, fileName)

                FileOutputStream(file).use { outputStream ->
                    document.writeTo(outputStream)
                }

                Result.success(file.absolutePath)
            }
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
