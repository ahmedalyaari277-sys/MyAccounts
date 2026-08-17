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
    fun exportPersonReport(context: Context, summary: PersonReportSummary, transactions: List<PersonReportTransaction>, startDateMillis: Long?, endDateMillisExclusive: Long?): Result<String> = try {
        val fileName = "MyAccounts_Person_Report_${safeFileName(summary.personName)}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.xlsx"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME,fileName); put(MediaStore.Downloads.MIME_TYPE,XLSX_MIME_TYPE); put(MediaStore.Downloads.RELATIVE_PATH,"${Environment.DIRECTORY_DOWNLOADS}/MyAccounts") }
            val resolver=context.contentResolver; val uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:throw IllegalStateException("تعذر إنشاء ملف Excel.")
            try { resolver.openOutputStream(uri).use { output -> if(output==null)throw IllegalStateException("تعذر فتح ملف Excel."); createWorkbook(output,summary,transactions,startDateMillis,endDateMillisExclusive) } }
            catch(e:Exception){resolver.delete(uri,null,null);throw e}
            Result.success("تم حفظ تقرير Excel في مجلد التنزيلات/MyAccounts")
        } else {
            val directory=File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"MyAccounts"); if(!directory.exists()&&!directory.mkdirs())throw IllegalStateException("تعذر إنشاء مجلد التقرير.")
            val file=File(directory,fileName); FileOutputStream(file).use{createWorkbook(it,summary,transactions,startDateMillis,endDateMillisExclusive)}; Result.success(file.absolutePath)
        }
    } catch(e:Exception){Result.failure(e)}

    private fun createWorkbook(outputStream:OutputStream,summary:PersonReportSummary,transactions:List<PersonReportTransaction>,startDateMillis:Long?,endDateMillisExclusive:Long?){ZipOutputStream(outputStream).use{zip->{writeEntry(zip,"[Content_Types].xml",contentTypesXml());writeEntry(zip,"_rels/.rels",rootRelationshipsXml());writeEntry(zip,"xl/workbook.xml",workbookXml());writeEntry(zip,"xl/_rels/workbook.xml.rels",workbookRelationshipsXml());writeEntry(zip,"xl/styles.xml",stylesXml());writeEntry(zip,"xl/worksheets/sheet1.xml",worksheetXml(summary,transactions,startDateMillis,endDateMillisExclusive))}}
    private fun writeEntry(zip:ZipOutputStream,path:String,content:String){zip.putNextEntry(ZipEntry(path));zip.write(content.toByteArray(Charsets.UTF_8));zip.closeEntry()}
    private fun contentTypesXml()="""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""
    private fun rootRelationshipsXml()="""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    private fun workbookXml()="""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="تقرير الشخص" sheetId="1" r:id="rId1"/></sheets></workbook>"""
    private fun workbookRelationshipsXml()="""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
    private fun stylesXml()="""
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <fonts count="4"><font><sz val="11"/><name val="Arial"/></font><font><b/><sz val="12"/><name val="Arial"/></font><font><b/><sz val="11"/><name val="Arial"/></font><font><color rgb="FFC02323"/><sz val="11"/><name val="Arial"/></font></fonts>
          <fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFEFEFEF"/><bgColor indexed="64"/></patternFill></fill></fills>
          <borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"/><right style="thin"/><top style="thin"/><bottom style="thin"/><diagonal/></border></borders>
          <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
          <cellXfs count="6"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="2" fillId="2" borderId="1" xfId="0"/><xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0"/><xf numFmtId="0" fontId="3" fillId="0" borderId="1" xfId="0"/><xf numFmtId="4" fontId="2" fillId="0" borderId="1" xfId="0"/></cellXfs>
        </styleSheet>
    """.trimIndent()

    private fun worksheetXml(summary:PersonReportSummary,transactions:List<PersonReportTransaction>,startDateMillis:Long?,endDateMillisExclusive:Long?):String{
        val rows=mutableListOf<String>(); var rowNumber=1
        rows+=row(rowNumber++,listOf(cell("تقرير حساب شخصي",1)))
        rows+=row(rowNumber++,listOf(cell("اسم الشخص",2),cell(summary.personName,0)))
        rows+=row(rowNumber++,listOf(cell("الهاتف",2),cell(summary.phone.ifBlank{"غير مسجل"},0)))
        rows+=row(rowNumber++,listOf(cell("العنوان",2),cell(summary.address.ifBlank{"غير مسجل"},0)))
        rows+=row(rowNumber++,listOf(cell("العملة",2),cell(currencyName(summary.currencyCode),0)))
        rows+=row(rowNumber++,listOf(cell("الفترة",2),cell(formatDateRange(startDateMillis,endDateMillisExclusive),0)))
        rows+=row(rowNumber++,listOf(cell("تاريخ إصدار التقرير",2),cell(formatDate(System.currentTimeMillis()),0)))
        rows+=row(rowNumber++,listOf(cell("ملخص الحساب",1)))
        rows+=row(rowNumber++,listOf(cell("الرصيد الافتتاحي",2),numericCell(summary.openingBalanceMinor,5)))
        rows+=row(rowNumber++,listOf(cell("إجمالي عليه خلال الفترة",2),numericCell(summary.periodReceivableMinor,4)))
        rows+=row(rowNumber++,listOf(cell("إجمالي له خلال الفترة",2),numericCell(summary.periodPayableMinor,5)))
        rows+=row(rowNumber++,listOf(cell("الرصيد الختامي",2),numericCell(summary.closingBalanceMinor,5)))
        rows+=row(rowNumber++,listOf(cell("عدد العمليات",2),integerCell(summary.transactionCount.toLong(),0)))
        rows+=row(rowNumber++,listOf(cell("كشف الحساب",1)))
        val headerRow=rowNumber
        rows+=row(rowNumber++,listOf(cell("التاريخ",3),cell("البيان",3),cell("عليه",3),cell("له",3),cell("الرصيد",3)))
        transactions.forEach{t->rows+=row(rowNumber++,listOf(cell(formatDate(t.transactionDate),0),cell(t.description.ifBlank{"—"},0),if(t.type=="RECEIVABLE")numericCell(t.amountMinor,4) else cell("—",0),if(t.type=="PAYABLE")numericCell(t.amountMinor,5) else cell("—",0),numericCell(t.balanceMinor,5)))}
        if(transactions.isEmpty())rows+=row(rowNumber++,listOf(cell("لا توجد عمليات خلال الفترة المحددة.",0)))
        val lastRow=rowNumber-1
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0" rightToLeft="1"/></sheetViews><sheetFormatPr defaultRowHeight="20"/><cols><col min="1" max="1" width="16" customWidth="1"/><col min="2" max="2" width="44" customWidth="1"/><col min="3" max="5" width="19" customWidth="1"/></cols><sheetData>${rows.joinToString("\n")}</sheetData><autoFilter ref="A$headerRow:E$lastRow"/></worksheet>"""
    }

    private fun row(rowNumber:Int,cells:List<String>)="<row r=\"$rowNumber\">${cells.joinToString("\n")}</row>"
    private fun cell(value:String,style:Int=0)="<c t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">${xmlEscape(value)}</t></is></c>"
    private fun numericCell(amountMinor:Long,style:Int=0)="<c t=\"n\" s=\"$style\"><v>${BigDecimal(amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString()}</v></c>"
    private fun integerCell(value:Long,style:Int=0)="<c t=\"n\" s=\"$style\"><v>$value</v></c>"
    private fun currencyName(code:String)=when(code){"YER"->"الريال اليمني";"SAR"->"الريال السعودي";"USD"->"الدولار الأمريكي";else->code}
    private fun formatDate(millis:Long)=SimpleDateFormat("dd/MM/yyyy",Locale("ar")).format(Date(millis))
    private fun formatDateRange(start:Long?,end:Long?)=if(start==null&&end==null)"كل الحساب" else "${start?.let(::formatDate)?:"غير محدد"} - ${end?.let{formatDate(addDays(it,-1))}?:"غير محدد"}"
    private fun addDays(millis:Long,days:Int)=Calendar.getInstance().apply{timeInMillis=millis;add(Calendar.DAY_OF_MONTH,days)}.timeInMillis
    private fun safeFileName(value:String)=value.replace(Regex("[\\\\/:*?\"<>|]"),"_").replace(Regex("\\s+"),"_").take(60).ifBlank{"Person"}
    private fun xmlEscape(value:String)=value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;").filter{it=='\n'||it=='\r'||it=='\t'||it>=' '}
    private const val XLSX_MIME_TYPE="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
}
