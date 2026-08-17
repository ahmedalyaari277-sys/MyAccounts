package com.myaccounts.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.myaccounts.app.data.reports.CurrencyReportPersonRow
import com.myaccounts.app.data.reports.CurrencyReportSummary
import com.myaccounts.app.data.reports.GeneralReportTransactionRow
import com.myaccounts.app.data.reports.PersonCurrencySummaryRow
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object GeneralReportsPdfExporter {
    fun exportPeopleReport(context: Context, currency: String, summary: CurrencyReportSummary, people: List<CurrencyReportPersonRow>, startDateMillis: Long?, endDateMillisExclusive: Long?): Result<String> = try {
        val document = PdfDocument(); val chunks = if (people.isEmpty()) listOf(emptyList()) else people.chunked(24)
        chunks.forEachIndexed { index, chunk ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(595,842,index+1).create()); var y = header(page.canvas,"تقرير الأشخاص",currency,startDateMillis,endDateMillisExclusive)
            val heading = paint(13, Color.BLACK, true); val red=paint(10,Color.rgb(190,35,35),true); val green=paint(10,Color.rgb(0,125,70),true); val text=paint(10,Color.BLACK,false); val line=paint(1,Color.DKGRAY,false)
            if(index==0){ page.canvas.drawText("ملخص: عليه ${formatAmount(summary.totalReceivableMinor)} | له ${formatAmount(summary.totalPayableMinor)} | الرصيد ${balanceText(summary.balanceMinor)}",560f,y,text); y+=28f }
            page.canvas.drawText("الرصيد",540f,y,heading); page.canvas.drawText("له",420f,y,green); page.canvas.drawText("عليه",330f,y,red); page.canvas.drawText("العملة",250f,y,heading); page.canvas.drawText("الشخص",100f,y,heading); y+=8f; page.canvas.drawLine(35f,y,560f,y,line); y+=18f
            chunk.forEach { p -> page.canvas.drawText(formatAmount(p.balanceMinor),540f,y,balancePaint(p.balanceMinor)); page.canvas.drawText(formatAmount(p.totalPayableMinor),420f,y,green); page.canvas.drawText(formatAmount(p.totalReceivableMinor),330f,y,red); page.canvas.drawText(currencyName(currency),250f,y,text); page.canvas.drawText(p.personName.take(24),100f,y,text); y+=9f; page.canvas.drawLine(35f,y,560f,y,line); y+=17f }
            if(chunk.isEmpty()) page.canvas.drawText("لا توجد بيانات ضمن الفترة المحددة.",560f,y,text)
            document.finishPage(page)
        }
        save(context,document,"MyAccounts_تقرير_الأشخاص_${currency}_${timestamp()}.pdf")
    } catch(e:Exception){Result.failure(e)}

    fun exportDetailedReport(context: Context, currency: String, transactions: List<GeneralReportTransactionRow>, startDateMillis: Long?, endDateMillisExclusive: Long?): Result<String> = try {
        val document=PdfDocument(); val chunks=if(transactions.isEmpty()) listOf(emptyList()) else transactions.chunked(28)
        chunks.forEachIndexed { index, chunk ->
            val page=document.startPage(PdfDocument.PageInfo.Builder(595,842,index+1).create()); var y=header(page.canvas,"التقرير التفصيلي للعمليات",currency,startDateMillis,endDateMillisExclusive)
            val heading=paint(10,Color.BLACK,true); val red=paint(9,Color.rgb(190,35,35),true); val green=paint(9,Color.rgb(0,125,70),true); val text=paint(9,Color.BLACK,false); val line=paint(1,Color.DKGRAY,false)
            page.canvas.drawText("عليه",540f,y,red); page.canvas.drawText("له",450f,y,green); page.canvas.drawText("البيان",335f,y,heading); page.canvas.drawText("العملة",230f,y,heading); page.canvas.drawText("الشخص",125f,y,heading); page.canvas.drawText("التاريخ",55f,y,heading); y+=8f; page.canvas.drawLine(35f,y,560f,y,line); y+=18f
            chunk.forEach { t -> page.canvas.drawText(if(t.type=="RECEIVABLE")formatAmount(t.amountMinor) else "—",540f,y,if(t.type=="RECEIVABLE")red else text); page.canvas.drawText(if(t.type=="PAYABLE")formatAmount(t.amountMinor) else "—",450f,y,if(t.type=="PAYABLE")green else text); page.canvas.drawText(t.description.ifBlank{"—"}.take(22),335f,y,text); page.canvas.drawText(currencyName(t.currencyCode),230f,y,text); page.canvas.drawText(t.personName.take(18),125f,y,text); page.canvas.drawText(formatDate(t.transactionDate),55f,y,text); y+=8f; page.canvas.drawLine(35f,y,560f,y,line); y+=16f }
            if(chunk.isEmpty()) page.canvas.drawText("لا توجد عمليات ضمن الفترة المحددة.",560f,y,text)
            document.finishPage(page)
        }
        save(context,document,"MyAccounts_التقرير_التفصيلي_${currency}_${timestamp()}.pdf")
    } catch(e:Exception){Result.failure(e)}

    fun exportSummaryReport(context: Context, currency: String, rows: List<PersonCurrencySummaryRow>, startDateMillis: Long?, endDateMillisExclusive: Long?): Result<String> = try {
        val document=PdfDocument(); val chunks=if(rows.isEmpty()) listOf(emptyList()) else rows.chunked(20)
        chunks.forEachIndexed { index, chunk ->
            val page=document.startPage(PdfDocument.PageInfo.Builder(595,842,index+1).create()); var y=header(page.canvas,"ملخص تقرير الأشخاص",currency,startDateMillis,endDateMillisExclusive)
            val heading=paint(9,Color.BLACK,true); val red=paint(8,Color.rgb(190,35,35),true); val green=paint(8,Color.rgb(0,125,70),true); val text=paint(8,Color.BLACK,false); val line=paint(1,Color.DKGRAY,false)
            page.canvas.drawText("الرصيد",540f,y,heading); page.canvas.drawText("له",450f,y,green); page.canvas.drawText("عليه",370f,y,red); page.canvas.drawText("الفترة الأولى: له ← عليه",275f,y,heading); page.canvas.drawText("الفترة الأخيرة: له ← عليه",180f,y,heading); page.canvas.drawText("الشخص",75f,y,heading); y+=8f; page.canvas.drawLine(35f,y,560f,y,line); y+=17f
            chunk.forEach { r -> page.canvas.drawText(formatAmount(r.balanceMinor),540f,y,balancePaint(r.balanceMinor)); page.canvas.drawText(formatAmount(r.totalPayableMinor),450f,y,green); page.canvas.drawText(formatAmount(r.totalReceivableMinor),370f,y,red); page.canvas.drawText(firstToFirstRange(r.firstPayableDate,r.firstReceivableDate),275f,y,text); page.canvas.drawText(lastToLastRange(r.lastPayableDate,r.lastReceivableDate),180f,y,text); page.canvas.drawText(r.personName.take(18),75f,y,text); y+=8f; page.canvas.drawLine(35f,y,560f,y,line); y+=15f }
            if(chunk.isEmpty()) page.canvas.drawText("لا توجد بيانات ضمن الفترة المحددة.",560f,y,text)
            document.finishPage(page)
        }
        save(context,document,"MyAccounts_ملخص_الأشخاص_${currency}_${timestamp()}.pdf")
    } catch(e:Exception){Result.failure(e)}

    private fun header(canvas: android.graphics.Canvas,title:String,currency:String,start:Long?,end:Long?):Float { val titlePaint=paint(20,Color.BLACK,true); val text=paint(11,Color.BLACK,false); var y=42f; canvas.drawText("MyAccounts - $title",560f,y,titlePaint); y+=25f; canvas.drawText("العملة: ${currencyName(currency)}",560f,y,text); y+=18f; canvas.drawText("الفترة: ${formatDateRange(start,end)}",560f,y,text); y+=18f; canvas.drawText("تاريخ إصدار التقرير: ${formatDate(System.currentTimeMillis())}",560f,y,text); y+=15f; canvas.drawLine(35f,y,560f,y,paint(1,Color.DKGRAY,false)); return y+22f }
    private fun paint(size:Int,color:Int,bold:Boolean)=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;textSize=size.toFloat();textAlign=Paint.Align.RIGHT;if(bold)typeface=Typeface.DEFAULT_BOLD}
    private fun balancePaint(v:Long)=paint(9,when{v>0->Color.rgb(190,35,35);v<0->Color.rgb(0,125,70);else->Color.DKGRAY},true)
    private fun balanceText(v:Long)=when{v>0->"${formatAmount(v)} (عليه)";v<0->"${formatAmount(-v)} (له)";else->"0 (متوازن)"}
    private fun currencyName(c:String)=when(c){"YER"->"الريال اليمني";"SAR"->"الريال السعودي";"USD"->"الدولار الأمريكي";else->c}
    private fun formatAmount(v:Long)=BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
    private fun formatDate(v:Long)=SimpleDateFormat("dd/MM/yyyy",Locale("ar")).format(Date(v))
    private fun formatDateRange(s:Long?,e:Long?)=if(s==null&&e==null)"كل الحساب" else "${s?.let(::formatDate)?:"غير محدد"} - ${e?.let{formatDate(addDays(it,-1))}?:"غير محدد"}"
    private fun firstToFirstRange(firstPayable:Long?,firstReceivable:Long?)=if(firstPayable==null&&firstReceivable==null)"—" else "${firstPayable?.let(::formatDate)?:"—"} - ${firstReceivable?.let(::formatDate)?:"—"}"
    private fun lastToLastRange(lastPayable:Long?,lastReceivable:Long?)=if(lastPayable==null&&lastReceivable==null)"—" else "${lastPayable?.let(::formatDate)?:"—"} - ${lastReceivable?.let(::formatDate)?:"—"}"
    private fun addDays(v:Long,d:Int)=Calendar.getInstance().apply{timeInMillis=v;add(Calendar.DAY_OF_MONTH,d)}.timeInMillis
    private fun timestamp()=SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())
    private fun save(context:Context,document:PdfDocument,fileName:String):Result<String>{return try{if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){val values=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,fileName);put(MediaStore.Downloads.MIME_TYPE,"application/pdf");put(MediaStore.Downloads.RELATIVE_PATH,"${Environment.DIRECTORY_DOWNLOADS}/MyAccounts")};val resolver=context.contentResolver;val uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:throw IllegalStateException("تعذر إنشاء ملف التقرير.");try{resolver.openOutputStream(uri).use{output->if(output==null)throw IllegalStateException("تعذر فتح ملف التقرير.");document.writeTo(output)}}catch(e:Exception){resolver.delete(uri,null,null);throw e};Result.success("تم حفظ التقرير في مجلد التنزيلات/MyAccounts")}else{val dir=File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"MyAccounts");if(!dir.exists()&&!dir.mkdirs())throw IllegalStateException("تعذر إنشاء مجلد التقرير.");val file=File(dir,fileName);FileOutputStream(file).use{document.writeTo(it)};Result.success(file.absolutePath)}}catch(e:Exception){Result.failure(e)}}
    private fun safeFileName(v:String)=v.replace(Regex("[^A-Za-z0-9_-]+"),"_").take(50)
}