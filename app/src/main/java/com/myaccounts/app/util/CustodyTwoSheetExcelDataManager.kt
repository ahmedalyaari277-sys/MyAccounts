package com.myaccounts.app.util

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.myaccounts.app.data.local.AppDatabase

/** Custody-only workbook: exactly two sheets, operations and category summary. */
object CustodyTwoSheetExcelDataManager {
    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val SUGGESTED_FILE_NAME = "MyAccounts_Custodies.xlsx"

    suspend fun exportActive(context: Context, uri: Uri): Result<CustodyExcelDataManager.ExportSummary> = runCatching {
        val temp = File.createTempFile("custody-excel-", ".xlsx", context.cacheDir)
        try {
            val summary = CustodyExcelDataManager.exportActive(context, Uri.fromFile(temp)).getOrThrow()
            val entries = readZip(temp.inputStream())
            val db = AppDatabase.getInstance(context)
            val dao = db.custodyDao()
            val transactions = dao.getAllCustodies(false).flatMap { dao.getAllTransactions(it.id, false) }
            val baseSheet = entries["xl/worksheets/sheet1.xml"] ?: error("ورقة عمليات العُهَد مفقودة.")
            val categorizedSheet = addCategoryColumn(baseSheet, transactions.map { it.categoryName.trim() })
            val summarySheet = buildSummarySheet(context)
            context.contentResolver.openOutputStream(uri)?.use { out -> writeWorkbook(out, categorizedSheet, summarySheet) } ?: error("تعذر فتح ملف Excel للكتابة.")
            summary
        } finally { temp.delete() }
    }

    fun previewImport(context: Context, uri: Uri): Result<CustodyExcelDataManager.ImportPreview> = runCatching {
        val entries = readZip(context.contentResolver.openInputStream(uri) ?: error("تعذر فتح ملف Excel."))
        require(sheetCount(entries["xl/workbook.xml"] ?: error("ملف Excel غير صالح.")) == 2) { "يجب أن يحتوي ملف العُهَد على Sheetين فقط." }
        val sheet = entries["xl/worksheets/sheet1.xml"] ?: error("ورقة عمليات العُهَد مفقودة.")
        val temp = singleSheetFile(context, sheet)
        try { CustodyExcelDataManager.previewImport(context, Uri.fromFile(temp)).getOrThrow() } finally { temp.delete() }
    }

    suspend fun import(context: Context, uri: Uri): Result<CustodyExcelDataManager.ImportSummary> = runCatching {
        val entries = readZip(context.contentResolver.openInputStream(uri) ?: error("تعذر فتح ملف Excel."))
        require(sheetCount(entries["xl/workbook.xml"] ?: error("ملف Excel غير صالح.")) == 2) { "يجب أن يحتوي ملف العُهَد على Sheetين فقط." }
        val sheet = entries["xl/worksheets/sheet1.xml"] ?: error("ورقة عمليات العُهَد مفقودة.")
        val categories = parseCategories(sheet)
        val temp = singleSheetFile(context, sheet)
        try {
            val result = CustodyExcelDataManager.import(context, Uri.fromFile(temp)).getOrThrow()
            val dao = AppDatabase.getInstance(context).custodyDao()
            categories.forEach { (externalId, category) ->
                dao.getTransactionByExternalId(externalId)?.let { tx -> dao.updateTransaction(tx.copy(categoryName = category.trim())) }
            }
            result
        } finally { temp.delete() }
    }

    private fun addCategoryColumn(sheet: ByteArray, categories: List<String>): ByteArray {
        var text = sheet.toString(Charsets.UTF_8)
        val header = "<c r=\"U1\" t=\"inlineStr\"><is><t>التصنيف</t></is></c>"
        text = text.replaceFirst("</row>", "$header</row>")
        val rowRegex = Regex("<row\\b[^>]*r=\\\"(\\d+)\\\"[^>]*>.*?</row>", RegexOption.DOT_MATCHES_ALL)
        var txIndex = 0
        text = rowRegex.replace(text) { match ->
            val rowNumber = match.groupValues[1].toIntOrNull() ?: return@replace match.value
            if (rowNumber <= 1) return@replace match.value
            val value = categories.getOrNull(txIndex++).orEmpty()
            match.value.replace("</row>", "<c r=\"U$rowNumber\" t=\"inlineStr\"><is><t>${escape(value)}</t></is></c></row>")
        }
        return text.toByteArray(Charsets.UTF_8)
    }

    private fun parseCategories(sheet: ByteArray): Map<String, String> {
        val text = sheet.toString(Charsets.UTF_8)
        val result = linkedMapOf<String, String>()
        val rows = Regex("<row\\b[^>]*r=\\\"(\\d+)\\\"[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
        rows.findAll(text).forEach { rowMatch ->
            if (rowMatch.groupValues[1] == "1") return@forEach
            val row = rowMatch.groupValues[2]
            val tx = cellValue(row, "B") ?: return@forEach
            val category = cellValue(row, "U").orEmpty()
            if (tx.isNotBlank()) result[unescape(tx)] = unescape(category)
        }
        return result
    }

    private fun cellValue(row: String, column: String): String? {
        val match = Regex("<c\\b[^>]*r=\\\"$column\\d+\\\"[^>]*>.*?<t[^>]*>(.*?)</t>.*?</c>", RegexOption.DOT_MATCHES_ALL).find(row) ?: return null
        return match.groupValues[1]
    }

    private suspend fun buildSummarySheet(context: Context): ByteArray {
        val dao = AppDatabase.getInstance(context).custodyDao()
        val rows = mutableListOf<List<String>>()
        rows += listOf("العهدة", "العملة", "التصنيف", "عدد العمليات", "إجمالي المبلغ")
        dao.getAllCustodies(false).forEach { custody ->
            val tx = dao.getAllTransactions(custody.id, false)
            tx.groupBy { Triple(it.currencyCode, it.categoryName.trim(), custody.name) }
                .toSortedMap(compareBy({ it.third }, { it.first }, { it.second }))
                .forEach { (key, values) ->
                    rows += listOf(key.third, key.first, key.second, values.size.toString(), values.sumOf { it.amountMinor }.toString())
                }
        }
        val xml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\" rightToLeft=\"1\"/></sheetViews><sheetData>")
            rows.forEachIndexed { ri, row -> append("<row r=\"${ri + 1}\">"); row.forEachIndexed { ci, value -> append("<c r=\"${column(ci)}${ri + 1}\" t=\"inlineStr\"><is><t>${escape(value)}</t></is></c>") }; append("</row>") }
            append("</sheetData></worksheet>")
        }
        return xml.toByteArray(Charsets.UTF_8)
    }

    private fun writeWorkbook(out: java.io.OutputStream, sheet1: ByteArray, sheet2: ByteArray) {
        ZipOutputStream(out.buffered()).use { zip ->
            entry(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>")
            entry(zip, "_rels/.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            entry(zip, "xl/workbook.xml", "<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"بيانات العُهَد\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"ملخص التصنيفات\" sheetId=\"2\" r:id=\"rId2\"/></sheets></workbook>")
            entry(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/></Relationships>")
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); zip.write(sheet1); zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet2.xml")); zip.write(sheet2); zip.closeEntry()
        }
    }

    private fun singleSheetFile(context: Context, sheet: ByteArray): File {
        val file = File.createTempFile("custody-one-sheet-", ".xlsx", context.cacheDir)
        file.outputStream().use { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                entry(zip, "[Content_Types].xml", "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>")
                entry(zip, "_rels/.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
                entry(zip, "xl/workbook.xml", "<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"بيانات العُهَد\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>")
                entry(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>")
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); zip.write(sheet); zip.closeEntry()
            }
        }
        return file
    }

    private fun readZip(input: java.io.InputStream): Map<String, ByteArray> { val result = mutableMapOf<String, ByteArray>(); ZipInputStream(input.buffered()).use { zip -> while (true) { val e = zip.nextEntry ?: break; if (e.isDirectory) continue; val out = ByteArrayOutputStream(); zip.copyTo(out); result[e.name] = out.toByteArray() } }; return result }
    private fun sheetCount(workbook: ByteArray) = Regex("<sheet\\b").findAll(workbook.toString(Charsets.UTF_8)).count()
    private fun entry(zip: ZipOutputStream, name: String, value: String) { zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
    private fun column(index: Int): String { var n = index + 1; val b = StringBuilder(); while (n > 0) { val r = (n - 1) % 26; b.append(('A'.code + r).toChar()); n = (n - 1) / 26 }; return b.reverse().toString() }
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    private fun unescape(value: String) = value.replace("&apos;", "'").replace("&quot;", "\"").replace("&gt;", ">") .replace("&lt;", "<").replace("&amp;", "&")
}
