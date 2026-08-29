package com.myaccounts.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myaccounts.app.data.custody.*
import com.myaccounts.app.ui.components.CalculatorButton
import com.myaccounts.app.ui.components.CalculatorOverlay
import com.myaccounts.app.ui.components.LocalCalculatorController
import com.myaccounts.app.ui.theme.Due
import com.myaccounts.app.ui.theme.Owed
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import com.myaccounts.app.util.CustodyAttachmentStorage
import com.myaccounts.app.util.TransactionAttachmentStorage
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val currencies = listOf("YER", "SAR", "USD")
private fun money(v: Long) = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun parseAmount(v: String): Long? = runCatching { BigDecimal(v.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }.getOrNull()
private fun typeName(t: String) = when (t) { CustodyTransactionType.RECEIVED_FROM_ORG -> "استلام من الجهة"; CustodyTransactionType.PAID_TO_PERSON -> "صرف للشخص"; CustodyTransactionType.RETURNED_FROM_PERSON -> "مرتجع من الشخص"; CustodyTransactionType.RETURNED_TO_ORG -> "مرتجع للجهة / تصفية"; else -> t }
private fun signed(v: Long) = when { v > 0 -> "عليه ${money(v)}"; v < 0 -> "له ${money(-v)}"; else -> "متوازن 0" }
private fun balanceColor(v: Long) = when { v > 0 -> Due; v < 0 -> Owed; else -> MaterialTheme.colorScheme.primary }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CustodyOperationsScreen(vm: CustodyViewModel, custodyId: Long, onBack: () -> Unit, onPerson: (Long) -> Unit) {
    val custody by vm.custody(custodyId).collectAsState(); val people by vm.persons(custodyId).collectAsState(); val accounts by vm.accounts(custodyId).collectAsState(); val tx by vm.transactions(custodyId).collectAsState(); val c = custody ?: return
    var currency by remember { mutableStateOf("YER") }; var newType by remember { mutableStateOf<String?>(null) }; var editing by remember { mutableStateOf<CustodyTransactionEntity?>(null) }; var deleting by remember { mutableStateOf<CustodyTransactionEntity?>(null) }; var addPerson by remember { mutableStateOf(false) }; var more by remember { mutableStateOf(false) }; var showReport by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(c.name, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton({ onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }, actions = { IconButton({ more = true }) { Icon(Icons.Default.MoreVert, "المزيد") }; DropdownMenu(more, { more = false }) { DropdownMenuItem({ Text("تقرير العهدة") }, { more = false; showReport = true }); DropdownMenuItem({ Text("أرشفة العهدة") }, { vm.archive(custodyId); onBack() }) } }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(c.name, fontWeight = FontWeight.Bold); Text("الجهة: ${c.organizationName}"); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) { currencies.forEach { code -> val b = accounts.firstOrNull { it.holderType == "OWNER" && it.personId == null && it.currencyCode == code }?.balanceMinor ?: 0L; Card(Modifier.weight(1f)) { Column(Modifier.padding(8.dp)) { Text(code, fontWeight = FontWeight.Bold); Text(signed(b), color = balanceColor(b), fontWeight = FontWeight.Bold) } } } } } } }
            item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { currencies.forEach { FilterChip(currency == it, { currency = it }, label = { Text(it) }) } } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) { Button({ newType = CustodyTransactionType.RECEIVED_FROM_ORG }, Modifier.weight(1f)) { Text("استلام من الجهة") }; OutlinedButton({ newType = CustodyTransactionType.RETURNED_TO_ORG }, Modifier.weight(1f)) { Text("مرتجع للجهة / تصفية") } } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("الأشخاص", fontWeight = FontWeight.Bold); TextButton({ addPerson = true }) { Text("إضافة شخص") } } }
            items(people, key = { it.id }) { p -> Card(Modifier.fillMaxWidth().clickable { onPerson(p.id) }) { Column(Modifier.padding(10.dp)) { Text(p.name, fontWeight = FontWeight.Bold); val b = tx.filter { it.personId == p.id && it.currencyCode == currency }.sumOf { CustodyBalanceRules.personDelta(it.type, it.amountMinor) }; Text(signed(b), color = balanceColor(b)) } } }
            item { Text("العمليات — $currency", fontWeight = FontWeight.Bold) }
            items(tx.filter { it.currencyCode == currency }.sortedByDescending { it.transactionDate }, key = { it.id }) { t -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(typeName(t.type), fontWeight = FontWeight.Bold); Text(SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(t.transactionDate)), style = MaterialTheme.typography.bodySmall) }; Text("${money(t.amountMinor)} $currency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodySmall); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { IconButton({ editing = t }) { Icon(Icons.Default.Edit, "تعديل") }; IconButton({ deleting = t }) { Icon(Icons.Default.Delete, "حذف") } } } } }
        }
    }
    if (addPerson) CustodyPersonDialog(people, { addPerson = false }) { vm.addPerson(custodyId, it); addPerson = false }
    newType?.let { t -> CustodyOperationDialog(vm, custodyId, people, currency, t, null, { newType = null }, { newType = null }) }
    editing?.let { t -> CustodyOperationDialog(vm, custodyId, people, t.currencyCode, t.type, t, { editing = null }, { editing = null }) }
    deleting?.let { t -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("حذف العملية") }, text = { Text("سيتم حذف العملية نهائيًا.") }, confirmButton = { TextButton({ vm.deleteTransaction(t.id); deleting = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ deleting = null }) { Text("إلغاء") } }) }
    if (showReport) CustodyReportDialog(c, accounts, tx, { showReport = false })
}

@Composable private fun CustodyPersonDialog(existing: List<CustodyPersonEntity>, onDismiss: () -> Unit, onSave: (CustodyPersonEntity) -> Unit) { var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; val matches = existing.filter { name.isNotBlank() && it.name.contains(name.trim(), true) }.take(5); AlertDialog(onDismissRequest = onDismiss, title = { Text("إضافة شخص") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().semantics { contentDescription = "الاسم" }, label = { Text("الاسم") }, singleLine = true); matches.forEach { s -> TextButton({ name = s.name; phone = s.phone; address = s.address; notes = s.notes }, Modifier.fillMaxWidth()) { Text(s.name) } }; OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth().semantics { contentDescription = "الهاتف" }, label = { Text("الهاتف") }, singleLine = true); OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth().semantics { contentDescription = "العنوان" }, label = { Text("العنوان") }, singleLine = true); OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth().semantics { contentDescription = "الملاحظات" }, label = { Text("الملاحظات") }, minLines = 2) } }, confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onSave(CustodyPersonEntity(custodyId = 0, name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim())) }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }) }

@Composable fun CustodyOperationDialog(vm: CustodyViewModel, custodyId: Long, people: List<CustodyPersonEntity>, defaultCurrency: String, initialType: String, transaction: CustodyTransactionEntity?, onDismiss: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current; val keyboard = LocalSoftwareKeyboardController.current; val calc = LocalCalculatorController.current; val scope = rememberCoroutineScope()
    var currency by remember(transaction?.id) { mutableStateOf(transaction?.currencyCode ?: defaultCurrency) }; var type by remember(transaction?.id) { mutableStateOf(transaction?.type ?: initialType) }; var personId by remember(transaction?.id) { mutableStateOf(transaction?.personId) }; var amount by remember(transaction?.id) { mutableStateOf(transaction?.let { money(it.amountMinor) } ?: "") }; var details by remember(transaction?.id) { mutableStateOf(transaction?.description ?: "") }; var date by remember(transaction?.id) { mutableStateOf(transaction?.transactionDate ?: System.currentTimeMillis()) }; var error by remember(transaction?.id) { mutableStateOf(false) }; var saving by remember(transaction?.id) { mutableStateOf(false) }; var attachments by remember(transaction?.id) { mutableStateOf<List<TransactionAttachmentStorage.SelectedAttachment>>(emptyList()) }
    val need = type == CustodyTransactionType.PAID_TO_PERSON || type == CustodyTransactionType.RETURNED_FROM_PERSON; val existing = remember(transaction?.id) { transaction?.let { vm.attachments(it.id) } ?: emptyList() }
    DisposableEffect(calc, transaction?.id) { calc.setResultConsumer { amount = it; error = false }; onDispose { calc.setResultConsumer(null) } }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { Surface(Modifier.fillMaxWidth(.95f), shape = MaterialTheme.shapes.large) { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (transaction == null) "إضافة عملية" else "تعديل العملية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) { OutlinedTextField(amount, { amount = it; error = false }, Modifier.weight(1.2f).semantics { contentDescription = "المبلغ للعملية" }, label = { Text("المبلغ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = error, enabled = !saving, trailingIcon = { CalculatorButton(onClick = calc::open) }); OutlinedTextField(SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(date)), {}, Modifier.weight(1f).semantics { contentDescription = "تاريخ العملية" }, label = { Text("التاريخ") }, readOnly = true, enabled = !saving, singleLine = true, trailingIcon = { IconButton({ val d = Calendar.getInstance().apply { timeInMillis = date }; DatePickerDialog(context, { _, y, m, day -> d.set(y, m, day, 12, 0, 0); date = d.timeInMillis }, d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH)).show() }) { Icon(Icons.Default.CalendarToday, "التاريخ") } }) }
        if (error) Text("تعذر حفظ العملية. تحقق من البيانات وحاول مرة أخرى.", color = MaterialTheme.colorScheme.error)
        OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth().semantics { contentDescription = "بيان العملية" }, label = { Text("التفاصيل") }, singleLine = true, enabled = !saving)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { currencies.forEach { FilterChip(currency == it, { if (!saving) currency = it }, label = { Text(it) }) } }
        listOf(CustodyTransactionType.RECEIVED_FROM_ORG, CustodyTransactionType.PAID_TO_PERSON, CustodyTransactionType.RETURNED_FROM_PERSON, CustodyTransactionType.RETURNED_TO_ORG).forEach { k -> if (!need || k == CustodyTransactionType.PAID_TO_PERSON || k == CustodyTransactionType.RETURNED_FROM_PERSON) Row(Modifier.fillMaxWidth().clickable(enabled = !saving) { type = k; if (k == CustodyTransactionType.RECEIVED_FROM_ORG || k == CustodyTransactionType.RETURNED_TO_ORG) personId = null }) { RadioButton(type == k, { if (!saving) type = k }); Text(typeName(k), Modifier.padding(top = 12.dp)) } }
        if (need && personId == null) people.forEach { p -> Row(Modifier.fillMaxWidth().clickable(enabled = !saving) { personId = p.id }) { RadioButton(personId == p.id, { if (!saving) personId = p.id }); Text(p.name, Modifier.padding(top = 12.dp)) } }
        if (transaction != null && existing.isNotEmpty()) Text("المرفقات الحالية: ${existing.size}", style = MaterialTheme.typography.labelLarge)
        TransactionAttachmentPicker(selectedAttachments = attachments, onAttachmentsChanged = { if (!saving) attachments = it })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(enabled = !saving, onClick = { val m = parseAmount(amount); if (m == null || m <= 0 || (need && personId == null)) { error = true; return@Button }; val selected = attachments.map { CustodyAttachmentStorage.Selected(it.uri, it.fileName, it.mimeType) }; saving = true; scope.launch { runCatching { if (transaction == null) vm.addTransactionAndWait(custodyId, currency, type, personId, m, details, date, selected) else vm.updateTransactionAndWait(transaction.id, currency, type, personId, m, details, date, selected) }.onSuccess { keyboard?.hide(); saving = false; onFinished() }.onFailure { saving = false; error = true } } }, Modifier.weight(1f).semantics { contentDescription = "حفظ العملية" }) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") }; OutlinedButton(enabled = !saving, onClick = { keyboard?.hide(); onDismiss() }, Modifier.weight(1f)) { Text("إلغاء") } }
    } } }
    if (calc.isOpen) Dialog(onDismissRequest = calc::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { Card(Modifier.fillMaxWidth(.92f).imePadding()) { CalculatorOverlay(expression = calc.expression, result = calc.result.orEmpty(), onKey = calc::press, onClear = calc::clear, onBackspace = calc::backspace, onDismiss = calc::close, onUseResult = calc::useResult) } }
}

@Composable private fun CustodyReportDialog(custody: CustodyEntity, accounts: List<CustodyAccountEntity>, transactions: List<CustodyTransactionEntity>, onDismiss: () -> Unit) {
    var currency by remember { mutableStateOf("YER") }
    val owner = accounts.firstOrNull { it.holderType == "OWNER" && it.personId == null && it.currencyCode == currency }
    val rows = transactions.filter { it.currencyCode == currency }.sortedBy { it.transactionDate }
    val balance = rows.filter { it.accountId == owner?.id }.sumOf { CustodyBalanceRules.ownerDelta(it.type, it.amountMinor) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تقرير العهدة") }, text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) { item { Text(custody.name, fontWeight = FontWeight.Bold); Text("الجهة: ${custody.organizationName}"); Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { currencies.forEach { code -> FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(code) }) } }; Text("الرصيد: ${signed(balance)}", fontWeight = FontWeight.Bold, color = balanceColor(balance)) }; items(rows, key = { it.id }) { transaction -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) { Text(typeName(transaction.type), fontWeight = FontWeight.Bold); Text("${money(transaction.amountMinor)} $currency"); if (transaction.description.isNotBlank()) Text(transaction.description) } } } } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } })
}
