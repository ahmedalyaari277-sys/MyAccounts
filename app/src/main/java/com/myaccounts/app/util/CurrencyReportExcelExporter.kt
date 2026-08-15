package com.myaccounts.app.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.myaccounts.app.data.reports.CurrencyReportPersonRow
import com.myaccounts.app.data.reports.CurrencyReportSummary
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CurrencyReportExcelExporter {

    fun exportCurrencyReport(
        context: Context,
        summary: CurrencyReportSummary,
        people: List<CurrencyReportPersonRow>
    ): Result<String> {
        return try {
            val fileName =
                "MyAccounts_Currency_Report_${safeFileName(summary.currencyCode)}_${
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
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
                        createWorkbook(outputStream, summary, people)
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
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IllegalStateException("تعذر إنشاء مجلد التقارير.")
                }

                val file = File(directory, fileName)
                FileOutputStream(file).use { outputStream ->
                    createWorkbook(outputStream, summary, people)
                }
                Result.success(file.absolutePath)
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun createWorkbook(
        outputStream: OutputStream,
        summary: CurrencyReportSummary,
        people: List<CurrencyReportPersonRow>
    ) {
        ZipOutputStream(outputStream).use { zip ->
            writeEntry(zip, "[Content_Types].xml", contentTypesXml())
            writeEntry(zip, "_rels/.rels", rootRelationshipsXml())
            writeEntry(zip, "xl/workbook.xml", workbookXml())
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelationshipsXml())
            writeEntry(zip, "xl/styles.xml", stylesXml())
            writeEntry(zip, "xl/worksheets/sheet1.xml", worksheetXml(summary, people))
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
            <sheets>
                <sheet name="تقرير الحسابات" sheetId="1" r:id="rId1"/>
            </sheets>
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
            <fonts count="2">
                <font><sz val="11"/><name val="Arial"/></font>
                <font><b/><sz val="11"/><name val="Arial"/></font>
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

    private fun worksheetXml(
        summary: CurrencyReportSummary,
        people: List<CurrencyReportPersonRow>
    ): String {
        val rows = mutableListOf<String>()
        var rowNumber = 1

        rows += row(rowNumber++, listOf(cell("تقرير الحسابات العام", 1)))
        rows += row(rowNumber++, listOf(cell("العملة"), cell(currencyName(summary.currencyCode))))
        rows += row(rowNumber++, listOf(cell("إجمالي عليه"), numericCell(summary.totalReceivableMinor)))
        rows += row(rowNumber++, listOf(cell("إجمالي له"), numericCell(summary.totalPayableMinor)))
        rows += row(rowNumber++, listOf(cell("الرصيد"), numericCell(summary.balanceMinor)))
        rows += row(rowNumber++, listOf(cell("عدد العمليات"), integerCell(summary.transactionCount.toLong())))
        rows += row(rowNumber++, listOf(cell("تفاصيل الأشخاص", 1)))

        val headerRow = rowNumber
        rows += row(
            rowNumber++,
            listOf(
                cell("#", 1),
                cell("اسم الشخص", 1),
                cell("عليه", 1),
                cell("له", 1),
                cell("الرصيد", 1),
                cell("عدد العمليات", 1)
            )
        )

        people.forEachIndexed { index, person ->
            rows += row(
                rowNumber++,
                listOf(
                    integerCell((index + 1).toLong()),
                    cell(person.personName),
                    numericCell(person.totalReceivableMinor),
                    numericCell(person.totalPayableMinor),
                    numericCell(person.balanceMinor),
                    integerCell(person.transactionCount.toLong())
                )
            )
        }

        if (people.isEmpty()) {
            rows += row(rowNumber, listOf(cell("لا توجد بيانات للأشخاص.")))
        }

        val lastRow = maxOf(rowNumber - 1, headerRow)

        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheetViews><sheetView workbookViewId="0" rightToLeft="1"/></sheetViews>
                <sheetFormatPr defaultRowHeight="18"/>
                <cols>
                    <col min="1" max="1" width="8" customWidth="1"/>
                    <col min="2" max="2" width="32" customWidth="1"/>
                    <col min="3" max="5" width="18" customWidth="1"/>
                    <col min="6" max="6" width="16" customWidth="1"/>
                </cols>
                <sheetData>${rows.joinToString("\n")}</sheetData>
                <autoFilter ref="A${headerRow}:F${lastRow}"/>
            </worksheet>
        """.trimIndent()
    }

    private fun row(rowNumber: Int, cells: List<String>) = """
        <row r="$rowNumber">${cells.joinToString("\n")}</row>
    """.trimIndent()

    private fun cell(value: String, style: Int = 0) = """
        <c t="inlineStr" s="$style"><is><t xml:space="preserve">${xmlEscape(value)}</t></is></c>
    """.trimIndent()

    private fun numericCell(amountMinor: Long, style: Int = 0): String {
        val value = BigDecimal(amountMinor)
            .movePointLeft(2)
            .stripTrailingZeros()
            .toPlainString()
        return "<c t=\"n\" s=\"$style\"><v>$value</v></c>"
    }

    private fun integerCell(value: Long, style: Int = 0) =
        "<c t=\"n\" s=\"$style\"><v>$value</v></c>"

    private fun currencyName(currencyCode: String): String = when (currencyCode) {
        "YER" -> "الريال اليمني"
        "SAR" -> "الريال السعودي"
        "USD" -> "الدولار الأمريكي"
        else -> currencyCode
    }

    private fun safeFileName(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "Report" }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private const val XLSX_MIME_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
}
