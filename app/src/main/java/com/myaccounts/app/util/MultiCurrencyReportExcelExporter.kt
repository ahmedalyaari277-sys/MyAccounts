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
import com.myaccounts.app.data.reports.PersonCurrencySummaryRow
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object MultiCurrencyReportExcelExporter {
    private const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private val currencies = listOf("YER", "SAR", "USD")

    fun exportPeopleReport(context: Context, summaries: List<CurrencyReportSummary>, people: List<CurrencyReportPersonRowWithCurrency>, start: Long?, end: Long?): Result<String> = save(context, "تقرير الأشخاص", peopleSheet(people, start, end))
    fun exportDetailedReport(context: Context, summaries: List<CurrencyReportSummary>, transactions: List<GeneralReportTransactionRow>, start: Long?, end: Long?): Result<String> = save(context, "التقرير العام", detailedSheet(summaries, transactions, start, end))
    fun exportSummaryReport(context: Context, rows: List<PersonCurrencySummaryRow>, start: Long?, end: Long?): Result<String> = save(context, "أرصدة الحسابات", summarySheet(rows, start, end))
    fun exportPersonReport(context: Context, report: MultiCurrencyPersonReport, start: Long?, end: Long?): Result<String> = save(context, "تقرير حساب ${report.personName}", personSheet(report, start, end))

    private fun save(context: Context, title: String, sheet: String): Result<String> = try {
        val fileName = "MyAccounts_${safe(title)}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.xlsx"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, MIME)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MyAccounts")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("تعذر إنشاء ملف Excel.")
            try {
                resolver.openOutputStream(uri).use { out ->
                    if (out == null) error("تعذر فتح ملف Excel.")
                    writeWorkbook(out, sheet)
                }
            } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
            Result.success("تم حفظ تقرير Excel في مجلد التنزيلات/MyAccounts")
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MyAccounts")
            if (!dir.exists() && !dir.mkdirs()) error("تعذر إنشاء مجلد التقرير.")
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

    private fun peopleSheet(people: List<CurrencyReportPersonRowWithCurrency>, start: Long?, end: Long?): String {
        val rows = mutableListOf<String>(); var n = 1
        rows += row(n++, listOf(cell("تقرير الأشخاص — جميع العملات", 1)))
        rows += row(n++, listOf(cell("الفترة: ${range(start, end)}", 2), cell("إصدار: ${date(System.currentTimeMillis())}", 2)))
        rows += row(n++, listOf(cell("كل عملة مستقلة ولا يتم جمع العملات معًا.", 2)))
        val header = n
        rows += row(n++, listOf(cell("الشخص", 4), cell("الريال اليمني", 3), cell("", 3), cell("", 3), cell("", 3), cell("الريال السعودي", 3), cell("", 3), cell("", 3), cell("", 3), cell("الدولار الأمريكي", 3), cell("", 3), cell("", 3), cell("", 3)))
        val sub = mutableListOf(cell("", 4)); currencies.forEach { sub += listOf(cell("عليه", 4), cell("له", 4), cell("الرصيد", 4), cell("عدد العمليات", 4)) }
        rows += row(n++, sub)
        val persons = people.map { it.personId to it.personName }.distinctBy { it.first }.sortedBy { it.second }
        persons.forEach { (id, name) ->
            val cells = mutableListOf(cell(name))
            currencies.forEach { c ->
                val p = people.firstOrNull { it.personId == id && it.currencyCode == c }
                cells += listOf(amountCell(p?.totalReceivableMinor ?: 0L, 5), amountCell(p?.totalPayableMinor ?: 0L, 6), cell(balanceText(p?.balanceMinor ?: 0L), balanceStyle(p?.balanceMinor ?: 0L)), integerCell((p?.transactionCount ?: 0).toLong()))
            }
            rows += row(n++, cells)
        }
        rows += totalPeopleRow(n++, people)
        return sheet(rows, "A${header}:M${n - 1}", listOf(28) + List(12) { 16 }, listOf("A1:M1", "A2:F2", "G2:M2", "A3:M3", "A4:A5", "B4:E4", "F4:I4", "J4:M4"))
    }

    private fun detailedSheet(summaries: List<CurrencyReportSummary>, transactions: List<GeneralReportTransactionRow>, start: Long?, end: Long?): String {
        val rows = mutableListOf<String>(); var n = 1
        rows += row(n++, listOf(cell("التقرير العام — جميع العملات", 1)))
        rows += row(n++, listOf(cell("الفترة: ${range(start, end)}", 2), cell("إصدار: ${date(System.currentTimeMillis())}", 2)))
        val header = n
        rows += row(n++, listOf(cell("التاريخ", 4), cell("الشخص", 4), cell("البيان", 4), cell("الريال اليمني", 3), cell("", 3), cell("", 3), cell("الريال السعودي", 3), cell("", 3), cell("", 3), cell("الدولار الأمريكي", 3), cell("", 3), cell("", 3)))
        val sub = mutableListOf(cell("", 4), cell("", 4), cell("", 4)); currencies.forEach { sub += listOf(cell("عليه", 4), cell("له", 4), cell("الرصيد", 4)) }
        rows += row(n++, sub)
        val balances = mutableMapOf<Pair<String, String>, Long>()
        transactions.sortedWith(compareBy<GeneralReportTransactionRow> { it.transactionDate }.thenBy { it.transactionId }).forEach { t ->
            val key = t.personName to t.currencyCode
            val old = balances[key] ?: 0L
            val next = if (t.type == "RECEIVABLE") old + t.amountMinor else old - t.amountMinor
            balances[key] = next
            val cells = mutableListOf(cell(date(t.transactionDate)), cell(t.personName), cell(t.description.ifBlank { "—" }))
            currencies.forEach { c ->
                if (c == t.currencyCode) cells += listOf(if (t.type == "RECEIVABLE") amountCell(t.amountMinor, 5) else cell("—"), if (t.type == "PAYABLE") amountCell(t.amountMinor, 6) else cell("—"), cell(balanceText(next), balanceStyle(next)))
                else cells += listOf(cell("—"), cell("—"), cell("—"))
            }
            rows += row(n++, cells)
        }
        rows += totalDetailedRow(n++, summaries)
        return sheet(rows, "A${header}:L${n - 1}", listOf(14, 24, 34) + List(9) { 15 }, listOf("A1:L1", "A2:F2", "G2:L2", "A4:A5", "B4:B5", "C4:C5", "D4:F4", "G4:I4", "J4:L4"))
    }

    private fun summarySheet(data: List<PersonCurrencySummaryRow>, start: Long?, end: Long?): String {
        val rows = mutableListOf<String>(); var n = 1
        rows += row(n++, listOf(cell("تقرير أرصدة الحسابات — جميع العملات", 1)))
        rows += row(n++, listOf(cell("الفترة: ${range(start, end)}", 2), cell("إصدار: ${date(System.currentTimeMillis())}", 2)))
        val header = n
        rows += row(n++, listOf(cell("الشخص", 4), cell("الريال اليمني", 3), cell("", 3), cell("", 3), cell("", 3), cell("الريال السعودي", 3), cell("", 3), cell("", 3), cell("", 3), cell("الدولار الأمريكي", 3), cell("", 3), cell("", 3), cell("", 3)))
        val sub = mutableListOf(cell("", 4)); currencies.forEach { sub += listOf(cell("إجمالي عليه", 4), cell("إجمالي له", 4), cell("الرصيد", 4), cell("عدد العمليات", 4)) }
        rows += row(n++, sub)
        val persons = data.map { it.personId to it.personName }.distinctBy { it.first }.sortedBy { it.second }
        persons.forEach { (id, name) ->
            val cells = mutableListOf(cell(name)); currencies.forEach { c ->
                val r = data.firstOrNull { it.personId == id && it.currencyCode == c }
                cells += listOf(amountCell(r?.totalReceivableMinor ?: 0L, 5), amountCell(r?.totalPayableMinor ?: 0L, 6), cell(balanceText(r?.balanceMinor ?: 0L), balanceStyle(r?.balanceMinor ?: 0L)), integerCell((r?.transactionCount ?: 0).toLong()))
            }; rows += row(n++, cells)
        }
        rows += totalSummaryRow(n++, data)
        return sheet(rows, "A${header}:M${n - 1}", listOf(28) + List(12) { 16 }, listOf("A1:M1", "A2:F2", "G2:M2", "A4:A5", "B4:E4", "F4:I4", "J4:M4"))
    }

    private fun personSheet(report: MultiCurrencyPersonReport, start: Long?, end: Long?): String {
        val rows = mutableListOf<String>(); var n = 1
        rows += row(n++, listOf(cell("تقرير حساب شخصي — جميع العملات", 1)))
        rows += row(n++, listOf(cell("الاسم: ${report.personName}", 2), cell("الهاتف: ${report.phone.ifBlank { "غير مسجل" }}", 2), cell("العنوان: ${report.address.ifBlank { "غير مسجل" }}", 2)))
        rows += row(n++, listOf(cell("الفترة: ${range(start, end)}", 2), cell("إصدار: ${date(System.currentTimeMillis())}", 2)))
        val header = n
        rows += row(n++, listOf(cell("التاريخ", 4), cell("البيان", 4), cell("الريال اليمني", 3), cell("", 3), cell("", 3), cell("الريال السعودي", 3), cell("", 3), cell("", 3), cell("الدولار الأمريكي", 3), cell("", 3), cell("", 3)))
        val sub = mutableListOf(cell("", 4), cell("", 4)); currencies.forEach { sub += listOf(cell("عليه", 4), cell("له", 4), cell("الرصيد", 4)) }
        rows += row(n++, sub)
        val currencyByTransaction = report.reports.flatMap { r -> r.transactions.map { it.transactionId to r.currencyCode } }.toMap()
        report.reports.flatMap { it.transactions }.sortedWith(compareBy({ it.transactionDate }, { it.transactionId })).forEach { tx ->
            val c0 = currencyByTransaction[tx.transactionId] ?: return@forEach
            val cells = mutableListOf(cell(date(tx.transactionDate)), cell(tx.description.ifBlank { "—" }))
            currencies.forEach { c ->
                if (c == c0) cells += listOf(if (tx.type == "RECEIVABLE") amountCell(tx.amountMinor, 5) else cell("—"), if (tx.type == "PAYABLE") amountCell(tx.amountMinor, 6) else cell("—"), cell(balanceText(tx.balanceMinor), balanceStyle(tx.balanceMinor)))
                else cells += listOf(cell("—"), cell("—"), cell("—"))
            }
            rows += row(n++, cells)
        }
        rows += personTotalRow(n++, report)
        return sheet(rows, "A${header}:K${n - 1}", listOf(14, 34) + List(9) { 16 }, listOf("A1:K1", "A2:C2", "D2:G2", "H2:K2", "A4:A5", "B4:B5", "C4:E4", "F4:H4", "I4:K4"))
    }

    private fun totalPeopleRow(n: Int, data: List<CurrencyReportPersonRowWithCurrency>): String {
        val cells = mutableListOf(cell("المجموع", 9)); currencies.forEach { c -> val x = data.filter { it.currencyCode == c }; val a=x.sumOf{it.totalReceivableMinor}; val l=x.sumOf{it.totalPayableMinor}; val b=a-l; cells += listOf(amountCell(a,5),amountCell(l,6),cell(balanceText(b),balanceStyle(b)),integerCell(x.sumOf{it.transactionCount}.toLong())) }; return row(n,cells)
    }
    private fun totalDetailedRow(n: Int, summaries: List<CurrencyReportSummary>): String {
        val cells=mutableListOf(cell("المجموع",9)); currencies.forEach{ c-> val s=summaries.firstOrNull{it.currencyCode==c}; val a=s?.totalReceivableMinor?:0L; val l=s?.totalPayableMinor?:0L; val b=a-l; cells += listOf(amountCell(a,5),amountCell(l,6),cell(balanceText(b),balanceStyle(b))) }; return row(n,cells)
    }
    private fun totalSummaryRow(n: Int, data: List<PersonCurrencySummaryRow>): String {
        val cells=mutableListOf(cell("المجموع",9)); currencies.forEach{ c-> val x=data.filter{it.currencyCode==c}; val a=x.sumOf{it.totalReceivableMinor}; val l=x.sumOf{it.totalPayableMinor}; val b=a-l; cells += listOf(amountCell(a,5),amountCell(l,6),cell(balanceText(b),balanceStyle(b)),integerCell(x.sumOf{it.transactionCount}.toLong())) }; return row(n,cells)
    }
    private fun personTotalRow(n: Int, report: MultiCurrencyPersonReport): String {
        val cells=mutableListOf(cell("المجموع",9),cell("",9)); currencies.forEach{ c-> val s=report.reports.firstOrNull{it.currencyCode==c}?.summary; val a=s?.periodReceivableMinor?:0L; val l=s?.periodPayableMinor?:0L; val b=a-l; cells += listOf(amountCell(a,5),amountCell(l,6),cell(balanceText(b),balanceStyle(b))) }; return row(n,cells)
    }

    private fun sheet(rows: List<String>, filter: String, widths: List<Int>, merges: List<String>): String {
        val cols=widths.mapIndexed{ i,w->"<col min=\"${i+1}\" max=\"${i+1}\" width=\"$w\" customWidth=\"1\"/>"}.joinToString("")
        val mergeXml=merges.joinToString(""){ "<mergeCell ref=\"$it\"/>" }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\" rightToLeft=\"1\"/></sheetViews><sheetFormatPr defaultRowHeight=\"21\"/><cols>$cols</cols><sheetData>${rows.joinToString("")}</sheetData><mergeCells count=\"${merges.size}\">$mergeXml</mergeCells><autoFilter ref=\"$filter\"/></worksheet>"
    }

    private fun row(n:Int,cells:List<String>)="<row r=\"$n\">${cells.joinToString("")}</row>"
    private fun cell(value:String,style:Int=0)=if(value.isEmpty())"<c s=\"$style\" t=\"inlineStr\"><is><t></t></is></c>" else "<c s=\"$style\" t=\"inlineStr\"><is><t>${xml(value)}</t></is></c>"
    private fun amountCell(minor:Long,style:Int)=cell(formatAmount(minor),style)
    private fun integerCell(value:Long)=cell(value.toString())
    private fun balanceStyle(value:Long)=when{value>0->7;value<0->8;else->9}
    private fun balanceText(value:Long)=when{value>0->"عليه ${formatAmount(value)}";value<0->"له ${formatAmount(-value)}";else->"متوازن 0"}
    private fun formatAmount(v:Long)=String.format(Locale.US,"%,d",kotlin.math.abs(v)/100)
    private fun currencyName(c:String)=when(c){"YER"->"الريال اليمني";"SAR"->"الريال السعودي";else->"الدولار الأمريكي"}
    private fun date(v:Long)=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date(v))
    private fun range(start:Long?,end:Long?)=if(start==null||end==null)"كل الفترة" else "${date(start)} إلى ${date(end-1)}"
    private fun safe(v:String)=v.replace(Regex("[^A-Za-z0-9_-]"),"_")
    private fun xml(v:String)=v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
    private fun entry(z:ZipOutputStream,name:String,data:String){z.putNextEntry(ZipEntry(name));z.write(data.toByteArray(Charsets.UTF_8));z.closeEntry()}

    private fun contentTypes()="""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""
    private fun rootRels()="""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    private fun workbook()="""<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="التقرير" sheetId="1" r:id="rId1"/></sheets></workbook>"""
    private fun workbookRels()="""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
    private fun styles()="""<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="4"><font><sz val="11"/><name val="Arial"/></font><font><b/><sz val="16"/><name val="Arial"/></font><font><b/><sz val="11"/><name val="Arial"/><color rgb="FFFFFFFF"/></font><font><b/><sz val="11"/><name val="Arial"/></font></fonts><fills count="6"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF1F4E78"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE2F0D9"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFCE4D6"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFD9EAF7"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="2"><border><left/><right/><top/><bottom/></border><border><left style="thin"/><right style="thin"/><top style="thin"/><bottom style="thin"/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="10"><xf fontId="0" fillId="0" borderId="1"/><xf fontId="1" fillId="0" borderId="1"><alignment horizontal="right" vertical="center"/></xf><xf fontId="0" fillId="5" borderId="1"><alignment horizontal="right" vertical="center"/></xf><xf fontId="2" fillId="2" borderId="1"><alignment horizontal="center" vertical="center"/></xf><xf fontId="2" fillId="2" borderId="1"><alignment horizontal="center" vertical="center"/></xf><xf fontId="3" fillId="4" borderId="1"><alignment horizontal="center" vertical="center"/></xf><xf fontId="3" fillId="3" borderId="1"><alignment horizontal="center" vertical="center"/></xf><xf fontId="3" fillId="4" borderId="1"><alignment horizontal="center" vertical="center"/></xf><xf fontId="3" fillId="3" borderId="1"><alignment horizontal="center" vertical="center"/></xf><xf fontId="2" fillId="5" borderId="1"><alignment horizontal="center" vertical="center"/></xf></cellXfs></styleSheet>"""
}
