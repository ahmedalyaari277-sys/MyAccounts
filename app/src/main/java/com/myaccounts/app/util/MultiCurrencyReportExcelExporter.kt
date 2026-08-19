package com.myaccounts.app.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.myaccounts.app.data.reports.CurrencyReportPersonRowWithCurrency
import com.myaccounts.app.data.reports.CurrencyReportSummary
import com.myaccounts.app.data.reports.GeneralReportTransactionRow
import com.myaccounts.app.data.reports.MultiCurrencyPersonReport
import com.myaccounts.app.data.reports.PersonCurrencyReport
import com.myaccounts.app.data.reports.PersonCurrencySummaryRow
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object MultiCurrencyReportExcelExporter {
    private const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private val currencies = listOf("YER", "SAR", "USD")

    fun exportPeopleReport(context: Context, summaries: List<CurrencyReportSummary>, people: List<CurrencyReportPersonRowWithCurrency>, start: Long?, end: Long?): Result<String> =
        save(context, "تقرير الأشخاص", peopleSheet(summaries, people, start, end))

    fun exportDetailedReport(context: Context, summaries: List<CurrencyReportSummary>, transactions: List<GeneralReportTransactionRow>, start: Long?, end: Long?): Result<String> =
        save(context, "التقرير العام", detailedSheet(summaries, transactions, start, end))

    fun exportSummaryReport(context: Context, rows: List<PersonCurrencySummaryRow>, start: Long?, end: Long?): Result<String> =
        save(context, "أرصدة الحسابات", summarySheet(rows, start, end))

    fun exportPersonReport(context: Context, report: MultiCurrencyPersonReport, start: Long?, end: Long?): Result<String> =
        save(context, "تقرير حساب ${report.personName}", personSheet(report, start, end))

    private fun save(context: Context, title: String, sheet: String): Result<String> = try {
        val fileName = "MyAccounts_${safe(title)}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.xlsx"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, MIME)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MyAccounts")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("تعذر إنشاء ملف Excel.")
            try {
                resolver.openOutputStream(uri).use { out ->
                    if (out == null) throw IllegalStateException("تعذر فتح ملف Excel.")
                    writeWorkbook(out, sheet)
                }
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            Result.success("تم حفظ تقرير Excel في مجلد التنزيلات/MyAccounts")
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MyAccounts")
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("تعذر إنشاء مجلد التقرير.")
            File(dir, fileName).also { FileOutputStream(it).use { out -> writeWorkbook(out, sheet) } }
            Result.success("تم حفظ تقرير Excel في مجلد MyAccounts")
        }
    } catch (e: Exception) { Result.failure(e) }

    private fun writeWorkbook(out: OutputStream, sheet: String) {
        ZipOutputStream(out).use { z ->
            entry(z, "[Content_Types].xml", contentTypes())
            entry(z, "_rels/.rels", rootRels())
            entry(z, "xl/workbook.xml", workbook())
            entry(z, "xl/_rels/workbook.xml.rels", workbookRels())
            entry(z, "xl/styles.xml", styles())
            entry(z, "xl/worksheets/sheet1.xml", sheet)
        }
    }

    private fun peopleSheet(summaries: List<CurrencyReportSummary>, people: List<CurrencyReportPersonRowWithCurrency>, start: Long?, end: Long?): String {
        val rows = mutableListOf<String>(); var n = 1
        rows += row(n++, listOf(cell("تقرير الأشخاص — جميع العملات", 1)))
        rows += row(n++, listOf(cell("الفترة: ${range(start, end)}", 2), cell("إصدار: ${date(System.currentTimeMillis())}", 2)))
        rows += row(n++, listOf(cell("المعلومات المالية لكل عملة مستقلة ولا يتم جمع العملات معًا.", 2)))
        val header = n
        val heads = mutableListOf(cell("الشخص", 3))
        currencies.forEach { c -> heads += listOf(cell(currencyName(c), 4), cell("", 4), cell("", 4), cell("", 4)) }
        rows += row(n++, heads)
        val sub = mutableListOf(cell("", 3)); currencies.forEach { sub += listOf(cell("عليه", 3), cell("له", 3), cell("الرصيد", 3), cell("عدد العمليات", 3)) }
        rows += row(n++, sub)
        val persons = people.map { it.personId to it.personName }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
        persons.forEach { (id, name) ->
            val cells = mutableListOf(cell(name, 0))
            currencies.forEach { c ->
                val p = people.firstOrNull { it.personId == id && it.currencyCode == c }
                cells += listOf(amountCell(p?.totalReceivableMinor ?: 0L, 5), amountCell(p?.totalPayableMinor ?: 0L, 6), cell(balanceText(p?.balanceMinor ?: 0L), balanceStyle(p?.balanceMinor ?: 0L)), integerCell((p?.transactionCount ?: 0).toLong()))
            }
            rows += row(n++, cells)
        }
        rows += totalPeopleRow(n++, people)
        return sheet(rows, "A${header}:M${n - 1}", listOf(28) + List(12) { 16 })
    }

    private fun detailedSheet(summaries: List<CurrencyReportSummary>, transactions: List<GeneralReportTransactionRow>, start: Long?, end: Long?): String {
        val rows = mutableListOf<String>(); var n = 1
        rows += row(n++, listOf(cell("التقرير العام — جميع العملات", 1)))
        rows += row(n++, listOf(cell("الفترة: ${range(start, end)}", 2), cell("إصدار: ${date(System.currentTimeMillis())}", 2)))
        val header = n
        val top = mutableListOf(cell("التاريخ", 3), cell("الشخص", 3), cell("البيان", 3))
        currencies.forEach { c -> top += listOf(cell(currencyName(c), 4), cell("", 4), cell("", 4)) }
        rows += row(n++, top)
        val sub = mutableListOf(cell("", 3), cell("", 3), cell("", 3)); currencies.forEach { sub += listOf(cell("عليه", 3), cell("له", 3), cell("الرصيد", 3)) }
        rows += row(n++, sub)
        val balances = mutableMapOf<Pair<Long, String>, Long>()
        transactions.forEach { t ->
            val key = t.personName.hashCode().toLong() to t.currencyCode
            val old = balances[key] ?: 0L
            val next = if (t.type == "RECEIVABLE") old + t.amountMinor else old - t.amountMinor
            balances[key] = next
            val cells = mutableListOf(cell(date(t.transactionDate)), cell(t.personName), cell(t.description.ifBlank { "—" }))
            currencies.forEach { c ->
                if (c == t.currencyCode) {
                    cells += if (t.type == "RECEIVABLE") amountCell(t.amountMinor, 5) else cell("—")
                    cells += if (t.type == "PAYABLE") amountCell(t.amountMinor, 6) else cell("—")
                    cells += cell(balanceText(next), balanceStyle(next))
                } else cells += listOf(cell("—"), cell("—"), cell("—"))
            }
            rows += row(n++, cells)
        }
        rows += totalDetailedRow(n++, summaries)
        return sheet(rows, "A${header}:L${n - 1}", listOf(14, 24, 34) + List(9) { 15 })
    }

    private fun summarySheet(rowsData: List<PersonCurrencySummaryRow>, start: Long?, end: Long?): String {
        val rows = mutableListOf<String>(); var n = 1
        rows += row(n++, listOf(cell("تقرير أرصدة الحسابات — جميع العملات", 1)))
        rows += row(n++, listOf(cell("الفترة: ${range(start, end)}", 2), cell("إصدار: ${date(System.currentTimeMillis())}", 2)))
        val header = n
        val top = mutableListOf(cell("الشخص", 3)); currencies.forEach { top += listOf(cell(currencyName(it), 4), cell("", 4), cell("", 4), cell("", 4)) }
        rows += row(n++, top)
        val sub = mutableListOf(cell("", 3)); currencies.forEach { sub += listOf(cell("إجمالي عليه", 3), cell("إجمالي له", 3), cell("الرصيد", 3), cell("عدد العمليات", 3)) }
        rows += row(n++, sub)
        val persons = rowsData.map { it.personId to it.personName }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
        persons.forEach { (id, name) ->
            val cells = mutableListOf(cell(name)); currencies.forEach { c ->
                val r = rowsData.firstOrNull { it.personId == id && it.currencyCode == c }
                cells += listOf(amountCell(r?.totalReceivableMinor ?: 0L, 5), amountCell(r?.totalPayableMinor ?: 0L, 6), cell(balanceText(r?.balanceMinor ?: 0L), balanceStyle(r?.balanceMinor ?: 0L)), integerCell((r?.transactionCount ?: 0).toLong()))
            }; rows += row(n++, cells)
        }
        rows += totalSummaryRow(n++, rowsData)
        return sheet(rows, "A${header}:M${n - 1}", listOf(28) + List(12) { 16 })
    }

    private fun personSheet(report: MultiCurrencyPersonReport, start: Long?, end: Long?): String {
        val rows = mutableListOf<String>(); var n = 1
        rows += row(n++, listOf(cell("تقرير حساب شخصي — جميع العملات", 1)))
        rows += row(n++, listOf(cell("الاسم: ${report.personName}", 2), cell("الهاتف: ${report.phone.ifBlank { "غير مسجل" }}", 2), cell("العنوان: ${report.address.ifBlank { "غير مسجل" }}", 2)))
        rows += row(n++, listOf(cell("الفترة: ${range(start, end)}", 2), cell("إصدار: ${date(System.currentTimeMillis())}", 2)))
        val header = n
        val top = mutableListOf(cell("التاريخ", 3), cell("البيان", 3)); currencies.forEach { top += listOf(cell(currencyName(it), 4), cell("", 4), cell("", 4)) }
        rows += row(n++, top)
        val sub = mutableListOf(cell("", 3), cell("", 3)); currencies.forEach { sub += listOf(cell("عليه", 3), cell("له", 3), cell("الرصيد", 3)) }
        rows += row(n++, sub)
        val byCurrency = report.reports.associateBy { it.currencyCode }
        val dates = report.reports.flatMap { it.transactions }.sortedWith(compareBy<PersonReportTransaction> { it.transactionDate }.thenBy { it.transactionId })
        val balances = currencies.associateWith { 0L }.toMutableMap()
        dates.forEach { tx ->
            val currency = report.reports.firstOrNull { it.transactions.any { t -> t.transactionId == tx.transactionId } }?.currencyCode ?: return@forEach
            balances[currency] = tx.balanceMinor
            val cells = mutableListOf(cell(date(tx.transactionDate)), cell(tx.description.ifBlank { "—" }))
            currencies.forEach { c ->
                if (c == currency) cells += listOf(if (tx.type == "RECEIVABLE") amountCell(tx.amountMinor, 5) else cell("—"), if (tx.type == "PAYABLE") amountCell(tx.amountMinor, 6) else cell("—"), cell(balanceText(tx.balanceMinor), balanceStyle(tx.balanceMinor)))
                else cells += listOf(cell("—"), cell("—"), cell("—"))
            }
            rows += row(n++, cells)
        }
        rows += personTotalRow(n++, report)
        return sheet(rows, "A${header}:K${n - 1}", listOf(14, 34) + List(9) { 16 })
    }

    private fun totalPeopleRow(n: Int, data: List<CurrencyReportPersonRowWithCurrency>): String {
        val cells = mutableListOf(cell("المجموع", 3)); currencies.forEach { c ->
            val x = data.filter { it.currencyCode == c }
            val a = x.sumOf { it.totalReceivableMinor }; val l = x.sumOf { it.totalPayableMinor }; val b = a - l
            cells += listOf(amountCell(a, 5), amountCell(l, 6), cell(balanceText(b), balanceStyle(b)), integerCell(x.sumOf { it.transactionCount }.toLong()))
        }; return row(n, cells)
    }

    private fun totalDetailedRow(n: Int, summaries: List<CurrencyReportSummary>): String {
        val cells = mutableListOf(cell("المجموع", 3)); currencies.forEach { c -> val s = summaries.firstOrNull { it.currencyCode == c }; val b = s?.balanceMinor ?: 0L; cells += listOf(amountCell(s?.totalReceivableMinor ?: 0L, 5), amountCell(s?.totalPayableMinor ?: 0L, 6), cell(balanceText(b), balanceStyle(b))) }; return row(n, cells)
    }

    private fun totalSummaryRow(n: Int, data: List<PersonCurrencySummaryRow>): String {
        val cells = mutableListOf(cell("المجموع", 3)); currencies.forEach { c -> val x = data.filter { it.currencyCode == c }; val a=x.sumOf{it.totalReceivableMinor}; val l=x.sumOf{it.totalPayableMinor}; val b=a-l; cells += listOf(amountCell(a,5), amountCell(l,6), cell(balanceText(b), balanceStyle(b)), integerCell(x.sumOf{it.transactionCount}.toLong())) }; return row(n,cells)
    }

    private fun personTotalRow(n: Int, report: MultiCurrencyPersonReport): String {
        val cells = mutableListOf(cell("المجموع", 3), cell("", 3)); currencies.forEach { c -> val s=report.reports.firstOrNull{it.currencyCode==c}?.summary; val a=s?.periodReceivableMinor?:0L; val l=s?.periodPayableMinor?:0L; val b=a-l; cells += listOf(amountCell(a,5),amountCell(l,6),cell(balanceText(b),balanceStyle(b))) }; return row(n,cells)
    }

    private fun sheet(rows: List<String>, filter: String, widths: List<Int>): String {
        val cols = widths.mapIndexed { i, w -> "<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>" }.joinToString("")
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\" rightToLeft=\"1\"/></sheetViews><sheetFormatPr defaultRowHeight=\"21\"/><cols>$cols</cols><sheetData>${rows.joinToString("")}</sheetData><mergeCells count=\"0\"></mergeCells><autoFilter ref=\"$filter\"/></worksheet>"
    }

    private fun row(n:Int,cells:List<String>)="<row r=\"$n\">${cells.joinToString("")}</row>"
    private fun cell(v:String,style:Int=0)="<c t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">${escape(v)}</t></is></c>"
    private fun amountCell(v:Long,style:Int)="<c t=\"n\" s=\"$style\"><v>${BigDecimal(v).movePointLeft(2).toPlainString()}</v></c>"
    private fun integerCell(v:Long)="<c t=\"n\" s=\"2\"><v>$v</v></c>"
    private fun balanceStyle(v:Long)=when{v>0L->5;v<0L->6;else->7}
    private fun balanceText(v:Long)=when{v>0L->"عليه ${format(v)}";v<0L->"له ${format(-v)}";else->"متعادل 0"}
    private fun format(v:Long)=BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
    private fun currencyName(c:String)=when(c){"YER"->"الريال اليمني";"SAR"->"الريال السعودي";"USD"->"الدولار الأمريكي";else->c}
    private fun date(v:Long)=SimpleDateFormat("dd/MM/yyyy",Locale("ar")).format(Date(v))
    private fun range(s:Long?,e:Long?)=if(s==null&&e==null)"كل الحساب" else "${s?.let(::date)?:"غير محدد"} - ${e?.let{date(it-1)}?:"غير محدد"}"
    private fun safe(v:String)=v.replace(Regex("[^\u0600-\u06FFA-Za-z0-9_-]+"),"_").take(50)
    private fun escape(v:String)=v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;").filter{it=='\n'||it=='\r'||it=='\t'||it>=' '}
    private fun entry(z:ZipOutputStream,p:String,v:String){z.putNextEntry(ZipEntry(p));z.write(v.toByteArray(Charsets.UTF_8));z.closeEntry()}
    private fun contentTypes()="<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>"
    private fun rootRels()="<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"
    private fun workbook()="<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"التقرير\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"
    private fun workbookRels()="<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>"
    private fun styles()="<?xml version=\"1.0\" encoding=\"UTF-8\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"5\"><font><sz val=\"11\"/><name val=\"Arial\"/></font><font><b/><sz val=\"15\"/><name val=\"Arial\"/></font><font><sz val=\"11\"/><name val=\"Arial\"/></font><font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"11\"/><name val=\"Arial\"/></font><font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"12\"/><name val=\"Arial\"/></font></fonts><fills count=\"4\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1565C0\"/><bgColor indexed=\"64\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFF2F2F2\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style=\"thin\"/><right style=\"thin\"/><top style=\"thin\"/><bottom style=\"thin\"/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"8\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"1\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"3\" fillId=\"2\" borderId=\"1\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"4\" fillId=\"2\" borderId=\"1\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"4\" fillId=\"2\" borderId=\"1\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"4\" fillId=\"2\" borderId=\"1\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"2\" fillId=\"3\" borderId=\"1\" xfId=\"0\"/></cellXfs></styleSheet>"
}
