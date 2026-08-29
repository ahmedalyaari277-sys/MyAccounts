package com.myaccounts.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.custody.*
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

private val CURRENCIES=listOf("YER","SAR","USD")
private fun money(v:Long)=BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun signed(v:Long)=when{v>0->"عليه "+money(v);v<0->"له "+money(-v);else->"متوازن 0"}
private fun positive(t:String)=t=="RECEIVED_FROM_ORG"||t=="RETURNED_FROM_PERSON"
private fun typeName(t:String)=when(t){"RECEIVED_FROM_ORG"->"استلام من الجهة";"PAID_TO_PERSON"->"صرف للشخص";"RETURNED_FROM_PERSON"->"مرتجع من الشخص";else->"مرتجع للجهة / تصفية"}

@Composable fun AppGatewayScreen(onAccounts:()->Unit,onCustodies:()->Unit,onSettings:()->Unit){
 Scaffold(topBar={TopAppBar(title={Text("MyAccounts")},actions={TextButton(onClick=onSettings){Text("الإعدادات")}})}){p->Column(Modifier.fillMaxSize().padding(p).padding(24.dp),verticalArrangement=Arrangement.Center){Button(onClick=onAccounts,Modifier.fillMaxWidth()){Text("دفتر الحسابات")};Spacer(Modifier.height(16.dp));Button(onClick=onCustodies,Modifier.fillMaxWidth()){Text("العُهَد")}}}}

@Composable fun CustodyHomeScreen(vm:CustodyViewModel,onBack:()->Unit,onOpen:(Long)->Unit){
 val list by vm.custodies.collectAsState();var add by remember{mutableStateOf(false)}
 Scaffold(topBar={TopAppBar(title={Text("العُهَد")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"رجوع")}})},floatingActionButton={FloatingActionButton(onClick={add=true}){Icon(Icons.Default.Add,"إضافة عهدة")}}){p->LazyColumn(Modifier.fillMaxSize().padding(p).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){items(list,key={it.id}){c->Card(Modifier.fillMaxWidth().clickable{onOpen(c.id)}){Column(Modifier.padding(16.dp)){Text(c.name,fontWeight=FontWeight.Bold);Text("الجهة: "+c.organizationName);if(c.phone.isNotBlank())Text(c.phone)}}}}}}
 if(add)AddCustodyDialog({add=false}){c->vm.create(c);add=false}
}

@Composable private fun AddCustodyDialog(close:()->Unit,save:(CustodyEntity)->Unit){
 var n by remember{mutableStateOf("")};var ph by remember{mutableStateOf("")};var a by remember{mutableStateOf("")};var no by remember{mutableStateOf("")};var o by remember{mutableStateOf("")};var op by remember{mutableStateOf("")};var oa by remember{mutableStateOf("")};var on by remember{mutableStateOf("")}
 AlertDialog(onDismissRequest=close,title={Text("إضافة صاحب عهدة")},text={LazyColumn{item{Field("اسم صاحب العهدة",n){n=it}};item{Field("الهاتف",ph){ph=it}};item{Field("العنوان",a){a=it}};item{Field("الملاحظات",no){no=it}};item{Text("بيانات الجهة",fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=8.dp))};item{Field("اسم الجهة",o){o=it}};item{Field("هاتف الجهة",op){op=it}};item{Field("عنوان الجهة",oa){oa=it}};item{Field("ملاحظات الجهة",on){on=it}}}},confirmButton={Button(enabled=n.isNotBlank()&&o.isNotBlank(),onClick={save(CustodyEntity(name=n.trim(),phone=ph.trim(),address=a.trim(),notes=no.trim(),organizationName=o.trim(),organizationPhone=op.trim(),organizationAddress=oa.trim(),organizationNotes=on.trim()))}){Text("حفظ")}},dismissButton={TextButton(onClick=close){Text("إلغاء")}})
}
@Composable private fun Field(l:String,v:String,f:(String)->Unit){OutlinedTextField(v,f,label={Text(l)},modifier=Modifier.fillMaxWidth())}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CustodyDetailScreen(vm:CustodyViewModel,id:Long,onBack:()->Unit){
 val c by vm.custody(id).collectAsState();val ps by vm.persons(id).collectAsState();val ac by vm.accounts(id).collectAsState();val tx by vm.transactions(id).collectAsState();var cur by remember{mutableStateOf("YER")};var addP by remember{mutableStateOf(false)};var addT by remember{mutableStateOf(false)};var report by remember{mutableStateOf(false)};var menu by remember{mutableStateOf(false)};if(c==null)return
 val owner=ac.firstOrNull{it.holderType=="OWNER"&&it.currencyCode==cur};val ownerTx=tx.filter{it.accountId==owner?.id};val ownerBalance=ownerTx.sumOf{if(positive(it.type))it.amountMinor else -it.amountMinor}
 Scaffold(topBar={TopAppBar(title={Text(c!!.name)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"رجوع")}},actions={Box{IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"المزيد")};DropdownMenu(menu,{menu=false}){DropdownMenuItem(text={Text("تقرير العهدة")},onClick={menu=false;report=true});DropdownMenuItem(text={Text("أرشفة العهدة")},onClick={menu=false;vm.archive(id);onBack()})}}})},floatingActionButton={FloatingActionButton(onClick={addT=true}){Icon(Icons.Default.Add,"إضافة عملية")}}){p->Column(Modifier.fillMaxSize().padding(p).padding(12.dp)){Text("الجهة: "+c!!.organizationName,fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp));Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){CURRENCIES.forEach{x->FilterChip(cur==x,{cur=x},{Text(x)})}};Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("حساب صاحب العهدة — "+cur,fontWeight=FontWeight.Bold);Text(signed(ownerBalance),fontWeight=FontWeight.Bold)}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("الأشخاص",fontWeight=FontWeight.Bold);TextButton(onClick={addP=true}){Text("إضافة شخص")}};LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(ps,key={it.id}){p->val a=ac.firstOrNull{it.personId==p.id&&it.currencyCode==cur};val b=a?.let{aa->tx.filter{it.accountId==aa.id}.sumOf{if(positive(it.type))it.amountMinor else -it.amountMinor}}?:0;Card(Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(p.name,fontWeight=FontWeight.Bold);Text(signed(b),fontWeight=FontWeight.Bold)}}}}}}
 if(addP)AddPersonDialog({addP=false}){n,ph,a,no->vm.addPerson(id,CustodyPersonEntity(custodyId=id,name=n,phone=ph,address=a,notes=no));addP=false}
 if(addT)AddCustodyTransactionDialog(ps,cur,{addT=false}){cc,t,pid,m,d->vm.addTransaction(id,cc,t,pid,m,d,System.currentTimeMillis());addT=false}
 if(report)CustodyReportDialog(c!!,ps,ac,tx,{report=false})
}

@Composable private fun AddPersonDialog(close:()->Unit,save:(String,String,String,String)->Unit){var n by remember{mutableStateOf("")};var p by remember{mutableStateOf("")};var a by remember{mutableStateOf("")};var no by remember{mutableStateOf("")};AlertDialog(onDismissRequest=close,title={Text("إضافة شخص")},text={Column{Field("الاسم",n){n=it};Field("الهاتف",p){p=it};Field("العنوان",a){a=it};Field("الملاحظات",no){no=it}}},confirmButton={Button(enabled=n.isNotBlank(),onClick={save(n.trim(),p.trim(),a.trim(),no.trim())}){Text("حفظ")}},dismissButton={TextButton(onClick=close){Text("إلغاء")}})}

@Composable private fun AddCustodyTransactionDialog(people:List<CustodyPersonEntity>,defaultCurrency:String,close:()->Unit,save:(String,String,Long?,Long,String)->Unit){var cur by remember{mutableStateOf(defaultCurrency)};var type by remember{mutableStateOf("RECEIVED_FROM_ORG")};var pid by remember{mutableStateOf<Long?>(null)};var value by remember{mutableStateOf("")};var desc by remember{mutableStateOf("")};val needsPerson=type=="PAID_TO_PERSON"||type=="RETURNED_FROM_PERSON";AlertDialog(onDismissRequest=close,title={Text("إضافة عملية مالية")},text={LazyColumn{item{Text("نوع العملية",fontWeight=FontWeight.Bold));listOf("RECEIVED_FROM_ORG" to "استلام من الجهة","PAID_TO_PERSON" to "صرف للشخص","RETURNED_FROM_PERSON" to "مرتجع من الشخص","RETURNED_TO_ORG" to "مرتجع للجهة / تصفية").forEach{(k,l)->item{Row{RadioButton(type==k,{type=k;if(!needsPerson)pid=null});Text(l)}}};item{Row{CURRENCIES.forEach{x->FilterChip(cur==x,{cur=x},{Text(x)})}}};if(needsPerson){item{Text("الشخص",fontWeight=FontWeight.Bold)};people.forEach{person->item{Row{RadioButton(pid==person.id,{pid=person.id});Text(person.name)}}}};item{Field("المبلغ",value){value=it}};item{Field("البيان",desc){desc=it}}}},confirmButton={Button(enabled=value.toBigDecimalOrNull()!=null&&value.toBigDecimal() > BigDecimal.ZERO&&(!needsPerson||pid!=null),onClick={save(cur,type,pid,value.toBigDecimal().movePointRight(2).longValueExact(),desc)}){Text("حفظ")}},dismissButton={TextButton(onClick=close){Text("إلغاء")}})}

@Composable private fun CustodyReportDialog(c:CustodyEntity,people:List<CustodyPersonEntity>,accounts:List<CustodyAccountEntity>,tx:List<CustodyTransactionEntity>,close:()->Unit){var cur by remember{mutableStateOf("YER")};val owner=accounts.firstOrNull{it.holderType=="OWNER"&&it.currencyCode==cur};val own=tx.filter{it.accountId==owner?.id};val b=own.sumOf{if(positive(it.type))it.amountMinor else -it.amountMinor};AlertDialog(onDismissRequest=close,title={Text("تقرير العهدة")},text={LazyColumn{item{Text(c.name,fontWeight=FontWeight.Bold);Text("الجهة: "+c.organizationName);Row{CURRENCIES.forEach{x->FilterChip(cur==x,{cur=x},{Text(x)})}};Text("رصيد صاحب العهدة: "+signed(b),fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));Text("العمليات",fontWeight=FontWeight.Bold)};items(tx.filter{it.currencyCode==cur},key={it.id}){t->Card(Modifier.fillMaxWidth().padding(vertical=3.dp)){Column(Modifier.padding(8.dp)){Text(typeName(t.type),fontWeight=FontWeight.Bold);Text("المبلغ: "+money(t.amountMinor));if(t.description.isNotBlank())Text(t.description);Text(SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(Date(t.transactionDate)),style=MaterialTheme.typography.bodySmall)}}}}},confirmButton={TextButton(onClick=close){Text("إغلاق")}})}
