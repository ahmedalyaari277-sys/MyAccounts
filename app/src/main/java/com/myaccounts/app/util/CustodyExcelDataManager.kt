package com.myaccounts.app.util

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.room.withTransaction
import com.myaccounts.app.data.custody.*
import com.myaccounts.app.data.local.AppDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.xmlpull.v1.XmlPullParser

object CustodyExcelDataManager {
    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val SUGGESTED_FILE_NAME = "MyAccounts_Custodies.xlsx"
    private const val SHEET_NAME = "بيانات العُهَد"
    private val HEADERS = listOf("معرف العهدة","معرف العملية","معرف الشخص","اسم صاحب العهدة","هاتف صاحب العهدة","عنوان صاحب العهدة","ملاحظات صاحب العهدة","اسم الجهة","هاتف الجهة","عنوان الجهة","ملاحظات الجهة","اسم الشخص","هاتف الشخص","عنوان الشخص","ملاحظات الشخص","العملة","نوع العملية","المبلغ","البيان","التاريخ")
    data class ExportSummary(val custodies:Int,val people:Int,val accounts:Int,val transactions:Int)
    data class ImportPreview(val custodies:Int,val people:Int,val accounts:Int,val transactions:Int,val errors:List<String>) { val isValid get()=errors.isEmpty() }
    data class ImportSummary(val custodiesAdded:Int,val peopleAdded:Int,val accountsAdded:Int,val transactionsAdded:Int)
    private data class Row(val n:Int,val custodyId:String,val transactionId:String,val personId:String,val owner:String,val ownerPhone:String,val ownerAddress:String,val ownerNotes:String,val org:String,val orgPhone:String,val orgAddress:String,val orgNotes:String,val person:String,val personPhone:String,val personAddress:String,val personNotes:String,val currency:String,val type:String,val amount:Long?,val description:String,val date:Long?)

    private fun openInput(context: Context, uri: Uri): InputStream = if (uri.scheme == "file") File(requireNotNull(uri.path)).inputStream() else context.contentResolver.openInputStream(uri) ?: error("تعذر فتح ملف Excel.")
    private fun openOutput(context: Context, uri: Uri): OutputStream = if (uri.scheme == "file") File(requireNotNull(uri.path)).outputStream() else context.contentResolver.openOutputStream(uri) ?: error("تعذر فتح ملف Excel للكتابة.")

    suspend fun exportActive(context:Context, uri:Uri):Result<ExportSummary> = runCatching {
        val db=AppDatabase.getInstance(context); val dao=db.custodyDao(); val cs=dao.getAllCustodies(false); val ps=cs.flatMap{dao.getAllPersons(it.id)}; val ac=cs.flatMap{dao.getAllAccounts(it.id)}; val tx=cs.flatMap{dao.getAllTransactions(it.id,false)}; val peopleById=ps.associateBy{it.id}; val custodyById=cs.associateBy{it.id}; val transactionsByCustody=tx.groupBy{it.custodyId}; val peopleWithTransactions=tx.mapNotNull{it.personId}.toSet()
        val rows=mutableListOf<Row>()
        tx.forEach{t-> val c=custodyById[t.custodyId]?:error("بيانات العهدة المرتبطة بالعملية غير موجودة."); val p=t.personId?.let(peopleById::get); rows+=Row(0,c.externalId,t.externalId,p?.externalId?:"",c.name,c.phone,c.address,c.notes,c.organizationName,c.organizationPhone,c.organizationAddress,c.organizationNotes,p?.name?:"",p?.phone?:"",p?.address?:"",p?.notes?:"",t.currencyCode,t.type,t.amountMinor,t.description,t.transactionDate)}
        cs.forEach { c ->
            if (transactionsByCustody[c.id].isNullOrEmpty()) {
                rows += Row(0,c.externalId,"","",c.name,c.phone,c.address,c.notes,c.organizationName,c.organizationPhone,c.organizationAddress,c.organizationNotes,"","","","","","",null,"",null)
            }
        }
        ps.filter { it.id !in peopleWithTransactions }.forEach { p ->
            val c = custodyById[p.custodyId] ?: error("بيانات العهدة المرتبطة بالشخص غير موجودة.")
            rows += Row(0,c.externalId,"",p.externalId,c.name,c.phone,c.address,c.notes,c.organizationName,c.organizationPhone,c.organizationAddress,c.organizationNotes,p.name,p.phone,p.address,p.notes,"","","",null,null)
        }
        openOutput(context,uri).use{createWorkbook(it,rows)}
        ExportSummary(cs.size,ps.size,ac.size,tx.size)
    }

    fun previewImport(context:Context,uri:Uri):Result<ImportPreview> = runCatching { validate(parseWorkbook(context,uri)) }

    suspend fun import(context:Context,uri:Uri):Result<ImportSummary> = runCatching {
        val rows=parseWorkbook(context,uri); val preview=validate(rows); check(preview.isValid){preview.errors.joinToString("\n")}; val db=AppDatabase.getInstance(context); val dao=db.custodyDao()
        db.withTransaction {
            var ca=0;var pa=0;var aa=0;var ta=0; val custodyIds=mutableMapOf<String,Long>(); val personIds=mutableMapOf<String,Long>()
            rows.groupBy{it.custodyId}.values.forEach{group->
                val first=group.first(); val existing=dao.getCustodyByExternalId(first.custodyId); val cid=existing?.id?:dao.insertCustody(CustodyEntity(name=first.owner,phone=first.ownerPhone,address=first.ownerAddress,notes=first.ownerNotes,organizationName=first.org,organizationPhone=first.orgPhone,organizationAddress=first.orgAddress,organizationNotes=first.orgNotes,externalId=first.custodyId)).also{ca++}; custodyIds[first.custodyId]=cid
                var currentAccounts=dao.getAllAccounts(cid)
                if(currentAccounts.none{it.holderType=="OWNER"}){dao.insertAccounts(listOf("YER","SAR","USD").map{CustodyAccountEntity(custodyId=cid,holderType="OWNER",currencyCode=it)});aa+=3;currentAccounts=dao.getAllAccounts(cid)}
                group.filter{it.personId.isNotBlank()}.groupBy{it.personId}.values.forEach{pg->val r=pg.first();val p=dao.getPersonByExternalId(cid,r.personId);val pid=p?.id?:dao.insertPerson(CustodyPersonEntity(custodyId=cid,name=r.person,phone=r.personPhone,address=r.personAddress,notes=r.personNotes,externalId=r.personId)).also{pa++};personIds["${first.custodyId}|${r.personId}"]=pid;if(currentAccounts.none{it.holderType=="PERSON"&&it.personId==pid}){dao.insertAccounts(listOf("YER","SAR","USD").map{CustodyAccountEntity(custodyId=cid,holderType="PERSON",personId=pid,currencyCode=it)});aa+=3}}
            }
            rows.forEach{r->if(r.transactionId.isNotBlank()&&dao.getTransactionByExternalId(r.transactionId)==null){val cid=custodyIds[r.custodyId]?:error("الصف ${r.n}: معرف العهدة غير صالح");val pid=if(r.personId.isBlank())null else personIds["${r.custodyId}|${r.personId}"];val account=dao.getOwnerAccount(cid,r.currency)?:error("الصف ${r.n}: حساب العملة غير موجود");dao.insertTransaction(CustodyTransactionEntity(custodyId=cid,accountId=account.id,personId=pid,currencyCode=r.currency,type=r.type,amountMinor=r.amount?:error("الصف ${r.n}: المبلغ غير صالح."),description=r.description,transactionDate=r.date?:error("الصف ${r.n}: التاريخ غير صالح."),externalId=r.transactionId));ta++}}
            custodyIds.values.distinct().forEach { CustodyBalanceRebuilder.rebuildCustodyInTransaction(db, it) }
            ImportSummary(ca,pa,aa,ta)
        }
    }

    private fun validate(rows:List<Row>):ImportPreview {
        val e=mutableListOf<String>(); val cs=rows.map{it.custodyId}.filter(String::isNotBlank).toSet(); val ps=rows.mapNotNull{if(it.personId.isBlank())null else "${it.custodyId}|${it.personId}"}.toSet(); val seen=mutableSetOf<String>(); var tx=0
        val allowedTypes=setOf(CustodyTransactionType.RECEIVED_FROM_ORG,CustodyTransactionType.PAID_TO_PERSON,CustodyTransactionType.RETURNED_FROM_PERSON,CustodyTransactionType.RETURNED_TO_ORG,CustodyTransactionType.ORG_LOAN_FROM_OWNER,CustodyTransactionType.ORG_LOAN_REPAYMENT,CustodyTransactionType.PERSON_LOAN_TO_OWNER,CustodyTransactionType.OWNER_REPAY_PERSON_LOAN)
        val personTypes=setOf(CustodyTransactionType.PAID_TO_PERSON,CustodyTransactionType.RETURNED_FROM_PERSON,CustodyTransactionType.PERSON_LOAN_TO_OWNER,CustodyTransactionType.OWNER_REPAY_PERSON_LOAN)
        val organizationTypes=setOf(CustodyTransactionType.RECEIVED_FROM_ORG,CustodyTransactionType.RETURNED_TO_ORG,CustodyTransactionType.ORG_LOAN_FROM_OWNER,CustodyTransactionType.ORG_LOAN_REPAYMENT)
        rows.forEach{r->
            if(r.custodyId.isBlank())e+="الصف ${r.n}: معرف العهدة مطلوب."; if(r.owner.isBlank())e+="الصف ${r.n}: اسم صاحب العهدة مطلوب."; if(r.org.isBlank())e+="الصف ${r.n}: اسم الجهة مطلوب."
            if(r.personId.isNotBlank() && r.person.isBlank())e+="الصف ${r.n}: اسم الشخص مطلوب عندما يكون معرف الشخص موجوداً."
            if(r.transactionId.isNotBlank()){
                tx++; if(r.currency !in listOf("YER","SAR","USD"))e+="الصف ${r.n}: العملة يجب أن تكون YER أو SAR أو USD."; if(!seen.add(r.transactionId))e+="الصف ${r.n}: معرف العملية مكرر."; if(r.type !in allowedTypes)e+="الصف ${r.n}: نوع عملية العهدة غير صالح."; if(r.amount==null||r.amount<=0)e+="الصف ${r.n}: المبلغ يجب أن يكون موجباً وبمنزلتين عشريتين كحد أقصى."; if(r.date==null)e+="الصف ${r.n}: التاريخ غير صالح."; if(r.type in personTypes&&r.personId.isBlank())e+="الصف ${r.n}: هذه العملية تتطلب شخصاً."; if(r.type in personTypes&&r.person.isBlank())e+="الصف ${r.n}: اسم الشخص مطلوب لهذه العملية."; if(r.type in organizationTypes&&r.personId.isNotBlank())e+="الصف ${r.n}: عملية الجهة لا ترتبط بشخص."
            }
        }
        return ImportPreview(cs.size,ps.size,cs.size*3+ps.size*3,tx,e.distinct().take(100))
    }

    private fun parseWorkbook(context:Context,uri:Uri):List<Row>{
        openInput(context,uri).use{input->{
            val entries=readZip(input); val wb=entries["xl/workbook.xml"]?:error("ملف Excel غير صالح."); check(sheetCount(wb)==1){"يجب أن يحتوي ملف Excel على Sheet واحد فقط."}; val sheet=entries["xl/worksheets/sheet1.xml"]?:error("ورقة البيانات مفقودة."); val ss=entries["xl/sharedStrings.xml"]?.let(::sharedStrings)?:emptyList(); return parseSheet(sheet,ss)
        }}
    }

    private fun readZip(input:InputStream):Map<String,ByteArray>{val m=mutableMapOf<String,ByteArray>();ZipInputStream(input.buffered()).use{z->while(true){val e=z.nextEntry?:break;if(e.isDirectory)continue;val b=ByteArrayOutputStream();z.copyTo(b);m[e.name]=b.toByteArray()}};return m}
    private fun sheetCount(b:ByteArray):Int{val p=Xml.newPullParser();p.setInput(ByteArrayInputStream(b),"UTF-8");var n=0;var e=p.eventType;while(e!=XmlPullParser.END_DOCUMENT){if(e==XmlPullParser.START_TAG&&p.name=="sheet")n++;e=p.next()};return n}
    private fun sharedStrings(b:ByteArray):List<String>{val out=mutableListOf<String>();val p=Xml.newPullParser();p.setInput(ByteArrayInputStream(b),"UTF-8");var s="";var inT=false;var e=p.eventType;while(e!=XmlPullParser.END_DOCUMENT){if(e==XmlPullParser.START_TAG&&p.name=="t"){s="";inT=true};if(e==XmlPullParser.TEXT&&inT)s+=p.text;if(e==XmlPullParser.END_TAG&&p.name=="t"){out+=s;inT=false};e=p.next()};return out}
    private fun parseSheet(b:ByteArray,ss:List<String>):List<Row>{val out=mutableListOf<Row>();val p=Xml.newPullParser();p.setInput(ByteArrayInputStream(b),"UTF-8");var cells=mutableMapOf<Int,String>();var col=-1;var typ="";var v="";var inV=false;var rn=0;var e=p.eventType;while(e!=XmlPullParser.END_DOCUMENT){when(e){XmlPullParser.START_TAG->when(p.name){"row"->{cells=mutableMapOf();rn=p.getAttributeValue(null,"r")?.toIntOrNull()?:rn+1};"c"->{col=column(p.getAttributeValue(null,"r")?:"");typ=p.getAttributeValue(null,"t")?:""};"v","t"->{v="";inV=true}};XmlPullParser.TEXT->if(inV)v+=p.text;XmlPullParser.END_TAG->when(p.name){"v","t"->{if(col>=0)cells[col]=if(typ=="s")ss.getOrNull(v.toIntOrNull()?:-1)?:v else v;inV=false};"c"->col=-1;"row"->if(rn==1){val h=(0..19).map{cells[it]?:""};require(h==HEADERS){"أعمدة ملف Excel للعُهَد غير مطابقة للصيغة المعتمدة."}}else if(rn>1){out+=row(rn,(0..19).map{cells[it]?:""})}}};e=p.next()};check(out.isNotEmpty()){ "ملف Excel لا يحتوي على بيانات." };return out}
    private fun row(n:Int,a:List<String>)=Row(n,a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],a[8],a[9],a[10],a[11],a[12],a[13],a[14],a[15].uppercase(Locale.ROOT),a[16],parseAmount(a[17]),a[18],parseDate(a[19]))
    private fun parseAmount(v:String)=runCatching{if(v.isBlank())return null;BigDecimal(v.replace(',','.')).setScale(2,RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()}.getOrNull()
    private fun parseDate(v:String)=runCatching{SimpleDateFormat("yyyy-MM-dd",Locale.US).apply{isLenient=false}.parse(v)?.time}.getOrNull()
    private fun column(r:String):Int{val l=r.takeWhile{it.isLetter()};var x=0;l.forEach{x=x*26+(it.uppercaseChar()-'A'+1)};return x-1}
    private fun esc(v:String)=v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
    private fun createWorkbook(out:OutputStream,rows:List<Row>){ZipOutputStream(out.buffered()).use{z->entry(z,"[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");entry(z,"_rels/.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");entry(z,"xl/workbook.xml","<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"$SHEET_NAME\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>".replace("$SHEET_NAME",SHEET_NAME));entry(z,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");entry(z,"xl/worksheets/sheet1.xml",sheetXml(rows))}}
    private fun entry(z:ZipOutputStream,n:String,s:String){z.putNextEntry(ZipEntry(n));z.write(s.toByteArray(Charsets.UTF_8));z.closeEntry()}
    private fun sheetXml(rows:List<Row>):String{val data=mutableListOf<List<String>>();data+=HEADERS;rows.forEach{r->data+=listOf(r.custodyId,r.transactionId,r.personId,r.owner,r.ownerPhone,r.ownerAddress,r.ownerNotes,r.org,r.orgPhone,r.orgAddress,r.orgNotes,r.person,r.personPhone,r.personAddress,r.personNotes,r.currency,r.type,r.amount?.let{BigDecimal(it).movePointLeft(2).toPlainString()}?:"",r.description,r.date?.let{SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date(it))}?:"")};return buildString{append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");data.forEachIndexed{ri,row->append("<row r=\"${ri+1}\">");row.forEachIndexed{ci,value->append("<c r=\"${colName(ci)}${ri+1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(value)}</t></is></c>")};append("</row>")};append("</sheetData></worksheet>")}}
    private fun colName(i:Int):String{var n=i+1;val s=StringBuilder();while(n>0){val r=(n-1)%26;s.append(('A'.code+r).toChar());n=(n-1)/26};return s.reverse().toString()}
}
