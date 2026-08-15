package com.myaccounts.app.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.myaccounts.app.data.reports.PersonReportSummary
import com.myaccounts.app.data.reports.PersonReportTransaction
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PersonReportExcelExporter {

    fun exportPersonReport(
        context: Context,
        summary: PersonReportSummary,
        transactions: List<PersonReportTransaction>,
        startDateMillis: Long?,
        endDateMillisExclusive: Long?
    ): Result<String> {
        return try {
            val fileName =
                "MyAccounts_Person_Report_${safeFileName(summary.personName)}_${
                    SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.US
                    ).format(Date())
                }.xlsx"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, XLSX_MIME_TYPE)
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/MyAccounts"
                    )
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: throw IllegalStateException("تعذر إنشاء ملف Excel.")

                try {
                    resolver.openOutputStream(uri).use { outputStream ->
                        if (outputStream == null) {
                            throw IllegalStateException("تعذر فتح ملف Excel.")
                        }

                        createWorkbook(
                            outputStream = outputStream,
                            summary = summary,
                            transactions = transactions,
                            startDateMillis = startDateMillis,
                            endDateMillisExclusive = endDateMillisExclusive
                        )
                    }
                } catch (exception: Exception) {
                    resolver.delete(uri, null, null)
                    throw exception
                }

                Result.success("تم حفظ تقرير Excel في مجلد التنزيلات/MyAccounts")
            } else {
                val directory = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                    "MyAccounts"
                )

                if (!directory.exists()) {
                    directory.mkdirs()
                }

                val file = File(directory, fileName)

                FileOutputStream(file).use { outputStream ->
                    createWorkbook(
                        outputStream = outputStream,
                        summary = summary,
                        transactions = transactions,
                        startDateMillis = startDateMillis,
                        endDateMillisExclusive = endDateMillisExclusive
                    )
                }

                Result.success(file.absolutePath)
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun createWorkbook(
        outputStream: OutputStream,
        summary: PersonReportSummary,
        transactions: List<PersonReportTransaction>,
        startDateMillis: Long?,
        endDateMillisExclusive: Long?
    ) {
        ZipOutputStream(outputStream).use { zip ->
            writeEntry(zip, "[Content_Types].xml", contentTypesXml())
            writeEntry(zip, "_rels/.rels", rootRelationshipsXml())
            writeEntry(zip, "xl/workbook.xml", workbookXml())
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelationshipsXml())
            writeEntry(zip, "xl/styles.xml", stylesXml())
            writeEntry(
                zip,
                "xl/worksheets/sheet1.xml",
                worksheetXml(
                    summary = summary,
                    transactions = transactions,
                    startDateMillis = startDateMillis,
                    endDateMillisExclusive = endDateMillisExclusive
                )
            )
        }
    }

    private fun writeEntry(
        zip: ZipOutputStream,
        path: String,
        content: String
    ) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypesXml(): String {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
            </Types>
        """.trimIndent()
    }

    private fun rootRelationshipsXml(): String {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship
                    Id="rId1"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                    Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent()
    }

    private fun workbookXml(): String {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook
                xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <sheets>
                    <sheet name="تقرير الشخص" sheetId="1" r:id="rId1"/>
                </sheets>
            </workbook>
        """.trimIndent()
    }

    private fun workbookRelationshipsXml(): String {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship
                    Id="rId1"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"
                    Target="worksheets/sheet1.xml"/>
                <Relationship
                    Id="rId2"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"
                    Target="styles.xml"/>
            </Relationships>
        """.trimIndent()
    }

    private fun stylesXml(): String {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <fonts count="2">
                    <font>
                        <sz val="11"/>
                        <name val="Arial"/>
                    </font>
                    <font>
                        <b/>
                        <sz val="11"/>
                        <name val="Arial"/>
                    </font>
                </fonts>
                <fills count="2">
                    <fill><patternFill patternType="none"/></fill>
                    <fill><patternFill patternType="gray125"/></fill>
                </fills>
                <borders count="1">
                    <border><left/><right/><top/><bottom/><diagonal/></border>
                </borders>
                <cellStyleXfs count="1">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
                </cellStyleXfs>
                <cellXfs count="2">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0"/>
                </cellXfs>
            </styleSheet>
        """.trimIndent()
    }

    private fun worksheetXml(
        summary: PersonReportSummary,
        transactions: List<PersonReportTransaction>,
        startDateMillis: Long?,
        endDateMillisExclusive: Long?
    ): String {
        val rows = mutableListOf<String>()
        var rowNumber = 1

        rows.add(
            row(
                rowNumber++,
                listOf(cell("تقرير حساب شخص", style = 1))
            )
        )

        rows.add(
            row(
                rowNumber++,
                listOf(cell("اسم الشخص"), cell(summary.personName))
            )
        )

        rows.add(
            row(
                rowNumber++,
                listOf(
                    cell("العملة"),
                    cell(currencyName(summary.currencyCode))
                )
            )
        )

        rows.add(
            row(
                rowNumber++,
                listOf(
                    cell("الفترة"),
                    cell(formatDateRange(startDateMillis, endDateMillisExclusive))
                )
            )
        )

        rows.add(
            row(
                rowNumber++,
                listOf(cell("ملخص الحساب", style = 1))
            )
        )

        rows.add(
            row(
                rowNumber++,
                listOf(
                    cell("البند", style = 1),
                    cell("المبلغ", style = 1)
                )
            )
        )

        rows.add(
            row(
                rowNumber++,
                listOf(
                    cell("الرصيد الافتتاحي"),
                    numericCell(summary.openingBalanceMinor)
                )
            )
        )

        rows.add(
            row(
                rowNumber++,
                listOf(
                    cell("إجمالي عليه خلال الفترة"),
                    numericCell(summary.periodReceivableMinor)
                )
            )
        )

        rows.add(
            row(
                rowNumber++,
                listOf(
                    cell("إجمالي له خلال الفترة"),
                    numericCell(summary.periodPayableMinor)
                )
            )
        )

        rows.add(
            row(
                rowNumber++,
                listOf(
                    cell("صافي حركة الفترة"),
                    numericCell(summary.periodBalanceMinor)
                )
            )
        )

        rows.add(
            row(
                rowNumber++,
                listOf(
                    cell("الرصيد الختامي"),
                    numericCell(summary.closingBalanceMinor)
                )
            )
        )

        rows.add(
            row(
                rowNumber++,
                listOf(
                    cell("عدد العمليات"),
                    integerCell(summary.transactionCount.toLong())
                )
            )
        )

        rows.add(
            row(
                rowNumber++,
                listOf(cell("تفاصيل العمليات", style = 1))
            )
        )

        val transactionHeaderRow = rowNumber

        rows.add(
            row(
                rowNumber++,
                listOf(
                    cell("#", style = 1),
                    cell("التاريخ", style = 1),
                    cell("النوع", style = 1),
                    cell("المبلغ", style = 1),
                    cell("الوصف", style = 1)
                )
            )
        )

        transactions.forEachIndexed { index, transaction ->
            rows.add(
                row(
                    rowNumber++,
                    listOf(
                        integerCell((index + 1).toLong()),
                        cell(formatDate(transaction.transactionDate)),
                        cell(transactionTypeName(transaction.type)),
                        numericCell(transaction.amountMinor),
                        cell(transaction.description)
                    )
                )
            )
        }

        if (transactions.isEmpty()) {
            rows.add(
                row(
                    rowNumber,
                    listOf(cell("لا توجد عمليات خلال الفترة المحددة."))
                )
            )
        }

        val lastRow = maxOf(rowNumber - 1, transactionHeaderRow)

        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheetViews>
                    <sheetView workbookViewId="0" rightToLeft="1"/>
                </sheetViews>
                <sheetFormatPr defaultRowHeight="18"/>
                <cols>
                    <col min="1" max="1" width="8" customWidth="1"/>
                    <col min="2" max="2" width="16" customWidth="1"/>
                    <col min="3" max="3" width="18" customWidth="1"/>
                    <col min="4" max="4" width="18" customWidth="1"/>
                    <col min="5" max="5" width="45" customWidth="1"/>
                </cols>
                <sheetData>
                    ${rows.joinToString("\n")}
                </sheetData>
                <autoFilter ref="A${transactionHeaderRow}:E${lastRow}"/>
            </worksheet>
        """.trimIndent()
    }

    private fun row(
        rowNumber: Int,
        cells: List<String>
    ): String {
        return """
            <row r="$rowNumber">
                ${cells.joinToString("\n")}
            </row>
        """.trimIndent()
    }

    private fun cell(
        value: String,
        style: Int = 0
    ): String {
        return """
            <c t="inlineStr" s="$style">
                <is>
                    <t xml:space="preserve">${xmlEscape(value)}</t>
                </is>
            </c>
        """.trimIndent()
    }

    private fun numericCell(
        amountMinor: Long,
        style: Int = 0
    ): String {
        val value = BigDecimal(amountMinor)
            .movePointLeft(2)
            .stripTrailingZeros()
            .toPlainString()

        return """
            <c t="n" s="$style">
                <v>$value</v>
            </c>
        """.trimIndent()
    }

    private fun integerCell(
        value: Long,
        style: Int = 0
    ): String {
        return """
            <c t="n" s="$style">
                <v>$value</v>
            </c>
        """.trimIndent()
    }

    private fun currencyName(
        currencyCode: String
    ): String {
        return when (currencyCode) {
            "YER" -> "الريال اليمني"
            "SAR" -> "الريال السعودي"
            "USD" -> "الدولار الأمريكي"
            else -> currencyCode
        }
    }

    private fun transactionTypeName(
        type: String
    ): String {
        return when (type) {
            "RECEIVABLE" -> "عليه"
            "PAYABLE" -> "له"
            else -> type
        }
    }

    private fun formatDate(
        millis: Long
    ): String {
        return SimpleDateFormat(
            "dd/MM/yyyy",
            Locale("ar")
        ).format(Date(millis))
    }

    private fun formatDateRange(
        startDateMillis: Long?,
        endDateMillisExclusive: Long?
    ): String {
        val start = startDateMillis?.let { formatDate(it) } ?: "غير محدد"
        val end = endDateMillisExclusive?.let {
            formatDate(addDays(it, -1))
        } ?: "غير محدد"
        return "$start - $end"
    }

    private fun addDays(
        millis: Long,
        days: Int
    ): Long {
        return Calendar.getInstance().apply {
            timeInMillis = millis
            add(Calendar.DAY_OF_MONTH, days)
        }.timeInMillis
    }

    private fun safeFileName(
        value: String
    ): String {
        return value
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .take(60)
            .ifBlank { "Person" }
    }

    private fun xmlEscape(
        value: String
    ): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
            .filter {
                it == '\n' ||
                    it == '\r' ||
                    it == '\t' ||
                    it >= ' '
            }
    }

    private const val XLSX_MIME_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
}
