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
private fun typeName(t: String) = when (t) {
    CustodyTransactionType.RECEIVED_FROM_ORG -> "استلام من الجهة"
    CustodyTransactionType.PAID_TO_PERSON -> "صرف للشخص"
    CustodyTransactionType.RETURNED_FROM_PERSON -> "مرتجع من الشخص"
    CustodyTransactionType.RETURNED_TO_ORG -> "مرتجع للجهة / تصفية"
    else -> t
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppGatewayScreen(onAccounts: () -> Unit, onCustodies: () -> Unit, onSettings: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("MyAccounts") }, actions = { TextButton(onClick = onSettings) { Text("الإعدادات") } }) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(24.dp), verticalArrangement = Arrangement.Center) {
            Button(onClick = onAccounts, Modifier.fillMaxWidth()) { Text("دفتر الحسابات") }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCustodies, Modifier.fillMaxWidth()) { Text("العُهَد") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyHomeScreen(vm: CustodyViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val list by vm.custodies.collectAsState()
    var adding by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("العُهَد") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }) }, floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "إضافة") } }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { c -> Card(Modifier.fillMaxWidth().clickable { onOpen(c.id) }) { Column(Modifier.padding(16.dp)) { Text(c.name, fontWeight = FontWeight.Bold); Text("الجهة: ${c.organizationName}") } } }
        }
    }
    if (adding) CustodyFormDialog({ adding = false }) { c -> vm.create(c); adding = false }
}

@Composable
private fun CustodyFormDialog(onDismiss: () -> Unit, onSave: (CustodyEntity) -> Unit) {
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }
    var org by remember { mutableStateOf("") }; var orgPhone by remember { mutableStateOf("") }; var orgAddress by remember { mutableStateOf("") }; var orgNotes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إضافة صاحب عهدة") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Field("اسم صاحب العهدة", name) { name = it } }; item { Field("الهاتف", phone) { phone = it } }; item { Field("العنوان", address) { address = it } }; item { Field("الملاحظات", notes) { notes = it } }
            item { Text("بيانات الجهة", fontWeight = FontWeight.Bold) }; item { Field("اسم الجهة", org) { org = it } }; item { Field("هاتف الجهة", orgPhone) { orgPhone = it } }; item { Field("عنوان الجهة", orgAddress) { orgAddress = it } }; item { Field("ملاحظات الجهة", orgNotes) { orgNotes = it } }
        }
    }, confirmButton = { Button(enabled = name.isNotBlank() && org.isNotBlank(), onClick = { onSave(CustodyEntity(name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim(), organizationName = org.trim(), organizationPhone = orgPhone.trim(), organizationAddress = orgAddress.trim(), organizationNotes = orgNotes.trim())) }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable private fun Field(label: String, value: String, onChange: (String) -> Unit) { OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyDetailScreen(vm: CustodyViewModel, id: Long, onBack: () -> Unit) {
    val c by vm.custody(id).collectAsState(); val people by vm.persons(id).collectAsState(); val accounts by vm.accounts(id).collectAsState(); val tx by vm.transactions(id).collectAsState()
    var currency by remember { mutableStateOf("YER") }; var addPerson by remember { mutableStateOf(false) }; var addTx by remember { mutableStateOf(false) }; var report by remember { mutableStateOf(false) }; var menu by remember { mutableStateOf(false) }
    val custody = c ?: return
    val owner = accounts.firstOrNull { it.holderType == "OWNER" && it.currencyCode == currency }
    val ownerBalance = tx.filter { it.accountId == owner?.id && it.currencyCode == currency }.sumOf { ownerDelta(it.type, it.amountMinor) }
    Scaffold(topBar = { TopAppBar(title = { Text(custody.name) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }, actions = { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "المزيد") }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("تقرير العهدة") }, onClick = { menu = false; report = true }); DropdownMenuItem(text = { Text("أرشفة العهدة") }, onClick = { menu = false; vm.archive(id); onBack() }) } }) }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("الجهة: ${custody.organizationName}", fontWeight = FontWeight.Bold) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { currencies.forEach { code -> FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(code) }) } } }
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("حساب صاحب العهدة — $currency", fontWeight = FontWeight.Bold); Text(signed(ownerBalance), fontWeight = FontWeight.Bold) } } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ addTx = true }, Modifier.weight(1f)) { Text("استلام من الجهة") }; OutlinedButton({ addTx = true }, Modifier.weight(1f)) { Text("مرتجع للجهة / تصفية") } } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("الأشخاص", fontWeight = FontWeight.Bold); TextButton({ addPerson = true }) { Text("إضافة شخص") } } }
            items(people, key = { it.id }) { person -> val b = tx.filter { it.personId == person.id && it.currencyCode == currency }.sumOf { personDelta(it.type, it.amountMinor) }; Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(person.name); Text(signed(b), fontWeight = FontWeight.Bold) } } }
            item { Text("العمليات", fontWeight = FontWeight.Bold) }
            items(tx.filter { it.currencyCode == currency }, key = { it.id }) { t -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) { Text(typeName(t.type), fontWeight = FontWeight.Bold); Text("${money(t.amountMinor)} $currency"); if (t.description.isNotBlank()) Text(t.description); Text(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(t.transactionDate)), style = MaterialTheme.typography.bodySmall) } } }
        }
    }
    if (addPerson) PersonFormDialog({ addPerson = false }) { vm.addPerson(id, it); addPerson = false }
    if (addTx) TransactionFormDialog(people, currency, { addTx = false }) { cur, type, pid, amount, desc -> vm.addTransaction(id, cur, type, pid, amount, desc, System.currentTimeMillis()); addTx = false }
    if (report) CustodyReportDialog(custody, accounts, tx, { report = false })
}

@Composable private fun PersonFormDialog(onDismiss: () -> Unit, onSave: (CustodyPersonEntity) -> Unit) {
    var n by remember { mutableStateOf("") }; var p by remember { mutableStateOf("") }; var a by remember { mutableStateOf("") }; var no by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إضافة شخص") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Field("الاسم", n) { n = it }; Field("الهاتف", p) { p = it }; Field("العنوان", a) { a = it }; Field("الملاحظات", no) { no = it } } }, confirmButton = { Button(enabled = n.isNotBlank(), onClick = { onSave(CustodyPersonEntity(custodyId = 0, name = n.trim(), phone = p.trim(), address = a.trim(), notes = no.trim())) }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable private fun TransactionFormDialog(people: List<CustodyPersonEntity>, defaultCurrency: String, onDismiss: () -> Unit, onSave: (String, String, Long?, Long, String) -> Unit) {
    var currency by remember { mutableStateOf(defaultCurrency) }; var type by remember { mutableStateOf(CustodyTransactionType.RECEIVED_FROM_ORG) }; var personId by remember { mutableStateOf<Long?>(null) }; var amount by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }
    val needsPerson = type == CustodyTransactionType.PAID_TO_PERSON || type == CustodyTransactionType.RETURNED_FROM_PERSON; val value = amount.toBigDecimalOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("عملية مالية") }, text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        item { OperationChoice("استلام من الجهة", type == CustodyTransactionType.RECEIVED_FROM_ORG) { type = CustodyTransactionType.RECEIVED_FROM_ORG; personId = null } }
        item { OperationChoice("صرف للشخص", type == CustodyTransactionType.PAID_TO_PERSON) { type = CustodyTransactionType.PAID_TO_PERSON } }
        item { OperationChoice("مرتجع من الشخص", type == CustodyTransactionType.RETURNED_FROM_PERSON) { type = CustodyTransactionType.RETURNED_FROM_PERSON } }
        item { OperationChoice("مرتجع للجهة / تصفية", type == CustodyTransactionType.RETURNED_TO_ORG) { type = CustodyTransactionType.RETURNED_TO_ORG; personId = null } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { currencies.forEach { code -> FilterChip(currency == code, { currency = code }, { Text(code) }) } } }
        if (needsPerson) items(people, key = { it.id }) { person -> Row(Modifier.fillMaxWidth()) { RadioButton(personId == person.id, { personId = person.id }); Text(person.name) } }
        item { Field("المبلغ", amount) { amount = it } }; item { Field("البيان", description) { description = it } }
    } }, confirmButton = { Button(enabled = value != null && value > BigDecimal.ZERO && (!needsPerson || personId != null), onClick = { onSave(currency, type, personId, value!!.movePointRight(2).longValueExact(), description.trim()) }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable private fun OperationChoice(label: String, selected: Boolean, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick)) { RadioButton(selected, onClick); Text(label, Modifier.padding(top = 12.dp)) } }

@Composable private fun CustodyReportDialog(c: CustodyEntity, accounts: List<CustodyAccountEntity>, tx: List<CustodyTransactionEntity>, onDismiss: () -> Unit) {
    var currency by remember { mutableStateOf("YER") }; val owner = accounts.firstOrNull { it.holderType == "OWNER" && it.currencyCode == currency }; val rows = tx.filter { it.currencyCode == currency }.sortedBy { it.transactionDate }; val balance = rows.filter { it.accountId == owner?.id }.sumOf { ownerDelta(it.type, it.amountMinor) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تقرير العهدة") }, text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) { item { Text(c.name, fontWeight = FontWeight.Bold); Text("الجهة: ${c.organizationName}"); Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { currencies.forEach { code -> FilterChip(currency == code, { currency = code }, { Text(code) }) } }; Text("الرصيد: ${signed(balance)}", fontWeight = FontWeight.Bold) }; items(rows, key = { it.id }) { t -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) { Text(typeName(t.type), fontWeight = FontWeight.Bold); Text("${money(t.amountMinor)} $currency"); if (t.description.isNotBlank()) Text(t.description) } } } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } })
}
