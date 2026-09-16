package com.myaccounts.app.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
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

    private const val CATEGORY_HEADER = "التصنيف"

    private fun fileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    suspend fun exportActive(context: Context, uri: Uri): Result<CustodyExcelDataManager.ExportSummary> = runCatching {
        val temp = File.createTempFile("custody-excel-", ".xlsx", context.cacheDir)
        try {
            val summary = CustodyExcelDataManager.exportActive(context, fileUri(context, temp)).getOrThrow()
            val entries = readZip(temp.inputStream())
            val dao = AppDatabase.getInstance(context).custodyDao()
            val transactions = dao.getAllCustodies(false).flatMap { dao.getAllTransactions(it.id, false) }
            val categoriesByTransactionId = transactions.associate { it.externalId to it.categoryName.trim() }
            val baseSheet = entries["xl/worksheets/sheet1.xml"] ?: error("ورقة عمليات العُهَد مفقودة.")
            val categorizedSheet = addCategoryColumn(baseSheet, categoriesByTransactionId)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                writeSingleSheetWorkbook(out, categorizedSheet)
            } ?: error("تعذر فتح ملف Excel للكتابة.")
            summary
        } finally {
            temp.delete()
        }
    }

    fun previewImport(context: Context, uri: Uri): Result<CustodyExcelDataManager.ImportPreview> = runCatching {
        val entries = readZip(
            context.contentResolver.openInputStream(uri) ?: error("تعذر فتح ملف Excel.")
        )
        require(
            sheetCount(entries["xl/workbook.xml"] ?: error("ملف Excel غير صالح.")) == 1
        ) { "يجب أن يحتوي ملف العُهَد على Sheet واحد فقط." }

        val sheet = entries["xl/worksheets/sheet1.xml"] ?: error("ورقة عمليات العُهَد مفقودة.")
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings) ?: emptyList()
        val normalizedSheet = removeCategoryColumn(sheet, sharedStrings)
        val temp = singleSheetFile(context, normalizedSheet)
        try {
            CustodyExcelDataManager.previewImport(context, fileUri(context, temp)).getOrThrow()
        } finally {
            temp.delete()
        }
    }

    suspend fun import(context: Context, uri: Uri): Result<CustodyExcelDataManager.ImportSummary> = runCatching {
        val entries = readZip(
            context.contentResolver.openInputStream(uri) ?: error("تعذر فتح ملف Excel.")
        )
        require(
            sheetCount(entries["xl/workbook.xml"] ?: error("ملف Excel غير صالح.")) == 1
        ) { "يجب أن يحتوي ملف العُهَد على Sheet واحد فقط." }

        val sheet = entries["xl/worksheets/sheet1.xml"] ?: error("ورقة عمليات العُهَد مفقودة.")
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings) ?: emptyList()
        val categories = parseCategories(sheet, sharedStrings)
        val normalizedSheet = removeCategoryColumn(sheet, sharedStrings)
        val temp = singleSheetFile(context, normalizedSheet)

        try {
            val result = CustodyExcelDataManager.import(context, fileUri(context, temp)).getOrThrow()
            if (categories.isNotEmpty()) {
                val dao = AppDatabase.getInstance(context).custodyDao()
                categories.forEach { (externalId, category) ->
                    dao.getTransactionByExternalId(externalId)?.let { transaction ->
                        dao.updateTransaction(transaction.copy(categoryName = category.trim()))
                    }
                }
            }
            result
        } finally {
            temp.delete()
        }
    }

    private fun addCategoryColumn(
        sheet: ByteArray,
        categoriesByTransactionId: Map<String, String>
    ): ByteArray {
        var text = sheet.toString(Charsets.UTF_8)
        text = text.replaceFirst(
            "</row>",
            "<c r=\"U1\" t=\"inlineStr\"><is><t>$CATEGORY_HEADER</t></is></c></row>"
        )
        val rowRegex = Regex("<row\\b[^>]*r=\"(\\d+)\"[^>]*>.*?</row>", RegexOption.DOT_MATCHES_ALL)
        text = rowRegex.replace(text) { match ->
            val rowNumber = match.groupValues[1].toIntOrNull() ?: return@replace match.value
            if (rowNumber <= 1) return@replace match.value
            val transactionId = cellValue(match.value, "B").orEmpty().let(::unescape)
            val category = categoriesByTransactionId[transactionId].orEmpty()
            match.value.replace(
                "</row>",
                "<c r=\"U$rowNumber\" t=\"inlineStr\"><is><t>${escape(category)}</t></is></c></row>"
            )
        }
        return text.toByteArray(Charsets.UTF_8)
    }

    /**
     * Accept both the current 20-column workbook and the app's 21-column
     * custody workbook that contains the optional "التصنيف" column.
     * The category column is detected from the header instead of assuming U,
     * so files produced by Excel with a different physical column position
     * remain importable.
     */
    private fun removeCategoryColumn(
        sheet: ByteArray,
        sharedStrings: List<String>
    ): ByteArray {
        val text = sheet.toString(Charsets.UTF_8)
        val categoryColumn = findHeaderColumn(text, sharedStrings, CATEGORY_HEADER)
            ?: return sheet

        val columnRegex = Regex(
            "<c\\b[^>]*\\br=\"${Regex.escape(categoryColumn)}\\d+\"[^>]*>.*?</c>",
            RegexOption.DOT_MATCHES_ALL
        )
        return columnRegex.replace(text, "").toByteArray(Charsets.UTF_8)
    }

    private fun parseCategories(
        sheet: ByteArray,
        sharedStrings: List<String>
    ): Map<String, String> {
        val text = sheet.toString(Charsets.UTF_8)
        val categoryColumn = findHeaderColumn(text, sharedStrings, CATEGORY_HEADER)
            ?: return emptyMap()

        val result = linkedMapOf<String, String>()
        Regex("<row\\b[^>]*r=\"(\\d+)\"[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .forEach { rowMatch ->
                if (rowMatch.groupValues[1] == "1") return@forEach
                val row = rowMatch.groupValues[2]
                val transactionId = cellValue(row, "B", sharedStrings).orEmpty().let(::unescape)
                val category = cellValue(row, categoryColumn, sharedStrings).orEmpty().let(::unescape)
                if (transactionId.isNotBlank()) result[transactionId] = category
            }
        return result
    }

    private fun findHeaderColumn(
        sheetText: String,
        sharedStrings: List<String>,
        header: String
    ): String? {
        val headerRow = Regex(
            "<row\\b[^>]*r=\"1\"[^>]*>(.*?)</row>",
            RegexOption.DOT_MATCHES_ALL
        ).find(sheetText)?.groupValues?.get(1) ?: return null

        val cellRegex = Regex(
            "<c\\b[^>]*\\br=\"([A-Z]+)1\"[^>]*>.*?</c>",
            RegexOption.DOT_MATCHES_ALL
        )
        return cellRegex.findAll(headerRow).firstNotNullOfOrNull { match ->
            val column = match.groupValues[1]
            val value = cellValue(headerRow, column, sharedStrings)
                .orEmpty()
                .let(::unescape)
                .trim()
            if (value == header) column else null
        }
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val out = mutableListOf<String>()
        val text = bytes.toString(Charsets.UTF_8)
        Regex("<si\\b.*?</si>", RegexOption.DOT_MATCHES_ALL).findAll(text).forEach { si ->
            out += Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                .findAll(si.value)
                .joinToString("") { it.groupValues[1] }
        }
        return out
    }

    private fun cellValue(
        row: String,
        column: String,
        sharedStrings: List<String> = emptyList()
    ): String? {
        val cell = Regex(
            "<c\\b[^>]*\\br=\"${Regex.escape(column)}\\d+\"[^>]*>(.*?)</c>",
            RegexOption.DOT_MATCHES_ALL
        ).find(row) ?: return null
        val content = cell.groupValues[1]
        val attributes = Regex("<c\\b([^>]*)>", RegexOption.DOT_MATCHES_ALL)
            .find(cell.value)?.groupValues?.get(1).orEmpty()
        val type = Regex("(?:^|\\s)t=\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)
            .find(attributes)?.groupValues?.get(1).orEmpty()
        val textValue = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
            .find(content)?.groupValues?.get(1)
        if (textValue != null) return textValue
        val rawValue = Regex("<v[^>]*>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
            .find(content)?.groupValues?.get(1) ?: return null
        return if (type == "s") {
            sharedStrings.getOrNull(rawValue.toIntOrNull() ?: -1) ?: rawValue
        } else {
            rawValue
        }
    }

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
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheet)
            zip.closeEntry()
        }
    }

    private fun readZip(input: java.io.InputStream): Map<String, ByteArray> = buildMap {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val out = ByteArrayOutputStream()
                    zip.copyTo(out)
                    put(entry.name, out.toByteArray())
                }
            }
        }
    }

    private fun sheetCount(workbook: ByteArray) =
        Regex("<sheet\\b").findAll(workbook.toString(Charsets.UTF_8)).count()

    private fun entry(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun escape(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun unescape(value: String) = value
        .replace("&apos;", "'")
        .replace("&quot;", "\"")
        .replace("&gt;", ">")
        .replace("&lt;", "<")
        .replace("&amp;", "&")
}