package com.myaccounts.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.custody.*
import com.myaccounts.app.ui.components.*
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import com.myaccounts.app.util.CustodyReportExporter
import com.myaccounts.app.util.ReportShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

private val reportCurrencies=listOf("ALL","YER","SAR","USD")
private val reportPeriods=listOf("ALL","TODAY","WEEK","MONTH")

@Composable
fun CustodyReportsScreen(vm:CustodyViewModel,onBack:()->Unit,custodyId:Long?=null){
 val custodies by vm.custodies.collectAsState()
 val context=LocalContext.current
 val scope=rememberCoroutineScope()
 var currency by remember{mutableStateOf("ALL")}
 var period by remember{mutableStateOf("ALL")}
 var reportType by remember{mutableStateOf("PEOPLE")}
 var busy by remember{mutableStateOf(false)}
 var message by remember{mutableStateOf<String?>(null)}
 val selected=if(custodyId==null)custodies else custodies.filter{it.id==custodyId}
 var snapshots by remember { mutableStateOf<List<CustodyReportSnapshot>>(emptyList()) }
 LaunchedEffect(selected.map { it.id }) { snapshots = vm.reportSnapshots(selected.map { it.id }) }
 val data=snapshots.map{CustodyReportData(it.custody,it.people,it.transactions).filter(currency,period)}
 fun runExport(pdf:Boolean){
  if(busy)return
  busy=true
  scope.launch(Dispatchers.IO){
   val r=CustodyReportExporter.export(context,if(custodyId==null)"التقارير العامة للعهد" else "تقرير العهدة",data,currency,reportType,pdf)
   withContext(Dispatchers.Main){message=r.fold({it},{it.message?:"تعذر إنشاء التقرير."});busy=false}
  }
 }
 fun share(pdf:Boolean){
  if(busy)return
  busy=true
  val mime=if(pdf)"application/pdf" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  val prefix=if(custodyId==null)"MyAccounts_تقارير_العهد" else "MyAccounts_تقرير_عهدة"
  scope.launch(Dispatchers.IO){
   val r=ReportShareUtil.shareGeneratedReport(context,prefix,mime){
    CustodyReportExporter.export(context,if(custodyId==null)"التقارير العامة للعهد" else "تقرير العهدة",data,currency,reportType,pdf)
   }
   withContext(Dispatchers.Main){message=r.fold({"تم فتح خيارات مشاركة التقرير."},{it.message?:"تعذر مشاركة التقرير."});busy=false}
  }
 }
 Scaffold(topBar={AppTopBar(if(custodyId==null)"تقارير العُهَد" else "تقرير العهدة",onBack)}){padding->
  LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   item{
    SummaryCard(if(custodyId==null)"مركز التقارير" else "حساب العهدة"){
     Text(if(custodyId==null)"عرض العهد والعملات دون جمع العملات المختلفة." else "تقرير هذه العهدة بنفس بنية تقرير حساب الشخص.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
     if(custodyId!=null)selected.firstOrNull()?.let{c->Text(c.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text("الجهة: "+c.organizationName+"  •  الحامل: "+c.holderName,style=MaterialTheme.typography.bodySmall)}
     Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){reportCurrencies.forEach{c->FilterChip(currency==c,{currency=c},label={Text(if(c=="ALL")"الكل" else c)},enabled=!busy)}}
    }
   }
   item{InformationCard{Text("الفترة",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){reportPeriods.forEach{p->FilterChip(period==p,{period=p},label={Text(periodName(p))},enabled=!busy)}}}}
   item{InformationCard{
    Text("نوع التقرير",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
     FilterChip(reportType=="PEOPLE",{reportType="PEOPLE"},label={Text("أصحاب العُهَد")})
     FilterChip(reportType=="DETAILED",{reportType="DETAILED"},label={Text("العمليات")})
     FilterChip(reportType=="SUMMARY",{reportType="SUMMARY"},label={Text("الأرصدة")})
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){PrimaryButton("Excel",{runExport(false)},Modifier.weight(1f),enabled=!busy);PrimaryButton("PDF",{runExport(true)},Modifier.weight(1f),enabled=!busy)}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){SecondaryButton("مشاركة Excel",{share(false)},Modifier.weight(1f),enabled=!busy);SecondaryButton("مشاركة PDF",{share(true)},Modifier.weight(1f),enabled=!busy)}
   }}
   item{CustodyOverallPreview(data,currency)}
   if(reportType=="PEOPLE")data.forEachIndexed{i,d->item(key="people_"+i){CustodyPeoplePreview(d,currency)}}
   else if(reportType=="SUMMARY")data.forEachIndexed{i,d->item(key="summary_"+i){CustodySummaryPreview(d,currency)}}
   else data.forEach{d->items(d.transactions,key={it.id}){t->
    val person=d.people.firstOrNull{it.id==t.personId}?.name
    CustodyOperationCard(operationType=typeName(t.type),amount=money(t.amountMinor)+" "+t.currencyCode,currency=t.currencyCode,date=SimpleDateFormat("dd/MM/yyyy HH:mm",Locale("ar")).format(Date(t.transactionDate)),description=listOfNotNull(d.custody.name,person,t.description.ifBlank{null}).joinToString(" — "),tone=tone(t.type))
   }}
   if(data.isEmpty())item{EmptyState(EmptyStateType.Reports,"لا توجد عُهَد","لا توجد بيانات متاحة لإصدار التقرير.")}
  }
 }
 message?.let{m->AlertDialog(onDismissRequest={message=null},text={Text(m)},confirmButton={TextButton({message=null}){Text("موافق")}})}
}

private data class CustodyReportData(val custody:CustodyEntity,val people:List<CustodyPersonEntity>,val transactions:List<CustodyTransactionEntity>){
 fun filter(currency:String,period:String):CustodyReportData{
  val now=System.currentTimeMillis()
  val start=when(period){"TODAY"->dayStart(now);"WEEK"->Calendar.getInstance().apply{timeInMillis=dayStart(now);set(Calendar.DAY_OF_WEEK,firstDayOfWeek)}.timeInMillis;"MONTH"->Calendar.getInstance().apply{timeInMillis=dayStart(now);set(Calendar.DAY_OF_MONTH,1)}.timeInMillis;else->null}
  val end=when(period){"TODAY"->dayEnd(now);"WEEK"->addDays(start!!,7);"MONTH"->addMonths(start!!,1);else->null}
  return copy(transactions=transactions.filter{(currency=="ALL"||it.currencyCode==currency)&&(start==null||it.transactionDate>=start&&it.transactionDate<end!!)}.sortedBy{it.transactionDate})
 }
}

@Composable private fun CustodyOverallPreview(data:List<CustodyReportData>,currency:String){
 val codes=if(currency=="ALL")listOf("YER","SAR","USD")else listOf(currency)
 SummaryCard("ملخص الأرصدة"){
  Row(Modifier.fillMaxWidth()){Text("البيان",Modifier.weight(1.3f),fontWeight=FontWeight.Bold);codes.forEach{Text(it,Modifier.weight(1f),fontWeight=FontWeight.Bold)}}
  summaryRow("إجمالي الاستلام",codes){c->data.sumOf{d->d.transactions.filter{it.currencyCode==c&&it.type==CustodyTransactionType.RECEIVED_FROM_ORG}.sumOf{it.amountMinor}}}
  summaryRow("إجمالي الصرف",codes){c->data.sumOf{d->d.transactions.filter{it.currencyCode==c&&it.type==CustodyTransactionType.PAID_TO_PERSON}.sumOf{it.amountMinor}}}
  summaryRow("مرتجع من الأشخاص",codes){c->data.sumOf{d->d.transactions.filter{it.currencyCode==c&&it.type==CustodyTransactionType.RETURNED_FROM_PERSON}.sumOf{it.amountMinor}}}
  summaryRow("مرتجع للجهة",codes){c->data.sumOf{d->d.transactions.filter{it.currencyCode==c&&it.type==CustodyTransactionType.RETURNED_TO_ORG}.sumOf{it.amountMinor}}}
  summaryRow("ذمة الجهة",codes){c->data.sumOf{d->d.transactions.filter{it.currencyCode==c}.sumOf{CustodyBalanceRules.ownerOrgDebtDelta(it.type,it.amountMinor)}}}
  summaryRow("ذمم الأطراف",codes){c->data.sumOf{d->d.transactions.filter{it.currencyCode==c}.sumOf{CustodyBalanceRules.ownerPeopleDebtDelta(it.type,it.amountMinor)}}}
  summaryRow("المتبقي النقدي",codes){c->data.sumOf{d->d.transactions.filter{it.currencyCode==c}.sumOf{CustodyBalanceRules.ownerCashDelta(it.type,it.amountMinor)}}}
 }
}
@Composable private fun summaryRow(label:String,codes:List<String>,value:(String)->Long){Row(Modifier.fillMaxWidth().padding(vertical=3.dp)){Text(label,Modifier.weight(1.3f),style=MaterialTheme.typography.bodySmall);codes.forEach{c->Text(money(value(c)),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.Bold)}}}
@Composable private fun CustodyPeoplePreview(d:CustodyReportData,currency:String){
 val codes=if(currency=="ALL")listOf("YER","SAR","USD")else listOf(currency)
 SummaryCard(d.custody.name){
  Text("الجهة: "+d.custody.organizationName+"  •  الحامل: "+d.custody.holderName,style=MaterialTheme.typography.bodySmall)
  Row(Modifier.fillMaxWidth()){Text("الطرف",Modifier.weight(1.2f),fontWeight=FontWeight.Bold);codes.forEach{Text(it,Modifier.weight(1f),fontWeight=FontWeight.Bold)}}
  d.people.forEach{p->Row(Modifier.fillMaxWidth().padding(vertical=3.dp)){Text(p.name,Modifier.weight(1.2f),maxLines=1);codes.forEach{c->val custody=d.transactions.filter{it.personId==p.id&&it.currencyCode==c}.sumOf{CustodyBalanceRules.personCustodyDelta(it.type,it.amountMinor)};val debt=d.transactions.filter{it.personId==p.id&&it.currencyCode==c}.sumOf{CustodyBalanceRules.personDebtDelta(it.type,it.amountMinor)};Text(money(custody)+" / "+money(debt),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)}}}
 }
}
@Composable private fun CustodySummaryPreview(d:CustodyReportData,currency:String){
 val codes=if(currency=="ALL")listOf("YER","SAR","USD")else listOf(currency)
 SummaryCard(d.custody.name){
  Text("الجهة: "+d.custody.organizationName+"  •  الحامل: "+d.custody.holderName,style=MaterialTheme.typography.bodySmall)
  codes.forEach{c->val rows=d.transactions.filter{it.currencyCode==c};InformationCard{Text(c,fontWeight=FontWeight.Bold);val cash=rows.sumOf{CustodyBalanceRules.ownerCashDelta(it.type,it.amountMinor)};val org=rows.sumOf{CustodyBalanceRules.ownerOrgDebtDelta(it.type,it.amountMinor)};val people=rows.sumOf{CustodyBalanceRules.ownerPeopleDebtDelta(it.type,it.amountMinor)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){BalanceAmount("نقد "+money(kotlin.math.abs(cash)),if(cash>0)BalanceStatus.Owed else if(cash<0)BalanceStatus.Due else BalanceStatus.Neutral);BalanceAmount("ذمم "+money(kotlin.math.abs(org+people)),if(org+people>0)BalanceStatus.Due else if(org+people<0)BalanceStatus.Owed else BalanceStatus.Neutral)}}}
 }
}
private fun typeName(t:String)=when(t){CustodyTransactionType.RECEIVED_FROM_ORG->"استلام من الجهة";CustodyTransactionType.PAID_TO_PERSON->"صرف للشخص";CustodyTransactionType.RETURNED_FROM_PERSON->"مرتجع من الشخص";CustodyTransactionType.RETURNED_TO_ORG->"مرتجع للجهة";CustodyTransactionType.ORG_LOAN_FROM_OWNER->"ذمة للجهة من الحامل";CustodyTransactionType.ORG_LOAN_REPAYMENT->"سداد ذمة الجهة";CustodyTransactionType.PERSON_LOAN_TO_OWNER->"اقتراض من الشخص";CustodyTransactionType.OWNER_REPAY_PERSON_LOAN->"سداد قرض الشخص";else->t}
private fun tone(t:String)=when(t){CustodyTransactionType.RECEIVED_FROM_ORG,CustodyTransactionType.RETURNED_FROM_PERSON,CustodyTransactionType.ORG_LOAN_REPAYMENT,CustodyTransactionType.PERSON_LOAN_TO_OWNER->CustodyOperationTone.ReceiveFromOrganization;CustodyTransactionType.PAID_TO_PERSON,CustodyTransactionType.RETURNED_TO_ORG,CustodyTransactionType.ORG_LOAN_FROM_OWNER,CustodyTransactionType.OWNER_REPAY_PERSON_LOAN->CustodyOperationTone.PayToPerson;else->CustodyOperationTone.Neutral}
private fun money(v:Long)=BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun periodName(v:String)=when(v){"ALL"->"كل الحساب";"TODAY"->"اليوم";"WEEK"->"الأسبوع";else->"الشهر"}
private fun dayStart(v:Long)=Calendar.getInstance().apply{timeInMillis=v;set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis
private fun dayEnd(v:Long)=addDays(dayStart(v),1)
private fun addDays(v:Long,d:Int)=Calendar.getInstance().apply{timeInMillis=v;add(Calendar.DAY_OF_MONTH,d)}.timeInMillis
private fun addMonths(v:Long,m:Int)=Calendar.getInstance().apply{timeInMillis=v;add(Calendar.MONTH,m)}.timeInMillis
