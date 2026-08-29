package com.myaccounts.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.custody.*
import java.math.BigDecimal

private val reportCurrencies = listOf("ALL", "YER", "SAR", "USD")
private fun amount(v: Long) = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyReportsScreen(vm: com.myaccounts.app.ui.viewmodel.CustodyViewModel, onBack:()->Unit) {
    val custodies by vm.custodies.collectAsState()
    var currency by remember { mutableStateOf("ALL") }
    var mode by remember { mutableStateOf(0) }
    Scaffold(topBar={TopAppBar(title={Text("تقارير العُهَد")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"رجوع")}})}){p->
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            item{Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("أصحاب العُهَد","الأرصدة","العمليات").forEachIndexed{i,s->FilterChip(selected=mode==i,onClick={mode=i},label={Text(s)})}}}
            item{Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){reportCurrencies.forEach{c->FilterChip(selected=currency==c,onClick={currency=c},label={Text(c)})}}}
            items(custodies,key={it.id}){c->
                val people by vm.persons(c.id).collectAsState(initial=emptyList())
                val tx by vm.transactions(c.id).collectAsState(initial=emptyList())
                val filtered=tx.filter{currency=="ALL"||it.currencyCode==currency}
                val currencies=if(currency=="ALL") listOf("YER","SAR","USD") else listOf(currency)
                Card(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(c.name,fontWeight=FontWeight.Bold);Text("الجهة: ${c.organizationName}")
                        when(mode){
                            0->{Text("عدد الأشخاص: ${people.size}");currencies.forEach{cur->val b=filtered.filter{it.currencyCode==cur}.sumOf{CustodyBalanceRules.ownerDelta(it.type,it.amountMinor)};Text("$cur — ${if(b>=0)"عليه" else "له"} ${amount(kotlin.math.abs(b))}")}}
                            1->{currencies.forEach{cur->val r=filtered.filter{it.currencyCode==cur};val received=r.filter{it.type==CustodyTransactionType.RECEIVED_FROM_ORG}.sumOf{it.amountMinor};val paid=r.filter{it.type==CustodyTransactionType.PAID_TO_PERSON}.sumOf{it.amountMinor};val returned=r.filter{it.type==CustodyTransactionType.RETURNED_FROM_PERSON}.sumOf{it.amountMinor};val toOrg=r.filter{it.type==CustodyTransactionType.RETURNED_TO_ORG}.sumOf{it.amountMinor};val b=received-paid+returned-toOrg;Text("$cur — مستلم ${amount(received)} | مصروف ${amount(paid)} | مرتجع أشخاص ${amount(returned)} | مرتجع جهة ${amount(toOrg)} | رصيد ${if(b>=0)"عليه" else "له"} ${amount(kotlin.math.abs(b))}")}}
                            2->{filtered.sortedByDescending{it.transactionDate}.forEach{t->Text("${t.currencyCode} — ${t.type} — ${amount(t.amountMinor)} — ${t.description}")}}
                        }
                    }
                }
            }
        }
    }
}
