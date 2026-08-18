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
        val workbook = worksheet(summary, transactions, startDateMillis, endDateMillisExclusive)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME,fileName); put(MediaStore.Downloads.MIME_TYPE,XLSX_MIME_TYPE); put(MediaStore.Downloads.RELATIVE_PATH,"${Environment.DIRECTORY_DOWNLOADS}/MyAccounts") }
            val resolver=context.contentResolver; val uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:throw IllegalStateException("تعذر إنشاء ملف Excel.")
            try { resolver.openOutputStream(uri).use{out->if(out==null)throw IllegalStateException("تعذر فتح ملف Excel.");writeWorkbook(out,workbook)} } catch(e:Exception){resolver.delete(uri,null,null);throw e}
            Result.success("تم حفظ تقرير Excel في مجلد التنزيلات/MyAccounts")
        } else { val dir=File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"MyAccounts");if(!dir.exists()&&!dir.mkdirs())throw IllegalStateException("تعذر إنشاء مجلد التقرير.");val file=File(dir,fileName);FileOutputStream(file).use{writeWorkbook(it,workbook)};Result.success(file.absolutePath) }
    } catch(e:Exception){Result.failure(e)}
    private fun writeWorkbook(out:OutputStream,sheet:String){ZipOutputStream(out).use{zip->entry(zip,"[Content_Types].xml",contentTypes());entry(zip,"_rels/.rels",rootRels());entry(zip,"xl/workbook.xml",workbook());entry(zip,"xl/_rels/workbook.xml.rels",workbookRels());entry(zip,"xl/styles.xml",styles());entry(zip,"xl/worksheets/sheet1.xml",sheet)}}
    private fun entry(zip:ZipOutputStream,path:String,value:String){zip.putNextEntry(ZipEntry(path));zip.write(value.toByteArray(Charsets.UTF_8));zip.closeEntry()}
    private fun contentTypes()="""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""
    private fun rootRels()="""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    private fun workbook()="""<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="تقرير الشخص" sheetId="1" r:id="rId1"/></sheets></workbook>"""
    private fun workbookRels()="""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
    private fun styles()="""<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="+0.00;-0.00;0.00"/></numFmts><fonts count="5"><font><sz val="11"/><name val="Arial"/></font><font><b/><sz val="14"/><name val="Arial"/></font><font><b/><sz val="11"/><name val="Arial"/></font><font><color rgb="FF00804A"/><b/><sz val="11"/><name val="Arial"/></font><font><color rgb="FFC02323"/><b/><sz val="11"/><name val="Arial"/></font></fonts><fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFEAF3EE"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFCEBEC"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"/><right style="thin"/><top style="thin"/><bottom style="thin"/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="8"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="3" fillId="2" borderId="1" xfId="0"/><xf numFmtId="0" fontId="4" fillId="3" borderId="1" xfId="0"/><xf numFmtId="164" fontId="3" fillId="2" borderId="1" xfId="0"/><xf numFmtId="164" fontId="4" fillId="3" borderId="1" xfId="0"/><xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0"/></cellXfs></styleSheet>"""
    private fun worksheet(s:PersonReportSummary,t:List<PersonReportTransaction>,start:Long?,end:Long?):String{val rows=mutableListOf<String>();var n=1;rows+=row(n++,listOf(cell("تقرير حساب شخصي",1)));rows+=row(n++,listOf(cell("الاسم: ${s.personName}",2),cell("الهاتف: ${s.phone.ifBlank{"غير مسجل"}}",2),cell("العملة: ${currencyName(s.currencyCode)}",2)));rows+=row(n++,listOf(cell("العنوان: ${s.address.ifBlank{"غير مسجل"}}",2),cell("الفترة: ${formatDateRange(start,end)}",2),cell("إصدار: ${formatDate(System.currentTimeMillis())}",2)));rows+=row(n++,listOf(cell("ملخص الحساب",2)));rows+=row(n++,listOf(cell("الرصيد الافتتاحي"),number(s.openingBalanceMinor,7),cell("عليه",3),number(s.periodReceivableMinor,5),cell("له",4),number(-s.periodPayableMinor,6),cell("الرصيد الختامي"),number(s.closingBalanceMinor,7)));rows+=row(n++,listOf(cell("كشف الحساب",2)));val header=n;rows+=row(n++,listOf(cell("التاريخ",2),cell("البيان",2),cell("عليه",3),cell("له",4),cell("الرصيد",2)));t.forEach{r->rows+=row(n++,listOf(cell(formatDate(r.transactionDate)),cell(r.description.ifBlank{"—"}),if(r.type=="RECEIVABLE")number(r.amountMinor,5) else cell("—"),if(r.type=="PAYABLE")number(-r.amountMinor,6) else cell("—"),number(r.balanceMinor,7)))};if(t.isEmpty())rows+=row(n++,listOf(cell("لا توجد عمليات خلال الفترة المحددة.")));return """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0" rightToLeft="1"/></sheetViews><sheetFormatPr defaultRowHeight="20"/><cols><col min="1" max="1" width="16" customWidth="1"/><col min="2" max="2" width="42" customWidth="1"/><col min="3" max="5" width="19" customWidth="1"/></cols><sheetData>${rows.joinToString("\n")}</sheetData><autoFilter ref="A$header:E${n-1}"/></worksheet>"""}
    private fun row(n:Int,cells:List<String>)="<row r=\"$n\">${cells.joinToString("")}</row>"
    private fun cell(v:String,style:Int=0)="<c t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">${escape(v)}</t></is></c>"
    private fun number(v:Long,style:Int)="<c t=\"n\" s=\"$style\"><v>${BigDecimal(v).movePointLeft(2).toPlainString()}</v></c>"
    private fun currencyName(c:String)=when(c){"YER"->"الريال اليمني";"SAR"->"الريال السعودي";"USD"->"الدولار الأمريكي";else->c}
    private fun formatDate(v:Long)=SimpleDateFormat("dd/MM/yyyy",Locale("ar")).format(Date(v))
    private fun formatDateRange(s:Long?,e:Long?)=if(s==null&&e==null)"كل الحساب" else "${s?.let(::formatDate)?:"غير محدد"} - ${e?.let{formatDate(addDays(it,-1))}?:"غير محدد"}"
    private fun addDays(v:Long,d:Int)=Calendar.getInstance().apply{timeInMillis=v;add(Calendar.DAY_OF_MONTH,d)}.timeInMillis
    private fun safeFileName(v:String)=v.replace(Regex("[\\\\/:*?\"<>|]"),"_").replace(Regex("\\s+"),"_").take(60).ifBlank{"Person"}
    private fun escape(v:String)=v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;").filter{it=='\n'||it=='\r'||it=='\t'||it>=' '}
    private const val XLSX_MIME_TYPE="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
}
