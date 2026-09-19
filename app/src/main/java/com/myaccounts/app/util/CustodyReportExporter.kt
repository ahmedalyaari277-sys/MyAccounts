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
import com.myaccounts.app.data.custody.*
import com.myaccounts.app.ui.screens.CustodyReportData
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CustodyReportExporter {
    fun export(context: Context,title: String,data: List<CustodyReportData>,currency: String,reportType: String,pdf: Boolean,periodLabel: String = "حسب الاختيار"): Result<String> =
        if(pdf) exportPdf(context,title,data,currency,reportType,periodLabel) else exportExcel(context,title,data,currency,reportType,periodLabel)

    private fun exportPdf(context: Context,title: String,data: List<CustodyReportData>,currency: String,reportType: String,periodLabel: String): Result<String> = runCatching {
        val doc=PdfDocument()
        val pages=if(reportType=="DETAILED") detailedPages(data,currency) else if(reportType=="PEOPLE" || reportType=="SUMMARY") data.map{listOf(it)} else listOf(data)
        val chunks=if(pages.isEmpty()) listOf(emptyList()) else pages
        chunks.forEachIndexed{index,chunk->
            val page=doc.startPage(PdfDocument.PageInfo.Builder(842,595,index+1).create())
            val c=page.canvas
            var y=34f
            val titlePaint=paint(18,Color.rgb(25,25,25),true)
            val textPaint=paint(9,Color.rgb(45,45,45),false)
            val headPaint=paint(9,Color.rgb(25,25,25),true)
            val green=paint(9,Color.rgb(0,125,70),true)
            val red=paint(9,Color.rgb(190,35,35),true)
            val line=linePaint()
            c.drawText(title,807f,y,titlePaint); y+=22
            c.drawText("العملة: "+currencyName(currency),807f,y,textPaint)
            c.drawText("الفترة: "+periodLabel,500f,y,textPaint)
            c.drawText("إصدار: "+date(System.currentTimeMillis()),260f,y,textPaint); y+=16
            c.drawLine(35f,y,807f,y,line); y+=20
            if (chunk.size == 1) {
                val custody = chunk.first().custody
                c.drawText("العهدة: "+custody.name,807f,y,textPaint)
                c.drawText("الجهة: "+custody.organizationName,560f,y,textPaint)
                c.drawText("الحامل: "+custody.holderName,300f,y,textPaint)
                y += 18
                c.drawLine(35f,y,807f,y,line)
                y += 18
            }
            when(reportType){
                "PEOPLE"->{ 
                    y=pdfPeople(c,y,chunk,currency,headPaint,green,red,line)
                }
                "SUMMARY"->{
                    y=pdfSummary(c,y,chunk,currency,headPaint,green,red,line)
                }
                else->{
                    y=pdfDetailed(c,y,chunk as List<CustodyReportData>,headPaint,green,red,line)
                }
            }
            c.drawLine(35f,560f,807f,560f,line)
            c.drawText("صفحة "+(index+1)+" من "+chunks.size,807f,578f,textPaint)
            doc.finishPage(page)
        }
        savePdf(context,doc,"MyAccounts_"+safe(title)+"_"+stamp()+".pdf")
    }

    private fun detailedPages(data: List<CustodyReportData>,currency:String): List<List<CustodyReportData>> = data.chunked(1)

    private fun pdfPeople(c:android.graphics.Canvas,y0:Float,data:List<CustodyReportData>,currency:String,h:Paint,green:Paint,red:Paint,line:Paint):Float{
        var y=y0
        val codes=codes(currency)
        data.forEach{d->
            c.drawText(d.custody.name,807f,y,h); y+=17
            c.drawText("الجهة: "+d.custody.organizationName+"   الحامل: "+d.custody.holderName,807f,y,paint(8,Color.DKGRAY,false)); y+=16
            val xs=if(codes.size==1) floatArrayOf(790f,560f,430f,300f,170f) else floatArrayOf(790f,650f,510f,370f,230f,90f)
            c.drawText("الطرف",xs[0],y,h)
            if(codes.size==1){c.drawText("العملة",xs[1],y,h);c.drawText("العهدة",xs[2],y,green);c.drawText("الذمة",xs[3],y,red)}
            else{c.drawText("YER عهدة",xs[1],y,green);c.drawText("YER ذمة",xs[2],y,red);c.drawText("SAR عهدة",xs[3],y,green);c.drawText("SAR ذمة",xs[4],y,red);c.drawText("USD",xs[5],y,green)}
            y+=14;c.drawLine(35f,y,807f,y,line);y+=15
            d.people.forEach{p->
                c.drawText(p.name.take(18),xs[0],y,paint(8,Color.DKGRAY,false))
                if(codes.size==1){
                    val cde=codes[0];val tx=d.transactions.filter{it.personId==p.id&&it.currencyCode==cde}
                    c.drawText(cde,xs[1],y,paint(8,Color.DKGRAY,false));c.drawText(money(tx.sumOf{CustodyBalanceRules.personCustodyDelta(it.type,it.amountMinor)}),xs[2],y,green);c.drawText(money(tx.sumOf{CustodyBalanceRules.personDebtDelta(it.type,it.amountMinor)}),xs[3],y,red)
                }else{
                    val vals=codes.map{cde->val tx=d.transactions.filter{it.personId==p.id&&it.currencyCode==cde};tx.sumOf{CustodyBalanceRules.personCustodyDelta(it.type,it.amountMinor)} to tx.sumOf{CustodyBalanceRules.personDebtDelta(it.type,it.amountMinor)}}
                    c.drawText(money(vals[0].first),xs[1],y,green);c.drawText(money(vals[0].second),xs[2],y,red);c.drawText(money(vals[1].first),xs[3],y,green);c.drawText(money(vals[1].second),xs[4],y,red);c.drawText(money(vals[2].first)+" / "+money(vals[2].second),xs[5],y,green)
                }
                c.drawLine(35f,y+6,807f,y+6,line);y+=20
                if(y>535f)return y
            }
        }
        return y
    }

    private fun pdfSummary(c:android.graphics.Canvas,y0:Float,data:List<CustodyReportData>,currency:String,h:Paint,green:Paint,red:Paint,line:Paint):Float{
        var y=y0;val codes=codes(currency)
        c.drawText("البيان",790f,y,h)
        var x=620f
        codes.forEach{cde->c.drawText(cde+" نقد",x,y,green);x-=130;c.drawText(cde+" ذمة",x,y,red);x-=130}
        y+=15;c.drawLine(35f,y,807f,y,line);y+=18
        val labels=listOf("إجمالي الاستلام","إجمالي الصرف","مرتجع من الأشخاص","مرتجع للجهة","ذمة الجهة","ذمم الأطراف","المتبقي النقدي","الفائض","العجز")
        labels.forEach{label->
            c.drawText(label,790f,y,h);x=620f
            codes.forEach{cde->
                val vals=totals(data,cde)
                val v=when(label){"إجمالي الاستلام"->vals.received;"إجمالي الصرف"->vals.paid;"مرتجع من الأشخاص"->vals.returnedFromPerson;"مرتجع للجهة"->vals.returnedToOrg;"ذمة الجهة"->vals.orgDebt;"ذمم الأطراف"->vals.peopleDebt;"المتبقي النقدي"->vals.cash;"الفائض"->vals.surplus;else->vals.deficit}
                c.drawText(money(v),x,y,if(label=="العجز")red else if(label=="الفائض"||label=="المتبقي النقدي")green else h);x-=130
                c.drawText(if(label=="ذمة الجهة"||label=="ذمم الأطراف")money(v) else "",x,y,red);x-=130
            }
            c.drawLine(35f,y+5,807f,y+5,line);y+=20
        }
        return y
    }

    private fun pdfDetailed(c:android.graphics.Canvas,y0:Float,data:List<CustodyReportData>,h:Paint,green:Paint,red:Paint,line:Paint):Float{
        var y=y0
        val codes=if(data.flatMap{it.transactions}.map{it.currencyCode}.distinct().size>1) listOf("YER","SAR","USD") else detailedCodes(data)
        val multi=codes.size>1
        if(multi){
            c.drawText("العهدة",790f,y,h); c.drawText("التاريخ",685f,y,h); c.drawText("النوع",585f,y,h); c.drawText("الطرف",470f,y,h)
            c.drawText("YER",365f,y,h); c.drawText("SAR",285f,y,h); c.drawText("USD",205f,y,h); c.drawText("البيان",95f,y,h)
        }else{
            c.drawText("العهدة",790f,y,h); c.drawText("التاريخ",665f,y,h); c.drawText("النوع",555f,y,h); c.drawText("الطرف",425f,y,h)
            c.drawText(codes.firstOrNull()?:"المبلغ",280f,y,h); c.drawText("البيان",120f,y,h)
        }
        y+=15f; c.drawLine(35f,y,807f,y,line); y+=18f
        data.flatMap{d->d.transactions.map{d to it}}.forEach{(d,t)->
            val person=d.people.firstOrNull{it.id==t.personId}?.name?:d.custody.holderName
            val positive=t.type==CustodyTransactionType.RECEIVED_FROM_ORG||t.type==CustodyTransactionType.RETURNED_FROM_PERSON||t.type==CustodyTransactionType.ORG_LOAN_REPAYMENT||t.type==CustodyTransactionType.PERSON_LOAN_TO_OWNER
            c.drawText(d.custody.name.take(14),790f,y,paint(7,Color.DKGRAY,false))
            if(multi){
                c.drawText(date(t.transactionDate),685f,y,paint(7,Color.DKGRAY,false)); c.drawText(typeName(t.type).take(16),585f,y,paint(7,Color.DKGRAY,false)); c.drawText(person.take(14),470f,y,paint(7,Color.DKGRAY,false))
                val xs=mapOf("YER" to 365f,"SAR" to 285f,"USD" to 205f)
                listOf("YER","SAR","USD").forEach{code->val x=xs[code]!!;if(t.currencyCode==code)c.drawText(money(t.amountMinor),x,y,if(positive)green else red)else c.drawText("—",x,y,paint(7,Color.GRAY,false))}
                c.drawText(t.description.ifBlank{"—"}.take(18),95f,y,paint(7,Color.DKGRAY,false))
            }else{
                c.drawText(date(t.transactionDate),665f,y,paint(7,Color.DKGRAY,false)); c.drawText(typeName(t.type).take(16),555f,y,paint(7,Color.DKGRAY,false)); c.drawText(person.take(14),425f,y,paint(7,Color.DKGRAY,false))
                c.drawText(money(t.amountMinor),280f,y,if(positive)green else red); c.drawText(t.description.ifBlank{"—"}.take(24),120f,y,paint(7,Color.DKGRAY,false))
            }
            c.drawLine(35f,y+5,807f,y+5,line); y+=18f
            if(y>535f)return y
        }
        return y
    }
    private fun detailedCodes(data:List<CustodyReportData>):List<String>{
        val found=data.flatMap{it.transactions}.map{it.currencyCode}.distinct()
        return if(found.size==1) found else listOf("YER","SAR","USD")
    }
    private fun exportExcel(context:Context,title:String,data:List<CustodyReportData>,currency:String,reportType:String,periodLabel:String):Result<String> = runCatching{
        val rows=if(reportType=="PEOPLE") peopleRows(data,currency,periodLabel) else if(reportType=="SUMMARY") summaryRows(data,currency,periodLabel) else detailedRows(data,currency,periodLabel)
        val sheet=sheetXml(rows)
        saveXlsx(context,sheet,"MyAccounts_"+safe(title)+"_"+stamp()+".xlsx")
    }

    private fun peopleRows(data:List<CustodyReportData>,currency:String,periodLabel:String):List<List<Cell>>{
        val codes=codes(currency);val rows=mutableListOf<List<Cell>>()
        rows+=listOf(Cell("تقرير أصحاب العُهَد",1))
        rows+=listOf(Cell("العملة: "+currencyName(currency),2),Cell("الفترة: "+periodLabel,2),Cell("إصدار: "+date(System.currentTimeMillis()),2))
        if(data.size==1){val d=data.first();rows+=listOf(Cell("العهدة: "+d.custody.name,2),Cell("الجهة: "+d.custody.organizationName,2),Cell("الحامل: "+d.custody.holderName,2))}
        val header=mutableListOf(Cell("العهدة",2),Cell("الجهة",2),Cell("الحامل",2),Cell("الطرف",2))
        codes.forEach{cde->{header+=Cell(cde+" — العهدة",3);header+=Cell(cde+" — الذمة",4)}};rows+=header
        data.forEach{d->d.people.forEach{p->
            val r=mutableListOf(Cell(d.custody.name,2),Cell(d.custody.organizationName,2),Cell(d.custody.holderName,2),Cell(p.name,2))
            codes.forEach{cde->val tx=d.transactions.filter{it.personId==p.id&&it.currencyCode==cde};r+=Cell(num(tx.sumOf{CustodyBalanceRules.personCustodyDelta(it.type,it.amountMinor)}),5);r+=Cell(num(tx.sumOf{CustodyBalanceRules.personDebtDelta(it.type,it.amountMinor)}),6)}
            rows+=r
        }};return rows
    }
    private fun summaryRows(data:List<CustodyReportData>,currency:String,periodLabel:String):List<List<Cell>>{
        val rows=mutableListOf<List<Cell>>();val codes=codes(currency)
        rows+=listOf(Cell("ملخص أرصدة العُهَد",1))
        rows+=listOf(Cell("العملة: "+currencyName(currency),2),Cell("الفترة: "+periodLabel,2),Cell("إصدار: "+date(System.currentTimeMillis()),2))
        if(data.size==1){val d=data.first();rows+=listOf(Cell("العهدة: "+d.custody.name,2),Cell("الجهة: "+d.custody.organizationName,2),Cell("الحامل: "+d.custody.holderName,2))}
        val h=mutableListOf(Cell("العهدة",2),Cell("الجهة",2),Cell("الحامل",2),Cell("البيان",2));codes.forEach{cde->{h+=Cell(cde+" — نقد",3);h+=Cell(cde+" — ذمم",4)}};rows+=h
        data.forEach{d->listOf("إجمالي الاستلام","إجمالي الصرف","مرتجع من الأشخاص","مرتجع للجهة","ذمة الجهة","ذمم الأطراف","المتبقي النقدي","الفائض","العجز").forEach{label->
            val r=mutableListOf(Cell(d.custody.name,2),Cell(d.custody.organizationName,2),Cell(d.custody.holderName,2),Cell(label,2))
            codes.forEach{cde->val v=totals(listOf(d),cde);val value=when(label){"إجمالي الاستلام"->v.received;"إجمالي الصرف"->v.paid;"مرتجع من الأشخاص"->v.returnedFromPerson;"مرتجع للجهة"->v.returnedToOrg;"ذمة الجهة"->v.orgDebt;"ذمم الأطراف"->v.peopleDebt;"المتبقي النقدي"->v.cash;"الفائض"->v.surplus;else->v.deficit};r+=Cell(num(value),if(label=="العجز")6 else if(label=="الفائض"||label=="المتبقي النقدي")5 else 2);r+=Cell(if(label=="ذمة الجهة"||label=="ذمم الأطراف")num(value) else "—",if(label=="ذمة الجهة"||label=="ذمم الأطراف")4 else 7)};rows+=r
        }};return rows
    }
    private fun detailedRows(data:List<CustodyReportData>,currency:String,periodLabel:String):List<List<Cell>>{
        val rows=mutableListOf<List<Cell>>(); val codes=codes(currency)
        rows+=listOf(Cell("التقرير التفصيلي للعمليات",1))
        rows+=listOf(Cell("العملة: "+currencyName(currency),2),Cell("الفترة: "+periodLabel,2),Cell("إصدار: "+date(System.currentTimeMillis()),2))
        if(data.size==1){val d=data.first();rows+=listOf(Cell("العهدة: "+d.custody.name,2),Cell("الجهة: "+d.custody.organizationName,2),Cell("الحامل: "+d.custody.holderName,2))}
        val multi=codes.size>1
        val header=mutableListOf(Cell("العهدة",2),Cell("الجهة",2),Cell("الحامل",2),Cell("التاريخ",2),Cell("النوع",2),Cell("الطرف",2))
        if(multi) codes.forEach{header+=Cell(it,3)} else header+=Cell(codes.firstOrNull()?:"المبلغ",3)
        header+=Cell("البيان",2); rows+=header
        data.forEach{d->d.transactions.forEach{t->
            val person=d.people.firstOrNull{it.id==t.personId}?.name?:d.custody.holderName
            val positive=t.type==CustodyTransactionType.RECEIVED_FROM_ORG||t.type==CustodyTransactionType.RETURNED_FROM_PERSON||t.type==CustodyTransactionType.ORG_LOAN_REPAYMENT||t.type==CustodyTransactionType.PERSON_LOAN_TO_OWNER
            val r=mutableListOf(Cell(d.custody.name,2),Cell(d.custody.organizationName,2),Cell(d.custody.holderName,2),Cell(date(t.transactionDate),2),Cell(typeName(t.type),2),Cell(person,2))
            if(multi) codes.forEach{code->r+=Cell(if(t.currencyCode==code) num(t.amountMinor) else "—",if(t.currencyCode==code) if(positive)5 else 6 else 2)}
            else r+=Cell(num(t.amountMinor),if(positive)5 else 6)
            r+=Cell(t.description.ifBlank{"—"},2); rows+=r
        }}; return rows
    }
    private fun totals(data:List<CustodyReportData>,currency:String):Tot{
        var r=0L;var p=0L;var rf=0L;var rt=0L;var od=0L;var pd=0L;var cash=0L
        data.forEach{d->d.transactions.filter{it.currencyCode==currency}.forEach{t->when(t.type){CustodyTransactionType.RECEIVED_FROM_ORG->r+=t.amountMinor;CustodyTransactionType.PAID_TO_PERSON->p+=t.amountMinor;CustodyTransactionType.RETURNED_FROM_PERSON->rf+=t.amountMinor;CustodyTransactionType.RETURNED_TO_ORG->rt+=t.amountMinor};od+=CustodyBalanceRules.ownerOrgDebtDelta(t.type,t.amountMinor);pd+=CustodyBalanceRules.ownerPeopleDebtDelta(t.type,t.amountMinor);cash+=CustodyBalanceRules.ownerCashDelta(t.type,t.amountMinor)}};return Tot(r,p,rf,rt,od,pd,cash,maxOf(cash,0),maxOf(-cash,0))
    }
    private fun codes(currency:String)=if(currency=="ALL")listOf("YER","SAR","USD")else listOf(currency)
    private fun sheetXml(rows:List<List<Cell>>):String{val cols=(1..maxOf(1,rows.maxOfOrNull{it.size}?:1)).joinToString(""){i->"<col min=\""+i+"\" max=\""+i+"\" width=\"20\" customWidth=\"1\"/>"};val data=rows.mapIndexed{ri,row->"<row r=\""+(ri+1)+"\">"+row.mapIndexed{ci,c->cellXml(c,ci+1,ri+1)}.joinToString("")+"</row>"}.joinToString("");return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\" rightToLeft=\"1\"/></sheetViews><cols>"+cols+"</cols><sheetData>"+data+"</sheetData></worksheet>"}
    private fun cellXml(c:Cell,col:Int,row:Int):String="<c r=\""+colLetter(col)+row+"\" t=\"inlineStr\" s=\""+c.style+"\"><is><t xml:space=\"preserve\">"+escape(c.value)+"</t></is></c>"
    private fun saveXlsx(context:Context,sheet:String,name:String):String{val out=java.io.ByteArrayOutputStream();ZipOutputStream(out).use{z->entry(z,"[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");entry(z,"_rels/.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");entry(z,"xl/workbook.xml","<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"التقرير\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");entry(z,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");entry(z,"xl/styles.xml",styles());entry(z,"xl/worksheets/sheet1.xml",sheet)};if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){val v=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,name);put(MediaStore.Downloads.MIME_TYPE,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/MyAccounts");put(MediaStore.Downloads.IS_PENDING,1)};val resolver=context.contentResolver;val uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v)?:error("تعذر إنشاء Excel");resolver.openOutputStream(uri).use{it?.write(out.toByteArray())};resolver.update(uri,ContentValues().apply{put(MediaStore.Downloads.IS_PENDING,0)},null,null);return "تم حفظ التقرير في مجلد التنزيلات/MyAccounts"};val dir=File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"MyAccounts").apply{if(!exists())mkdirs()};File(dir,name).writeBytes(out.toByteArray());return dir.resolve(name).absolutePath}
    private fun styles()="<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"5\"><font><sz val=\"11\"/><name val=\"Arial\"/></font><font><b/><sz val=\"14\"/><name val=\"Arial\"/></font><font><b/><sz val=\"11\"/><name val=\"Arial\"/></font><font><color rgb=\"FF00804A\"/><b/><sz val=\"11\"/><name val=\"Arial\"/></font><font><color rgb=\"FFC02323\"/><b/><sz val=\"11\"/><name val=\"Arial\"/></font></fonts><fills count=\"4\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFEAF3EE\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFCEBEC\"/></patternFill></fill></fills><borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style=\"thin\"/><right style=\"thin\"/><top style=\"thin\"/><bottom style=\"thin\"/></border></borders><cellXfs count=\"7\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\"/><xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"1\"/><xf numFmtId=\"0\" fontId=\"3\" fillId=\"2\" borderId=\"1\"/><xf numFmtId=\"0\" fontId=\"4\" fillId=\"3\" borderId=\"1\"/><xf numFmtId=\"2\" fontId=\"3\" fillId=\"2\" borderId=\"1\"/><xf numFmtId=\"2\" fontId=\"4\" fillId=\"3\" borderId=\"1\"/><xf numFmtId="0" fontId="0" fillId="4" borderId="1"/></cellXfs></styleSheet>"
    private fun entry(z:ZipOutputStream,path:String,value:String){z.putNextEntry(ZipEntry(path));z.write(value.toByteArray(Charsets.UTF_8));z.closeEntry()}
    private fun savePdf(context:Context,doc:PdfDocument,name:String):String{if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){val v=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,name);put(MediaStore.Downloads.MIME_TYPE,"application/pdf");put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/MyAccounts");put(MediaStore.Downloads.IS_PENDING,1)};val resolver=context.contentResolver;val uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v)?:error("تعذر إنشاء PDF");resolver.openOutputStream(uri).use{out->requireNotNull(out);doc.writeTo(out)};resolver.update(uri,ContentValues().apply{put(MediaStore.Downloads.IS_PENDING,0)},null,null);return "تم حفظ التقرير في مجلد التنزيلات/MyAccounts"};val dir=File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"MyAccounts").apply{if(!exists())mkdirs()};val file=File(dir,name);FileOutputStream(file).use{doc.writeTo(it)};return file.absolutePath}
    private fun paint(size:Int,color:Int,bold:Boolean)=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;textSize=size.toFloat();textAlign=Paint.Align.RIGHT;typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)}
    private fun linePaint()=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(170,170,170);strokeWidth=1f}
    private fun money(v:Long)=BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
    private fun num(v:Long,style:Int=0)=money(v)
    private fun date(v:Long)=SimpleDateFormat("dd/MM/yyyy",Locale("ar")).format(Date(v))
    private fun stamp()=SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())
    private fun safe(v:String)=v.replace(Regex("[^\\u0600-\\u06FFA-Za-z0-9_-]+"),"_").take(60)
    private fun escape(v:String)=v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
    private fun colLetter(n:Int)=('A'.code+n-1).toChar().toString()
    private fun currencyName(v:String)=when(v){"YER"->"ريال يمني";"SAR"->"ريال سعودي";"USD"->"دولار أمريكي";else->"جميع العملات"}
    private fun typeName(v:String)=when(v){CustodyTransactionType.RECEIVED_FROM_ORG->"استلام من الجهة";CustodyTransactionType.PAID_TO_PERSON->"صرف للشخص";CustodyTransactionType.RETURNED_FROM_PERSON->"مرتجع من الشخص";CustodyTransactionType.RETURNED_TO_ORG->"مرتجع للجهة";CustodyTransactionType.ORG_LOAN_FROM_OWNER->"ذمة للجهة من الحامل";CustodyTransactionType.ORG_LOAN_REPAYMENT->"سداد ذمة الجهة";CustodyTransactionType.PERSON_LOAN_TO_OWNER->"اقتراض من الشخص";CustodyTransactionType.OWNER_REPAY_PERSON_LOAN->"سداد قرض الشخص";else->v}
}