package com.myaccounts.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myaccounts.app.data.custody.*
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import java.math.BigDecimal

private val personCurrencies=listOf("YER","SAR","USD")
private fun personMoney(v:Long)=BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun personSigned(v:Long)=when{v>0->"عليه ${personMoney(v)}";v<0->"له ${personMoney(-v)}";else->"متوازن 0"}
private fun personType(t:String)=when(t){CustodyTransactionType.PAID_TO_PERSON->"صرف للشخص";CustodyTransactionType.RETURNED_FROM_PERSON->"مرتجع من الشخص";else->t}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CustodyPersonOperationsScreen(vm:CustodyViewModel,custodyId:Long,personId:Long,onBack:()->Unit){
 val people by vm.persons(custodyId).collectAsState();val tx by vm.transactions(custodyId).collectAsState();val person=people.firstOrNull{it.id==personId}?:return
 var currency by remember{mutableStateOf("YER")};var add by remember{mutableStateOf(false)};var edit by remember{mutableStateOf<CustodyTransactionEntity?>(null)};var del by remember{mutableStateOf<CustodyTransactionEntity?>(null)}
 val rows=tx.filter{it.personId==personId&&it.currencyCode==currency}.sortedByDescending{it.transactionDate};val balance=rows.sumOf{CustodyBalanceRules.personDelta(it.type,it.amountMinor)}
 Scaffold(topBar={TopAppBar(title={Text(person.name,fontWeight=FontWeight.Bold)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"رجوع")}})},floatingActionButton={FloatingActionButton(onClick={add=true}){Icon(Icons.Default.Add,"إضافة عملية")}}){p->LazyColumn(Modifier.fillMaxSize().padding(p).padding(12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
  item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(13.dp)){Text("الرصيد — $currency",fontWeight=FontWeight.Bold);Text(personSigned(balance),fontSize=21.sp,fontWeight=FontWeight.Bold,color=when{balance>0->MaterialTheme.colorScheme.error;balance<0->MaterialTheme.colorScheme.secondary;else->MaterialTheme.colorScheme.primary})}}}
  item{Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){personCurrencies.forEach{FilterChip(currency==it,onClick={currency=it},label={Text(it)})}}}
  items(rows,key={it.id}){t->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(personType(t.type),fontWeight=FontWeight.Bold);Text(personMoney(t.amountMinor),style=MaterialTheme.typography.titleMedium)};if(t.description.isNotBlank())Text(t.description,style=MaterialTheme.typography.bodySmall);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){IconButton(onClick={edit=t}){Icon(Icons.Default.Edit,"تعديل")};IconButton(onClick={del=t}){Icon(Icons.Default.Delete,"حذف")}}}}}
 }}
 if(add)CustodyOperationDialog(vm,custodyId,listOf(person),currency,CustodyTransactionType.PAID_TO_PERSON,null,{add=false},personId){add=false}
 edit?.let{t->CustodyOperationDialog(vm,custodyId,listOf(person),t.currencyCode,t.type,t,{edit=null},personId){edit=null}}
 del?.let{t->AlertDialog(onDismissRequest={del=null},title={Text("حذف العملية")},text={Text("سيتم حذف العملية نهائيًا.")},confirmButton={TextButton(onClick={vm.deleteTransaction(t.id);del=null}){Text("حذف",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={del=null}){Text("إلغاء")}})}
}
