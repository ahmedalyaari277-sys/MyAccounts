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
    private const val LEFT = 35f
    private const val RIGHT = 560f

    fun exportPeopleReport(context: Context, currency: String, summary: CurrencyReportSummary, people: List<CurrencyReportPersonRow>, startDateMillis: Long?, endDateMillisExclusive: Long?): Result<String> = try {
        val document = PdfDocument(); val chunks = if (people.isEmpty()) listOf(emptyList()) else people.chunked(22)
        chunks.forEachIndexed { index, chunk ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
            val canvas = page.canvas; var y = header(canvas, "تقرير الأشخاص", currency, startDateMillis, endDateMillisExclusive)
            val heading = paint(10, Color.rgb(25,25,25), true); val green = paint(9, Color.rgb(0,125,70), true); val red = paint(9, Color.rgb(190,35,35), true); val text = paint(9, Color.rgb(35,35,35), false); val line = linePaint()
            if (index == 0) { canvas.drawText("ملخص الحساب", RIGHT, y, heading); y += 17f; drawSummary(canvas, y, summary, green, red); y += 24f }
            y = tableTop(canvas, y, listOf("الشخص", "العملة", "عليه", "له", "الرصيد"), heading, green, red, intArrayOf(105, 205, 315, 405, 540), line)
            chunk.forEach { p ->
                canvas.drawText(p.personName.take(20), 105f, y, text); canvas.drawText(currencyName(currency), 205f, y, text)
                canvas.drawText("+${formatAmount(p.totalReceivableMinor)}", 315f, y, green); canvas.drawText("-${formatAmount(p.totalPayableMinor)}", 405f, y, red); canvas.drawText(signedBalance(p.balanceMinor), 540f, y, balancePaint(p.balanceMinor)); rowLines(canvas, y, line); y += 25f
            }
            if (chunk.isEmpty()) canvas.drawText("لا توجد بيانات ضمن الفترة المحددة.", RIGHT, y, text)
            footer(canvas, index + 1, chunks.size, line, text); document.finishPage(page)
        }
        save(context, document, "MyAccounts_تقرير_الأشخاص_${currency}_${timestamp()}.pdf")
    } catch (e: Exception) { Result.failure(e) }

    fun exportDetailedReport(context: Context, currency: String, transactions: List<GeneralReportTransactionRow>, startDateMillis: Long?, endDateMillisExclusive: Long?): Result<String> = try {
        val document = PdfDocument(); val chunks = if (transactions.isEmpty()) listOf(emptyList()) else transactions.chunked(25)
        chunks.forEachIndexed { index, chunk ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create()); val canvas = page.canvas
            var y = header(canvas, "التقرير التفصيلي للعمليات", currency, startDateMillis, endDateMillisExclusive)
            val heading = paint(9, Color.rgb(25,25,25), true); val green = paint(8, Color.rgb(0,125,70), true); val red = paint(8, Color.rgb(190,35,35), true); val text = paint(8, Color.rgb(35,35,35), false); val line = linePaint()
            y = tableTop(canvas, y, listOf("التاريخ", "الشخص", "البيان", "عليه", "له"), heading, green, red, intArrayOf(55, 150, 310, 420, 515), line)
            chunk.forEach { t ->
                canvas.drawText(formatDate(t.transactionDate), 55f, y, text); canvas.drawText(t.personName.take(17), 150f, y, text); canvas.drawText(t.description.ifBlank { "—" }.take(25), 310f, y, text)
                canvas.drawText(if (t.type == "RECEIVABLE") "+${formatAmount(t.amountMinor)}" else "—", 420f, y, if (t.type == "RECEIVABLE") green else text)
                canvas.drawText(if (t.type == "PAYABLE") "-${formatAmount(t.amountMinor)}" else "—", 515f, y, if (t.type == "PAYABLE") red else text); rowLines(canvas, y, line); y += 24f
            }
            if (chunk.isEmpty()) canvas.drawText("لا توجد عمليات ضمن الفترة المحددة.", RIGHT, y, text)
            footer(canvas, index + 1, chunks.size, line, text); document.finishPage(page)
        }
        save(context, document, "MyAccounts_التقرير_التفصيلي_${currency}_${timestamp()}.pdf")
    } catch (e: Exception) { Result.failure(e) }

    fun exportSummaryReport(context: Context, currency: String, rows: List<PersonCurrencySummaryRow>, startDateMillis: Long?, endDateMillisExclusive: Long?): Result<String> = try {
        val document = PdfDocument(); val chunks = if (rows.isEmpty()) listOf(emptyList()) else rows.chunked(18)
        chunks.forEachIndexed { index, chunk ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create()); val canvas = page.canvas
            var y = header(canvas, "ملخص تقرير الأشخاص", currency, startDateMillis, endDateMillisExclusive)
            val heading = paint(8, Color.rgb(25,25,25), true); val green = paint(8, Color.rgb(0,125,70), true); val red = paint(8, Color.rgb(190,35,35), true); val text = paint(7, Color.rgb(35,35,35), false); val line = linePaint()
            y = tableTop(canvas, y, listOf("الشخص", "عليه", "له", "الرصيد", "أول عملية", "آخر عملية"), heading, green, red, intArrayOf(75, 210, 290, 380, 475, 550), line)
            chunk.forEach { r ->
                canvas.drawText(r.personName.take(18), 75f, y, text); canvas.drawText("+${formatAmount(r.totalReceivableMinor)}", 210f, y, green); canvas.drawText("-${formatAmount(r.totalPayableMinor)}", 290f, y, red); canvas.drawText(signedBalance(r.balanceMinor), 380f, y, balancePaint(r.balanceMinor)); canvas.drawText(firstDate(r), 475f, y, text); canvas.drawText(lastDate(r), 550f, y, text); rowLines(canvas, y, line); y += 24f
            }
            if (chunk.isEmpty()) canvas.drawText("لا توجد بيانات ضمن الفترة المحددة.", RIGHT, y, text)
            footer(canvas, index + 1, chunks.size, line, text); document.finishPage(page)
        }
        save(context, document, "MyAccounts_ملخص_الأشخاص_${currency}_${timestamp()}.pdf")
    } catch (e: Exception) { Result.failure(e) }

    private fun header(canvas: Canvas, title: String, currency: String, start: Long?, end: Long?): Float {
        val titlePaint = paint(18, Color.rgb(25,25,25), true); val text = paint(9, Color.rgb(45,45,45), false); var y = 42f
        canvas.drawText(title, RIGHT, y, titlePaint); y += 23f
        canvas.drawText("العملة: ${currencyName(currency)}", RIGHT, y, text); canvas.drawText("الفترة: ${formatDateRange(start,end)}", 350f, y, text); canvas.drawText("إصدار: ${formatDate(System.currentTimeMillis())}", 170f, y, text); y += 18f
        canvas.drawLine(LEFT, y, RIGHT, y, linePaint()); return y + 22f
    }

    private fun drawSummary(canvas: Canvas, y: Float, summary: CurrencyReportSummary, green: Paint, red: Paint) {
        canvas.drawText("عليه: +${formatAmount(summary.totalReceivableMinor)}", RIGHT, y, green)
        canvas.drawText("له: -${formatAmount(summary.totalPayableMinor)}", 390f, y, red)
        canvas.drawText("الرصيد: ${signedBalance(summary.balanceMinor)}", 215f, y, balancePaint(summary.balanceMinor))
        canvas.drawText("العمليات: ${summary.transactionCount}", 70f, y, paint(8, Color.DKGRAY, false))
    }

    private fun tableTop(canvas: Canvas, y: Float, labels: List<String>, heading: Paint, green: Paint, red: Paint, x: IntArray, line: Paint): Float {
        val bounds = floatArrayOf(35f, 120f, 220f, 315f, 410f, 510f, 560f); canvas.drawLine(LEFT, y-12f, RIGHT, y-12f, line)
        labels.forEachIndexed { i, label -> canvas.drawText(label, x[i].toFloat(), y, when(label){"عليه"->green;"له"->red;else->heading}) }
        bounds.forEach { canvas.drawLine(it, y-12f, it, y+10f, line) }; canvas.drawLine(LEFT, y+10f, RIGHT, y+10f, line); return y+25f
    }
    private fun rowLines(canvas: Canvas, y: Float, line: Paint) { canvas.drawLine(LEFT, y+9f, RIGHT, y+9f, line) }
    private fun footer(canvas: Canvas, page: Int, total: Int, line: Paint, text: Paint) { canvas.drawLine(LEFT, 790f, RIGHT, 790f, line); canvas.drawText("صفحة $page من $total", RIGHT, 807f, text) }
    private fun paint(size: Int, color: Int, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color; textSize=size.toFloat(); textAlign=Paint.Align.RIGHT; typeface=Typeface.create("sans-serif", if(bold) Typeface.BOLD else Typeface.NORMAL) }
    private fun linePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(170,170,170); strokeWidth=1f }
    private fun balancePaint(v: Long) = paint(8, when { v>0->Color.rgb(0,125,70); v<0->Color.rgb(190,35,35); else->Color.DKGRAY }, true)
    private fun signedBalance(v: Long) = when { v>0->"+${formatAmount(v)} (عليه)"; v<0->"-${formatAmount(-v)} (له)"; else->"0 (متوازن)" }
    private fun firstDate(r: PersonCurrencySummaryRow) = listOfNotNull(r.firstReceivableDate, r.firstPayableDate).minOrNull()?.let(::formatDate) ?: "—"
    private fun lastDate(r: PersonCurrencySummaryRow) = listOfNotNull(r.lastReceivableDate, r.lastPayableDate).maxOrNull()?.let(::formatDate) ?: "—"
    private fun currencyName(c:String)=when(c){"YER"->"الريال اليمني";"SAR"->"الريال السعودي";"USD"->"الدولار الأمريكي";else->c}
    private fun formatAmount(v:Long)=BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
    private fun formatDate(v:Long)=SimpleDateFormat("dd/MM/yyyy",Locale("ar")).format(Date(v))
    private fun formatDateRange(s:Long?,e:Long?)=if(s==null&&e==null)"كل الحساب" else "${s?.let(::formatDate)?:"غير محدد"} - ${e?.let{formatDate(addDays(it,-1))}?:"غير محدد"}"
    private fun addDays(v:Long,d:Int)=Calendar.getInstance().apply{timeInMillis=v;add(Calendar.DAY_OF_MONTH,d)}.timeInMillis
    private fun timestamp()=SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())
    private fun save(context:Context,document:PdfDocument,fileName:String):Result<String> = try { if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){val values=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,fileName);put(MediaStore.Downloads.MIME_TYPE,"application/pdf");put(MediaStore.Downloads.RELATIVE_PATH,"${Environment.DIRECTORY_DOWNLOADS}/MyAccounts");put(MediaStore.Downloads.IS_PENDING,1)};val resolver=context.contentResolver;val uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:throw IllegalStateException("تعذر إنشاء ملف التقرير.");try{resolver.openOutputStream(uri).use{output->if(output==null)throw IllegalStateException("تعذر فتح ملف التقرير.");document.writeTo(output)};resolver.update(uri,ContentValues().apply{put(MediaStore.Downloads.IS_PENDING,0)},null,null)}catch(e:Exception){resolver.delete(uri,null,null);throw e};Result.success("تم حفظ التقرير في مجلد التنزيلات/MyAccounts")}else{val dir=File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"MyAccounts");if(!dir.exists()&&!dir.mkdirs())throw IllegalStateException("تعذر إنشاء مجلد التقرير.");val file=File(dir,fileName);FileOutputStream(file).use{document.writeTo(it)};Result.success(file.absolutePath)}}catch(e:Exception){Result.failure(e)}
}
