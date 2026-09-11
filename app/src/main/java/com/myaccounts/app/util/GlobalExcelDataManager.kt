package com.myaccounts.app.util

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Human-readable whole-app Excel transfer.
 * The workbook intentionally contains exactly two sheets: accounts and custody.
 * The existing section managers remain the source of truth for their schemas.
 */
object GlobalExcelDataManager {
    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val SUGGESTED_FILE_NAME = "MyAccounts_All_Data.xlsx"

    data class ExportSummary(
        val accountPeople: Int,
        val accountAccounts: Int,
        val accountTransactions: Int,
        val custodyCustodies: Int,
        val custodyPeople: Int,
        val custodyAccounts: Int,
        val custodyTransactions: Int
    )

    data class ImportPreview(
        val account: ExcelDataManager.ImportPreview,
        val custody: CustodyExcelDataManager.ImportPreview
    ) {
        val isValid: Boolean get() = account.isValid && custody.isValid
    }

    data class ImportSummary(
        val account: ExcelDataManager.ImportSummary,
        val custody: CustodyExcelDataManager.ImportSummary
    )

    suspend fun exportActive(context: Context, uri: Uri): Result<ExportSummary> = runCatching {
        val accountFile = File.createTempFile("myaccounts-account-excel-", ".xlsx", context.cacheDir)
        val custodyFile = File.createTempFile("myaccounts-custody-excel-", ".xlsx", context.cacheDir)
        try {
            val accountUri = Uri.fromFile(accountFile)
            val custodyUri = Uri.fromFile(custodyFile)
            val account = ExcelDataManager.exportActive(context, accountUri).getOrThrow()
            val custody = CustodyExcelDataManager.exportActive(context, custodyUri).getOrThrow()
            val accountEntries = readZip(accountFile)
            val custodyEntries = readZip(custodyFile)
            writeCombinedWorkbook(
                context.contentResolver.openOutputStream(uri) ?: error("تعذر فتح ملف Excel للكتابة."),
                accountEntries["xl/worksheets/sheet1.xml"] ?: error("بيانات الحسابات غير موجودة."),
                custodyEntries["xl/worksheets/sheet1.xml"] ?: error("بيانات العُهَد غير موجودة.")
            )
            ExportSummary(
                account.people, account.accounts, account.transactions,
                custody.custodies, custody.people, custody.accounts, custody.transactions
            )
        } finally {
            accountFile.delete()
            custodyFile.delete()
        }
    }

    fun previewImport(context: Context, uri: Uri): Result<ImportPreview> = runCatching {
        val entries = readZip(context.contentResolver.openInputStream(uri) ?: error("تعذر فتح ملف Excel."))
        val sheets = sheetNames(entries["xl/workbook.xml"] ?: error("ملف Excel غير صالح."))
        check(sheets.size == 2) { "يجب أن يحتوي ملف Excel العام على Sheetين فقط: الحسابات والعُهَد." }
        val accountSheet = entries["xl/worksheets/sheet1.xml"] ?: error("ورقة الحسابات مفقودة.")
        val custodySheet = entries["xl/worksheets/sheet2.xml"] ?: error("ورقة العُهَد مفقودة.")
        val accountUri = materializeSingleSheet(context, accountSheet, "accounts-preview")
        val custodyUri = materializeSingleSheet(context, custodySheet, "custody-preview")
        try {
            val account = ExcelDataManager.previewImport(context, accountUri).getOrThrow()
            val custody = CustodyExcelDataManager.previewImport(context, custodyUri).getOrThrow()
            ImportPreview(account, custody)
        } finally {
            File(accountUri.path ?: "").delete()
            File(custodyUri.path ?: "").delete()
        }
    }

    suspend fun import(context: Context, uri: Uri): Result<ImportSummary> = runCatching {
        val entries = readZip(context.contentResolver.openInputStream(uri) ?: error("تعذر فتح ملف Excel."))
        val sheets = sheetNames(entries["xl/workbook.xml"] ?: error("ملف Excel غير صالح."))
        check(sheets.size == 2) { "يجب أن يحتوي ملف Excel العام على Sheetين فقط: الحسابات والعُهَد." }
        val accountSheet = entries["xl/worksheets/sheet1.xml"] ?: error("ورقة الحسابات مفقودة.")
        val custodySheet = entries["xl/worksheets/sheet2.xml"] ?: error("ورقة العُهَد مفقودة.")
        val accountUri = materializeSingleSheet(context, accountSheet, "accounts-import")
        val custodyUri = materializeSingleSheet(context, custodySheet, "custody-import")
        try {
            val accountPreview = ExcelDataManager.previewImport(context, accountUri).getOrThrow()
            val custodyPreview = CustodyExcelDataManager.previewImport(context, custodyUri).getOrThrow()
            check(accountPreview.isValid) { accountPreview.errors.joinToString("\n") }
            check(custodyPreview.isValid) { custodyPreview.errors.joinToString("\n") }
            val account = ExcelDataManager.import(context, accountUri).getOrThrow()
            val custody = CustodyExcelDataManager.import(context, custodyUri).getOrThrow()
            ImportSummary(account, custody)
        } finally {
            File(accountUri.path ?: "").delete()
            File(custodyUri.path ?: "").delete()
        }
    }

    private fun materializeSingleSheet(context: Context, sheet: ByteArray, prefix: String): Uri {
        val file = File.createTempFile("$prefix-", ".xlsx", context.cacheDir)
        context.contentResolver.openOutputStream(Uri.fromFile(file))!!.use { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                entry(zip, "[Content_Types].xml", contentTypes())
                entry(zip, "_rels/.rels", rootRels())
                entry(zip, "xl/workbook.xml", workbookXml("بيانات"))
                entry(zip, "xl/_rels/workbook.xml.rels", workbookRels())
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); zip.write(sheet); zip.closeEntry()
            }
        }
        return Uri.fromFile(file)
    }

    private fun writeCombinedWorkbook(out: java.io.OutputStream, accountSheet: ByteArray, custodySheet: ByteArray) {
        ZipOutputStream(out.buffered()).use { zip ->
            entry(zip, "[Content_Types].xml", combinedContentTypes())
            entry(zip, "_rels/.rels", rootRels())
            entry(zip, "xl/workbook.xml", combinedWorkbook())
            entry(zip, "xl/_rels/workbook.xml.rels", combinedWorkbookRels())
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); zip.write(accountSheet); zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet2.xml")); zip.write(custodySheet); zip.closeEntry()
        }
    }

    private fun sheetNames(workbook: ByteArray): List<String> {
        val text = workbook.toString(Charsets.UTF_8)
        return Regex("<sheet\\b[^>]*name=\\\"([^\\\"]+)\\\"").findAll(text).map { it.groupValues[1] }.toList()
    }

    private fun readZip(input: InputStream): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val out = ByteArrayOutputStream()
                zip.copyTo(out)
                result[entry.name] = out.toByteArray()
            }
        }
        return result
    }

    private fun entry(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry()
    }

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""
    private fun combinedContentTypes() = """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""
    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    private fun workbookXml(name: String) = """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="$name" sheetId="1" r:id="rId1"/></sheets></workbook>"""
    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""
    private fun combinedWorkbook() = """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="بيانات الحسابات" sheetId="1" r:id="rId1"/><sheet name="بيانات العُهَد" sheetId="2" r:id="rId2"/></sheets></workbook>"""
    private fun combinedWorkbookRels() = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/></Relationships>"""
}
