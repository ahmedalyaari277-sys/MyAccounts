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
    fun exportPersonReport(context: Context, summary: PersonReportSummary, transactions: List<PersonReportTransaction>, startDateMillis: Long?, endDateMillisExclusive: Long?): Result<String> = try {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val left = 35f
        val right = 560f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f; textAlign = Paint.Align.RIGHT }
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190,35,35); textSize = 10f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
        val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0,125,70); textSize = 10f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; strokeWidth = 1f }
        val lightLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; strokeWidth = 1f }

        fun header(canvas: android.graphics.Canvas, yStart: Float): Float {
            var y = yStart
            canvas.drawText("تقرير حساب شخصي", right, y, titlePaint); y += 27f
            canvas.drawText("الاسم: ${summary.personName}", right, y, headingPaint); y += 20f
            canvas.drawText("الهاتف: ${summary.phone.ifBlank { "غير مسجل" }}", right, y, textPaint); y += 18f
            canvas.drawText("العنوان: ${summary.address.ifBlank { "غير مسجل" }}", right, y, textPaint); y += 18f
            canvas.drawText("العملة: ${currencyName(summary.currencyCode)}", right, y, textPaint); y += 18f
            canvas.drawText("الفترة: ${formatDateRange(startDateMillis, endDateMillisExclusive)}", right, y, textPaint); y += 18f
            canvas.drawText("تاريخ إصدار التقرير: ${formatDate(System.currentTimeMillis())}", right, y, textPaint); y += 18f
            canvas.drawText("عدد العمليات: ${summary.transactionCount}", right, y, textPaint); y += 12f
            canvas.drawLine(left, y, right, y, linePaint)
            return y + 20f
        }

        fun tableHeader(canvas: android.graphics.Canvas, yStart: Float): Float {
            var y = yStart
            canvas.drawLine(left, y - 14f, right, y - 14f, linePaint)
            canvas.drawText("الرصيد", 540f, y, headingPaint)
            canvas.drawText("له", 435f, y, greenPaint)
            canvas.drawText("عليه", 345f, y, redPaint)
            canvas.drawText("البيان", 215f, y, headingPaint)
            canvas.drawText("التاريخ", 65f, y, headingPaint)
            y += 9f
            canvas.drawLine(left, y, right, y, linePaint)
            return y + 19f
        }

        val rowsPerPage = 18
        val chunks = if (transactions.isEmpty()) listOf(emptyList()) else transactions.chunked(rowsPerPage)
        chunks.forEachIndexed { index, chunk ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create())
            val canvas = page.canvas
            var y = header(canvas, 42f)
            if (index == 0) {
                canvas.drawText("ملخص الحساب", right, y, headingPaint); y += 22f
                canvas.drawText("الرصيد الافتتاحي: ${balanceText(summary.openingBalanceMinor)}", right, y, textPaint); y += 18f
                canvas.drawText("إجمالي عليه خلال الفترة: ${formatAmount(summary.periodReceivableMinor)}", right, y, redPaint); y += 18f
                canvas.drawText("إجمالي له خلال الفترة: ${formatAmount(summary.periodPayableMinor)}", right, y, greenPaint); y += 18f
                canvas.drawText("الرصيد: ${balanceText(summary.closingBalanceMinor)}", right, y, balancePaint(summary.closingBalanceMinor)); y += 25f
            }
            y = tableHeader(canvas, y)
            if (chunk.isEmpty()) {
                canvas.drawText("لا توجد عمليات خلال الفترة المحددة.", right, y, textPaint)
            } else {
                chunk.forEach { transaction ->
                    canvas.drawText(formatDate(transaction.transactionDate), 65f, y, textPaint)
                    canvas.drawText(transaction.description.ifBlank { "—" }.take(24), 215f, y, textPaint)
                    canvas.drawText(if (transaction.type == "RECEIVABLE") formatAmount(transaction.amountMinor) else "—", 345f, y, if (transaction.type == "RECEIVABLE") redPaint else textPaint)
                    canvas.drawText(if (transaction.type == "PAYABLE") formatAmount(transaction.amountMinor) else "—", 435f, y, if (transaction.type == "PAYABLE") greenPaint else textPaint)
                    canvas.drawText(formatAmount(transaction.balanceMinor), 540f, y, balancePaint(transaction.balanceMinor))
                    y += 10f
                    canvas.drawLine(left, y, right, y, lightLinePaint)
                    y += 17f
                }
            }
            document.finishPage(page)
        }

        val fileName = "MyAccounts_Person_Report_${safeFileName(summary.personName)}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MyAccounts")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("تعذر إنشاء ملف تقرير الشخص.")
            try { resolver.openOutputStream(uri).use { output -> if (output == null) throw IllegalStateException("تعذر فتح ملف تقرير الشخص."); document.writeTo(output) } }
            catch (e: Exception) { resolver.delete(uri, null, null); throw e }
            Result.success("تم حفظ تقرير الشخص في مجلد التنزيلات/MyAccounts")
        } else {
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MyAccounts")
            if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("تعذر إنشاء مجلد التقرير.")
            val file = File(directory, fileName)
            FileOutputStream(file).use { document.writeTo(it) }
            Result.success(file.absolutePath)
        }
    } catch (exception: Exception) { Result.failure(exception) }

    private fun balancePaint(value: Long) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = when { value > 0 -> Color.rgb(190,35,35); value < 0 -> Color.rgb(0,125,70); else -> Color.DKGRAY }; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
    private fun balanceText(value: Long) = when { value > 0 -> "${formatAmount(value)} (عليه)"; value < 0 -> "${formatAmount(-value)} (له)"; else -> "0 (متوازن)" }
    private fun currencyName(code: String) = when (code) { "YER" -> "الريال اليمني"; "SAR" -> "الريال السعودي"; "USD" -> "الدولار الأمريكي"; else -> code }
    private fun formatAmount(value: Long) = BigDecimal(value).movePointLeft(2).stripTrailingZeros().toPlainString()
    private fun formatDate(value: Long) = SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date(value))
    private fun formatDateRange(start: Long?, end: Long?) = if (start == null && end == null) "كل الحساب" else "${start?.let(::formatDate) ?: "غير محدد"} - ${end?.let { formatDate(addDays(it,-1)) } ?: "غير محدد"}"
    private fun addDays(value: Long, days: Int) = Calendar.getInstance().apply { timeInMillis=value; add(Calendar.DAY_OF_MONTH,days) }.timeInMillis
    private fun safeFileName(value: String) = value.replace(Regex("[\\\\/:*?\"<>|]"), "_").replace(Regex("\\s+"), "_").take(60).ifBlank { "Person" }
}
