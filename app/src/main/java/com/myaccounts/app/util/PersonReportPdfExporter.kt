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
import com.myaccounts.app.data.reports.PersonReportSummary
import com.myaccounts.app.data.reports.PersonReportTransaction
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PersonReportPdfExporter {

    fun exportPersonReport(
        context: Context,
        summary: PersonReportSummary,
        transactions: List<PersonReportTransaction>,
        startDateMillis: Long?,
        endDateMillisExclusive: Long?
    ): Result<String> {

        return try {

            val document = PdfDocument()

            val pageWidth = 595
            val pageHeight = 842

            val titlePaint = Paint().apply {
                color = Color.BLACK
                textSize = 21f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }

            val headingPaint = Paint().apply {
                color = Color.BLACK
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }

            val subHeadingPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }

            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 11f
                typeface = Typeface.DEFAULT
                isAntiAlias = true
            }

            val smallPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 9f
                typeface = Typeface.DEFAULT
                isAntiAlias = true
            }

            val positivePaint = Paint().apply {
                color = Color.rgb(0, 120, 70)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }

            val negativePaint = Paint().apply {
                color = Color.rgb(180, 30, 30)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
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
            var y = 45f

            fun drawHeader() {

                canvas.drawText(
                    "MyAccounts - تقرير حساب شخص",
                    40f,
                    y,
                    titlePaint
                )

                y += 30f

                canvas.drawText(
                    "الاسم: ${summary.personName}",
                    40f,
                    y,
                    headingPaint
                )

                y += 23f

                canvas.drawText(
                    "العملة: ${currencyName(summary.currencyCode)}",
                    40f,
                    y,
                    textPaint
                )

                y += 20f

                canvas.drawText(
                    "الفترة: ${
                        formatDateRange(
                            startDateMillis,
                            endDateMillisExclusive
                        )
                    }",
                    40f,
                    y,
                    smallPaint
                )

                y += 20f

                canvas.drawLine(
                    40f,
                    y,
                    555f,
                    y,
                    linePaint
                )

                y += 25f
            }

            fun startNewPage() {

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
                y = 45f

                drawHeader()
            }

            fun ensureSpace(
                requiredHeight: Float
            ) {
                if (y + requiredHeight > 790f) {
                    startNewPage()
                }
            }

            fun drawSummaryRow(
                label: String,
                amountMinor: Long
            ) {

                ensureSpace(22f)

                canvas.drawText(
                    label,
                    50f,
                    y,
                    textPaint
                )

                val amountText =
                    formatAmount(amountMinor)

                val paint =
                    when {
                        amountMinor > 0L ->
                            positivePaint

                        amountMinor < 0L ->
                            negativePaint

                        else ->
                            textPaint
                    }

                canvas.drawText(
                    amountText,
                    390f,
                    y,
                    paint
                )

                y += 22f
            }

            drawHeader()

            canvas.drawText(
                "ملخص الحساب",
                40f,
                y,
                headingPaint
            )

            y += 28f

            drawSummaryRow(
                label = "الرصيد الافتتاحي",
                amountMinor =
                    summary.openingBalanceMinor
            )

            drawSummaryRow(
                label = "إجمالي عليه خلال الفترة",
                amountMinor =
                    summary.periodReceivableMinor
            )

            drawSummaryRow(
                label = "إجمالي له خلال الفترة",
                amountMinor =
                    summary.periodPayableMinor
            )

            drawSummaryRow(
                label = "صافي حركة الفترة",
                amountMinor =
                    summary.periodBalanceMinor
            )

            drawSummaryRow(
                label = "الرصيد الختامي",
                amountMinor =
                    summary.closingBalanceMinor
            )

            ensureSpace(45f)

            y += 5f

            canvas.drawText(
                "عدد العمليات: ${summary.transactionCount}",
                50f,
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

            y += 28f

            canvas.drawText(
                "تفاصيل العمليات",
                40f,
                y,
                headingPaint
            )

            y += 28f

            if (transactions.isEmpty()) {

                canvas.drawText(
                    "لا توجد عمليات خلال الفترة المحددة.",
                    50f,
                    y,
                    textPaint
                )

            } else {

                transactions.forEachIndexed { index, transaction ->

                    ensureSpace(105f)

                    canvas.drawText(
                        "${index + 1}. ${formatDate(transaction.transactionDate)}",
                        45f,
                        y,
                        subHeadingPaint
                    )

                    y += 20f

                    canvas.drawText(
                        "النوع: ${
                            transactionTypeName(
                                transaction.type
                            )
                        }",
                        60f,
                        y,
                        textPaint
                    )

                    y += 20f

                    val amountPaint =
                        if (transaction.amountMinor > 0L) {
                            positivePaint
                        } else {
                            negativePaint
                        }

                    canvas.drawText(
                        "المبلغ: ${
                            formatAmount(
                                transaction.amountMinor
                            )
                        } ${currencyName(summary.currencyCode)}",
                        60f,
                        y,
                        amountPaint
                    )

                    y += 20f

                    if (transaction.description.isNotBlank()) {

                        canvas.drawText(
                            "الوصف:",
                            60f,
                            y,
                            textPaint
                        )

                        y += 18f

                        val descriptionLines =
                            splitTextIntoLines(
                                transaction.description,
                                470f,
                                textPaint
                            )

                        descriptionLines.forEach { line ->

                            ensureSpace(18f)

                            canvas.drawText(
                                line,
                                75f,
                                y,
                                textPaint
                            )

                            y += 17f
                        }
                    }

                    y += 8f

                    canvas.drawLine(
                        45f,
                        y,
                        550f,
                        y,
                        linePaint
                    )

                    y += 20f
                }
            }

            document.finishPage(page)

            val fileName =
                "MyAccounts_Person_Report_${
                    safeFileName(
                        summary.personName
                    )
                }_${
                    SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.US
                    ).format(Date())
                }.pdf"

            val outputMessage: String

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
            ) {

                val values =
                    ContentValues().apply {

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
                            "تعذر إنشاء ملف تقرير الشخص."
                        )

                resolver
                    .openOutputStream(uri)
                    .use { outputStream ->

                        if (outputStream == null) {
                            throw IllegalStateException(
                                "تعذر فتح ملف تقرير الشخص."
                            )
                        }

                        document.writeTo(
                            outputStream
                        )
                    }

                outputMessage =
                    "تم حفظ تقرير الشخص في مجلد التنزيلات/MyAccounts"

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

                FileOutputStream(file)
                    .use { outputStream ->

                        document.writeTo(
                            outputStream
                        )
                    }

                outputMessage =
                    file.absolutePath
            }

            document.close()

            Result.success(
                outputMessage
            )

        } catch (exception: Exception) {

            Result.failure(
                exception
            )
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

    private fun transactionTypeName(
        type: String
    ): String {
        return when (type) {
            "RECEIVABLE" -> "عليه"
            "PAYABLE" -> "له"
            else -> type
        }
    }

    private fun formatAmount(
        amountMinor: Long
    ): String {
        return BigDecimal(amountMinor)
            .movePointLeft(2)
            .stripTrailingZeros()
            .toPlainString()
    }

    private fun formatDate(
        millis: Long
    ): String {
        return SimpleDateFormat(
            "dd/MM/yyyy",
            Locale("ar")
        ).format(
            Date(millis)
        )
    }

    private fun formatDateRange(
        startDateMillis: Long?,
        endDateMillisExclusive: Long?
    ): String {

        val start =
            startDateMillis?.let {
                formatDate(it)
            } ?: "غير محدد"

        val end =
            endDateMillisExclusive
                ?.let {
                    formatDate(
                        addDays(
                            it,
                            -1
                        )
                    )
                }
                ?: "غير محدد"

        return "$start - $end"
    }

    private fun addDays(
        millis: Long,
        days: Int
    ): Long {
        val calendar =
            java.util.Calendar
                .getInstance()
                .apply {
                    timeInMillis = millis
                    add(
                        java.util.Calendar.DAY_OF_MONTH,
                        days
                    )
                }

        return calendar.timeInMillis
    }

    private fun safeFileName(
        value: String
    ): String {

        return value
            .replace(
                Regex("[\\\\/:*?\"<>|]"),
                "_"
            )
            .replace(
                Regex("\\s+"),
                "_"
            )
            .take(60)
    }

    private fun splitTextIntoLines(
        text: String,
        maxWidth: Float,
        paint: Paint
    ): List<String> {

        val result =
            mutableListOf<String>()

        val words =
            text.trim()
                .split(
                    Regex("\\s+")
                )

        if (words.isEmpty()) {
            return result
        }

        var currentLine = ""

        for (word in words) {

            val candidate =
                if (currentLine.isEmpty()) {
                    word
                } else {
                    "$currentLine $word"
                }

            if (
                paint.measureText(
                    candidate
                ) <= maxWidth
            ) {

                currentLine = candidate

            } else {

                if (currentLine.isNotEmpty()) {
                    result.add(currentLine)
                }

                currentLine = word
            }
        }

        if (currentLine.isNotEmpty()) {
            result.add(currentLine)
        }

        return result
    }
}
