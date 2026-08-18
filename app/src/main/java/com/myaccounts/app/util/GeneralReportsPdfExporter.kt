package com.myaccounts.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
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
    private const val LEFT=35f; private const val RIGHT=560f
    fun exportPeopleReport(context:Context,currency:String,summary:CurrencyReportSummary,people:List<CurrencyReportPersonRow>,start:Long?,end:Long?):Result<String>=try{val doc=PdfDocument();val chunks=if(people.isEmpty())listOf(emptyList())else people.chunked(22);chunks.forEachIndexed{idx,chunk->val page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,idx+1).create());val c=page.canvas;var y=header(c,"تقرير الأشخاص",currency,start,end);val h=paint(10,Color.rgb(25,25,25),true);val green=paint(9,Color.rgb(0,125,70),true);val red=paint(9,Color.rgb(190,35,35),true);val text=paint(9,Color.rgb(35,35,35),false);val line=linePaint();if(idx==0){c.drawText("ملخص الحساب",RIGHT,y,h);y+=17f;summary(c,y,summary,green,red);y+=24f};y=tableHeader(c,y,listOf("الشخص","العملة","عليه","له","الرصيد"),h,green,red,line);chunk.forEach{p->c.drawText(p.personName.take(20),540f,y,text);c.drawText(currencyName(currency),430f,y,text);c.drawText("+${amount(p.totalReceivableMinor)}",330f,y,green);c.drawText("-${amount(p.totalPayableMinor)}",235f,y,red);c.drawText(balance(p.balanceMinor),95f,y,balancePaint(p.balanceMinor));row(c,y,line);y+=25f};if(chunk.isEmpty())c.drawText("لا توجد بيانات ضمن الفترة المحددة.",RIGHT,y,text);footer(c,idx+1,chunks.size,line,text);doc.finishPage(page)};save(context,doc,"MyAccounts_تقرير_الأشخاص_${currency}_${stamp()}.pdf")}catch(e:Exception){Result.failure(e)}
    fun exportDetailedReport(context:Context,currency:String,transactions:List<GeneralReportTransactionRow>,start:Long?,end:Long?):Result<String>=try{val doc=PdfDocument();val chunks=if(transactions.isEmpty())listOf(emptyList())else transactions.chunked(25);chunks.forEachIndexed{idx,chunk->val page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,idx+1).create());val c=page.canvas;var y=header(c,"التقرير التفصيلي للعمليات",currency,start,end);val h=paint(9,Color.rgb(25,25,25),true);val green=paint(8,Color.rgb(0,125,70),true);val red=paint(8,Color.rgb(190,35,35),true);val text=paint(8,Color.rgb(35,35,35),false);val line=linePaint();y=tableHeader(c,y,listOf("التاريخ","الشخص","البيان","عليه","له"),h,green,red,line);chunk.forEach{t->c.drawText(formatDate(t.transactionDate),550f,y,text);c.drawText(t.personName.take(17),455f,y,text);c.drawText(t.description.ifBlank{"—"}.take(25),350f,y,text);c.drawText(if(t.type=="RECEIVABLE")"+${amount(t.amountMinor)}" else "—",250f,y,if(t.type=="RECEIVABLE")green else text);c.drawText(if(t.type=="PAYABLE")"-${amount(t.amountMinor)}" else "—",145f,y,if(t.type=="PAYABLE")red else text);row(c,y,line);y+=24f};if(chunk.isEmpty())c.drawText("لا توجد عمليات ضمن الفترة المحددة.",RIGHT,y,text);footer(c,idx+1,chunks.size,line,text);doc.finishPage(page)};save(context,doc,"MyAccounts_التقرير_التفصيلي_${currency}_${stamp()}.pdf")}catch(e:Exception){Result.failure(e)}
    fun exportSummaryReport(context:Context,currency:String,rows:List<PersonCurrencySummaryRow>,start:Long?,end:Long?):Result<String>=try{val doc=PdfDocument();val chunks=if(rows.isEmpty())listOf(emptyList())else rows.chunked(18);chunks.forEachIndexed{idx,chunk->val page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,idx+1).create());val c=page.canvas;var y=header(c,"ملخص تقرير الأشخاص",currency,start,end);val h=paint(8,Color.rgb(25,25,25),true);val green=paint(8,Color.rgb(0,125,70),true);val red=paint(8,Color.rgb(190,35,35),true);val text=paint(7,Color.rgb(35,35,35),false);val line=linePaint();y=tableHeader(c,y,listOf("الشخص","عليه","له","الرصيد","أول عملية","آخر عملية"),h,green,red,line);chunk.forEach{r->c.drawText(r.personName.take(18),540f,y,text);c.drawText("+${amount(r.totalReceivableMinor)}",430f,y,green);c.drawText("-${amount(r.totalPayableMinor)}",350f,y,red);c.drawText(balance(r.balanceMinor),265f,y,balancePaint(r.balanceMinor));c.drawText(first(r),170f,y,text);c.drawText(last(r),75f,y,text);row(c,y,line);y+=24f};if(chunk.isEmpty())c.drawText("لا توجد بيانات ضمن الفترة المحددة.",RIGHT,y,text);footer(c,idx+1,chunks.size,line,text);doc.finishPage(page)};save(context,doc,"MyAccounts_ملخص_الأشخاص_${currency}_${stamp()}.pdf")}catch(e:Exception){Result.failure(e)}
    private fun header(c:Canvas,title:String,currency:String,start:Long?,end:Long?):Float{val t=paint(18,Color.rgb(25,25,25),true);val x=paint(9,Color.rgb(45,45,45),false);var y=42f;c.drawText(title,RIGHT,y,t);y+=23f;c.drawText("العملة: ${currencyName(currency)}",RIGHT,y,x);c.drawText("الفترة: ${range(start,end)}",350f,y,x);c.drawText("إصدار: ${formatDate(System.currentTimeMillis())}",170f,y,x);y+=18f;c.drawLine(LEFT,y,RIGHT,y,linePaint());return y+22f}
    private fun summary(c:Canvas,y:Float,s:CurrencyReportSummary,green:Paint,red:Paint){c.drawText("عليه: +${amount(s.totalReceivableMinor)}",RIGHT,y,green);c.drawText("له: -${amount(s.totalPayableMinor)}",390f,y,red);c.drawText("الرصيد: ${balance(s.balanceMinor)}",220f,y,balancePaint(s.balanceMinor));c.drawText("العمليات: ${s.transactionCount}",75f,y,paint(8,Color.DKGRAY,false))}
    private fun tableHeader(c:Canvas,y:Float,labels:List<String>,h:Paint,green:Paint,red:Paint,line:Paint):Float{val xs=floatArrayOf(540f,430f,330f,230f,130f,45f);c.drawLine(LEFT,y-12f,RIGHT,y-12f,line);labels.forEachIndexed{i,l->c.drawText(l,xs[i],y,when(l){"عليه"->green;"له"->red;else->h})};floatArrayOf(35f,120f,220f,315f,410f,510f,560f).forEach{c.drawLine(it,y-12f,it,y+10f,line)};c.drawLine(LEFT,y+10f,RIGHT,y+10f,line);return y+25f}
    private fun row(c:Canvas,y:Float,line:Paint){c.drawLine(LEFT,y+9f,RIGHT,y+9f,line)}
    private fun footer(c:Canvas,page:Int,total:Int,line:Paint,text:Paint){c.drawLine(LEFT,790f,RIGHT,790f,line);c.drawText("صفحة $page من $total",RIGHT,807f,text)}
    private fun paint(size:Int,color:Int,bold:Boolean)=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;textSize=size.toFloat();textAlign=Paint.Align.RIGHT;typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)}
    private fun linePaint()=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(170,170,170);strokeWidth=1f}
    private fun balancePaint(v:Long)=paint(8,when{v>0->Color.rgb(0,125,70);v<0->Color.rgb(190,35,35);else->Color.DKGRAY},true)
    private fun balance(v:Long)=when{v>0->"+${amount(v)} (عليه)";v<0->"-${amount(-v)} (له)";else->"0 (متوازن)"}
    private fun first(r:PersonCurrencySummaryRow)=listOfNotNull(r.firstReceivableDate,r.firstPayableDate).minOrNull()?.let(::formatDate)?:"—"
    private fun last(r:PersonCurrencySummaryRow)=listOfNotNull(r.lastReceivableDate,r.lastPayableDate).maxOrNull()?.let(::formatDate)?:"—"
    private fun currencyName(c:String)=when(c){"YER"->"الريال اليمني";"SAR"->"الريال السعودي";"USD"->"الدولار الأمريكي";else->c}
    private fun amount(v:Long)=BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
    private fun formatDate(v:Long)=SimpleDateFormat("dd/MM/yyyy",Locale("ar")).format(Date(v))
    private fun range(s:Long?,e:Long?)=if(s==null&&e==null)"كل الحساب" else "${s?.let(::formatDate)?:"غير محدد"} - ${e?.let{formatDate(addDays(it,-1))}?:"غير محدد"}"
    private fun addDays(v:Long,d:Int)=Calendar.getInstance().apply{timeInMillis=v;add(Calendar.DAY_OF_MONTH,d)}.timeInMillis
    private fun stamp()=SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())
    private fun save(context:Context,doc:PdfDocument,fileName:String):Result<String>=try{if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){val values=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,fileName);put(MediaStore.Downloads.MIME_TYPE,"application/pdf");put(MediaStore.Downloads.RELATIVE_PATH,"${Environment.DIRECTORY_DOWNLOADS}/MyAccounts");put(MediaStore.Downloads.IS_PENDING,1)};val resolver=context.contentResolver;val uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:throw IllegalStateException("تعذر إنشاء ملف التقرير.");try{resolver.openOutputStream(uri).use{out->if(out==null)throw IllegalStateException("تعذر فتح ملف التقرير.");doc.writeTo(out)};resolver.update(uri,ContentValues().apply{put(MediaStore.Downloads.IS_PENDING,0)},null,null)}catch(e:Exception){resolver.delete(uri,null,null);throw e};Result.success("تم حفظ التقرير في مجلد التنزيلات/MyAccounts")}else{val dir=File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"MyAccounts");if(!dir.exists()&&!dir.mkdirs())throw IllegalStateException("تعذر إنشاء مجلد التقرير.");val file=File(dir,fileName);FileOutputStream(file).use{doc.writeTo(it)};Result.success(file.absolutePath)}}catch(e:Exception){Result.failure(e)}
}
