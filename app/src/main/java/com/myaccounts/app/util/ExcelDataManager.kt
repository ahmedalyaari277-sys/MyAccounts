package com.myaccounts.app.util

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.room.withTransaction
import com.myaccounts.app.data.local.AppDatabase
import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.PersonEntity
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.TransactionType
import com.myaccounts.app.data.local.dao.ExcelExportRow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.xmlpull.v1.XmlPullParser

/**
 * The official MyAccounts Excel interchange format.
 * One workbook, exactly one worksheet, active data only.
 */
object ExcelDataManager {
    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val SUGGESTED_FILE_NAME = "MyAccounts_Data.xlsx"

    private const val SHEET_NAME = "بيانات MyAccounts"
    private const val MAX_UNCOMPRESSED_BYTES = 50L * 1024L * 1024L
    private val HEADERS = listOf(
        "معرف الشخص", "معرف العملية", "الاسم", "الهاتف", "العنوان", "الملاحظات",
        "العملة", "نوع العملية", "المبلغ", "البيان", "التاريخ"
    )

    data class ExportSummary(val people: Int, val accounts: Int, val transactions: Int)
    data class ImportSummary(val peopleAdded: Int, val accountsAdded: Int, val transactionsAdded: Int, val skippedDuplicates: Int)
    data class ImportPreview(
        val people: Int,
        val accounts: Int,
        val transactions: Int,
        val duplicateTransactions: Int,
        val errors: List<String>
    ) {
        val isValid: Boolean get() = errors.isEmpty()
    }

    private data class ImportRow(
        val rowNumber: Int,
        val personExternalId: String,
        val transactionExternalId: String,
        val name: String,
        val phone: String,
        val address: String,
        val notes: String,
        val currencyCode: String,
        val transactionType: TransactionType?,
        val amountMinor: Long?,
        val description: String,
        val transactionDate: Long?
    )

    fun exportActive(context: Context, uri: Uri): Result<ExportSummary> = runCatching {
        val db = AppDatabase.getInstance(context)
        val rows = db.ledgerDao().getActiveExcelRows()
        val peopleIds = rows.map { it.personExternalId }.toSet()
        val accountKeys = rows.map { it.personExternalId + "|" + it.currencyCode }.toSet()
        val transactionCount = rows.count { !it.transactionExternalId.isNullOrBlank() }
        context.contentResolver.openOutputStream(uri)?.use { output ->
            createWorkbook(output, rows)
        } ?: error("تعذر فتح ملف Excel للكتابة.")
        ExportSummary(peopleIds.size, accountKeys.size, transactionCount)
    }

    fun previewImport(context: Context, uri: Uri): Result<ImportPreview> = runCatching {
        val rows = parseWorkbook(context, uri)
        validateRows(rows, AppDatabase.getInstance(context))
    }

    fun import(context: Context, uri: Uri): Result<ImportSummary> = runCatching {
        val db = AppDatabase.getInstance(context)
        val rows = parseWorkbook(context, uri)
        val preview = validateRows(rows, db)
        check(preview.isValid) { preview.errors.joinToString("\n") }

        db.withTransaction {
            var peopleAdded = 0
            var accountsAdded = 0
            var transactionsAdded = 0
            var skippedDuplicates = 0
            val personIds = mutableMapOf<String, Long>()
            val accountIds = mutableMapOf<String, Long>()

            rows.forEach { row ->
                val person = db.ledgerDao().getPersonByExternalId(row.personExternalId)
                val personId = when {
                    person != null && !person.isActive -> error("الصف ${row.rowNumber}: معرف الشخص مرتبط بسجل مؤرشف، والاستيراد لا يلمس الأرشيف.")
                    person != null -> person.id
                    else -> {
                        val newId = db.ledgerDao().insertPerson(
                            PersonEntity(
                                name = row.name,
                                phone = row.phone,
                                address = row.address,
                                notes = row.notes,
                                externalId = row.personExternalId
                            )
                        )
                        peopleAdded++
                        newId
                    }
                }
                personIds[row.personExternalId] = personId

                val accountKey = row.personExternalId + "|" + row.currencyCode
                val accountId = accountIds[accountKey] ?: db.ledgerDao().getCurrencyAccount(personId, row.currencyCode)?.id
                    ?: db.ledgerDao().insertCurrencyAccount(
                        CurrencyAccountEntity(personId = personId, currencyCode = row.currencyCode)
                    ).also { accountsAdded++ }
                accountIds[accountKey] = accountId

                if (row.transactionExternalId.isNotBlank()) {
                    if (db.transactionDao().getTransactionByExternalId(row.transactionExternalId) != null) {
                        skippedDuplicates++
                    } else {
                        db.transactionDao().insertTransactionAndUpdateBalance(
                            TransactionEntity(
                                accountId = accountId,
                                type = row.transactionType!!,
                                amountMinor = row.amountMinor!!,
                                description = row.description,
                                transactionDate = row.transactionDate!!,
                                externalId = row.transactionExternalId
                            )
                        )
                        transactionsAdded++
                    }
                }
            }
            ImportSummary(peopleAdded, accountsAdded, transactionsAdded, skippedDuplicates)
        }
    }

    private fun validateRows(rows: List<ImportRow>, db: AppDatabase): ImportPreview {
        val errors = mutableListOf<String>()
        val personIds = mutableSetOf<String>()
        val accountKeys = mutableSetOf<String>()
        val transactionIds = mutableSetOf<String>()
        var transactions = 0
        var duplicates = 0

        rows.forEach { row ->
            if (row.personExternalId.isBlank()) errors += "الصف ${row.rowNumber}: معرف الشخص مطلوب."
            if (row.name.isBlank()) errors += "الصف ${row.rowNumber}: اسم الشخص مطلوب."
            if (row.currencyCode !in setOf("YER", "SAR", "USD")) errors += "الصف ${row.rowNumber}: العملة يجب أن تكون YER أو SAR أو USD."
            if (row.personExternalId.isNotBlank()) personIds += row.personExternalId
            if (row.personExternalId.isNotBlank() && row.currencyCode.isNotBlank()) accountKeys += row.personExternalId + "|" + row.currencyCode

            val existingPerson = if (row.personExternalId.isBlank()) null else db.ledgerDao().getPersonByExternalId(row.personExternalId)
            if (existingPerson != null && !existingPerson.isActive) errors += "الصف ${row.rowNumber}: معرف الشخص مرتبط بسجل مؤرشف."

            if (row.transactionExternalId.isNotBlank()) {
                transactions++
                if (!transactionIds.add(row.transactionExternalId)) {
                    duplicates++
                    errors += "الصف ${row.rowNumber}: معرف العملية مكرر داخل الملف."
                }
                if (row.transactionType == null) errors += "الصف ${row.rowNumber}: نوع العملية يجب أن يكون عليه/له أو RECEIVABLE/PAYABLE."
                if (row.amountMinor == null || row.amountMinor <= 0L) errors += "الصف ${row.rowNumber}: المبلغ يجب أن يكون رقمًا موجبًا وبحد أقصى منزلتين عشريتين."
                if (row.transactionDate == null) errors += "الصف ${row.rowNumber}: التاريخ غير صالح. استخدم yyyy-MM-dd."
            }
        }

        return ImportPreview(personIds.size, accountKeys.size, transactions, duplicates, errors.distinct().take(100))
    }

    private fun parseWorkbook(context: Context, uri: Uri): List<ImportRow> {
        val entries = readZipEntries(context.contentResolver.openInputStream(uri) ?: error("تعذر فتح ملف Excel."))
        val workbook = entries["xl/workbook.xml"] ?: error("ملف Excel غير صالح: workbook.xml مفقود.")
        val sheets = parseWorkbookSheetCount(workbook)
        check(sheets == 1) { "يجب أن يحتوي ملف Excel على Sheet واحد فقط." }
        val sheet = entries["xl/worksheets/sheet1.xml"] ?: error("ملف Excel غير صالح: الورقة الأولى مفقودة.")
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings) ?: emptyList()
        return parseSheet(sheet, sharedStrings)
    }

    private fun readZipEntries(input: InputStream): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    total += read
                    check(total <= MAX_UNCOMPRESSED_BYTES) { "ملف Excel أكبر من الحد المسموح." }
                    out.write(buffer, 0, read)
                }
                result[entry.name] = out.toByteArray()
            }
        }
        return result
    }

    private fun parseWorkbookSheetCount(bytes: ByteArray): Int {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var count = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "sheet") count++
            event = parser.next()
        }
        return count
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var current = StringBuilder()
        var inText = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "t") { inText = true; current = StringBuilder() }
                XmlPullParser.TEXT -> if (inText) current.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "t" && inText) { inText = false; result += current.toString() }
            }
            event = parser.next()
        }
        return result
    }

    private fun parseSheet(bytes: ByteArray, sharedStrings: List<String>): List<ImportRow> {
        val rows = mutableListOf<ImportRow>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var currentCells = mutableMapOf<Int, String>()
        var currentTypes = mutableMapOf<Int, String>()
        var rowNumber = 0
        var cellColumn = -1
        var cellType = ""
        var cellValue = StringBuilder()
        var inValue = false
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> { currentCells = mutableMapOf(); currentTypes = mutableMapOf(); rowNumber = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: (rowNumber + 1) }
                    "c" -> {
                        val ref = parser.getAttributeValue(null, "r") ?: ""
                        cellColumn = columnIndex(ref)
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                    }
                    "v", "t" -> { inValue = true; cellValue = StringBuilder() }
                }
                XmlPullParser.TEXT -> if (inValue) cellValue.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> {
                        if (cellColumn >= 0) { currentCells[cellColumn] = cellValue.toString(); currentTypes[cellColumn] = cellType }
                        inValue = false
                    }
                    "c" -> cellColumn = -1
                    "row" -> if (rowNumber > 1) {
                        val values = (0..10).map { column ->
                            val raw = currentCells[column] ?: ""
                            if (currentTypes[column] == "s") sharedStrings.getOrNull(raw.toIntOrNull() ?: -1) ?: raw else raw
                        }
                        rows += parseImportRow(rowNumber, values)
                    }
                }
            }
            event = parser.next()
        }

        check(rows.isNotEmpty()) { "ملف Excel لا يحتوي على بيانات قابلة للاستيراد." }
        return rows
    }

    private fun parseImportRow(rowNumber: Int, values: List<String>): ImportRow {
        val transactionId = values[1].trim()
        val type = parseTransactionType(values[7])
        return ImportRow(
            rowNumber = rowNumber,
            personExternalId = values[0].trim(),
            transactionExternalId = transactionId,
            name = values[2].trim(),
            phone = values[3].trim(),
            address = values[4].trim(),
            notes = values[5].trim(),
            currencyCode = normalizeCurrency(values[6]),
            transactionType = type,
            amountMinor = parseAmount(values[8]),
            description = values[9].trim(),
            transactionDate = parseDate(values[10].trim())
        )
    }

    private fun parseAmount(value: String): Long? = runCatching {
        if (value.isBlank()) return null
        BigDecimal(value.trim().replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
    }.getOrNull()

    private fun parseDate(value: String): Long? {
        if (value.isBlank()) return null
        value.toDoubleOrNull()?.let { serial ->
            if (serial > 1 && serial < 100000) {
                val calendar = Calendar.getInstance(Locale.US)
                calendar.timeInMillis = 0
                calendar.set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                return calendar.timeInMillis + (serial * 86_400_000.0).toLong()
            }
        }
        listOf("yyyy-MM-dd", "dd-MM-yyyy", "dd-MM-yyyy HH:mm", "yyyy-MM-dd HH:mm").forEach { pattern ->
            runCatching {
                val format = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                return format.parse(value)?.time
            }
        }
        return null
    }

    private fun parseTransactionType(value: String): TransactionType? = when (value.trim().uppercase(Locale.ROOT)) {
        "RECEIVABLE", "عليه" -> TransactionType.RECEIVABLE
        "PAYABLE", "له" -> TransactionType.PAYABLE
        else -> null
    }

    private fun normalizeCurrency(value: String): String = when (value.trim().uppercase(Locale.ROOT)) {
        "YER", "ريال يمني", "الريال اليمني" -> "YER"
        "SAR", "ريال سعودي", "الريال السعودي" -> "SAR"
        "USD", "دولار", "الدولار", "الدولار الأمريكي" -> "USD"
        else -> value.trim().uppercase(Locale.ROOT)
    }

    private fun columnIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase(Locale.ROOT)
        var result = 0
        letters.forEach { result = result * 26 + (it - 'A' + 1) }
        return result - 1
    }

    private fun createWorkbook(output: java.io.OutputStream, rows: List<ExcelExportRow>) {
        ZipOutputStream(output.buffered()).use { zip ->
            writeEntry(zip, "[Content_Types].xml", contentTypesXml())
            writeEntry(zip, "_rels/.rels", rootRelationshipsXml())
            writeEntry(zip, "xl/workbook.xml", workbookXml())
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelationshipsXml())
            writeEntry(zip, "xl/styles.xml", stylesXml())
            writeEntry(zip, "xl/worksheets/sheet1.xml", worksheetXml(rows))
        }
    }

    private fun writeEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypesXml() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
            <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
            <Default Extension="xml" ContentType="application/xml"/>
            <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
            <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
            <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
        </Types>
    """.trimIndent()

    private fun rootRelationshipsXml() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()

    private fun workbookXml() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
            <sheets><sheet name="$SHEET_NAME" sheetId="1" r:id="rId1"/></sheets>
        </workbook>
    """.trimIndent()

    private fun workbookRelationshipsXml() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
            <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
    """.trimIndent()

    private fun stylesXml() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
            <fonts count="2"><font><sz val="11"/><name val="Arial"/></font><font><b/><sz val="11"/><name val="Arial"/></font></fonts>
            <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
            <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
            <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
            <cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0"/></cellXfs>
        </styleSheet>
    """.trimIndent()

    private fun worksheetXml(rows: List<ExcelExportRow>): String {
        val xmlRows = mutableListOf<String>()
        xmlRows += rowXml(1, HEADERS.map { textCell(it, 1) })
        var rowNumber = 2
        rows.forEach { item ->
            xmlRows += rowXml(rowNumber++, listOf(
                textCell(item.personExternalId),
                textCell(item.transactionExternalId ?: ""),
                textCell(item.name),
                textCell(item.phone),
                textCell(item.address),
                textCell(item.notes),
                textCell(item.currencyCode),
                textCell(item.transactionType?.let { if (it == TransactionType.RECEIVABLE) "عليه" else "له" } ?: ""),
                item.amountMinor?.let { numericCell(it) } ?: textCell(""),
                textCell(item.description ?: ""),
                item.transactionDate?.let { textCell(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it))) } ?: textCell("")
            ))
        }
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheetViews><sheetView workbookViewId="0" rightToLeft="1"/></sheetViews>
                <sheetFormatPr defaultRowHeight="18"/>
                <cols>
                    <col min="1" max="1" width="18" customWidth="1"/><col min="2" max="2" width="18" customWidth="1"/>
                    <col min="3" max="3" width="28" customWidth="1"/><col min="4" max="4" width="18" customWidth="1"/>
                    <col min="5" max="6" width="28" customWidth="1"/><col min="7" max="8" width="16" customWidth="1"/>
                    <col min="9" max="9" width="16" customWidth="1"/><col min="10" max="10" width="30" customWidth="1"/>
                    <col min="11" max="11" width="14" customWidth="1"/>
                </cols>
                <sheetData>${xmlRows.joinToString("\n")}</sheetData>
                <autoFilter ref="A1:K${maxOf(1, rowNumber - 1)}"/>
            </worksheet>
        """.trimIndent()
    }

    private fun rowXml(number: Int, cells: List<String>) = "<row r=\"$number\">${cells.mapIndexed { index, cell -> cell.replace("CELLREF", columnName(index + 1) + number) }.joinToString("")}</row>"

    private fun textCell(value: String, style: Int = 0) = "<c r=\"CELLREF\" t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">${xmlEscape(value)}</t></is></c>"
    private fun numericCell(amountMinor: Long) = "<c r=\"CELLREF\" t=\"n\"><v>${BigDecimal(amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString()}</v></c>"

    private fun columnName(number: Int): String {
        var n = number
        val out = StringBuilder()
        while (n > 0) { val rem = (n - 1) % 26; out.append(('A'.code + rem).toChar()); n = (n - 1) / 26 }
        return out.reverse().toString()
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
}
