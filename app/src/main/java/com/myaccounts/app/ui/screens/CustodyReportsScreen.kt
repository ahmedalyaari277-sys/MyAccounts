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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.custody.*
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import com.myaccounts.app.data.custody.CustodyFinancialSummary
import com.myaccounts.app.util.CustodyReportExporter
import com.myaccounts.app.util.ReportShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal

private val reportCurrencies = listOf("ALL", "YER", "SAR", "USD")
private fun amount(v: Long) = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun debtLabel(value: Long, positive: String, negative: String) = when { value > 0 -> positive; value < 0 -> negative; else -> "متوازن" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyReportsScreen(vm: CustodyViewModel, onBack: () -> Unit) {
    val custodies by vm.custodies.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currency by remember { mutableStateOf("ALL") }
    var mode by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("تقارير العُهَد") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }) }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("العهد والأشخاص", "الأرصدة", "العمليات").forEachIndexed { i, s -> FilterChip(selected = mode == i, onClick = { mode = i }, label = { Text(s) }) } }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { reportCurrencies.forEach { c -> FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) }) } }
            }
            items(custodies, key = { it.id }) { c ->
                val people by vm.persons(c.id).collectAsState(initial = emptyList())
                val tx by vm.transactions(c.id).collectAsState(initial = emptyList())
                val accounts by vm.accounts(c.id).collectAsState(initial = emptyList())
                val filtered = tx.filter { currency == "ALL" || it.currencyCode == currency }
                val currencies = if (currency == "ALL") listOf("YER", "SAR", "USD") else listOf(currency)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(c.name, fontWeight = FontWeight.Bold)
                        Text("الجهة: ${c.organizationName}")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { scope.launch(Dispatchers.IO) { message = CustodyReportExporter.exportExcel(context, c, filtered, currency).fold({ "تم إنشاء تقرير Excel للعهدة." }, { "تعذر إنشاء Excel: ${it.message}" }) } }, modifier = Modifier.weight(1f)) { Text("Excel") }
                            Button(onClick = { scope.launch(Dispatchers.IO) { message = CustodyReportExporter.exportPdf(context, c, filtered, currency).fold({ "تم إنشاء تقرير PDF للعهدة." }, { "تعذر إنشاء PDF: ${it.message}" }) } }, modifier = Modifier.weight(1f)) { Text("PDF") }
                            OutlinedButton(onClick = { scope.launch(Dispatchers.IO) { val result = ReportShareUtil.shareGeneratedReport(context, "MyAccounts_تقرير_عهدة_${c.name}", "application/pdf") { CustodyReportExporter.exportPdf(context, c, filtered, currency) }; message = result.fold({ "تم فتح خيارات مشاركة التقرير." }, { "تعذر مشاركة التقرير: ${it.message}" }) } }, modifier = Modifier.weight(1f)) { Text("مشاركة") }
                        }
                        when (mode) {
                            0 -> {
                                Text("عدد الأشخاص: ${people.size}")
                                currencies.forEach { cur ->
                                    val owner = CustodyFinancialSummary.custodyOwnerBalance(filtered, cur)
                                    val peopleTotal = people.sumOf { p -> CustodyFinancialSummary.personCustodyBalance(filtered, p.id, cur) }
                                    val holder = accounts.firstOrNull { it.holderType == "OWNER" && it.personId == null && it.currencyCode == cur }?.balanceMinor ?: owner - peopleTotal
                                    Text("$cur — العهدة: ${amount(kotlin.math.abs(owner))} | لدى الحامل: ${amount(kotlin.math.abs(holder))} | لدى الأشخاص: ${amount(kotlin.math.abs(peopleTotal))}")
                                }
                            }
                            1 -> currencies.forEach { cur ->
                                val received = filtered.filter { it.currencyCode == cur && it.type == CustodyTransactionType.RECEIVED_FROM_ORG }.sumOf { it.amountMinor }
                                val paid = filtered.filter { it.currencyCode == cur && it.type == CustodyTransactionType.PAID_TO_PERSON }.sumOf { it.amountMinor }
                                val returned = filtered.filter { it.currencyCode == cur && it.type == CustodyTransactionType.RETURNED_FROM_PERSON }.sumOf { it.amountMinor }
                                val toOrg = filtered.filter { it.currencyCode == cur && it.type == CustodyTransactionType.RETURNED_TO_ORG }.sumOf { it.amountMinor }
                                val orgLoan = filtered.filter { it.currencyCode == cur && it.type == CustodyTransactionType.ORG_LOAN_FROM_OWNER }.sumOf { it.amountMinor }
                                val orgRepay = filtered.filter { it.currencyCode == cur && it.type == CustodyTransactionType.ORG_LOAN_REPAYMENT }.sumOf { it.amountMinor }
                                val personLoan = filtered.filter { it.currencyCode == cur && it.type == CustodyTransactionType.PERSON_LOAN_TO_OWNER }.sumOf { it.amountMinor }
                                val personRepay = filtered.filter { it.currencyCode == cur && it.type == CustodyTransactionType.OWNER_REPAY_PERSON_LOAN }.sumOf { it.amountMinor }
                                val ownerCustody = CustodyFinancialSummary.custodyOwnerBalance(filtered, cur)
                                val orgDebt = CustodyFinancialSummary.ownerOrganizationDebt(filtered, cur)
                                val peopleDebt = CustodyFinancialSummary.ownerPeopleDebt(filtered, cur)
                                Text("$cur — استلام ${amount(received)} | صرف ${amount(paid)} | مرتجع أشخاص ${amount(returned)} | مرتجع جهة ${amount(toOrg)}")
                                Text("تسليف الجهة ${amount(orgLoan)} | سداد تسليف الجهة ${amount(orgRepay)} | تسليف الأشخاص ${amount(personLoan)} | سداد التسليفات ${amount(personRepay)}")
                                Text("رصيد العهدة ${amount(kotlin.math.abs(ownerCustody))} — ${if (ownerCustody >= 0) "متبقي" else "عجز"} | ذمة الجهة ${amount(kotlin.math.abs(orgDebt))} — ${debtLabel(orgDebt, "مستحق له", "مستحق عليه")} | ذمم الأشخاص ${amount(kotlin.math.abs(peopleDebt))} — ${debtLabel(peopleDebt, "له على الأشخاص", "عليه للأشخاص")}")
                            }
                            2 -> filtered.sortedByDescending { it.transactionDate }.forEach { t ->
                                val personName = t.personId?.let { id -> people.firstOrNull { it.id == id }?.name }
                                val label = when (t.type) {
                                    CustodyTransactionType.RECEIVED_FROM_ORG -> "استلام من الجهة"
                                    CustodyTransactionType.RETURNED_TO_ORG -> "مرتجع للجهة"
                                    CustodyTransactionType.PAID_TO_PERSON -> "صرف${personName?.let { " لـ $it" } ?: ""}"
                                    CustodyTransactionType.RETURNED_FROM_PERSON -> "مرتجع${personName?.let { " من $it" } ?: ""}"
                                    CustodyTransactionType.ORG_LOAN_FROM_OWNER -> "تسليف الجهة"
                                    CustodyTransactionType.ORG_LOAN_REPAYMENT -> "سداد تسليف الجهة"
                                    CustodyTransactionType.PERSON_LOAN_TO_OWNER -> "تسليف لحامل العهدة${personName?.let { " من $it" } ?: ""}"
                                    CustodyTransactionType.OWNER_REPAY_PERSON_LOAN -> "سداد تسليف للشخص${personName?.let { " لـ $it" } ?: ""}"
                                    else -> t.type
                                }
                                Text("${t.currencyCode} — $label — ${amount(t.amountMinor)} — ${t.description}")
                            }
                        }
                    }
                }
            }
        }
    }
    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, text = { Text(text) }, confirmButton = { TextButton(onClick = { message = null }) { Text("موافق") } }) }
}
