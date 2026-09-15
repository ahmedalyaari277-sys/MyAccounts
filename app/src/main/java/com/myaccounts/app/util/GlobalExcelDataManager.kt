package com.myaccounts.app.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Whole-app Excel transfer: exactly two sheets, one for accounts and one for custody. */
object GlobalExcelDataManager {
    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val SUGGESTED_FILE_NAME = "MyAccounts_All_Data.xlsx"
    data class ExportSummary(val accountPeople:Int,val accountAccounts:Int,val accountTransactions:Int,val custodyCustodies:Int,val custodyPeople:Int,val custodyAccounts:Int,val custodyTransactions:Int)
    data class ImportPreview(val account:ExcelDataManager.ImportPreview,val custody:CustodyTwoSheetExcelDataManager.ImportPreview){ val isValid:Boolean get()=account.isValid&&custody.isValid }
    data class ImportSummary(val account:ExcelDataManager.ImportSummary,val custody:CustodyExcelDataManager.ImportSummary)

    private fun fileUri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    suspend fun exportActive(context:Context, uri:Uri):Result<ExportSummary> = runCatching {
        val accountFile=File.createTempFile("myaccounts-account-excel-",".xlsx",context.cacheDir)
        val custodyFile=File.createTempFile("myaccounts-custody-excel-",".xlsx",context.cacheDir)
        try {
            val account=ExcelDataManager.exportActive(context,fileUri(context,accountFile)).getOrThrow()
            val custody=CustodyTwoSheetExcelDataManager.exportActive(context,fileUri(context,custodyFile)).getOrThrow()
            val ae=readZip(accountFile.inputStream()); val ce=readZip(custodyFile.inputStream())
            val accountSheet=ae["xl/worksheets/sheet1.xml"]?:error("بيانات الحسابات غير موجودة.")
            val custodySheet=ce["xl/worksheets/sheet1.xml"]?:error("بيانات العُهَد غير موجودة.")
            context.contentResolver.openOutputStream(uri)?.use{writeCombinedWorkbook(it,accountSheet,custodySheet)}?:error("تعذر فتح ملف Excel للكتابة.")
            ExportSummary(account.people,account.accounts,account.transactions,custody.custodies,custody.people,custody.accounts,custody.transactions)
        } finally { accountFile.delete(); custodyFile.delete() }
    }

    fun previewImport(context:Context,uri:Uri):Result<ImportPreview> = runCatching {
        val entries=readZip(context.contentResolver.openInputStream(uri)?:error("تعذر فتح ملف Excel."))
        val sheets=sheetNames(entries["xl/workbook.xml"]?:error("ملف Excel غير صالح."))
        check(sheets.size==2){"يجب أن يحتوي ملف Excel العام على Sheetين فقط: الحسابات والعُهَد."}
        check(sheets[0]=="بيانات الحسابات" && sheets[1]=="بيانات العُهَد"){"ترتيب وأسماء أوراق Excel العام غير صحيحة."}
        val accountSheet=entries["xl/worksheets/sheet1.xml"]?:error("ورقة الحسابات مفقودة.")
        val custodySheet=entries["xl/worksheets/sheet2.xml"]?:error("ورقة العُهَد مفقودة.")
        val (accountUri,accountFile)=materializeSingleSheet(context,accountSheet,"accounts-preview")
        val (custodyUri,custodyFile)=materializeSingleSheet(context,custodySheet,"custody-preview")
        try { ImportPreview(ExcelDataManager.previewImport(context,accountUri).getOrThrow(),CustodyTwoSheetExcelDataManager.previewImport(context,custodyUri).getOrThrow()) }
        finally { accountFile.delete(); custodyFile.delete() }
    }

    suspend fun import(context:Context,uri:Uri):Result<ImportSummary> = runCatching {
        val entries=readZip(context.contentResolver.openInputStream(uri)?:error("تعذر فتح ملف Excel."))
        val sheets=sheetNames(entries["xl/workbook.xml"]?:error("ملف Excel غير صالح."))
        check(sheets.size==2){"يجب أن يحتوي ملف Excel العام على Sheetين فقط: الحسابات والعُهَد."}
        check(sheets[0]=="بيانات الحسابات" && sheets[1]=="بيانات العُهَد"){"ترتيب وأسماء أوراق Excel العام غير صحيحة."}
        val accountSheet=entries["xl/worksheets/sheet1.xml"]?:error("ورقة الحسابات مفقودة.")
        val custodySheet=entries["xl/worksheets/sheet2.xml"]?:error("ورقة العُهَد مفقودة.")
        val (accountUri,accountFile)=materializeSingleSheet(context,accountSheet,"accounts-import")
        val (custodyUri,custodyFile)=materializeSingleSheet(context,custodySheet,"custody-import")
        val snapshotFile=File.createTempFile("myaccounts-global-import-snapshot-",".myaccounts",context.cacheDir)
        val snapshotUri=fileUri(context,snapshotFile)
        try {
            val ap=ExcelDataManager.previewImport(context,accountUri).getOrThrow()
            val cp=CustodyTwoSheetExcelDataManager.previewImport(context,custodyUri).getOrThrow()
            check(ap.isValid){ap.errors.joinToString("\n")}
            check(cp.isValid){cp.errors.joinToString("\n")}
            com.myaccounts.app.data.custody.CustodyAttachmentStore.ensureSchema(context)
            val snapshotResult=ScopedBackupManager.createBackup(context,snapshotUri,BackupScope.ALL)
            if(snapshotResult.isFailure && snapshotResult.exceptionOrNull()?.message != "لم يتم العثور على أي بيانات لحفظها في النسخة الاحتياطية.") {
                snapshotResult.getOrThrow()
            }
            try {
                ImportSummary(ExcelDataManager.import(context,accountUri).getOrThrow(),CustodyTwoSheetExcelDataManager.import(context,custodyUri).getOrThrow())
            } catch (failure: Throwable) {
                val rollback=ScopedBackupManager.restoreBackup(context,snapshotUri,BackupScope.ALL)
                if(rollback.isFailure) {
                    throw IllegalStateException("فشل استيراد ملف Excel وفشلت محاولة التراجع عن التغييرات: ${rollback.exceptionOrNull()?.message ?: "خطأ غير معروف"}", failure)
                }
                throw failure
            }
        } finally {
            accountFile.delete()
            custodyFile.delete()
            snapshotFile.delete()
        }
    }

    private fun materializeSingleSheet(context:Context,sheet:ByteArray,prefix:String):Pair<Uri,File> {
        val file=File.createTempFile("$prefix-",".xlsx",context.cacheDir)
        file.outputStream().use{out->ZipOutputStream(out.buffered()).use{zip->{
            entry(zip,"[Content_Types].xml",contentTypes())
            entry(zip,"_rels/.rels",rootRels())
            entry(zip,"xl/workbook.xml",workbookXml())
            entry(zip,"xl/_rels/workbook.xml.rels",workbookRels())
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"));zip.write(sheet);zip.closeEntry()
        }}}
        return fileUri(context,file) to file
    }

    private fun writeCombinedWorkbook(out:java.io.OutputStream,accountSheet:ByteArray,custodySheet:ByteArray){
        val accounts = normalizeWorksheetXml(accountSheet)
        val custody = normalizeWorksheetXml(custodySheet)
        ZipOutputStream(out.buffered()).use { zip ->
            entry(zip,"[Content_Types].xml",combinedContentTypes())
            entry(zip,"_rels/.rels",rootRels())
            entry(zip,"xl/workbook.xml",combinedWorkbook())
            entry(zip,"xl/_rels/workbook.xml.rels",combinedWorkbookRels())
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); zip.write(accounts); zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet2.xml")); zip.write(custody); zip.closeEntry()
        }
    }

    private fun normalizeWorksheetXml(sheet:ByteArray):ByteArray {
        val text = sheet.toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
        require(text.startsWith("<?xml") || text.startsWith("<worksheet")) { "ورقة Excel غير صالحة." }
        require(text.contains("<worksheet")) { "ورقة Excel لا تحتوي على عنصر worksheet." }
        return text.toByteArray(Charsets.UTF_8)
    }
    private fun sheetNames(workbook:ByteArray): List<String> = Regex("<sheet\\b[^>]*name=\\\"([^\\\"]+)\\\"").findAll(workbook.toString(Charsets.UTF_8)).map{it.groupValues[1]}.toList()
    private fun readZip(input:InputStream):Map<String,ByteArray> = buildMap{ZipInputStream(input.buffered()).use{zip->while(true){val e=zip.nextEntry?:break;if(!e.isDirectory){val out=ByteArrayOutputStream();zip.copyTo(out);put(e.name,out.toByteArray())}}}}
    private fun entry(zip:ZipOutputStream,name:String,value:String){zip.putNextEntry(ZipEntry(name));zip.write(value.toByteArray(Charsets.UTF_8));zip.closeEntry()}
    private fun contentTypes()="""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""
    private fun combinedContentTypes()="""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""
    private fun rootRels()="""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    private fun workbookXml()="""<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="بيانات" sheetId="1" r:id="rId1"/></sheets></workbook>"""
    private fun workbookRels()="""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""
    private fun combinedWorkbook()="""<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="بيانات الحسابات" sheetId="1" r:id="rId1"/><sheet name="بيانات العُهَد" sheetId="2" r:id="rId2"/></sheets></workbook>"""
    private fun combinedWorkbookRels()="""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/></Relationships>"""
}