package com.myaccounts.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.ui.components.AppTopBar
import com.myaccounts.app.ui.components.BalanceAmount
import com.myaccounts.app.ui.components.BalanceStatus
import com.myaccounts.app.ui.components.EmptyState
import com.myaccounts.app.ui.components.EmptyStateType
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.viewmodel.CustodyViewModel

private val custodyHomeCurrencies=listOf("YER","SAR","USD")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyHomeWithArchiveScreen(vm:CustodyViewModel,onBack:()->Unit,onOpen:(Long)->Unit,onArchive:()->Unit,onReports:()->Unit,onBackupRestore:()->Unit,onTransfer:()->Unit){
    val custodies by vm.custodies.collectAsState();var adding by remember{mutableStateOf(false)};var showMoreMenu by remember{mutableStateOf(false)}
    Scaffold(topBar={AppTopBar(title="العُهَد",onBack=onBack,actions={IconButton(onClick=onReports){Icon(Icons.Default.Assessment,"التقارير")};IconButton(onClick={showMoreMenu=true}){Icon(Icons.Default.MoreVert,"المزيد من الخيارات")};DropdownMenu(expanded=showMoreMenu,onDismissRequest={showMoreMenu=false}){DropdownMenuItem(text={Text("النقل والاستيراد والتصدير")},leadingIcon={Icon(Icons.Default.Archive,null)},onClick={showMoreMenu=false;onTransfer()});DropdownMenuItem(text={Text("النسخ الاحتياطي والاستعادة")},leadingIcon={Icon(Icons.Default.Backup,null)},onClick={showMoreMenu=false;onBackupRestore()});DropdownMenuItem(text={Text("الأرشيف")},leadingIcon={Icon(Icons.Default.Archive,null)},onClick={showMoreMenu=false;onArchive()})}})},floatingActionButton={FloatingActionButton(onClick={adding=true}){Icon(Icons.Default.Add,"إضافة عهدة")}}){padding->LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){if(custodies.isEmpty())item{EmptyState(type=EmptyStateType.Custody,title="لا توجد عُهَد",description="أضف أول عهدة للبدء في متابعة أصحاب العُهَد والعمليات المالية.")};items(custodies,key={it.id}){custody->val accounts by vm.accounts(custody.id).collectAsState(initial=emptyList());InformationCard(Modifier.fillMaxWidth().clickable{onOpen(custody.id)}){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(custody.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("الجهة: ${custody.organizationName}",style=MaterialTheme.typography.bodyLarge);if(custody.phone.isNotBlank())Text(custody.phone,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}};Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){custodyHomeCurrencies.forEach{code->val balance=accounts.firstOrNull{it.holderType=="OWNER"&&it.personId==null&&it.currencyCode==code}?.balanceMinor?:0L;InformationCard(Modifier.padding(0.dp)){Text(code,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold);BalanceAmount(amount=when{balance>0->"عليه ${balance/100.0}";balance<0->"له ${(-balance)/100.0}";else->"متوازن 0"},status=when{balance>0->BalanceStatus.Due;balance<0->BalanceStatus.Owed;else->BalanceStatus.Neutral},label=code)}}}}}}}}
    if(adding)CustodyCreateDialog(onDismiss={adding=false}){vm.create(it);adding=false}
}

@Composable private fun CustodyCreateDialog(onDismiss:()->Unit,onSave:(CustodyEntity)->Unit){
    var name by remember{mutableStateOf("")}
    var phone by remember{mutableStateOf("")}
    var address by remember{mutableStateOf("")}
    var notes by remember{mutableStateOf("")}
    var organization by remember{mutableStateOf("")}
    var organizationPhone by remember{mutableStateOf("")}
    var organizationAddress by remember{mutableStateOf("")}
    var organizationNotes by remember{mutableStateOf("")}
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("إضافة صاحب عهدة")},
        text={Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("بيانات حامل العهدة",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("اسم صاحب العهدة")},singleLine=true)
            OutlinedTextField(phone,{phone=it},Modifier.fillMaxWidth(),label={Text("هاتف صاحب العهدة")},singleLine=true)
            OutlinedTextField(address,{address=it},Modifier.fillMaxWidth(),label={Text("عنوان صاحب العهدة")},singleLine=true)
            OutlinedTextField(notes,{notes=it},Modifier.fillMaxWidth(),label={Text("ملاحظات صاحب العهدة")},minLines=2)
            Text("بيانات جهة العهدة",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            OutlinedTextField(organization,{organization=it},Modifier.fillMaxWidth(),label={Text("اسم جهة العهدة")},singleLine=true)
            OutlinedTextField(organizationPhone,{organizationPhone=it},Modifier.fillMaxWidth(),label={Text("هاتف جهة العهدة")},singleLine=true)
            OutlinedTextField(organizationAddress,{organizationAddress=it},Modifier.fillMaxWidth(),label={Text("عنوان جهة العهدة")},singleLine=true)
            OutlinedTextField(organizationNotes,{organizationNotes=it},Modifier.fillMaxWidth(),label={Text("ملاحظات جهة العهدة")},minLines=2)
        }},
        confirmButton={TextButton(enabled=name.isNotBlank()&&organization.isNotBlank(),onClick={onSave(CustodyEntity(name=name.trim(),phone=phone.trim(),address=address.trim(),notes=notes.trim(),organizationName=organization.trim(),organizationPhone=organizationPhone.trim(),organizationAddress=organizationAddress.trim(),organizationNotes=organizationNotes.trim()))}){Text("حفظ")}},
        dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}}
    )
}
