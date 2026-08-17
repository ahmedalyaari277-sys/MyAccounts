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
import com.myaccounts.app.data.reports.GeneralReportTransactionRow
import com.myaccounts.app.data.reports.PersonCurrencySummaryRow
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object GeneralReportsPdfExporter {

    fun exportPeopleReport(
        context: Context,
        currency: String,
        summary: CurrencyReportSummary,
        people: List<CurrencyReportPersonRow>,
        startDateMillis: Long?,
        endDateMillisExclusive: Long?
    ): Result<String> = createPdf(context, "تقرير الأشخاص", currency, startDateMillis, endDateMillisExclusive) { document, page ->
        drawPeopleReport(document, page, summary, people)
    }

    fun exportDetailedReport(
        context: Context,
        currency: String,
        transactions: List<GeneralReportTransactionRow>,
        startDateMillis: Long?,
        endDateMillisExclusive: Long?
    ): Result<String> = createPdf(context, "التقرير التفصيلي للعمليات", currency, startDateMillis, endDateMillisExclusive) { document, page ->
        drawDetailedReport(document, page, transactions)
    }

    fun exportSummaryReport(
        context: Context,
        currency: String,
        rows: List<PersonCurrencySummaryRow>,
        startDateMillis: Long?,
        endDateMillisExclusive: Long?
    ): Result<String> = createPdf(context, "ملخص تقرير الأشخاص", currency, startDateMillis, endDateMillisExclusive) { document, page ->
        drawSummaryReport(document, page, rows)
    }

    private fun createPdf(
        context: Context,
        title: String,
        currency: String,
        startDateMillis: Long?,
        endDateMillisExclusive: Long?,
        drawer: (PdfDocument, PdfDocument.Page) -> Unit
    ): Result<String> {
        return try {
            val document = PdfDocument()
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            drawer(document, page)
            document.finishPage(page)
            val fileName = "MyAccounts_${safeFileName(title)}_${currency}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"
            save(context, document, fileName)
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun drawHeader(canvas: android.graphics.Canvas, title: String, currency: String, start: Long?, end: Long?): Float {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 11f; textAlign = Paint.Align.RIGHT }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; strokeWidth = 1f }
        var y = 42f
        val right = 560f
        canvas.drawText("MyAccounts - $title", right, y, titlePaint)
        y += 25f
        canvas.drawText("العملة: ${currencyName(currency)}", right, y, textPaint)
        y += 18f
        canvas.drawText("الفترة: ${formatDateRange(start, end)}", right, y, textPaint)
        y += 18f
        canvas.drawText("تاريخ إصدار التقرير: ${formatDate(System.currentTimeMillis())}", right, y, textPaint)
        y += 15f
        canvas.drawLine(35f, y, right, y, linePaint)
        return y + 22f
    }

    private fun drawPeopleReport(document: PdfDocument, page: PdfDocument.Page, summary: CurrencyReportSummary, people: List<CurrencyReportPersonRow>) {
        val canvas = page.canvas
        var y = drawHeader(canvas, "تقرير الأشخاص", summary.currencyCode, null, null)
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
        val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190, 35, 35); textSize = 10f; textAlign = Paint.Align.RIGHT }
        val green = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 125, 70); textSize = 10f; textAlign = Paint.Align.RIGHT }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f; textAlign = Paint.Align.RIGHT }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; strokeWidth = 1f }
        canvas.drawText("ملخص: عليه ${formatAmount(summary.totalReceivableMinor)} | له ${formatAmount(summary.totalPayableMinor)} | الرصيد ${balanceText(summary.balanceMinor)}", 560f, y, text)
        y += 28f
        canvas.drawLine(35f, y - 12f, 560f, y - 12f, line)
        canvas.drawText("الرصيد", 540f, y, heading)
        canvas.drawText("له", 420f, y, green)
        canvas.drawText("عليه", 330f, y, red)
        canvas.drawText("العملة", 250f, y, heading)
        canvas.drawText("الشخص", 100f, y, heading)
        y += 8f
        canvas.drawLine(35f, y, 560f, y, line)
        y += 18f
        people.forEach { person ->
            if (y > 780f) return@forEach
            canvas.drawText(formatAmount(person.balanceMinor), 540f, y, balancePaint(person.balanceMinor))
            canvas.drawText(formatAmount(person.totalPayableMinor), 420f, y, green)
            canvas.drawText(formatAmount(person.totalReceivableMinor), 330f, y, red)
            canvas.drawText(currencyName(summary.currencyCode), 250f, y, text)
            canvas.drawText(person.personName.take(24), 100f, y, text)
            y += 9f
            canvas.drawLine(35f, y, 560f, y, line)
            y += 17f
        }
    }

    private fun drawDetailedReport(document: PdfDocument, page: PdfDocument.Page, transactions: List<GeneralReportTransactionRow>) {
        val canvas = page.canvas
        var y = drawHeader(canvas, "التقرير التفصيلي للعمليات", transactions.firstOrNull()?.currencyCode ?: "YER", null, null)
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
        val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190, 35, 35); textSize = 9f; textAlign = Paint.Align.RIGHT }
        val green = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 125, 70); textSize = 9f; textAlign = Paint.Align.RIGHT }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9f; textAlign = Paint.Align.RIGHT }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; strokeWidth = 1f }
        canvas.drawText("عليه", 540f, y, red)
        canvas.drawText("له", 450f, y, green)
        canvas.drawText("البيان", 335f, y, heading)
        canvas.drawText("العملة", 230f, y, heading)
        canvas.drawText("الشخص", 125f, y, heading)
        canvas.drawText("التاريخ", 55f, y, heading)
        y += 8f
        canvas.drawLine(35f, y, 560f, y, line)
        y += 18f
        transactions.forEach { transaction ->
            if (y > 780f) return@forEach
            canvas.drawText(if (transaction.type == "RECEIVABLE") formatAmount(transaction.amountMinor) else "—", 540f, y, if (transaction.type == "RECEIVABLE") red else text)
            canvas.drawText(if (transaction.type == "PAYABLE") formatAmount(transaction.amountMinor) else "—", 450f, y, if (transaction.type == "PAYABLE") green else text)
            canvas.drawText(transaction.description.ifBlank { "—" }.take(22), 335f, y, text)
            canvas.drawText(currencyName(transaction.currencyCode), 230f, y, text)
            canvas.drawText(transaction.personName.take(18), 125f, y, text)
            canvas.drawText(formatDate(transaction.transactionDate), 55f, y, text)
            y += 8f
            canvas.drawLine(35f, y, 560f, y, line)
            y += 16f
        }
    }

    private fun drawSummaryReport(document: PdfDocument, page: PdfDocument.Page, rows: List<PersonCurrencySummaryRow>) {
        val canvas = page.canvas
        var y = drawHeader(canvas, "ملخص تقرير الأشخاص", rows.firstOrNull()?.currencyCode ?: "YER", null, null)
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
        val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190, 35, 35); textSize = 8f; textAlign = Paint.Align.RIGHT }
        val green = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 125, 70); textSize = 8f; textAlign = Paint.Align.RIGHT }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 8f; textAlign = Paint.Align.RIGHT }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; strokeWidth = 1f }
        canvas.drawText("الرصيد", 540f, y, heading)
        canvas.drawText("له", 450f, y, green)
        canvas.drawText("عليه", 370f, y, red)
        canvas.drawText("فترة له", 275f, y, heading)
        canvas.drawText("فترة عليه", 180f, y, heading)
        canvas.drawText("الشخص", 75f, y, heading)
        y += 8f
        canvas.drawLine(35f, y, 560f, y, line)
        y += 17f
        rows.forEach { row ->
            if (y > 770f) return@forEach
            canvas.drawText(formatAmount(row.balanceMinor), 540f, y, balancePaint(row.balanceMinor))
            canvas.drawText(formatAmount(row.totalPayableMinor), 450f, y, green)
            canvas.drawText(formatAmount(row.totalReceivableMinor), 370f, y, red)
            canvas.drawText(dateRange(row.firstPayableDate, row.lastPayableDate), 275f, y, text)
            canvas.drawText(dateRange(row.firstReceivableDate, row.lastReceivableDate), 180f, y, text)
            canvas.drawText(row.personName.take(18), 75f, y, text)
            y += 8f
            canvas.drawLine(35f, y, 560f, y, line)
            y += 15f
        }
    }

    private fun save(context: Context, document: PdfDocument, fileName: String): Result<String> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MyAccounts")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("تعذر إنشاء ملف التقرير.")
                try { resolver.openOutputStream(uri).use { output -> if (output == null) throw IllegalStateException("تعذر فتح ملف التقرير."); document.writeTo(output) } }
                catch (e: Exception) { resolver.delete(uri, null, null); throw e }
                Result.success("تم حفظ التقرير في مجلد التنزيلات/MyAccounts")
            } else {
                val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MyAccounts")
                if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("تعذر إنشاء مجلد التقرير.")
                val file = File(directory, fileName)
                FileOutputStream(file).use { document.writeTo(it) }
                Result.success(file.absolutePath)
            }
        } catch (exception: Exception) { Result.failure(exception) }
    }

    private fun balancePaint(value: Long) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (value > 0) Color.rgb(190, 35, 35) else if (value < 0) Color.rgb(0, 125, 70) else Color.DKGRAY; textSize = 9f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
    private fun balanceText(value: Long) = when { value > 0 -> "${formatAmount(value)} (عليه)"; value < 0 -> "${formatAmount(-value)} (له)"; else -> "0 (متوازن)" }
    private fun currencyName(code: String) = when (code) { "YER" -> "الريال اليمني"; "SAR" -> "الريال السعودي"; "USD" -> "الدولار الأمريكي"; else -> code }
    private fun formatAmount(value: Long) = BigDecimal(value).movePointLeft(2).stripTrailingZeros().toPlainString()
    private fun formatDate(millis: Long) = SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date(millis))
    private fun formatDateRange(start: Long?, end: Long?) = if (start == null && end == null) "كل الحساب" else "${start?.let(::formatDate) ?: "غير محدد"} - ${end?.let { formatDate(addDays(it, -1)) } ?: "غير محدد"}"
    private fun dateRange(first: Long?, last: Long?) = if (first == null) "—" else "${formatDate(first)} - ${formatDate(last ?: first)}"
    private fun addDays(millis: Long, days: Int) = Calendar.getInstance().apply { timeInMillis = millis; add(Calendar.DAY_OF_MONTH, days) }.timeInMillis
    private fun safeFileName(value: String) = value.replace(Regex("[^A-Za-z0-9_-]+"), "_").take(50)
}
