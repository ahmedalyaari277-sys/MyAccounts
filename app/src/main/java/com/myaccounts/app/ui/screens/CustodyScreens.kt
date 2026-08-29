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
import java.util.Date
import java.util.Locale

private val currencies = listOf("YER", "SAR", "USD")
private fun money(v: Long) = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun ownerDelta(type: String, amount: Long) = CustodyBalanceRules.ownerDelta(type, amount)
private fun personDelta(type: String, amount: Long) = CustodyBalanceRules.personDelta(type, amount)
private fun signed(v: Long) = when { v > 0 -> "عليه ${money(v)}"; v < 0 -> "له ${money(-v)}"; else -> "متوازن 0" }
private fun typeName(t: String) = when (t) { "RECEIVED_FROM_ORG" -> "استلام من الجهة"; "PAID_TO_PERSON" -> "صرف للشخص"; "RETURNED_FROM_PERSON" -> "مرتجع من الشخص"; "RETURNED_TO_ORG" -> "مرتجع للجهة / تصفية"; else -> t }

@Composable fun AppGatewayScreen(onAccounts: () -> Unit, onCustodies: () -> Unit, onSettings: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("MyAccounts") }, actions = { TextButton(onClick = onSettings) { Text("الإعدادات") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) { Button(onClick = onAccounts, Modifier.fillMaxWidth()) { Text("دفتر الحسابات") }; Spacer(Modifier.height(16.dp)); Button(onClick = onCustodies, Modifier.fillMaxWidth()) { Text("العُهَد") } }
    }
}

@Composable fun CustodyHomeScreen(vm: CustodyViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val list by vm.custodies.collectAsState(); var add by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("العُهَد") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }) }, floatingActionButton = { FloatingActionButton(onClick = { add = true }) { Icon(Icons.Default.Add, "إضافة عهدة") } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(list, key = { it.id }) { c -> Card(Modifier.fillMaxWidth().clickable { onOpen(c.id) }) { Column(Modifier.padding(16.dp)) { Text(c.name, fontWeight = FontWeight.Bold); Text("الجهة: ${c.organizationName}"); if (c.phone.isNotBlank()) Text(c.phone) } } } }
    }
    if (add) AddCustodyDialog({ add = false }) { vm.create(it); add = false }
}

@Composable private fun AddCustodyDialog(close: () -> Unit, save: (CustodyEntity) -> Unit) {
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; var org by remember { mutableStateOf("") }; var orgPhone by remember { mutableStateOf("") }; var orgAddress by remember { mutableStateOf("") }; var orgNotes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("إضافة صاحب عهدة") }, text = { LazyColumn { item { Field("اسم صاحب العهدة", name) { name = it } }; item { Field("الهاتف", phone) { phone = it } }; item { Field("العنوان", address) { address = it } }; item { Field("الملاحظات", notes) { notes = it } }; item { Text("بيانات الجهة", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }; item { Field("اسم الجهة", org) { org = it } }; item { Field("هاتف الجهة", orgPhone) { orgPhone = it } }; item { Field("عنوان الجهة", orgAddress) { orgAddress = it } }; item { Field("ملاحظات الجهة", orgNotes) { orgNotes = it } } } }, confirmButton = { Button(enabled = name.isNotBlank() && org.isNotBlank(), onClick = { save(CustodyEntity(name.trim(), phone.trim(), address.trim(), notes.trim(), org.trim(), orgPhone.trim(), orgAddress.trim(), orgNotes.trim())) }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = close) { Text("إلغاء") } })
}
@Composable private fun Field(label: String, value: String, onValueChange: (String) -> Unit) { OutlinedTextField(value, onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth()) }

@Composable fun CustodyDetailScreen(vm: CustodyViewModel, id: Long, onBack: () -> Unit) {
    val custody by vm.custody(id).collectAsState(); val people by vm.persons(id).collectAsState(); val accounts by vm.accounts(id).collectAsState(); val transactions by vm.transactions(id).collectAsState(); var currency by remember { mutableStateOf("YER") }; var addPerson by remember { mutableStateOf(false) }; var addTransaction by remember { mutableStateOf(false) }; var report by remember { mutableStateOf(false) }; var menu by remember { mutableStateOf(false) }; val c = custody ?: return
    val owner = accounts.firstOrNull { it.holderType == "OWNER" && it.currencyCode == currency }; val ownerBalance = transactions.filter { it.accountId == owner?.id }.sumOf { ownerDelta(it.type, it.amountMinor) }
    Scaffold(topBar = { TopAppBar(title = { Text(c.name) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }, actions = { Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "المزيد") }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("تقرير العهدة") }, onClick = { menu = false; report = true }); DropdownMenuItem(text = { Text("أرشفة العهدة") }, onClick = { menu = false; vm.archive(id); onBack() }) } } }) }, floatingActionButton = { FloatingActionButton(onClick = { addTransaction = true }) { Icon(Icons.Default.Add, "إضافة عملية") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) { Text("الجهة: ${c.organizationName}", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { currencies.forEach { x -> FilterChip(currency == x, { currency = x }, { Text(x) }) } }; Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Column(Modifier.padding(12.dp)) { Text("حساب صاحب العهدة — $currency", fontWeight = FontWeight.Bold); Text(signed(ownerBalance), fontWeight = FontWeight.Bold) } }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("الأشخاص", fontWeight = FontWeight.Bold); TextButton(onClick = { addPerson = true }) { Text("إضافة شخص") } }; LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(people, key = { it.id }) { person -> val balance = transactions.filter { it.personId == person.id && it.currencyCode == currency }.sumOf { personDelta(it.type, it.amountMinor) }; Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(person.name, fontWeight = FontWeight.Bold); Text(signed(balance), fontWeight = FontWeight.Bold) } } } } }
    }
    if (addPerson) AddPersonDialog({ addPerson = false }) { n, p, a, no -> vm.addPerson(id, CustodyPersonEntity(0, id, n, p, a, no)); addPerson = false }
    if (addTransaction) AddTransactionDialog(people, currency, { addTransaction = false }) { cur, type, personId, amount, desc -> vm.addTransaction(id, cur, type, personId, amount, desc, System.currentTimeMillis()); addTransaction = false }
    if (report) CustodyReportDialog(c, accounts, transactions, { report = false })
}

@Composable private fun AddPersonDialog(close: () -> Unit, save: (String, String, String, String) -> Unit) { var n by remember { mutableStateOf("") }; var p by remember { mutableStateOf("") }; var a by remember { mutableStateOf("") }; var no by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = close, title = { Text("إضافة شخص") }, text = { Column { Field("الاسم", n) { n = it }; Field("الهاتف", p) { p = it }; Field("العنوان", a) { a = it }; Field("الملاحظات", no) { no = it } } }, confirmButton = { Button(enabled = n.isNotBlank(), onClick = { save(n.trim(), p.trim(), a.trim(), no.trim()) }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = close) { Text("إلغاء") } }) }

@Composable private fun AddTransactionDialog(people: List<CustodyPersonEntity>, defaultCurrency: String, close: () -> Unit, save: (String, String, Long?, Long, String) -> Unit) {
    var cur by remember { mutableStateOf(defaultCurrency) }; var type by remember { mutableStateOf(CustodyTransactionType.RECEIVED_FROM_ORG) }; var personId by remember { mutableStateOf<Long?>(null) }; var amount by remember { mutableStateOf("") }; var desc by remember { mutableStateOf("") }; val needsPerson = type == CustodyTransactionType.PAID_TO_PERSON || type == CustodyTransactionType.RETURNED_FROM_PERSON
    AlertDialog(onDismissRequest = close, title = { Text("إضافة عملية مالية") }, text = { LazyColumn { item { Text("نوع العملية", fontWeight = FontWeight.Bold) }; item { Row { RadioButton(type == CustodyTransactionType.RECEIVED_FROM_ORG, { type = CustodyTransactionType.RECEIVED_FROM_ORG; personId = null }); Text("استلام من الجهة") } }; item { Row { RadioButton(type == CustodyTransactionType.PAID_TO_PERSON, { type = CustodyTransactionType.PAID_TO_PERSON }); Text("صرف للشخص") } }; item { Row { RadioButton(type == CustodyTransactionType.RETURNED_FROM_PERSON, { type = CustodyTransactionType.RETURNED_FROM_PERSON }); Text("مرتجع من الشخص") } }; item { Row { RadioButton(type == CustodyTransactionType.RETURNED_TO_ORG, { type = CustodyTransactionType.RETURNED_TO_ORG; personId = null }); Text("مرتجع للجهة / تصفية") } }; item { Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { currencies.forEach { x -> FilterChip(cur == x, { cur = x }, { Text(x) }) } } }; if (needsPerson) { item { Text("الشخص", fontWeight = FontWeight.Bold) }; items(people, key = { it.id }) { person -> Row(Modifier.fillMaxWidth()) { RadioButton(personId == person.id, { personId = person.id }); Text(person.name) } } }; item { Field("المبلغ", amount) { amount = it } }; item { Field("البيان", desc) { desc = it } } } }, confirmButton = { val n = amount.toBigDecimalOrNull(); Button(enabled = n != null && n > BigDecimal.ZERO && (!needsPerson || personId != null), onClick = { save(cur, type, personId, n!!.movePointRight(2).longValueExact(), desc) }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = close) { Text("إلغاء") } })
}

@Composable private fun CustodyReportDialog(c: CustodyEntity, accounts: List<CustodyAccountEntity>, transactions: List<CustodyTransactionEntity>, close: () -> Unit) { var cur by remember { mutableStateOf("YER") }; val owner = accounts.firstOrNull { it.holderType == "OWNER" && it.currencyCode == cur }; val ownerTx = transactions.filter { it.accountId == owner?.id }; val ownerBalance = ownerTx.sumOf { ownerDelta(it.type, it.amountMinor) }; AlertDialog(onDismissRequest = close, title = { Text("تقرير العهدة") }, text = { LazyColumn { item { Text(c.name, fontWeight = FontWeight.Bold); Text("الجهة: ${c.organizationName}"); Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { currencies.forEach { x -> FilterChip(cur == x, { cur = x }, { Text(x) }) } }; Text("رصيد صاحب العهدة: ${signed(ownerBalance)}", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("العمليات", fontWeight = FontWeight.Bold) }; items(transactions.filter { it.currencyCode == cur }.sortedByDescending { it.transactionDate }, key = { it.id }) { t -> Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Column(Modifier.padding(8.dp)) { Text(typeName(t.type), fontWeight = FontWeight.Bold); Text("المبلغ: ${money(t.amountMinor)}"); if (t.description.isNotBlank()) Text(t.description); Text(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(t.transactionDate)), style = MaterialTheme.typography.bodySmall) } } } } }, confirmButton = { TextButton(onClick = close) { Text("إغلاق") } }) }
