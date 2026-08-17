package com.myaccounts.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
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
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val LEFT = 35f
    private const val RIGHT = 560f
    private const val CONTENT_TOP = 42f
    private const val CONTENT_BOTTOM = 790f

    fun exportPersonReport(context: Context, summary: PersonReportSummary, transactions: List<PersonReportTransaction>, startDateMillis: Long?, endDateMillisExclusive: Long?): Result<String> = try {
        val document = PdfDocument()
        val titlePaint = paint(20f, true, Color.rgb(25, 25, 25))
        val headingPaint = paint(13f, true, Color.rgb(35, 35, 35))
        val textPaint = paint(10f, false, Color.rgb(35, 35, 35))
        val redPaint = paint(10f, true, Color.rgb(190, 35, 35))
        val greenPaint = paint(10f, true, Color.rgb(0, 125, 70))
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(185, 185, 185); strokeWidth = 1f }
        val lightLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(225, 225, 225); strokeWidth = 1f }
        val chunks = if (transactions.isEmpty()) listOf(emptyList()) else transactions.chunked(18)

        chunks.forEachIndexed { pageIndex, chunk ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create())
            val canvas = page.canvas
            var y = drawHeader(canvas, summary, startDateMillis, endDateMillisExclusive, titlePaint, headingPaint, textPaint, linePaint)
            if (pageIndex == 0) {
                canvas.drawText("ملخص الحساب", RIGHT, y, headingPaint); y += 20f
                canvas.drawText("الرصيد الافتتاحي: ${balanceText(summary.openingBalanceMinor)}", RIGHT, y, textPaint); y += 17f
                canvas.drawText("إجمالي عليه خلال الفترة: ${formatAmount(summary.periodReceivableMinor)}", RIGHT, y, redPaint); y += 17f
                canvas.drawText("إجمالي له خلال الفترة: ${formatAmount(summary.periodPayableMinor)}", RIGHT, y, greenPaint); y += 17f
                canvas.drawText("الرصيد الختامي: ${balanceText(summary.closingBalanceMinor)}", RIGHT, y, balancePaint(summary.closingBalanceMinor)); y += 24f
            }
            y = drawTableHeader(canvas, y, headingPaint, redPaint, greenPaint, linePaint)
            if (chunk.isEmpty()) {
                canvas.drawText("لا توجد عمليات خلال الفترة المحددة.", RIGHT, y, textPaint)
            } else {
                chunk.forEach { transaction ->
                    canvas.drawText(formatDate(transaction.transactionDate), 78f, y, textPaint)
                    drawSingleLineRight(canvas, transaction.description.ifBlank { "—" }, 310f, y, 220f, textPaint)
                    canvas.drawText(if (transaction.type == "RECEIVABLE") formatAmount(transaction.amountMinor) else "—", 355f, y, if (transaction.type == "RECEIVABLE") redPaint else textPaint)
                    canvas.drawText(if (transaction.type == "PAYABLE") formatAmount(transaction.amountMinor) else "—", 450f, y, if (transaction.type == "PAYABLE") greenPaint else textPaint)
                    canvas.drawText(formatAmount(transaction.balanceMinor), 545f, y, balancePaint(transaction.balanceMinor))
                    y += 10f
                    canvas.drawLine(LEFT, y, RIGHT, y, lightLinePaint)
                    y += 17f
                }
            }
            drawFooter(canvas, pageIndex + 1, chunks.size, linePaint, textPaint)
            document.finishPage(page)
        }

        val fileName = "MyAccounts_Person_Report_${safeFileName(summary.personName)}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"
        saveDocument(context, document, fileName)
    } catch (exception: Exception) { Result.failure(exception) }

    private fun drawHeader(canvas: Canvas, summary: PersonReportSummary, start: Long?, end: Long?, titlePaint: Paint, headingPaint: Paint, textPaint: Paint, linePaint: Paint): Float {
        var y = CONTENT_TOP
        canvas.drawText("تقرير حساب شخصي", RIGHT, y, titlePaint); y += 27f
        canvas.drawText("الاسم: ${summary.personName}", RIGHT, y, headingPaint); y += 19f
        canvas.drawText("الهاتف: ${summary.phone.ifBlank { "غير مسجل" }}", RIGHT, y, textPaint); y += 17f
        y = drawWrappedRight(canvas, "العنوان: ${summary.address.ifBlank { "غير مسجل" }}", RIGHT, y, 500f, textPaint) + 2f
        canvas.drawText("العملة: ${currencyName(summary.currencyCode)}", RIGHT, y, textPaint); y += 17f
        canvas.drawText("الفترة: ${formatDateRange(start, end)}", RIGHT, y, textPaint); y += 17f
        canvas.drawText("تاريخ إصدار التقرير: ${formatDate(System.currentTimeMillis())}", RIGHT, y, textPaint); y += 17f
        canvas.drawText("عدد العمليات: ${summary.transactionCount}", RIGHT, y, textPaint); y += 12f
        canvas.drawLine(LEFT, y, RIGHT, y, linePaint)
        return y + 20f
    }

    private fun drawTableHeader(canvas: Canvas, yStart: Float, heading: Paint, red: Paint, green: Paint, line: Paint): Float {
        var y = yStart
        canvas.drawLine(LEFT, y - 14f, RIGHT, y - 14f, line)
        canvas.drawText("الرصيد", 545f, y, heading)
        canvas.drawText("له", 450f, y, green)
        canvas.drawText("عليه", 355f, y, red)
        canvas.drawText("البيان", 310f, y, heading)
        canvas.drawText("التاريخ", 78f, y, heading)
        y += 9f
        canvas.drawLine(LEFT, y, RIGHT, y, line)
        return y + 19f
    }

    private fun drawSingleLineRight(canvas: Canvas, value: String, right: Float, y: Float, width: Float, paint: Paint) {
        val safe = if (paint.measureText(value) <= width) value else {
            var end = value.length
            while (end > 0 && paint.measureText("…" + value.substring(0, end)) > width) end--
            if (end > 0) "…" + value.substring(0, end) else "…"
        }
        canvas.drawText(safe, right, y, paint)
    }

    private fun drawWrappedRight(canvas: Canvas, text: String, right: Float, yStart: Float, width: Float, paint: Paint): Float {
        var y = yStart
        var remaining = text.trim()
        while (remaining.isNotEmpty()) {
            var end = remaining.length
            while (end > 1 && paint.measureText(remaining.substring(0, end)) > width) end--
            val breakAt = remaining.lastIndexOf(' ', end - 1).takeIf { it > 0 } ?: end
            val line = remaining.substring(0, breakAt).trim()
            canvas.drawText(line, right, y, paint)
            y += 17f
            remaining = remaining.substring(breakAt).trim()
        }
        return y
    }

    private fun drawFooter(canvas: Canvas, page: Int, total: Int, line: Paint, text: Paint) {
        canvas.drawLine(LEFT, CONTENT_BOTTOM, RIGHT, CONTENT_BOTTOM, line)
        canvas.drawText("صفحة $page من $total", RIGHT, CONTENT_BOTTOM + 17f, text)
    }

    private fun saveDocument(context: Context, document: PdfDocument, fileName: String): Result<String> = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MyAccounts")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("تعذر إنشاء ملف تقرير الشخص.")
            try {
                resolver.openOutputStream(uri).use { output -> if (output == null) throw IllegalStateException("تعذر فتح ملف تقرير الشخص."); document.writeTo(output) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
            Result.success("تم حفظ تقرير الشخص في مجلد التنزيلات/MyAccounts")
        } else {
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MyAccounts")
            if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("تعذر إنشاء مجلد التقرير.")
            val file = File(directory, fileName)
            FileOutputStream(file).use { document.writeTo(it) }
            Result.success(file.absolutePath)
        }
    } catch (e: Exception) { Result.failure(e) }

    private fun paint(size: Float, bold: Boolean, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun balancePaint(value: Long) = paint(10f, true, when { value > 0L -> Color.rgb(190, 35, 35); value < 0L -> Color.rgb(0, 125, 70); else -> Color.DKGRAY })
    private fun balanceText(value: Long) = when { value > 0L -> "${formatAmount(value)} (عليه)"; value < 0L -> "${formatAmount(-value)} (له)"; else -> "0 (متوازن)" }
    private fun currencyName(code: String) = when (code) { "YER" -> "الريال اليمني"; "SAR" -> "الريال السعودي"; "USD" -> "الدولار الأمريكي"; else -> code }
    private fun formatAmount(value: Long) = BigDecimal(value).movePointLeft(2).stripTrailingZeros().toPlainString()
    private fun formatDate(value: Long) = SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date(value))
    private fun formatDateRange(start: Long?, end: Long?) = if (start == null && end == null) "كل الحساب" else "${start?.let(::formatDate) ?: "غير محدد"} - ${end?.let { formatDate(addDays(it, -1)) } ?: "غير محدد"}"
    private fun addDays(value: Long, days: Int) = Calendar.getInstance().apply { timeInMillis = value; add(Calendar.DAY_OF_MONTH, days) }.timeInMillis
    private fun safeFileName(value: String) = value.replace(Regex("[\\\\/:*?\"<>|]"), "_").replace(Regex("\\s+"), "_").take(60).ifBlank { "Person" }
}
