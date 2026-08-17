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
import java.util.Calendar
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
            val left = 35f
            val right = 560f

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }
            val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 10f
                textAlign = Paint.Align.RIGHT
            }
            val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(190, 35, 35)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }
            val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0, 125, 70)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                strokeWidth = 1f
            }
            val lightLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }

            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas
            var y = 42f

            fun drawReportHeader() {
                canvas.drawText("MyAccounts - تقرير حساب شخصي", right, y, titlePaint)
                y += 27f
                canvas.drawText("الاسم: ${summary.personName}", right, y, headingPaint)
                y += 20f
                canvas.drawText("العملة: ${currencyName(summary.currencyCode)}", right, y, textPaint)
                y += 18f
                canvas.drawText("الفترة: ${formatDateRange(startDateMillis, endDateMillisExclusive)}", right, y, textPaint)
                y += 18f
                canvas.drawText("تاريخ إصدار التقرير: ${formatDate(System.currentTimeMillis())}", right, y, textPaint)
                y += 18f
                canvas.drawText("عدد العمليات: ${summary.transactionCount}", right, y, textPaint)
                y += 12f
                canvas.drawLine(left, y, right, y, linePaint)
                y += 20f
            }

            fun newPage() {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 42f
                drawReportHeader()
                drawTableHeader()
            }

            fun ensureSpace(height: Float) {
                if (y + height > 790f) newPage()
            }

            fun drawTableHeader() {
                val headerTop = y - 14f
                canvas.drawLine(left, headerTop, right, headerTop, linePaint)
                canvas.drawText("الرصيد", 540f, y, headingPaint)
                canvas.drawText("له", 435f, y, greenPaint)
                canvas.drawText("عليه", 345f, y, redPaint)
                canvas.drawText("البيان", 215f, y, headingPaint)
                canvas.drawText("التاريخ", 65f, y, headingPaint)
                y += 9f
                canvas.drawLine(left, y, right, y, linePaint)
                y += 19f
            }

            fun amountPaint(type: String): Paint = if (type == "RECEIVABLE") redPaint else greenPaint

            drawReportHeader()
            canvas.drawText("ملخص الحساب", right, y, headingPaint)
            y += 22f
            canvas.drawText("الرصيد الافتتاحي: ${balanceText(summary.openingBalanceMinor)}", right, y, textPaint)
            y += 18f
            canvas.drawText("إجمالي عليه خلال الفترة: ${formatAmount(summary.periodReceivableMinor)}", right, y, redPaint)
            y += 18f
            canvas.drawText("إجمالي له خلال الفترة: ${formatAmount(summary.periodPayableMinor)}", right, y, greenPaint)
            y += 18f
            canvas.drawText("الرصيد: ${balanceText(summary.closingBalanceMinor)}", right, y, balancePaint(summary.closingBalanceMinor))
            y += 25f
            drawTableHeader()

            if (transactions.isEmpty()) {
                canvas.drawText("لا توجد عمليات خلال الفترة المحددة.", right, y, textPaint)
            } else {
                transactions.forEach { transaction ->
                    ensureSpace(32f)
                    canvas.drawText(formatDate(transaction.transactionDate), 65f, y, textPaint)
                    canvas.drawText(transaction.description.ifBlank { "—" }.take(24), 215f, y, textPaint)
                    canvas.drawText(
                        if (transaction.type == "RECEIVABLE") formatAmount(transaction.amountMinor) else "—",
                        345f,
                        y,
                        if (transaction.type == "RECEIVABLE") redPaint else textPaint
                    )
                    canvas.drawText(
                        if (transaction.type == "PAYABLE") formatAmount(transaction.amountMinor) else "—",
                        435f,
                        y,
                        if (transaction.type == "PAYABLE") greenPaint else textPaint
                    )
                    canvas.drawText(
                        formatAmount(transaction.balanceMinor),
                        540f,
                        y,
                        balancePaint(transaction.balanceMinor)
                    )
                    y += 10f
                    canvas.drawLine(left, y, right, y, lightLinePaint)
                    y += 17f
                }
            }

            document.finishPage(page)
            val fileName = "MyAccounts_Person_Report_${safeFileName(summary.personName)}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MyAccounts")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("تعذر إنشاء ملف تقرير الشخص.")
                try {
                    resolver.openOutputStream(uri).use { outputStream ->
                        if (outputStream == null) throw IllegalStateException("تعذر فتح ملف تقرير الشخص.")
                        document.writeTo(outputStream)
                    }
                } catch (exception: Exception) {
                    resolver.delete(uri, null, null)
                    throw exception
                }
                Result.success("تم حفظ تقرير الشخص في مجلد التنزيلات/MyAccounts")
            } else {
                val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MyAccounts")
                if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("تعذر إنشاء مجلد التقرير.")
                val file = File(directory, fileName)
                FileOutputStream(file).use { outputStream -> document.writeTo(outputStream) }
                Result.success(file.absolutePath)
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun balancePaint(amountMinor: Long): Paint = when {
        amountMinor > 0L -> redPaintTemplate()
        amountMinor < 0L -> greenPaintTemplate()
        else -> Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 10f
            textAlign = Paint.Align.RIGHT
        }
    }

    private fun redPaintTemplate() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(190, 35, 35)
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.RIGHT
    }

    private fun greenPaintTemplate() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 125, 70)
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.RIGHT
    }

    private fun balanceText(amountMinor: Long): String = when {
        amountMinor > 0L -> "${formatAmount(amountMinor)} (عليه)"
        amountMinor < 0L -> "${formatAmount(-amountMinor)} (له)"
        else -> "0 (متوازن)"
    }

    private fun currencyName(currencyCode: String): String = when (currencyCode) {
        "YER" -> "الريال اليمني"
        "SAR" -> "الريال السعودي"
        "USD" -> "الدولار الأمريكي"
        else -> currencyCode
    }

    private fun formatAmount(amountMinor: Long): String = BigDecimal(amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString()
    private fun formatDate(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date(millis))

    private fun formatDateRange(startDateMillis: Long?, endDateMillisExclusive: Long?): String {
        if (startDateMillis == null && endDateMillisExclusive == null) return "كل الحساب"
        val start = startDateMillis?.let(::formatDate) ?: "غير محدد"
        val end = endDateMillisExclusive?.let { formatDate(addDays(it, -1)) } ?: "غير محدد"
        return "$start - $end"
    }

    private fun addDays(millis: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        add(Calendar.DAY_OF_MONTH, days)
    }.timeInMillis

    private fun safeFileName(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), "_")
        .take(60)
        .ifBlank { "Person" }
}
