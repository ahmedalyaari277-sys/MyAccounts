package com.myaccounts.app.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.myaccounts.app.data.reports.CurrencyReportPersonRow
import com.myaccounts.app.data.reports.CurrencyReportSummary
import com.myaccounts.app.data.reports.GeneralReportTransactionRow
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

object GeneralReportsExcelExporter {

    fun exportPeopleReport(context: Context, currency: String, summary: CurrencyReportSummary, people: List<CurrencyReportPersonRow>, start: Long?, end: Long?): Result<String> =
        export(context, "تقرير الأشخاص", currency, worksheetPeople(summary, people, start, end))

    fun exportDetailedReport(context: Context, currency: String, transactions: List<GeneralReportTransactionRow>, start: Long?, end: Long?): Result<String> =
        export(context, "التقرير التفصيلي", currency, worksheetDetailed(currency, transactions, start, end))

    fun exportSummaryReport(context: Context, currency: String, rows: List<PersonCurrencySummaryRow>, start: Long?, end: Long?): Result<String> =
        export(context, "ملخص تقرير الأشخاص", currency, worksheetSummary(currency, rows, start, end))

    private fun export(context: Context, title: String, currency: String, worksheet: String): Result<String> {
        return try {
            val fileName = "MyAccounts_${safeFileName(title)}_${currency}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.xlsx"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, XLSX_MIME_TYPE)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MyAccounts")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("تعذر إنشاء ملف Excel.")
                try { resolver.openOutputStream(uri).use { output -> if (output == null) throw IllegalStateException("تعذر فتح ملف Excel."); writeWorkbook(output, worksheet) } }
                catch (e: Exception) { resolver.delete(uri, null, null); throw e }
                Result.success("تم حفظ التقرير في مجلد التنزيلات/MyAccounts")
            } else {
                val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MyAccounts")
                if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("تعذر إنشاء مجلد التقرير.")
                val file = File(directory, fileName)
                FileOutputStream(file).use { writeWorkbook(it, worksheet) }
                Result.success(file.absolutePath)
            }
        } catch (exception: Exception) { Result.failure(exception) }
    }

    private fun writeWorkbook(output: OutputStream, worksheet: String) {
        ZipOutputStream(output).use { zip ->
            entry(zip, "[Content_Types].xml", contentTypes())
            entry(zip, "_rels/.rels", rootRelationships())
            entry(zip, "xl/workbook.xml", workbook())
            entry(zip, "xl/_rels/workbook.xml.rels", workbookRelationships())
            entry(zip, "xl/styles.xml", styles())
            entry(zip, "xl/worksheets/sheet1.xml", worksheet)
        }
    }

    private fun entry(zip: ZipOutputStream, path: String, value: String) {
        zip.putNextEntry(ZipEntry(path)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry()
    }

    private fun contentTypes() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
          <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
        </Types>
    """.trimIndent()

    private fun rootRelationships() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()

    private fun workbook() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets><sheet name="التقرير" sheetId="1" r:id="rId1"/></sheets>
        </workbook>
    """.trimIndent()

    private fun workbookRelationships() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
    """.trimIndent()

    private fun styles() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <fonts count="4"><font><sz val="11"/><name val="Arial"/></font><font><b/><sz val="11"/><name val="Arial"/></font><font><color rgb="FFC02323"/><sz val="11"/><name val="Arial"/></font><font><color rgb="FF007D46"/><sz val="11"/><name val="Arial"/></font></fonts>
          <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
          <borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"/><right style="thin"/><top style="thin"/><bottom style="thin"/><diagonal/></border></borders>
          <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
          <cellXfs count="7"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="1" xfId="0"/><xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0"/><xf numFmtId="0" fontId="3" fillId="0" borderId="1" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="1" xfId="0"/></cellXfs>
        </styleSheet>
    """.trimIndent()

    private fun worksheetPeople(summary: CurrencyReportSummary, people: List<CurrencyReportPersonRow>, start: Long?, end: Long?): String {
        val rows = mutableListOf<String>(); var n = 1
        rows += row(n++, listOf(cell("تقرير الأشخاص", 3)))
        rows += row(n++, listOf(cell("العملة", 3), cell(currencyName(summary.currencyCode), 2)))
        rows += row(n++, listOf(cell("الفترة", 3), cell(formatDateRange(start, end), 2)))
        rows += row(n++, listOf(cell("تاريخ إصدار التقرير", 3), cell(formatDate(System.currentTimeMillis()), 2)))
        rows += row(n++, listOf(cell("الشخص", 3), cell("العملة", 3), cell("عليه", 3), cell("له", 3), cell("الرصيد", 3)))
        val header = n - 1
        people.forEach { p -> rows += row(n++, listOf(cell(p.personName, 2), cell(currencyName(summary.currencyCode), 2), numeric(p.totalReceivableMinor, 4), numeric(p.totalPayableMinor, 5), balanceNumeric(p.balanceMinor))) }
        if (people.isEmpty()) rows += row(n++, listOf(cell("لا توجد بيانات.", 2)))
        return worksheet(rows, "A$header:E${n - 1}", "32,18,18,18,18")
    }

    private fun worksheetDetailed(currency: String, transactions: List<GeneralReportTransactionRow>, start: Long?, end: Long?): String {
        val rows = mutableListOf<String>(); var n = 1
        rows += row(n++, listOf(cell("التقرير التفصيلي للعمليات", 3)))
        rows += row(n++, listOf(cell("العملة", 3), cell(currencyName(currency), 2)))
        rows += row(n++, listOf(cell("الفترة", 3), cell(formatDateRange(start, end), 2)))
        rows += row(n++, listOf(cell("تاريخ إصدار التقرير", 3), cell(formatDate(System.currentTimeMillis()), 2)))
        rows += row(n++, listOf(cell("التاريخ", 3), cell("الشخص", 3), cell("العملة", 3), cell("البيان", 3), cell("عليه", 3), cell("له", 3)))
        val header = n - 1
        transactions.forEach { t -> rows += row(n++, listOf(cell(formatDate(t.transactionDate), 2), cell(t.personName, 2), cell(currencyName(t.currencyCode), 2), cell(t.description.ifBlank { "—" }, 2), if (t.type == "RECEIVABLE") numeric(t.amountMinor, 4) else cell("—", 2), if (t.type == "PAYABLE") numeric(t.amountMinor, 5) else cell("—", 2))) }
        if (transactions.isEmpty()) rows += row(n++, listOf(cell("لا توجد عمليات.", 2)))
        return worksheet(rows, "A$header:F${n - 1}", "16,28,18,42,18,18")
    }

    private fun worksheetSummary(currency: String, rowsData: List<PersonCurrencySummaryRow>, start: Long?, end: Long?): String {
        val rows = mutableListOf<String>(); var n = 1
        rows += row(n++, listOf(cell("ملخص تقرير الأشخاص", 3)))
        rows += row(n++, listOf(cell("العملة", 3), cell(currencyName(currency), 2)))
        rows += row(n++, listOf(cell("الفترة", 3), cell(formatDateRange(start, end), 2)))
        rows += row(n++, listOf(cell("تاريخ إصدار التقرير", 3), cell(formatDate(System.currentTimeMillis()), 2)))
        rows += row(n++, listOf(cell("الشخص", 3), cell("العملة", 3), cell("عليه", 3), cell("له", 3), cell("الرصيد", 3), cell("الفترة الأولى: له ← عليه", 3), cell("الفترة الأخيرة: له ← عليه", 3)))
        val header = n - 1
        rowsData.forEach { r -> rows += row(n++, listOf(cell(r.personName, 2), cell(currencyName(r.currencyCode), 2), numeric(r.totalReceivableMinor, 4), numeric(r.totalPayableMinor, 5), balanceNumeric(r.balanceMinor), cell(firstToFirstRange(r.firstPayableDate, r.firstReceivableDate), 2), cell(lastToLastRange(r.lastPayableDate, r.lastReceivableDate), 2))) }
        if (rowsData.isEmpty()) rows += row(n++, listOf(cell("لا توجد بيانات.", 2)))
        return worksheet(rows, "A$header:G${n - 1}", "28,18,18,18,18,32,32")
    }

    private fun worksheet(rows: List<String>, filter: String, widths: String): String {
        val widthParts = widths.split(',')
        val cols = widthParts.mapIndexed { i, width -> "<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$width\" customWidth=\"1\"/>" }.joinToString("")
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetViews><sheetView workbookViewId="0" rightToLeft="1"/></sheetViews>
              <sheetFormatPr defaultRowHeight="18"/>
              <cols>$cols</cols>
              <sheetData>${rows.joinToString("\n")}</sheetData>
              <autoFilter ref="$filter"/>
            </worksheet>
        """.trimIndent()
    }

    private fun row(n: Int, cells: List<String>) = "<row r=\"$n\">${cells.joinToString("\n")}</row>"
    private fun cell(value: String, style: Int = 0) = "<c t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">${escape(value)}</t></is></c>"
    private fun numeric(value: Long, style: Int = 0) = "<c t=\"n\" s=\"$style\"><v>${BigDecimal(value).movePointLeft(2).stripTrailingZeros().toPlainString()}</v></c>"
    private fun balanceNumeric(value: Long) = numeric(value, when { value > 0L -> 4; value < 0L -> 5; else -> 3 })
    private fun currencyName(code: String) = when (code) { "YER" -> "الريال اليمني"; "SAR" -> "الريال السعودي"; "USD" -> "الدولار الأمريكي"; else -> code }
    private fun formatDate(millis: Long) = SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date(millis))
    private fun formatDateRange(start: Long?, end: Long?) = if (start == null && end == null) "كل الحساب" else "${start?.let(::formatDate) ?: "غير محدد"} - ${end?.let { formatDate(it - 1) } ?: "غير محدد"}"
    private fun firstToFirstRange(firstPayable: Long?, firstReceivable: Long?) = if (firstPayable == null && firstReceivable == null) "—" else "${firstPayable?.let(::formatDate) ?: "—"} - ${firstReceivable?.let(::formatDate) ?: "—"}"
    private fun lastToLastRange(lastPayable: Long?, lastReceivable: Long?) = if (lastPayable == null && lastReceivable == null) "—" else "${lastPayable?.let(::formatDate) ?: "—"} - ${lastReceivable?.let(::formatDate) ?: "—"}"
    private fun safeFileName(value: String) = value.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_').take(80)
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;").filter { it == '\n' || it == '\r' || it == '\t' || it >= ' ' }
    private const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
}
