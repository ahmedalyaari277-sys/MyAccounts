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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myaccounts.app.data.custody.CustodyBalanceRules
import com.myaccounts.app.data.custody.CustodyPersonEntity
import com.myaccounts.app.data.custody.CustodyTransactionAttachmentEntity
import com.myaccounts.app.data.custody.CustodyTransactionEntity
import com.myaccounts.app.data.custody.CustodyTransactionType
import com.myaccounts.app.ui.components.BalanceAmount
import com.myaccounts.app.ui.components.BalanceStatus
import com.myaccounts.app.ui.components.CalculatorButton
import com.myaccounts.app.ui.components.CalculatorOverlay
import com.myaccounts.app.ui.components.CurrencyChip
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.LocalCalculatorController
import com.myaccounts.app.ui.components.PrimaryButton
import com.myaccounts.app.ui.components.SecondaryButton
import com.myaccounts.app.ui.components.SummaryCard
import com.myaccounts.app.ui.components.TransactionCard
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import com.myaccounts.app.util.CustodyAttachmentStorage
import com.myaccounts.app.util.TransactionAttachmentStorage
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val currencies = listOf("YER", "SAR", "USD")
private fun money(v: Long) = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun parseAmount(v: String): Long? = runCatching { BigDecimal(v.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }.getOrNull()
private fun signed(v: Long) = when { v > 0 -> "عليه ${money(v)}"; v < 0 -> "له ${money(-v)}"; else -> "متوازن 0" }
private fun balanceStatus(v: Long) = when { v > 0 -> BalanceStatus.Due; v < 0 -> BalanceStatus.Owed; else -> BalanceStatus.Neutral }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyPersonOperationsScreen(vm: CustodyViewModel, custodyId: Long, personId: Long, onBack: () -> Unit) {
    val people by vm.persons(custodyId).collectAsState()
    val tx by vm.transactions(custodyId).collectAsState()
    val person = people.firstOrNull { it.id == personId } ?: return
    var currency by remember { mutableStateOf("YER") }
    var add by remember { mutableStateOf(false) }
    var initialType by remember { mutableStateOf(CustodyTransactionType.PAID_TO_PERSON) }
    var editing by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    var deleting by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    val rows = tx.filter { it.personId == personId && it.currencyCode == currency }.sortedByDescending { it.transactionDate }
    val balance = rows.sumOf { CustodyBalanceRules.personDelta(it.type, it.amountMinor) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(person.name, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { initialType = CustodyTransactionType.PAID_TO_PERSON; add = true }, modifier = Modifier.semantics { contentDescription = "إضافة عملية" }) { Text("+") }
        }
    ) { pad ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SummaryCard(title = "حساب الشخص") {
                    Text(person.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (person.phone.isNotBlank()) Text(person.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (person.address.isNotBlank()) Text(person.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BalanceAmount(amount = signed(balance), status = balanceStatus(balance), label = currency)
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    currencies.forEach { code -> CurrencyChip(currency = code, selected = currency == code, onClick = { currency = code }) }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(text = "صرف للشخص", onClick = { initialType = CustodyTransactionType.PAID_TO_PERSON; add = true }, modifier = Modifier.weight(1f).semantics { contentDescription = "صرف للشخص" })
                    SecondaryButton(text = "مرتجع من الشخص", onClick = { initialType = CustodyTransactionType.RETURNED_FROM_PERSON; add = true }, modifier = Modifier.weight(1f).semantics { contentDescription = "مرتجع من الشخص" })
                }
            }
            if (rows.isEmpty()) {
                item {
                    InformationCard {
                        Text("لا توجد عمليات لهذا الشخص بهذه العملة.", style = MaterialTheme.typography.bodyLarge)
                        Text("يمكنك تسجيل صرف أو مرتجع من الأزرار أعلاه.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(rows, key = { it.id }) { t ->
                val typeLabel = if (t.type == CustodyTransactionType.PAID_TO_PERSON) "صرف للشخص" else "مرتجع من الشخص"
                TransactionCard(
                    operationType = typeLabel,
                    amount = "${money(t.amountMinor)} $currency",
                    status = if (t.type == CustodyTransactionType.PAID_TO_PERSON) "عليه" else "له",
                    description = t.description.takeIf { it.isNotBlank() },
                    date = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(t.transactionDate)),
                    amountStatus = if (t.type == CustodyTransactionType.PAID_TO_PERSON) BalanceStatus.Due else BalanceStatus.Owed,
                    actions = {
                        IconButton(onClick = { editing = t }, modifier = Modifier.semantics { contentDescription = "تعديل" }) { Icon(Icons.Default.Edit, "تعديل") }
                        IconButton(onClick = { deleting = t }, modifier = Modifier.semantics { contentDescription = "حذف" }) { Icon(Icons.Default.Delete, "حذف") }
                    }
                )
            }
        }
    }
    if (add) PersonOperationDialog(vm, custodyId, person, currency, null, onDismiss = { add = false }, onFinished = { add = false }, initialType = initialType)
    editing?.let { t -> PersonOperationDialog(vm, custodyId, person, t.currencyCode, t, onDismiss = { editing = null }, onFinished = { editing = null }, initialType = t.type) }
    deleting?.let { t -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("حذف العملية") }, text = { Text("سيتم حذف العملية نهائيًا.") }, confirmButton = { TextButton(onClick = { vm.deleteTransaction(t.id); deleting = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } }) }
}

@Composable
private fun PersonOperationDialog(vm: CustodyViewModel, custodyId: Long, person: CustodyPersonEntity, defaultCurrency: String, transaction: CustodyTransactionEntity?, onDismiss: () -> Unit, onFinished: () -> Unit, initialType: String) {
    val context = LocalContext.current; val keyboard = LocalSoftwareKeyboardController.current; val calc = LocalCalculatorController.current; val scope = rememberCoroutineScope()
    var currency by remember(transaction?.id) { mutableStateOf(transaction?.currencyCode ?: defaultCurrency) }; var type by remember(transaction?.id) { mutableStateOf(transaction?.type ?: initialType) }; var amount by remember(transaction?.id) { mutableStateOf(transaction?.let { money(it.amountMinor) } ?: "") }; var details by remember(transaction?.id) { mutableStateOf(transaction?.description ?: "") }; var date by remember(transaction?.id) { mutableStateOf(transaction?.transactionDate ?: System.currentTimeMillis()) }; var error by remember(transaction?.id) { mutableStateOf(false) }; var saving by remember(transaction?.id) { mutableStateOf(false) }; var attachments by remember(transaction?.id) { mutableStateOf<List<TransactionAttachmentStorage.SelectedAttachment>>(emptyList()) }; var deletedAttachments by remember(transaction?.id) { mutableStateOf<List<CustodyTransactionAttachmentEntity>>(emptyList()) }
    val existing = remember(transaction?.id) { transaction?.let { vm.attachments(it.id) } ?: emptyList() }
    val visibleExisting = existing.filter { saved -> deletedAttachments.none { it.id == saved.id } }
    DisposableEffect(calc, transaction?.id) { calc.setResultConsumer { amount = it; error = false }; onDispose { calc.setResultConsumer(null) } }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(.95f).semantics { contentDescription = "حوار العملية" }, shape = MaterialTheme.shapes.large) { Column(modifier = Modifier.verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (transaction == null) "إضافة عملية" else "تعديل العملية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { contentDescription = "حوار العملية" })
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it; error = false }, modifier = Modifier.weight(1.2f).semantics { contentDescription = "المبلغ للعملية" }, label = { Text("المبلغ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = error, enabled = !saving, trailingIcon = { CalculatorButton(onClick = calc::open) })
                OutlinedTextField(SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(date)), {}, modifier = Modifier.weight(1f).semantics { contentDescription = "تاريخ العملية" }, label = { Text("التاريخ") }, readOnly = true, enabled = !saving, singleLine = true, trailingIcon = { IconButton(enabled = !saving, onClick = { val d = Calendar.getInstance().apply { timeInMillis = date }; DatePickerDialog(context, { _, y, m, day -> d.set(y, m, day, 12, 0, 0); date = d.timeInMillis }, d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH)).show() }) { Icon(Icons.Default.CalendarToday, "التاريخ") } })
            }
            if (error) Text("تعذر حفظ العملية. تحقق من البيانات.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(details, { details = it }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "بيان العملية" }, label = { Text("التفاصيل") }, singleLine = true, enabled = !saving)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { currencies.forEach { CurrencyChip(currency = it, selected = currency == it, onClick = { if (!saving) currency = it }) } }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(text = "صرف للشخص", onClick = { if (!saving) type = CustodyTransactionType.PAID_TO_PERSON }, modifier = Modifier.weight(1f))
                SecondaryButton(text = "مرتجع من الشخص", onClick = { if (!saving) type = CustodyTransactionType.RETURNED_FROM_PERSON }, modifier = Modifier.weight(1f))
            }
            if (transaction != null && visibleExisting.isNotEmpty()) {
                Text("المرفقات الحالية: ${visibleExisting.size}", style = MaterialTheme.typography.labelLarge)
                visibleExisting.forEach { a -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(a.fileName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge); TextButton(enabled = !saving, onClick = { deletedAttachments = deletedAttachments + a }) { Text("حذف") } } }
            }
            TransactionAttachmentPicker(selectedAttachments = attachments, onAttachmentsChanged = { if (!saving) attachments = it })
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(text = if (saving) "جارٍ الحفظ…" else "حفظ", onClick = {
                    val m = parseAmount(amount); if (m == null || m <= 0) { error = true; return@PrimaryButton }; val selected = attachments.map { CustodyAttachmentStorage.Selected(it.uri, it.fileName, it.mimeType) }; saving = true
                    scope.launch { runCatching { if (transaction == null) vm.addTransactionAndWait(custodyId, currency, type, person.id, m, details, date, selected) else vm.updateTransactionAndWait(transaction.id, currency, type, person.id, m, details, date, selected, deletedAttachments) }.onSuccess { keyboard?.hide(); saving = false; onFinished() }.onFailure { saving = false; error = true } }
                }, modifier = Modifier.weight(1f).semantics { contentDescription = "حفظ العملية" })
                SecondaryButton(text = "إلغاء", onClick = { keyboard?.hide(); onDismiss() }, modifier = Modifier.weight(1f))
            }
        } }
    }
    if (calc.isOpen) Dialog(onDismissRequest = calc::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { Card(modifier = Modifier.fillMaxWidth(.92f).imePadding()) { CalculatorOverlay(expression = calc.expression, result = calc.result.orEmpty(), onKey = calc::press, onClear = calc::clear, onBackspace = calc::backspace, onDismiss = calc::close, onUseResult = calc::useResult) } }
}
