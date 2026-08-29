package com.myaccounts.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myaccounts.app.data.custody.CustodyBalanceRules
import com.myaccounts.app.data.custody.CustodyPersonEntity
import com.myaccounts.app.data.custody.CustodyTransactionEntity
import com.myaccounts.app.data.custody.CustodyTransactionType
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

private val pc = listOf("YER", "SAR", "USD")
private fun pm(v: Long) = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun pp(v: String): Long? = runCatching { BigDecimal(v.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }.getOrNull()
private fun ps(v: Long) = when { v > 0 -> "عليه ${pm(v)}"; v < 0 -> "له ${pm(-v)}"; else -> "متوازن 0" }
private fun balanceColor(v: Long) = when { v > 0 -> Due; v < 0 -> Owed; else -> MaterialTheme.colorScheme.primary }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyPersonOperationsScreen(vm: CustodyViewModel, custodyId: Long, personId: Long, onBack: () -> Unit) {
    val people by vm.persons(custodyId).collectAsState(); val tx by vm.transactions(custodyId).collectAsState(); val person = people.firstOrNull { it.id == personId } ?: return
    var currency by remember { mutableStateOf("YER") }; var add by remember { mutableStateOf(false) }; var editing by remember { mutableStateOf<CustodyTransactionEntity?>(null) }; var deleting by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    val rows = tx.filter { it.personId == personId && it.currencyCode == currency }.sortedByDescending { it.transactionDate }; val balance = rows.sumOf { CustodyBalanceRules.personDelta(it.type, it.amountMinor) }
    Scaffold(topBar = { TopAppBar(title = { Text(person.name, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton({ onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }) }, floatingActionButton = { FloatingActionButton({ add = true }) { Text("+") } }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(13.dp)) { Text("الرصيد — $currency", fontWeight = FontWeight.Bold); Text(ps(balance), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = balanceColor(balance)) } } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { pc.forEach { FilterChip(currency == it, { currency = it }, label = { Text(it) }) } } }
            items(rows, key = { it.id }) { t -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(if (t.type == CustodyTransactionType.PAID_TO_PERSON) "صرف للشخص" else "مرتجع من الشخص", fontWeight = FontWeight.Bold); Text(SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(t.transactionDate)), style = MaterialTheme.typography.bodySmall) }; Text("${pm(t.amountMinor)} $currency", style = MaterialTheme.typography.titleMedium); if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodySmall); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { IconButton({ editing = t }) { Icon(Icons.Default.Edit, "تعديل") }; IconButton({ deleting = t }) { Icon(Icons.Default.Delete, "حذف") } } } } }
        }
    }
    if (add) PersonOperationDialog(vm, custodyId, person, currency, null, { add = false }) { add = false }
    editing?.let { t -> PersonOperationDialog(vm, custodyId, person, t.currencyCode, t, { editing = null }) { editing = null } }
    deleting?.let { t -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("حذف العملية") }, text = { Text("سيتم حذف العملية نهائيًا.") }, confirmButton = { TextButton({ vm.deleteTransaction(t.id); deleting = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ deleting = null }) { Text("إلغاء") } }) }
}

@Composable
private fun PersonOperationDialog(vm: CustodyViewModel, custodyId: Long, person: CustodyPersonEntity, currency0: String, transaction: CustodyTransactionEntity?, onDismiss: () -> Unit, onFinished: () -> Unit) {
    val ctx = LocalContext.current; val kb = LocalSoftwareKeyboardController.current; val calc = LocalCalculatorController.current; val scope = rememberCoroutineScope()
    var currency by remember(transaction?.id) { mutableStateOf(transaction?.currencyCode ?: currency0) }; var type by remember(transaction?.id) { mutableStateOf(transaction?.type ?: CustodyTransactionType.PAID_TO_PERSON) }; var amount by remember(transaction?.id) { mutableStateOf(transaction?.let { pm(it.amountMinor) } ?: "") }; var details by remember(transaction?.id) { mutableStateOf(transaction?.description ?: "") }; var date by remember(transaction?.id) { mutableStateOf(transaction?.transactionDate ?: System.currentTimeMillis()) }; var error by remember(transaction?.id) { mutableStateOf(false) }; var saving by remember(transaction?.id) { mutableStateOf(false) }; var attachments by remember(transaction?.id) { mutableStateOf<List<TransactionAttachmentStorage.SelectedAttachment>>(emptyList()) }
    val existing = remember(transaction?.id) { transaction?.let { vm.attachments(it.id) } ?: emptyList() }
    DisposableEffect(calc, transaction?.id) { calc.setResultConsumer { amount = it; error = false }; onDispose { calc.setResultConsumer(null) } }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { Surface(Modifier.fillMaxWidth(.95f), shape = MaterialTheme.shapes.large) { Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (transaction == null) "إضافة عملية" else "تعديل العملية", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) { OutlinedTextField(amount, { amount = it; error = false }, Modifier.weight(1.2f).semantics { contentDescription = "المبلغ للعملية" }, label = { Text("المبلغ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = error, enabled = !saving, trailingIcon = { CalculatorButton(onClick = calc::open) }); OutlinedTextField(SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(date)), {}, Modifier.weight(1f).semantics { contentDescription = "تاريخ العملية" }, label = { Text("التاريخ") }, readOnly = true, enabled = !saving, singleLine = true, trailingIcon = { IconButton({ val d = Calendar.getInstance().apply { timeInMillis = date }; DatePickerDialog(ctx, { _, y, m, day -> d.set(y, m, day, 12, 0, 0); date = d.timeInMillis }, d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH)).show() }) { Icon(Icons.Default.CalendarToday, "التاريخ") } }) }
        if (error) Text("تعذر حفظ العملية. تحقق من البيانات وحاول مرة أخرى.", color = MaterialTheme.colorScheme.error)
        OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth().semantics { contentDescription = "بيان العملية" }, label = { Text("التفاصيل") }, singleLine = true, enabled = !saving)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { pc.forEach { FilterChip(currency == it, { if (!saving) currency = it }, label = { Text(it) }) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(type == CustodyTransactionType.PAID_TO_PERSON, { if (!saving) type = CustodyTransactionType.PAID_TO_PERSON }, label = { Text("صرف للشخص") }); FilterChip(type == CustodyTransactionType.RETURNED_FROM_PERSON, { if (!saving) type = CustodyTransactionType.RETURNED_FROM_PERSON }, label = { Text("مرتجع من الشخص") }) }
        if (existing.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("المرفقات الحالية", fontWeight = FontWeight.Bold); existing.forEach { a -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(a.fileName, Modifier.weight(1f)); TextButton(enabled = !saving, onClick = { vm.deleteAttachment(a) }) { Text("حذف") } } } }
        TransactionAttachmentPicker(selectedAttachments = attachments, onAttachmentsChanged = { if (!saving) attachments = it })
        Button(enabled = !saving, onClick = { val m = pp(amount); if (m == null || m <= 0) { error = true; return@Button }; val selected = attachments.map { CustodyAttachmentStorage.Selected(it.uri, it.fileName, it.mimeType) }; saving = true; scope.launch { runCatching { if (transaction == null) vm.addTransactionAndWait(custodyId, currency, type, person.id, m, details, date, selected) else vm.updateTransactionAndWait(transaction.id, currency, type, person.id, m, details, date, selected) }.onSuccess { kb?.hide(); saving = false; onFinished() }.onFailure { saving = false; error = true } } }, Modifier.fillMaxWidth().semantics { contentDescription = "حفظ العملية" }) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") }
        OutlinedButton(enabled = !saving, onClick = { kb?.hide(); onDismiss() }, Modifier.fillMaxWidth()) { Text("إلغاء") }
    } } }
    if (calc.isOpen) Dialog(onDismissRequest = calc::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { Card(Modifier.fillMaxWidth(.92f).imePadding()) { CalculatorOverlay(expression = calc.expression, result = calc.result.orEmpty(), onKey = calc::press, onClear = calc::clear, onBackspace = calc::backspace, onDismiss = calc::close, onUseResult = calc::useResult) } }
}
