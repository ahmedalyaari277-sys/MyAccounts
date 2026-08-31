package com.myaccounts.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.myaccounts.app.data.custody.*
import com.myaccounts.app.ui.components.CalculatorButton
import com.myaccounts.app.ui.components.CalculatorOverlay
import com.myaccounts.app.ui.components.LocalCalculatorController
import com.myaccounts.app.ui.theme.Due
import com.myaccounts.app.ui.theme.Owed
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

private val custodyLedgerCurrencies = listOf("YER", "SAR", "USD")

private fun custodyMoney(v: Long): String = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()

private fun custodyParseAmount(v: String): Long? = runCatching {
    BigDecimal(v.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
}.getOrNull()

private fun custodyOperationLabel(type: String, owner: Boolean, personName: String = ""): String = when (type) {
    CustodyTransactionType.RECEIVED_FROM_ORG -> "استلام من الجهة"
    CustodyTransactionType.RETURNED_TO_ORG -> "مرتجع للجهة"
    CustodyTransactionType.PAID_TO_PERSON -> if (owner && personName.isNotBlank()) "صرف لـ $personName" else "صرف"
    CustodyTransactionType.RETURNED_FROM_PERSON -> if (owner && personName.isNotBlank()) "مرتجع من $personName" else "مرتجع"
    else -> type
}

private fun custodyBalanceStatus(value: Long, owner: Boolean): String = when {
    value > 0L -> if (owner) "المتبقي لديه" else "لديه"
    value < 0L -> if (owner) "عجز" else "مرتجع زائد"
    else -> "متوازن"
}

private fun custodyBalanceColor(value: Long): androidx.compose.ui.graphics.Color = when {
    value > 0L -> Due
    value < 0L -> Owed
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyLedgerScreen(
    vm: CustodyViewModel,
    custodyId: Long,
    personId: Long?,
    onBack: () -> Unit,
    dialogWidth: Float = .92f
) {
    val people by vm.persons(custodyId).collectAsState()
    val accounts by vm.accounts(custodyId).collectAsState()
    val transactions by vm.transactions(custodyId).collectAsState()
    val custody by vm.custody(custodyId).collectAsState()
    val owner = personId == null
    val person = people.firstOrNull { it.id == personId }
    if (!owner && person == null) return

    val title = if (owner) custody?.name ?: "صاحب العهدة" else person!!.name
    val visibleAccounts = accounts.filter { a ->
        if (owner) a.holderType == "OWNER" && a.personId == null
        else a.holderType == "PERSON" && a.personId == personId
    }
    var selectedCurrency by remember(visibleAccounts) { mutableStateOf(visibleAccounts.firstOrNull()?.currencyCode ?: "YER") }
    var showAdd by remember { mutableStateOf(false) }
    var initialType by remember(owner) {
        mutableStateOf(if (owner) CustodyTransactionType.RECEIVED_FROM_ORG else CustodyTransactionType.PAID_TO_PERSON)
    }
    var editing by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    var deleting by remember { mutableStateOf<CustodyTransactionEntity?>(null) }

    val rows = transactions.filter { t ->
        t.currencyCode == selectedCurrency && if (owner) true else t.personId == personId
    }.filter { t ->
        if (owner) {
            accounts.any { it.id == t.accountId && it.holderType == "OWNER" && it.personId == null && it.currencyCode == selectedCurrency }
        } else {
            t.personId == personId
        }
    }.sortedByDescending { it.transactionDate }

    val balance = if (owner) {
        visibleAccounts.firstOrNull { it.currencyCode == selectedCurrency }?.balanceMinor ?: 0L
    } else {
        transactions.filter { it.currencyCode == selectedCurrency && it.personId == personId }
            .sumOf { CustodyBalanceRules.personDelta(it.type, it.amountMinor) }
    }
    val balanceColor = custodyBalanceColor(balance)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("عمليات $title", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    initialType = if (owner) CustodyTransactionType.RECEIVED_FROM_ORG else CustodyTransactionType.PAID_TO_PERSON
                    showAdd = true
                },
                modifier = Modifier.semantics { contentDescription = "إضافة عملية" }
            ) { Icon(Icons.Default.Add, contentDescription = null) }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                custodyLedgerCurrencies.forEach { code ->
                    if (visibleAccounts.any { it.currencyCode == code }) {
                        FilterChip(
                            selected = selectedCurrency == code,
                            onClick = { selectedCurrency = code },
                            label = { Text(code, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        if (owner) "الرصيد الحالي" else "الرصيد الحالي",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(custodyMoney(kotlin.math.abs(balance)), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = balanceColor)
                    Text(custodyBalanceStatus(balance, owner), style = MaterialTheme.typography.bodySmall, color = balanceColor)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (rows.isEmpty()) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("لا توجد عمليات حتى الآن", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("أضف أول عملية باستخدام زر +", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(rows, key = { it.id }) { t ->
                        val personNameForOwner = if (owner && t.personId != null) people.firstOrNull { it.id == t.personId }?.name.orEmpty() else ""
                        val attachmentCount = remember(t.id) { vm.attachments(t.id).size }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(custodyOperationLabel(t.type, owner, personNameForOwner), fontWeight = FontWeight.Bold)
                                    Text(SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(t.transactionDate)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("${custodyMoney(t.amountMinor)} $selectedCurrency", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    if (attachmentCount > 0) Icon(Icons.Default.AttachFile, contentDescription = "المرفقات")
                                    IconButton(onClick = { editing = t }, modifier = Modifier.semantics { contentDescription = "تعديل" }) { Icon(Icons.Default.Edit, contentDescription = "تعديل") }
                                    IconButton(onClick = { deleting = t }, modifier = Modifier.semantics { contentDescription = "حذف" }) { Icon(Icons.Default.Delete, contentDescription = "حذف") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        CustodyLedgerOperationDialog(vm, custodyId, personId, owner, selectedCurrency, initialType, null, dialogWidth, { showAdd = false }, { showAdd = false })
    }
    editing?.let { t ->
        CustodyLedgerOperationDialog(vm, custodyId, if (owner) t.personId else personId, owner, t.currencyCode, t.type, t, .92f, { editing = null }, { editing = null })
    }
    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف العملية") },
            text = { Text("سيتم حذف العملية نهائيًا.") },
            confirmButton = { TextButton(onClick = { vm.deleteTransaction(t.id); deleting = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun CustodyLedgerOperationDialog(
    vm: CustodyViewModel,
    custodyId: Long,
    personId: Long?,
    owner: Boolean,
    defaultCurrency: String,
    initialType: String,
    transaction: CustodyTransactionEntity?,
    dialogWidth: Float = .92f,
    onDismiss: () -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val calc = LocalCalculatorController.current
    val scope = rememberCoroutineScope()
    var currency by remember(transaction?.id) { mutableStateOf(transaction?.currencyCode ?: defaultCurrency) }
    var type by remember(transaction?.id) { mutableStateOf(transaction?.type ?: initialType) }
    var amount by remember(transaction?.id) { mutableStateOf(transaction?.let { custodyMoney(it.amountMinor) } ?: "") }
    var details by remember(transaction?.id) { mutableStateOf(transaction?.description ?: "") }
    var date by remember(transaction?.id) { mutableStateOf(transaction?.transactionDate ?: System.currentTimeMillis()) }
    var saving by remember(transaction?.id) { mutableStateOf(false) }
    var error by remember(transaction?.id) { mutableStateOf(false) }
    var attachments by remember(transaction?.id) { mutableStateOf<List<TransactionAttachmentStorage.SelectedAttachment>>(emptyList()) }
    var deletedAttachments by remember(transaction?.id) { mutableStateOf<List<CustodyTransactionAttachmentEntity>>(emptyList()) }
    val existing = remember(transaction?.id) { transaction?.let { vm.attachments(it.id) } ?: emptyList() }
    val visibleExisting = existing.filter { a -> deletedAttachments.none { it.id == a.id } }
    DisposableEffect(calc, transaction?.id) { calc.setResultConsumer { amount = it; error = false }; onDispose { calc.setResultConsumer(null) } }
    val allowedTypes = if (owner) listOf(CustodyTransactionType.RECEIVED_FROM_ORG, CustodyTransactionType.RETURNED_TO_ORG) else listOf(CustodyTransactionType.PAID_TO_PERSON, CustodyTransactionType.RETURNED_FROM_PERSON)

    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(Modifier.fillMaxWidth(dialogWidth).imePadding(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (transaction == null) "إضافة عملية" else "تعديل العملية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        amount,
                        { amount = it; error = false },
                        Modifier.weight(1.35f),
                        label = { Text("المبلغ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        enabled = !saving,
                        isError = error,
                        trailingIcon = { CalculatorButton(onClick = calc::open) }
                    )
                    OutlinedTextField(
                        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(date)),
                        {},
                        Modifier.weight(1f),
                        label = { Text("التاريخ") },
                        readOnly = true,
                        singleLine = true,
                        enabled = !saving,
                        trailingIcon = {
                            IconButton(enabled = !saving, onClick = {
                                val d = Calendar.getInstance().apply { timeInMillis = date }
                                DatePickerDialog(
                                    context,
                                    { _, y, m, day -> d.set(y, m, day, 12, 0, 0); d.set(Calendar.MILLISECOND, 0); date = d.timeInMillis },
                                    d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }) { Icon(Icons.Default.CalendarToday, contentDescription = "اختيار التاريخ") }
                        }
                    )
                }
                if (error) Text("أدخل مبلغًا صحيحًا أكبر من صفر.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth(), label = { Text("التفاصيل") }, singleLine = true, enabled = !saving)
                Text("العملة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { custodyLedgerCurrencies.forEach { code -> FilterChip(selected = currency == code, onClick = { if (!saving) currency = code }, label = { Text(code) }) } }
                Text("نوع العملية", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { allowedTypes.forEach { kind -> FilterChip(selected = type == kind, onClick = { if (!saving) type = kind }, label = { Text(custodyOperationLabel(kind, owner)) }) } }
                if (transaction != null && visibleExisting.isNotEmpty()) {
                    Text("المرفقات الحالية: ${visibleExisting.size}", fontWeight = FontWeight.Bold)
                    visibleExisting.forEach { a ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AttachFile, contentDescription = null)
                            Text(a.fileName, modifier = Modifier.weight(1f), maxLines = 1)
                            IconButton(enabled = !saving, onClick = { deletedAttachments = deletedAttachments + a }) { Icon(Icons.Default.Delete, contentDescription = "حذف المرفق") }
                        }
                    }
                }
                TransactionAttachmentPicker(selectedAttachments = attachments, onAttachmentsChanged = { if (!saving) attachments = it })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !saving,
                        onClick = {
                            val parsed = custodyParseAmount(amount)
                            if (parsed == null || parsed <= 0L) { error = true; return@Button }
                            val selected = attachments.map { CustodyAttachmentStorage.Selected(it.uri, it.fileName, it.mimeType) }
                            saving = true
                            scope.launch {
                                runCatching {
                                    if (transaction == null) vm.addTransactionAndWait(custodyId, currency, type, personId, parsed, details.trim(), date, selected)
                                    else vm.updateTransactionAndWait(transaction.id, currency, type, personId, parsed, details.trim(), date, selected, deletedAttachments)
                                }.onSuccess { keyboard?.hide(); saving = false; onFinished() }.onFailure { saving = false; error = true }
                            }
                        },
                        modifier = Modifier.weight(1f).semantics { contentDescription = "حفظ العملية" }
                    ) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") }
                    OutlinedButton(enabled = !saving, onClick = { keyboard?.hide(); onDismiss() }, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                }
            }
        }
    }
    if (calc.isOpen) {
        Dialog(onDismissRequest = calc::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Card(Modifier.fillMaxWidth(.92f).imePadding()) {
                CalculatorOverlay(
                    expression = calc.expression,
                    result = calc.result.orEmpty(),
                    onKey = calc::press,
                    onClear = calc::clear,
                    onBackspace = calc::backspace,
                    onDismiss = calc::close,
                    onUseResult = calc::useResult
                )
            }
        }
    }
}
