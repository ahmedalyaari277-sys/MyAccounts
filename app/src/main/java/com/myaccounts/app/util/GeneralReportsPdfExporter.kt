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
    private const val LEFT = 35f
    private const val RIGHT = 560f
    private const val BOTTOM = 790f

    fun exportPeopleReport(context: Context, currency: String, summary: CurrencyReportSummary, people: List<CurrencyReportPersonRow>, startDateMillis: Long?, endDateMillisExclusive: Long?): Result<String> = try {
        val document = PdfDocument()
        val chunks = if (people.isEmpty()) listOf(emptyList()) else people.chunked(24)
        chunks.forEachIndexed { index, chunk ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
            val canvas = page.canvas
            var y = header(canvas, "تقرير الأشخاص", currency, startDateMillis, endDateMillisExclusive)
            val heading = paint(12f, true, Color.rgb(35,35,35)); val red = paint(10f,true,Color.rgb(190,35,35)); val green=paint(10f,true,Color.rgb(0,125,70)); val text=paint(10f,false,Color.rgb(35,35,35)); val line=linePaint()
            if (index == 0) { canvas.drawText("ملخص التقرير", RIGHT, y, heading); y += 20f; canvas.drawText("إجمالي عليه: ${formatAmount(summary.totalReceivableMinor)}", RIGHT, y, red); y += 17f; canvas.drawText("إجمالي له: ${formatAmount(summary.totalPayableMinor)}", RIGHT, y, green); y += 17f; canvas.drawText("الرصيد: ${balanceText(summary.balanceMinor)}", RIGHT, y, balancePaint(summary.balanceMinor)); y += 22f }
            canvas.drawLine(LEFT,y-8f,RIGHT,y-8f,line); canvas.drawText("الرصيد",540f,y,heading); canvas.drawText("له",420f,y,green); canvas.drawText("عليه",330f,y,red); canvas.drawText("العملة",250f,y,heading); canvas.drawText("الشخص",100f,y,heading); y += 10f; canvas.drawLine(LEFT,y,RIGHT,y,line); y += 18f
            chunk.forEach { person ->
                canvas.drawText(formatAmount(person.balanceMinor),540f,y,balancePaint(person.balanceMinor)); canvas.drawText(formatAmount(person.totalPayableMinor),420f,y,green); canvas.drawText(formatAmount(person.totalReceivableMinor),330f,y,red); canvas.drawText(currencyName(currency),250f,y,text); drawLimited(canvas,person.personName,100f,y,130f,text); y += 10f; canvas.drawLine(LEFT,y,RIGHT,y,line); y += 17f
            }
            if (chunk.isEmpty()) canvas.drawText("لا توجد بيانات ضمن الفترة المحددة.",RIGHT,y,text)
            footer(canvas,index+1,chunks.size,line,text); document.finishPage(page)
        }
        save(context,document,"MyAccounts_تقرير_الأشخاص_${currency}_${timestamp()}.pdf")
    } catch(e:Exception){ Result.failure(e) }

    fun exportDetailedReport(context: Context, currency: String, transactions: List<GeneralReportTransactionRow>, startDateMillis: Long?, endDateMillisExclusive: Long?): Result<String> = try {
        val document=PdfDocument(); val chunks=if(transactions.isEmpty()) listOf(emptyList()) else transactions.chunked(28)
        chunks.forEachIndexed { index, chunk ->
            val page=document.startPage(PdfDocument.PageInfo.Builder(595,842,index+1).create()); val canvas=page.canvas; var y=header(canvas,"التقرير التفصيلي للعمليات",currency,startDateMillis,endDateMillisExclusive)
            val heading=paint(10f,true,Color.rgb(35,35,35)); val red=paint(9f,true,Color.rgb(190,35,35)); val green=paint(9f,true,Color.rgb(0,125,70)); val text=paint(9f,false,Color.rgb(35,35,35)); val line=linePaint()
            canvas.drawText("عليه",540f,y,red); canvas.drawText("له",450f,y,green); canvas.drawText("البيان",335f,y,heading); canvas.drawText("العملة",230f,y,heading); canvas.drawText("الشخص",125f,y,heading); canvas.drawText("التاريخ",55f,y,heading); y+=8f; canvas.drawLine(LEFT,y,RIGHT,y,line); y+=18f
            chunk.forEach { t -> drawLimited(canvas,if(t.type=="RECEIVABLE")formatAmount(t.amountMinor) else "—",540f,y,85f,if(t.type=="RECEIVABLE")red else text); drawLimited(canvas,if(t.type=="PAYABLE")formatAmount(t.amountMinor) else "—",450f,y,80f,if(t.type=="PAYABLE")green else text); drawLimited(canvas,t.description.ifBlank{"—"},335f,y,105f,text); drawLimited(canvas,currencyName(t.currencyCode),230f,y,85f,text); drawLimited(canvas,t.personName,125f,y,95f,text); canvas.drawText(formatDate(t.transactionDate),55f,y,text); y+=9f; canvas.drawLine(LEFT,y,RIGHT,y,line); y+=16f }
            if(chunk.isEmpty()) canvas.drawText("لا توجد عمليات ضمن الفترة المحددة.",RIGHT,y,text)
            footer(canvas,index+1,chunks.size,line,text); document.finishPage(page)
        }
        save(context,document,"MyAccounts_التقرير_التفصيلي_${currency}_${timestamp()}.pdf")
    } catch(e:Exception){ Result.failure(e) }

    fun exportSummaryReport(context: Context, currency: String, rows: List<PersonCurrencySummaryRow>, startDateMillis: Long?, endDateMillisExclusive: Long?): Result<String> = try {
        val document=PdfDocument(); val chunks=if(rows.isEmpty()) listOf(emptyList()) else rows.chunked(20)
        chunks.forEachIndexed { index, chunk ->
            val page=document.startPage(PdfDocument.PageInfo.Builder(595,842,index+1).create()); val canvas=page.canvas; var y=header(canvas,"ملخص تقرير الأشخاص",currency,startDateMillis,endDateMillisExclusive)
            val heading=paint(9f,true,Color.rgb(35,35,35)); val red=paint(8f,true,Color.rgb(190,35,35)); val green=paint(8f,true,Color.rgb(0,125,70)); val text=paint(8f,false,Color.rgb(35,35,35)); val line=linePaint()
            canvas.drawText("الرصيد",540f,y,heading); canvas.drawText("له",450f,y,green); canvas.drawText("عليه",370f,y,red); canvas.drawText("الفترة الأولى: له ← عليه",275f,y,heading); canvas.drawText("الفترة الأخيرة: له ← عليه",180f,y,heading); canvas.drawText("الشخص",75f,y,heading); y+=8f; canvas.drawLine(LEFT,y,RIGHT,y,line); y+=17f
            chunk.forEach { r -> canvas.drawText(formatAmount(r.balanceMinor),540f,y,balancePaint(r.balanceMinor)); canvas.drawText(formatAmount(r.totalPayableMinor),450f,y,green); canvas.drawText(formatAmount(r.totalReceivableMinor),370f,y,red); drawLimited(canvas,firstToFirstRange(r.firstPayableDate,r.firstReceivableDate),275f,y,90f,text); drawLimited(canvas,lastToLastRange(r.lastPayableDate,r.lastReceivableDate),180f,y,90f,text); drawLimited(canvas,r.personName,75f,y,65f,text); y+=8f; canvas.drawLine(LEFT,y,RIGHT,y,line); y+=15f }
            if(chunk.isEmpty()) canvas.drawText("لا توجد بيانات ضمن الفترة المحددة.",RIGHT,y,text)
            footer(canvas,index+1,chunks.size,line,text); document.finishPage(page)
        }
        save(context,document,"MyAccounts_ملخص_الأشخاص_${currency}_${timestamp()}.pdf")
    } catch(e:Exception){ Result.failure(e) }

    private fun header(canvas: android.graphics.Canvas,title:String,currency:String,start:Long?,end:Long?):Float { val titlePaint=paint(19f,true,Color.rgb(25,25,25)); val text=paint(10f,false,Color.rgb(35,35,35)); var y=42f; canvas.drawText(title,RIGHT,y,titlePaint); y+=26f; canvas.drawText("العملة: ${currencyName(currency)}",RIGHT,y,text); y+=17f; canvas.drawText("الفترة: ${formatDateRange(start,end)}",RIGHT,y,text); y+=17f; canvas.drawText("تاريخ إصدار التقرير: ${formatDate(System.currentTimeMillis())}",RIGHT,y,text); y+=15f; canvas.drawLine(LEFT,y,RIGHT,y,linePaint()); return y+22f }
    private fun footer(canvas: android.graphics.Canvas,page:Int,total:Int,line:Paint,text:Paint){canvas.drawLine(LEFT,BOTTOM,RIGHT,BOTTOM,line);canvas.drawText("صفحة $page من $total",RIGHT,BOTTOM+17f,text)}
    private fun drawLimited(canvas: android.graphics.Canvas,value:String,right:Float,y:Float,width:Float,paint:Paint){var text=value;if(paint.measureText(text)>width){var end=text.length;while(end>0&&paint.measureText("…"+text.substring(0,end))>width)end--;text=if(end>0)"…"+text.substring(0,end) else "…"};canvas.drawText(text,right,y,paint)}
    private fun paint(size:Float,bold:Boolean,color:Int)=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;textSize=size;textAlign=Paint.Align.RIGHT;typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)}
    private fun linePaint()=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(185,185,185);strokeWidth=1f}
    private fun balancePaint(v:Long)=paint(9f,true,when{v>0->Color.rgb(190,35,35);v<0->Color.rgb(0,125,70);else->Color.DKGRAY})
    private fun balanceText(v:Long)=when{v>0->"${formatAmount(v)} (عليه)";v<0->"${formatAmount(-v)} (له)";else->"0 (متوازن)"}
    private fun currencyName(c:String)=when(c){"YER"->"الريال اليمني";"SAR"->"الريال السعودي";"USD"->"الدولار الأمريكي";else->c}
    private fun formatAmount(v:Long)=BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
    private fun formatDate(v:Long)=SimpleDateFormat("dd/MM/yyyy",Locale("ar")).format(Date(v))
    private fun formatDateRange(s:Long?,e:Long?)=if(s==null&&e==null)"كل الحساب" else "${s?.let(::formatDate)?:"غير محدد"} - ${e?.let{formatDate(addDays(it,-1))}?:"غير محدد"}"
    private fun firstToFirstRange(firstPayable:Long?,firstReceivable:Long?)=if(firstPayable==null&&firstReceivable==null)"—" else "${firstPayable?.let(::formatDate)?:"—"} - ${firstReceivable?.let(::formatDate)?:"—"}"
    private fun lastToLastRange(lastPayable:Long?,lastReceivable:Long?)=if(lastPayable==null&&lastReceivable==null)"—" else "${lastPayable?.let(::formatDate)?:"—"} - ${lastReceivable?.let(::formatDate)?:"—"}"
    private fun addDays(v:Long,d:Int)=Calendar.getInstance().apply{timeInMillis=v;add(Calendar.DAY_OF_MONTH,d)}.timeInMillis
    private fun timestamp()=SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())
    private fun save(context:Context,document:PdfDocument,fileName:String):Result<String>=try{if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){val values=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,fileName);put(MediaStore.Downloads.MIME_TYPE,"application/pdf");put(MediaStore.Downloads.RELATIVE_PATH,"${Environment.DIRECTORY_DOWNLOADS}/MyAccounts");put(MediaStore.Downloads.IS_PENDING,1)};val resolver=context.contentResolver;val uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:throw IllegalStateException("تعذر إنشاء ملف التقرير.");try{resolver.openOutputStream(uri).use{output->if(output==null)throw IllegalStateException("تعذر فتح ملف التقرير.");document.writeTo(output)};resolver.update(uri,ContentValues().apply{put(MediaStore.Downloads.IS_PENDING,0)},null,null)}catch(e:Exception){resolver.delete(uri,null,null);throw e};Result.success("تم حفظ التقرير في مجلد التنزيلات/MyAccounts")}else{val dir=File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"MyAccounts");if(!dir.exists()&&!dir.mkdirs())throw IllegalStateException("تعذر إنشاء مجلد التقرير.");val file=File(dir,fileName);FileOutputStream(file).use{document.writeTo(it)};Result.success(file.absolutePath)}}catch(e:Exception){Result.failure(e)}
}
