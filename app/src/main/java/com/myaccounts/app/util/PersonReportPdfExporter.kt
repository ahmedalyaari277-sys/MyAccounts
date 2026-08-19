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
    private const val BLUE = 0xFF1565C0.toInt()
    private const val RED = 0xFFC62828.toInt()
    private const val GREEN = 0xFF16834A.toInt()

    fun exportPersonReport(context: Context, summary: PersonReportSummary, transactions: List<PersonReportTransaction>, startDateMillis: Long?, endDateMillisExclusive: Long?): Result<String> = try {
        val document = PdfDocument()
        val title = paint(19f, true, Color.rgb(25, 25, 25))
        val heading = paint(11f, true, Color.rgb(25, 25, 25))
        val text = paint(9f, false, Color.rgb(35, 35, 35))
        val chunks = if (transactions.isEmpty()) listOf(emptyList()) else transactions.chunked(22)
        chunks.forEachIndexed { pageIndex, chunk ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create())
            val canvas = page.canvas
            var y = drawHeader(canvas, summary, startDateMillis, endDateMillisExclusive, title, heading, text)
            y = drawTableHeader(canvas, y)
            if (chunk.isEmpty()) canvas.drawText("لا توجد عمليات خلال الفترة المحددة.", RIGHT, y, text)
            else chunk.forEach { transaction -> drawTableRow(canvas, y, transaction, text); y += 27f }
            if (pageIndex == chunks.lastIndex) drawTotalRow(canvas, y, summary, text)
            drawFooter(canvas, pageIndex + 1, chunks.size, text)
            document.finishPage(page)
        }
        val fileName = "MyAccounts_Person_Report_${safeFileName(summary.personName)}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"
        saveDocument(context, document, fileName)
    } catch (exception: Exception) { Result.failure(exception) }

    private fun drawHeader(canvas: Canvas, summary: PersonReportSummary, start: Long?, end: Long?, title: Paint, heading: Paint, text: Paint): Float {
        var y = CONTENT_TOP
        canvas.drawText("تقرير حساب شخصي", RIGHT, y, title); y += 25f
        canvas.drawText("الاسم: ${summary.personName}", RIGHT, y, heading)
        canvas.drawText("الهاتف: ${summary.phone.ifBlank { "غير مسجل" }}", 315f, y, text); y += 18f
        canvas.drawText("العنوان: ${summary.address.ifBlank { "غير مسجل" }}", RIGHT, y, text)
        canvas.drawText("العملة: ${currencyName(summary.currencyCode)}", 315f, y, text); y += 18f
        canvas.drawText("الفترة: ${formatDateRange(start, end)}", RIGHT, y, text)
        canvas.drawText("إصدار: ${formatDate(System.currentTimeMillis())}", 315f, y, text); y += 18f
        canvas.drawText("عدد العمليات: ${summary.transactionCount}", RIGHT, y, text)
        canvas.drawLine(LEFT, y + 7f, RIGHT, y + 7f, linePaint())
        return y + 24f
    }

    private fun drawTableHeader(canvas: Canvas, yStart: Float): Float {
        val top = yStart - 15f
        val xs = floatArrayOf(35f, 145f, 245f, 335f, 455f, 560f)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE }
        var i = 0
        while (i < xs.size - 1) {
            canvas.drawRect(xs[i], top, xs[i + 1], yStart + 12f, bg)
            i++
        }
        val white = paint(9f, true, Color.WHITE)
        canvas.drawText("التاريخ", 550f, yStart, white)
        canvas.drawText("البيان", 445f, yStart, white)
        canvas.drawText("عليه", 325f, yStart, white)
        canvas.drawText("له", 235f, yStart, white)
        canvas.drawText("الرصيد", 135f, yStart, white)
        xs.forEach { canvas.drawLine(it, top, it, yStart + 12f, linePaint()) }
        canvas.drawLine(LEFT, top, RIGHT, top, linePaint())
        canvas.drawLine(LEFT, yStart + 12f, RIGHT, yStart + 12f, linePaint())
        return yStart + 28f
    }

    private fun drawTableRow(canvas: Canvas, y: Float, transaction: PersonReportTransaction, text: Paint) {
        val bottom = y + 10f
        val xs = floatArrayOf(35f, 145f, 245f, 335f, 455f, 560f)
        xs.forEach { canvas.drawLine(it, y - 14f, it, bottom, linePaint()) }
        canvas.drawText(formatDate(transaction.transactionDate), 550f, y, text)
        drawSingleLineRight(canvas, transaction.description.ifBlank { "—" }, 445f, y, 95f, text)
        val onUs = transaction.type == "RECEIVABLE"
        val amount = formatAmount(transaction.amountMinor)
        canvas.drawText(if (onUs) "+$amount" else "—", 325f, y, paint(9f, true, if (onUs) GREEN else Color.DKGRAY))
        canvas.drawText(if (!onUs) "-$amount" else "—", 235f, y, paint(9f, true, if (!onUs) RED else Color.DKGRAY))
        canvas.drawText(signedBalance(transaction.balanceMinor), 135f, y, balancePaint(transaction.balanceMinor))
        canvas.drawLine(LEFT, bottom, RIGHT, bottom, linePaint())
    }

    private fun drawTotalRow(canvas: Canvas, y: Float, summary: PersonReportSummary, text: Paint) {
        val top = y - 14f
        val bottom = y + 13f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEAF2FB.toInt() }
        canvas.drawRect(LEFT, top, RIGHT, bottom, bg)
        val totalPaint = paint(9f, true, Color.rgb(30, 30, 30))
        canvas.drawText("المجموع", RIGHT, y + 4f, totalPaint)
        canvas.drawText("عليه: +${formatAmount(summary.periodReceivableMinor)}", 390f, y + 4f, paint(9f, true, GREEN))
        canvas.drawText("له: -${formatAmount(summary.periodPayableMinor)}", 260f, y + 4f, paint(9f, true, RED))
        canvas.drawText("الرصيد: ${signedBalance(summary.periodBalanceMinor)}", 120f, y + 4f, balancePaint(summary.periodBalanceMinor))
    }

    private fun drawSingleLineRight(canvas: Canvas, value: String, right: Float, y: Float, width: Float, paint: Paint) {
        var safe = value
        while (safe.length > 1 && paint.measureText(safe) > width) safe = safe.dropLast(1)
        if (safe != value) safe = safe.dropLast(1).plus("…")
        canvas.drawText(safe, right, y, paint)
    }

    private fun drawFooter(canvas: Canvas, page: Int, total: Int, text: Paint) {
        canvas.drawLine(LEFT, CONTENT_BOTTOM, RIGHT, CONTENT_BOTTOM, linePaint())
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

    private fun paint(size: Float, bold: Boolean, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; textAlign = Paint.Align.RIGHT; typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL) }
    private fun linePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(150, 150, 150); strokeWidth = 1f }
    private fun balancePaint(value: Long) = paint(9f, true, when { value > 0L -> GREEN; value < 0L -> RED; else -> Color.DKGRAY })
    private fun signedBalance(value: Long) = when { value > 0L -> "+${formatAmount(value)} (عليه)"; value < 0L -> "-${formatAmount(-value)} (له)"; else -> "0 (متوازن)" }
    private fun currencyName(code: String) = when (code) { "YER" -> "الريال اليمني"; "SAR" -> "الريال السعودي"; "USD" -> "الدولار الأمريكي"; else -> code }
    private fun formatAmount(value: Long) = BigDecimal(value).movePointLeft(2).stripTrailingZeros().toPlainString()
    private fun formatDate(value: Long) = SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date(value))
    private fun formatDateRange(start: Long?, end: Long?) = if (start == null && end == null) "كل الحساب" else "${start?.let(::formatDate) ?: "غير محدد"} - ${end?.let { formatDate(addDays(it, -1)) } ?: "غير محدد"}"
    private fun addDays(value: Long, days: Int) = Calendar.getInstance().apply { timeInMillis = value; add(Calendar.DAY_OF_MONTH, days) }.timeInMillis
    private fun safeFileName(value: String) = value.replace(Regex("[\\\\/:*?\"<>|]"), "_").replace(Regex("\\s+"), "_").take(60).ifBlank { "Person" }
}
