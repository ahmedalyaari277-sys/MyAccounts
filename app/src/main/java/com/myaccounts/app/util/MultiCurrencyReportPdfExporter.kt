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
import com.myaccounts.app.data.reports.CurrencyReportPersonRowWithCurrency
import com.myaccounts.app.data.reports.CurrencyReportSummary
import com.myaccounts.app.data.reports.GeneralReportTransactionRow
import com.myaccounts.app.data.reports.MultiCurrencyPersonReport
import com.myaccounts.app.data.reports.PersonCurrencySummaryRow
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MultiCurrencyReportPdfExporter {
    private const val W = 842
    private const val H = 595
    private const val LEFT = 24f
    private const val RIGHT = 818f
    private const val BLUE = 0xFF1565C0.toInt()
    private const val RED = 0xFFC62828.toInt()
    private const val GREEN = 0xFF16834A.toInt()
    private val currencies = listOf("YER", "SAR", "USD")

    fun exportPeopleReport(context: Context, summaries: List<CurrencyReportSummary>, people: List<CurrencyReportPersonRowWithCurrency>, start: Long?, end: Long?): Result<String> =
        save(context, "تقرير الأشخاص", buildPeople(summaries, people, start, end))

    fun exportDetailedReport(context: Context, summaries: List<CurrencyReportSummary>, transactions: List<GeneralReportTransactionRow>, start: Long?, end: Long?): Result<String> =
        save(context, "التقرير العام", buildDetailed(summaries, transactions, start, end))

    fun exportSummaryReport(context: Context, rows: List<PersonCurrencySummaryRow>, start: Long?, end: Long?): Result<String> =
        save(context, "أرصدة الحسابات", buildSummary(rows, start, end))

    fun exportPersonReport(context: Context, report: MultiCurrencyPersonReport, start: Long?, end: Long?): Result<String> =
        save(context, "تقرير حساب ${report.personName}", buildPerson(report, start, end))

    private fun buildPeople(summaries: List<CurrencyReportSummary>, people: List<CurrencyReportPersonRowWithCurrency>, start: Long?, end: Long?): PdfDocument {
        val doc = PdfDocument()
        val rows = people.map { it.personId to it.personName }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
        val pages = rows.chunked(10).ifEmpty { listOf(emptyList()) }
        pages.forEachIndexed { i, chunk ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, i + 1).create())
            val c = page.canvas
            header(c, "تقرير الأشخاص — جميع العملات", "الفترة: ${range(start, end)}")
            val cols = peopleColumns()
            tableHeader(c, 105f, cols)
            var y = 137f
            chunk.forEach { (id, name) ->
                val cells = mutableListOf(name)
                currencies.forEach { cur ->
                    val p = people.firstOrNull { it.personId == id && it.currencyCode == cur }
                    cells += listOf(
                        format(p?.totalReceivableMinor ?: 0),
                        format(p?.totalPayableMinor ?: 0),
                        balanceText(p?.balanceMinor ?: 0),
                        "${p?.transactionCount ?: 0}"
                    )
                }
                row(c, y, cols, cells)
                y += 36f
            }
            if (i == pages.lastIndex) totalPeople(c, y, cols, people)
            footer(c, i + 1, pages.size)
            doc.finishPage(page)
        }
        return doc
    }

    private fun buildDetailed(summaries: List<CurrencyReportSummary>, transactions: List<GeneralReportTransactionRow>, start: Long?, end: Long?): PdfDocument {
        val doc = PdfDocument()
        val chunks = transactions.chunked(9).ifEmpty { listOf(emptyList()) }
        chunks.forEachIndexed { i, chunk ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, i + 1).create())
            val c = page.canvas
            header(c, "التقرير العام — جميع العملات", "الفترة: ${range(start, end)}")
            val cols = detailedColumns()
            tableHeader(c, 105f, cols)
            var y = 137f
            val balances = mutableMapOf<String, Long>()
            chunk.forEach { t ->
                val key = "${t.personName}|${t.currencyCode}"
                val next = (balances[key] ?: 0) + if (t.type == "RECEIVABLE") t.amountMinor else -t.amountMinor
                balances[key] = next
                val cells = mutableListOf(date(t.transactionDate), t.personName, t.description.ifBlank { "—" })
                currencies.forEach { cur ->
                    if (cur == t.currencyCode) {
                        cells += listOf(
                            if (t.type == "RECEIVABLE") format(t.amountMinor) else "—",
                            if (t.type == "PAYABLE") format(t.amountMinor) else "—",
                            balanceText(next)
                        )
                    } else {
                        cells += listOf("—", "—", "—")
                    }
                }
                row(c, y, cols, cells)
                y += 42f
            }
            if (i == chunks.lastIndex) totalDetailed(c, y, cols, summaries)
            footer(c, i + 1, chunks.size)
            doc.finishPage(page)
        }
        return doc
    }

    private fun buildSummary(rowsData: List<PersonCurrencySummaryRow>, start: Long?, end: Long?): PdfDocument {
        val doc = PdfDocument()
        val persons = rowsData.map { it.personId to it.personName }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
        val pages = persons.chunked(9).ifEmpty { listOf(emptyList()) }
        pages.forEachIndexed { i, chunk ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, i + 1).create())
            val c = page.canvas
            header(c, "تقرير أرصدة الحسابات — جميع العملات", "الفترة: ${range(start, end)}")
            val cols = summaryColumns()
            tableHeader(c, 105f, cols)
            var y = 137f
            chunk.forEach { (id, name) ->
                val cells = mutableListOf(name)
                currencies.forEach { cur ->
                    val r = rowsData.firstOrNull { it.personId == id && it.currencyCode == cur }
                    cells += listOf(
                        format(r?.totalReceivableMinor ?: 0),
                        format(r?.totalPayableMinor ?: 0),
                        balanceText(r?.balanceMinor ?: 0),
                        "${r?.transactionCount ?: 0}"
                    )
                }
                row(c, y, cols, cells)
                y += 40f
            }
            if (i == pages.lastIndex) totalSummary(c, y, cols, rowsData)
            footer(c, i + 1, pages.size)
            doc.finishPage(page)
        }
        return doc
    }

    private fun buildPerson(report: MultiCurrencyPersonReport, start: Long?, end: Long?): PdfDocument {
        val doc = PdfDocument()
        val transactions = report.reports
            .flatMap { r -> r.transactions.map { r.currencyCode to it } }
            .sortedWith(
                compareBy<Pair<String, com.myaccounts.app.data.reports.PersonReportTransaction>> { it.second.transactionDate }
                    .thenBy { it.second.transactionId }
            )
        val pages = transactions.chunked(7).ifEmpty { listOf(emptyList()) }
        pages.forEachIndexed { i, chunk ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, i + 1).create())
            val c = page.canvas
            header(
                c,
                "تقرير حساب ${report.personName} — جميع العملات",
                "${report.phone.ifBlank { "غير مسجل" }}  |  ${report.address.ifBlank { "غير مسجل" }}  |  ${range(start, end)}"
            )
            val cols = personColumns()
            tableHeader(c, 105f, cols)
            var y = 137f
            chunk.forEach { (cur, t) ->
                val cells = mutableListOf(date(t.transactionDate), t.description.ifBlank { "—" })
                currencies.forEach { cc ->
                    if (cc == cur) {
                        cells += listOf(
                            if (t.type == "RECEIVABLE") format(t.amountMinor) else "—",
                            if (t.type == "PAYABLE") format(t.amountMinor) else "—",
                            balanceText(t.balanceMinor)
                        )
                    } else {
                        cells += listOf("—", "—", "—")
                    }
                }
                row(c, y, cols, cells)
                y += 48f
            }
            if (i == pages.lastIndex) totalPerson(c, y, cols, report)
            footer(c, i + 1, pages.size)
            doc.finishPage(page)
        }
        return doc
    }

    private data class Col(val left: Float, val right: Float, val title: String)

    private fun peopleColumns(): List<Col> {
        val out = mutableListOf(Col(24f, 150f, "الشخص"))
        var x = 150f
        currencies.forEach {
            out += Col(x, x + 55f, "عليه / ${currencyName(it)}")
            out += Col(x + 55f, x + 110f, "له / ${currencyName(it)}")
            out += Col(x + 110f, x + 175f, "الرصيد / ${currencyName(it)}")
            out += Col(x + 175f, x + 220f, "العمليات")
            x += 220f
        }
        return out
    }

    private fun detailedColumns(): List<Col> {
        val out = mutableListOf(
            Col(24f, 84f, "التاريخ"),
            Col(84f, 174f, "الشخص"),
            Col(174f, 278f, "البيان")
        )
        var x = 278f
        currencies.forEach {
            out += Col(x, x + 94f, "عليه / ${currencyName(it)}")
            out += Col(x + 94f, x + 188f, "له / ${currencyName(it)}")
            out += Col(x + 188f, x + 282f, "الرصيد / ${currencyName(it)}")
            x += 180f
        }
        return out
    }

    private fun summaryColumns() = peopleColumns()

    private fun personColumns(): List<Col> {
        val out = mutableListOf(Col(24f, 94f, "التاريخ"), Col(94f, 224f, "البيان"))
        var x = 224f
        currencies.forEach {
            out += Col(x, x + 90f, "عليه / ${currencyName(it)}")
            out += Col(x + 90f, x + 180f, "له / ${currencyName(it)}")
            out += Col(x + 180f, x + 270f, "الرصيد / ${currencyName(it)}")
            x += 180f
        }
        return out
    }

    private fun header(c: Canvas, title: String, info: String) {
        val t = paint(18f, true, Color.rgb(30, 30, 30))
        c.drawText(title, RIGHT, 34f, t)
        c.drawText(info, RIGHT, 58f, paint(9f, false, Color.DKGRAY))
        c.drawLine(LEFT, 70f, RIGHT, 70f, line())
    }

    private fun tableHeader(c: Canvas, y: Float, cols: List<Col>) {
        val p = paint(8f, true, Color.WHITE)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE }
        cols.forEach { col ->
            c.drawRect(col.left, y - 18f, col.right, y + 18f, bg)
            c.drawText(col.title, (col.left + col.right) / 2f, y + 3f, centerPaint(p))
        }
        c.drawLine(LEFT, y + 18f, RIGHT, y + 18f, line())
    }

    private fun row(c: Canvas, y: Float, cols: List<Col>, values: List<String>) {
        cols.forEachIndexed { i, col ->
            c.drawLine(col.left, y - 18f, col.left, y + 18f, line())
            val v = values.getOrNull(i).orEmpty()
            val color = when {
                v.startsWith("عليه") -> RED
                v.startsWith("له") -> GREEN
                else -> Color.rgb(35, 35, 35)
            }
            c.drawText(
                single(v, col.right - 5f, col.right - col.left - 8f),
                col.right - 5f,
                y + 4f,
                paint(8f, true, color)
            )
        }
        c.drawLine(LEFT, y + 18f, RIGHT, y + 18f, line())
    }

    private fun totalPeople(c: Canvas, y: Float, cols: List<Col>, data: List<CurrencyReportPersonRowWithCurrency>) {
        val values = mutableListOf("المجموع")
        currencies.forEach { cur ->
            val x = data.filter { it.currencyCode == cur }
            val a = x.sumOf { it.totalReceivableMinor }
            val l = x.sumOf { it.totalPayableMinor }
            values += listOf(format(a), format(l), balanceText(a - l), x.sumOf { it.transactionCount }.toString())
        }
        row(c, y, cols, values)
    }

    private fun totalDetailed(c: Canvas, y: Float, cols: List<Col>, summaries: List<CurrencyReportSummary>) {
        val values = mutableListOf("المجموع", "", "")
        currencies.forEach { cur ->
            val x = summaries.firstOrNull { it.currencyCode == cur }
            values += listOf(
                format(x?.totalReceivableMinor ?: 0),
                format(x?.totalPayableMinor ?: 0),
                balanceText(x?.balanceMinor ?: 0)
            )
        }
        row(c, y, cols, values)
    }

    private fun totalSummary(c: Canvas, y: Float, cols: List<Col>, data: List<PersonCurrencySummaryRow>) {
        val values = mutableListOf("المجموع")
        currencies.forEach { cur ->
            val x = data.filter { it.currencyCode == cur }
            val a = x.sumOf { it.totalReceivableMinor }
            val l = x.sumOf { it.totalPayableMinor }
            values += listOf(format(a), format(l), balanceText(a - l), x.sumOf { it.transactionCount }.toString())
        }
        row(c, y, cols, values)
    }

    private fun totalPerson(c: Canvas, y: Float, cols: List<Col>, report: MultiCurrencyPersonReport) {
        val values = mutableListOf("المجموع", "")
        currencies.forEach { cur ->
            val s = report.reports.firstOrNull { it.currencyCode == cur }?.summary
            val a = s?.periodReceivableMinor ?: 0
            val l = s?.periodPayableMinor ?: 0
            values += listOf(format(a), format(l), balanceText(a - l))
        }
        row(c, y, cols, values)
    }

    private fun footer(c: Canvas, page: Int, total: Int) {
        c.drawLine(LEFT, H - 28f, RIGHT, H - 28f, line())
        c.drawText("صفحة $page من $total", RIGHT, H - 10f, paint(8f, false, Color.DKGRAY))
    }

    private fun save(context: Context, title: String, document: PdfDocument): Result<String> = try {
        val fileName = "MyAccounts_${safe(title)}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MyAccounts")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("تعذر إنشاء ملف PDF.")
            try {
                resolver.openOutputStream(uri).use { out ->
                    if (out == null) throw IllegalStateException("تعذر فتح ملف PDF.")
                    document.writeTo(out)
                }
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                    null,
                    null
                )
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            Result.success("تم حفظ تقرير PDF في مجلد التنزيلات/MyAccounts")
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MyAccounts")
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("تعذر إنشاء مجلد التقرير.")
            File(dir, fileName).also {
                FileOutputStream(it).use { out -> document.writeTo(out) }
            }
            Result.success("تم حفظ تقرير PDF في مجلد MyAccounts")
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun paint(size: Float, bold: Boolean, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = size
        this.color = color
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        textAlign = Paint.Align.RIGHT
    }

    private fun centerPaint(base: Paint) = Paint(base).apply { textAlign = Paint.Align.CENTER }

    private fun line() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(205, 205, 205)
        strokeWidth = 1f
    }

    private fun single(value: String, right: Float, width: Float): String {
        var text = value
        val p = paint(8f, false, Color.BLACK)
        while (text.length > 1 && p.measureText(text) > width) text = text.dropLast(1)
        return if (text == value) text else text.dropLast(1) + "…"
    }

    private fun balanceText(value: Long) = when {
        value > 0L -> "عليه ${format(value)}"
        value < 0L -> "له ${format(-value)}"
        else -> "متعادل 0"
    }

    private fun format(value: Long) = BigDecimal(value).movePointLeft(2).stripTrailingZeros().toPlainString()
    private fun date(value: Long) = SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date(value))
    private fun range(start: Long?, end: Long?) = if (start == null && end == null) "كل الحساب" else "${start?.let(::date) ?: "غير محدد"} - ${end?.let { date(it - 1) } ?: "غير محدد"}"
    private fun currencyName(code: String) = when (code) {
        "YER" -> "الريال اليمني"
        "SAR" -> "الريال السعودي"
        "USD" -> "الدولار الأمريكي"
        else -> code
    }
    private fun safe(value: String) = value.replace(Regex("[^\\u0600-\\u06FFA-Za-z0-9_-]+"), "_").take(50)
}
