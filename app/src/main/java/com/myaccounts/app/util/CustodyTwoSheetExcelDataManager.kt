package com.myaccounts.app.util

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.myaccounts.app.data.local.AppDatabase

/** Custody-only workbook: exactly one sheet, including operation categories. */
object CustodyTwoSheetExcelDataManager {
    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val SUGGESTED_FILE_NAME = "MyAccounts_Custodies.xlsx"

    suspend fun exportActive(context: Context, uri: Uri): Result<CustodyExcelDataManager.ExportSummary> = runCatching {
        val temp = File.createTempFile("custody-excel-", ".xlsx", context.cacheDir)
        try {
            val summary = CustodyExcelDataManager.exportActive(context, Uri.fromFile(temp)).getOrThrow()
            val entries = readZip(temp.inputStream())
            val transactions = AppDatabase.getInstance(context).custodyDao().getAllCustodies(false).flatMap { AppDatabase.getInstance(context).custodyDao().getAllTransactions(it.id, false) }
            val baseSheet = entries["xl/worksheets/sheet1.xml"] ?: error("ورقة عمليات العُهَد مفقودة.")
            val categorizedSheet = addCategoryColumn(baseSheet, transactions.map { it.categoryName.trim() })
            context.contentResolver.openOutputStream(uri)?.use { out -> writeSingleSheetWorkbook(out, categorizedSheet) } ?: error("تعذر فتح ملف Excel للكتابة.")
            summary
        } finally { temp.delete() }
    }

    fun previewImport(context: Context, uri: Uri): Result<CustodyExcelDataManager.ImportPreview> = runCatching {
        val entries = readZip(context.contentResolver.openInputStream(uri) ?: error("تعذر فتح ملف Excel."))
        require(sheetCount(entries["xl/workbook.xml"] ?: error("ملف Excel غير صالح.")) == 1) { "يجب أن يحتوي ملف العُهَد على Sheet واحد فقط." }
        val sheet = entries["xl/worksheets/sheet1.xml"] ?: error("ورقة عمليات العُهَد مفقودة.")
        val temp = singleSheetFile(context, sheet)
        try { CustodyExcelDataManager.previewImport(context, Uri.fromFile(temp)).getOrThrow() } finally { temp.delete() }
    }

    suspend fun import(context: Context, uri: Uri): Result<CustodyExcelDataManager.ImportSummary> = runCatching {
        val entries = readZip(context.contentResolver.openInputStream(uri) ?: error("تعذر فتح ملف Excel."))
        require(sheetCount(entries["xl/workbook.xml"] ?: error("ملف Excel غير صالح.")) == 1) { "يجب أن يحتوي ملف العُهَد على Sheet واحد فقط." }
        val sheet = entries["xl/worksheets/sheet1.xml"] ?: error("ورقة عمليات العُهَد مفقودة.")
        val categories = parseCategories(sheet)
        val temp = singleSheetFile(context, sheet)
        try {
            val result = CustodyExcelDataManager.import(context, Uri.fromFile(temp)).getOrThrow()
            val dao = AppDatabase.getInstance(context).custodyDao()
            categories.forEach { (externalId, category) -> dao.getTransactionByExternalId(externalId)?.let { tx -> dao.updateTransaction(tx.copy(categoryName = category.trim())) } }
            result
        } finally { temp.delete() }
    }

    private fun addCategoryColumn(sheet: ByteArray, categories: List<String>): ByteArray {
        var text = sheet.toString(Charsets.UTF_8)
        text = text.replaceFirst("</row>", "<c r=\"U1\" t=\"inlineStr\"><is><t>التصنيف</t></is></c></row>")
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
        Regex("<row\\b[^>]*r=\\\"(\\d+)\\\"[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(text).forEach { rowMatch ->
            if (rowMatch.groupValues[1] == "1") return@forEach
            val row = rowMatch.groupValues[2]
            val tx = cellValue(row, "B") ?: return@forEach
            val category = cellValue(row, "U").orEmpty()
            if (tx.isNotBlank()) result[unescape(tx)] = unescape(category)
        }
        return result
    }

    private fun cellValue(row: String, column: String): String? = Regex("<c\\b[^>]*r=\\\"$column\\d+\\\"[^>]*>.*?<t[^>]*>(.*?)</t>.*?</c>", RegexOption.DOT_MATCHES_ALL).find(row)?.groupValues?.get(1)

    private fun singleSheetFile(context: Context, sheet: ByteArray): File {
        val file = File.createTempFile("custody-one-sheet-", ".xlsx", context.cacheDir)
        file.outputStream().use { out -> writeSingleSheetWorkbook(out, sheet) }
        return file
    }

    private fun writeSingleSheetWorkbook(out: java.io.OutputStream, sheet: ByteArray) {
        ZipOutputStream(out.buffered()).use { zip ->
            entry(zip, "[Content_Types].xml", "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>")
            entry(zip, "_rels/.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            entry(zip, "xl/workbook.xml", "<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"بيانات العُهَد\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>")
            entry(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>")
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); zip.write(sheet); zip.closeEntry()
        }
    }

    private fun readZip(input: java.io.InputStream): Map<String, ByteArray> = buildMap { ZipInputStream(input.buffered()).use { zip -> while (true) { val e = zip.nextEntry ?: break; if (!e.isDirectory) { val out = ByteArrayOutputStream(); zip.copyTo(out); put(e.name, out.toByteArray()) } } } }
    private fun sheetCount(workbook: ByteArray) = Regex("<sheet\\b").findAll(workbook.toString(Charsets.UTF_8)).count()
    private fun entry(zip: ZipOutputStream, name: String, value: String) { zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    private fun unescape(value: String) = value.replace("&apos;", "'").replace("&quot;", "\"").replace("&gt;", ">") .replace("&lt;", "<").replace("&amp;", "&")
}
