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
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.data.custody.CustodyTransactionEntity
import com.myaccounts.app.data.custody.CustodyTransactionType
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CustodyReportExporter {
    fun exportPdf(context: Context, custody: CustodyEntity, transactions: List<CustodyTransactionEntity>, currency: String): Result<String> = runCatching {
        val filtered = transactions.filter { currency == "ALL" || it.currencyCode == currency }.sortedBy { it.transactionDate }
        val doc = PdfDocument()
        val chunks = if (filtered.isEmpty()) listOf(emptyList()) else filtered.chunked(24)
        chunks.forEachIndexed { pageIndex, chunk ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create())
            val canvas = page.canvas
            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 9f; textAlign = Paint.Align.RIGHT }
            val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
            var y = 42f
            canvas.drawText("تقرير العهدة — ${custody.name}", 560f, y, title)
            y += 22f
            canvas.drawText("الجهة: ${custody.organizationName}", 560f, y, text)
            canvas.drawText("العملة: ${if (currency == "ALL") "كل العملات" else currencyName(currency)}", 300f, y, text)
            y += 28f
            canvas.drawText("التاريخ", 560f, y, bold); canvas.drawText("النوع", 455f, y, bold); canvas.drawText("المبلغ", 300f, y, bold); canvas.drawText("البيان", 125f, y, bold)
            y += 18f
            chunk.forEach { t ->
                canvas.drawText(date(t.transactionDate), 560f, y, text)
                canvas.drawText(typeName(t.type), 455f, y, text)
                canvas.drawText("${amount(t.amountMinor)} ${t.currencyCode}", 300f, y, text)
                canvas.drawText(t.description.ifBlank { "—" }.take(25), 125f, y, text)
                canvas.drawLine(35f, y + 6f, 560f, y + 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; strokeWidth = 1f })
                y += 28f
            }
            if (chunk.isEmpty()) canvas.drawText("لا توجد عمليات ضمن الاختيار.", 560f, y, text)
            canvas.drawText("صفحة ${pageIndex + 1} من ${chunks.size}", 560f, 810f, text)
            doc.finishPage(page)
        }
        savePdf(context, doc, "MyAccounts_تقرير_عهدة_${safe(custody.name)}_${stamp()}.pdf")
    }

    fun exportExcel(context: Context, custody: CustodyEntity, transactions: List<CustodyTransactionEntity>, currency: String): Result<String> = runCatching {
        val filtered = transactions.filter { currency == "ALL" || it.currencyCode == currency }.sortedBy { it.transactionDate }
        val rows = mutableListOf<String>()
        rows += row(1, listOf(cell("تقرير العهدة", 1)))
        rows += row(2, listOf(cell("صاحب العهدة: ${custody.name}", 2), cell("الجهة: ${custody.organizationName}", 2), cell("العملة: ${if (currency == "ALL") "كل العملات" else currencyName(currency)}", 2)))
        rows += row(3, listOf(cell("التاريخ", 2), cell("النوع", 2), cell("العملة", 2), cell("المبلغ", 2), cell("البيان", 2), cell("المعرف", 2)))
        filtered.forEachIndexed { index, t -> rows += row(index + 4, listOf(cell(date(t.transactionDate)), cell(typeName(t.type)), cell(t.currencyCode), number(t.amountMinor), cell(t.description), cell(t.externalId))) }
        if (filtered.isEmpty()) rows += row(4, listOf(cell("لا توجد عمليات ضمن الاختيار.")))
        val sheet = """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0" rightToLeft="1"/></sheetViews><cols><col min="1" max="1" width="18" customWidth="1"/><col min="2" max="2" width="28" customWidth="1"/><col min="3" max="3" width="12" customWidth="1"/><col min="4" max="4" width="18" customWidth="1"/><col min="5" max="5" width="42" customWidth="1"/><col min="6" max="6" width="38" customWidth="1"/></cols><sheetData>${rows.joinToString("")}</sheetData><autoFilter ref="A3:F${maxOf(3, filtered.size + 3)}"/></worksheet>"""
        saveXlsx(context, sheet, "MyAccounts_تقرير_عهدة_${safe(custody.name)}_${stamp()}.xlsx")
    }

    private fun savePdf(context: Context, doc: PdfDocument, name: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, "application/pdf"); put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MyAccounts"); put(MediaStore.Downloads.IS_PENDING, 1) }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("تعذر إنشاء PDF")
            try { resolver.openOutputStream(uri).use { out -> requireNotNull(out); doc.writeTo(out) }; resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) } catch (e: Throwable) { resolver.delete(uri, null, null); throw e }
            return "تم حفظ PDF في مجلد التنزيلات/MyAccounts"
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MyAccounts").apply { if (!exists()) mkdirs() }
        val file = File(dir, name); FileOutputStream(file).use { doc.writeTo(it) }; return file.absolutePath
    }

    private fun saveXlsx(context: Context, sheet: String, name: String): String {
        val writer: (OutputStream) -> Unit = { out -> ZipOutputStream(out).use { z ->
            entry(z, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            entry(z, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            entry(z, "xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="تقرير العهدة" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            entry(z, "xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            entry(z, "xl/worksheets/sheet1.xml", sheet)
        } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MyAccounts"); put(MediaStore.Downloads.IS_PENDING, 1) }
            val resolver = context.contentResolver; val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("تعذر إنشاء Excel")
            try { resolver.openOutputStream(uri).use { out -> requireNotNull(out); writer(out) }; resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) } catch (e: Throwable) { resolver.delete(uri, null, null); throw e }
            return "تم حفظ Excel في مجلد التنزيلات/MyAccounts"
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MyAccounts").apply { if (!exists()) mkdirs() }; val file = File(dir, name); FileOutputStream(file).use(writer); return file.absolutePath
    }

    private fun entry(z: ZipOutputStream, path: String, value: String) { z.putNextEntry(ZipEntry(path)); z.write(value.toByteArray(Charsets.UTF_8)); z.closeEntry() }
    private fun row(n: Int, cells: List<String>) = "<row r=\"$n\">${cells.joinToString("")}</row>"
    private fun cell(v: String, style: Int = 0) = "<c t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">${escape(v)}</t></is></c>"
    private fun number(v: Long) = "<c t=\"n\"><v>${BigDecimal(v).movePointLeft(2).toPlainString()}</v></c>"
    private fun escape(v: String) = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    private fun amount(v: Long) = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
    private fun date(v: Long) = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ar")).format(Date(v))
    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    private fun safe(v: String) = v.replace(Regex("[^\u0600-\u06FFA-Za-z0-9_-]+"), "_").take(60)
    private fun currencyName(v: String) = when (v) { "YER" -> "الريال اليمني"; "SAR" -> "الريال السعودي"; "USD" -> "الدولار الأمريكي"; else -> v }
    private fun typeName(v: String) = when (v) { CustodyTransactionType.RECEIVED_FROM_ORG -> "استلام من الجهة"; CustodyTransactionType.PAID_TO_PERSON -> "صرف للشخص"; CustodyTransactionType.RETURNED_FROM_PERSON -> "مرتجع من الشخص"; CustodyTransactionType.RETURNED_TO_ORG -> "مرتجع للجهة"; else -> v }
}
