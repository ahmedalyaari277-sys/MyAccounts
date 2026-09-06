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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalDensity
import com.myaccounts.app.data.custody.*
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val custodyCurrencies = listOf("YER", "SAR", "USD")
private fun money(v: Long): String = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun ownerDelta(type: String, amount: Long): Long = CustodyBalanceRules.ownerDelta(type, amount)
private fun personDelta(type: String, amount: Long): Long = CustodyBalanceRules.personDelta(type, amount)
private fun signed(v: Long): String = when {
    v > 0 -> "عليه ${money(v)}"
    v < 0 -> "له ${money(-v)}"
    else -> "متوازن 0"
}
private fun typeName(type: String): String = when (type) {
    CustodyTransactionType.RECEIVED_FROM_ORG -> "استلام من الجهة"
    CustodyTransactionType.PAID_TO_PERSON -> "صرف للشخص"
    CustodyTransactionType.RETURNED_FROM_PERSON -> "مرتجع من الشخص"
    CustodyTransactionType.RETURNED_TO_ORG -> "مرتجع للجهة / تصفية"
    else -> type
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppGatewayScreen(onAccounts: () -> Unit, onCustodies: () -> Unit, onSettings: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("MyAccounts") }, actions = { TextButton(onClick = onSettings) { Text("الإعدادات") } }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Button(onClick = onAccounts, modifier = Modifier.fillMaxWidth()) { Text("دفتر الحسابات") }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCustodies, modifier = Modifier.fillMaxWidth()) { Text("العُهَد") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyHomeScreen(vm: CustodyViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val custodies by vm.custodies.collectAsState()
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("العُهَد") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }) },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "إضافة عهدة") } }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(custodies, key = { it.id }) { custody ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(custody.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(custody.name, fontWeight = FontWeight.Bold)
                        Text("الجهة: ${custody.organizationName}")
                    }
                }
            }
        }
    }
    if (adding) {
        CustodyFormDialog(onDismiss = { adding = false }) { custody ->
            vm.create(custody)
            adding = false
        }
    }
}

@Composable
private fun CustodyFormDialog(onDismiss: () -> Unit, onSave: (CustodyEntity) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var organizationPhone by remember { mutableStateOf("") }
    var organizationAddress by remember { mutableStateOf("") }
    var organizationNotes by remember { mutableStateOf("") }
    var deliveredByName by remember { mutableStateOf("") }
    var deliveredByPhone by remember { mutableStateOf("") }
    var deliveredByAddress by remember { mutableStateOf("") }
    var deliveredByNotes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة صاحب عهدة") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().imePadding(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item { TextFieldFull("اسم صاحب العهدة", name) { name = it } }
                item { TextFieldFull("الهاتف", phone) { phone = it } }
                item { TextFieldFull("العنوان", address) { address = it } }
                item { TextFieldFull("الملاحظات", notes) { notes = it } }
                item { Text("بيانات الجهة", fontWeight = FontWeight.Bold) }
                item { TextFieldFull("اسم الجهة", organization) { organization = it } }
                item { TextFieldFull("هاتف الجهة", organizationPhone) { organizationPhone = it } }
                item { TextFieldFull("عنوان الجهة", organizationAddress) { organizationAddress = it } }
                item { TextFieldFull("ملاحظات الجهة", organizationNotes) { organizationNotes = it } }
                item { Text("بيانات مسلِّم العهدة", fontWeight = FontWeight.Bold) }
                item { TextFieldFull("اسم مسلِّم العهدة", deliveredByName) { deliveredByName = it } }
                item { TextFieldFull("هاتف مسلِّم العهدة", deliveredByPhone) { deliveredByPhone = it } }
                item { TextFieldFull("عنوان مسلِّم العهدة", deliveredByAddress) { deliveredByAddress = it } }
                item { TextFieldFull("ملاحظات مسلِّم العهدة", deliveredByNotes) { deliveredByNotes = it } }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank() && organization.isNotBlank() && deliveredByName.isNotBlank(), onClick = {
                onSave(CustodyEntity(
                    name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim(),
                    organizationName = organization.trim(), organizationPhone = organizationPhone.trim(),
                    organizationAddress = organizationAddress.trim(), organizationNotes = organizationNotes.trim(),
                    deliveredByName = deliveredByName.trim(), deliveredByPhone = deliveredByPhone.trim(),
                    deliveredByAddress = deliveredByAddress.trim(), deliveredByNotes = deliveredByNotes.trim()
                ))
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TextFieldFull(label: String, value: String, onChange: (String) -> Unit) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { state ->
                if (state.isFocused) scope.launch { bringIntoViewRequester.bringIntoView() }
            }
            .semantics { contentDescription = label },
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyDetailScreen(vm: CustodyViewModel, id: Long, onBack: () -> Unit) {
    val custody by vm.custody(id).collectAsState()
    val people by vm.persons(id).collectAsState()
    val accounts by vm.accounts(id).collectAsState()
    val transactions by vm.transactions(id).collectAsState()
    var currency by remember { mutableStateOf("YER") }
    var addPerson by remember { mutableStateOf(false) }
    var addTransaction by remember { mutableStateOf(false) }
    var transactionType by remember { mutableStateOf(CustodyTransactionType.RECEIVED_FROM_ORG) }
    var showReport by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val current = custody ?: return
    val ownerAccount = accounts.firstOrNull { it.holderType == "OWNER" && it.personId == null && it.currencyCode == currency }
    val ownerBalance = transactions.filter { it.accountId == ownerAccount?.id && it.currencyCode == currency }.sumOf { ownerDelta(it.type, it.amountMinor) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "المزيد") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("تقرير العهدة") }, onClick = { menuExpanded = false; showReport = true })
                        DropdownMenuItem(text = { Text("أرشفة العهدة") }, onClick = { menuExpanded = false; vm.archive(id); onBack() })
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("الجهة: ${current.organizationName}", fontWeight = FontWeight.Bold) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    custodyCurrencies.forEach { code ->
                        FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(code) })
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("حساب صاحب العهدة — $currency", fontWeight = FontWeight.Bold)
                        Text(signed(ownerBalance), fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { transactionType = CustodyTransactionType.RECEIVED_FROM_ORG; addTransaction = true }, modifier = Modifier.weight(1f)) { Text("استلام من الجهة") }
                    OutlinedButton(onClick = { transactionType = CustodyTransactionType.RETURNED_TO_ORG; addTransaction = true }, modifier = Modifier.weight(1f)) { Text("مرتجع للجهة / تصفية") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الأشخاص", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { addPerson = true }) { Text("إضافة شخص") }
                }
            }
            items(people, key = { it.id }) { person ->
                val balance = transactions.filter { it.personId == person.id && it.currencyCode == currency }.sumOf { personDelta(it.type, it.amountMinor) }
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(person.name)
                        Text(signed(balance), fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { Text("العمليات", fontWeight = FontWeight.Bold) }
            items(transactions.filter { it.currencyCode == currency }, key = { it.id }) { transaction ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(typeName(transaction.type), fontWeight = FontWeight.Bold)
                        Text("${money(transaction.amountMinor)} $currency")
                        if (transaction.description.isNotBlank()) Text(transaction.description)
                        Text(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(transaction.transactionDate)), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (addPerson) {
        PersonFormDialog(onDismiss = { addPerson = false }) { person ->
            vm.addPerson(id, person)
            addPerson = false
        }
    }
    if (addTransaction) {
        CustodyTransactionDialog(
            people = people,
            defaultCurrency = currency,
            initialType = transactionType,
            onDismiss = { addTransaction = false }
        ) { cur, type, personId, amount, description ->
            vm.addTransaction(id, cur, type, personId, amount, description, System.currentTimeMillis())
            addTransaction = false
        }
    }
    if (showReport) CustodyReportDialog(current, accounts, transactions, onDismiss = { showReport = false })
}

@Composable
private fun PersonFormDialog(onDismiss: () -> Unit, onSave: (CustodyPersonEntity) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { contentDescription = "حوار إضافة شخص" },
        title = { Text("إضافة شخص") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextFieldFull("الاسم", name) { name = it }
                TextFieldFull("الهاتف", phone) { phone = it }
                TextFieldFull("العنوان", address) { address = it }
                TextFieldFull("الملاحظات", notes) { notes = it }
            }
        },
        confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onSave(CustodyPersonEntity(custodyId = 0, name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim())) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun CustodyTransactionDialog(
    people: List<CustodyPersonEntity>,
    defaultCurrency: String,
    initialType: String,
    onDismiss: () -> Unit,
    onSave: (String, String, Long?, Long, String) -> Unit
) {
    var currency by remember { mutableStateOf(defaultCurrency) }
    var type by remember { mutableStateOf(initialType) }
    var personId by remember { mutableStateOf<Long?>(null) }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val needsPerson = type == CustodyTransactionType.PAID_TO_PERSON || type == CustodyTransactionType.RETURNED_FROM_PERSON
    val parsed = amount.toBigDecimalOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { contentDescription = "حوار العملية" },
        title = { Text("عملية مالية") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.imePadding()) {
                item { OperationChoice("استلام من الجهة", type == CustodyTransactionType.RECEIVED_FROM_ORG) { type = CustodyTransactionType.RECEIVED_FROM_ORG; personId = null } }
                item { OperationChoice("صرف للشخص", type == CustodyTransactionType.PAID_TO_PERSON) { type = CustodyTransactionType.PAID_TO_PERSON } }
                item { OperationChoice("مرتجع من الشخص", type == CustodyTransactionType.RETURNED_FROM_PERSON) { type = CustodyTransactionType.RETURNED_FROM_PERSON } }
                item { OperationChoice("مرتجع للجهة / تصفية", type == CustodyTransactionType.RETURNED_TO_ORG) { type = CustodyTransactionType.RETURNED_TO_ORG; personId = null } }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        custodyCurrencies.forEach { code -> FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(code) }) }
                    }
                }
                if (needsPerson) {
                    items(people, key = { it.id }) { person ->
                        Row(Modifier.fillMaxWidth().clickable { personId = person.id }) {
                            RadioButton(selected = personId == person.id, onClick = { personId = person.id })
                            Text(person.name, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
                item { TextFieldFull("المبلغ", amount) { amount = it } }
                item { TextFieldFull("البيان", description) { description = it } }
            }
        },
        confirmButton = {
            Button(enabled = parsed != null && parsed > BigDecimal.ZERO && (!needsPerson || personId != null), onClick = {
                val minor = parsed!!.movePointRight(2).longValueExact()
                onSave(currency, type, personId, minor, description.trim())
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun OperationChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun CustodyReportDialog(custody: CustodyEntity, accounts: List<CustodyAccountEntity>, transactions: List<CustodyTransactionEntity>, onDismiss: () -> Unit) {
    var currency by remember { mutableStateOf("YER") }
    val owner = accounts.firstOrNull { it.holderType == "OWNER" && it.personId == null && it.currencyCode == currency }
    val rows = transactions.filter { it.currencyCode == currency }.sortedBy { it.transactionDate }
    val balance = rows.filter { it.accountId == owner?.id }.sumOf { ownerDelta(it.type, it.amountMinor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تقرير العهدة") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Text(custody.name, fontWeight = FontWeight.Bold)
                    Text("الجهة: ${custody.organizationName}")
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        custodyCurrencies.forEach { code -> FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(code) }) }
                    }
                }
                item { Text("الرصيد: ${signed(balance)}", fontWeight = FontWeight.Bold) }
                items(rows, key = { it.id }) { transaction ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp)) {
                            Text(typeName(transaction.type), fontWeight = FontWeight.Bold)
                            Text("${money(transaction.amountMinor)} $currency")
                            if (transaction.description.isNotBlank()) Text(transaction.description)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}
