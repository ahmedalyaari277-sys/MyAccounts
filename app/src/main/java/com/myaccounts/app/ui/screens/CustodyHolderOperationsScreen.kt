package com.myaccounts.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myaccounts.app.data.custody.CustodyTransactionEntity
import com.myaccounts.app.data.custody.CustodyTransactionType
import com.myaccounts.app.ui.components.BalanceAmount
import com.myaccounts.app.ui.components.BalanceStatus
import com.myaccounts.app.ui.components.CalculatorButton
import com.myaccounts.app.ui.components.CalculatorOverlay
import com.myaccounts.app.ui.components.CustodyOperationCard
import com.myaccounts.app.ui.components.CustodyOperationTone
import com.myaccounts.app.ui.components.CurrencyChip
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.LocalCalculatorController
import com.myaccounts.app.ui.components.PrimaryButton
import com.myaccounts.app.ui.components.SecondaryButton
import com.myaccounts.app.ui.components.SummaryCard
import com.myaccounts.app.ui.components.StatusChip
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val holderCurrencies = listOf("YER", "SAR", "USD")
private fun holderMoney(v: Long) = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun holderParseAmount(v: String): Long? = runCatching {
    BigDecimal(v.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
}.getOrNull()
private fun holderTypeName(type: String) = when (type) {
    CustodyTransactionType.RECEIVED_FROM_ORG -> "استلام من الجهة"
    CustodyTransactionType.RETURNED_TO_ORG -> "مرتجع للجهة / تصفية"
    else -> type
}
private fun holderSigned(v: Long) = when {
    v > 0 -> "عليه ${holderMoney(v)}"
    v < 0 -> "له ${holderMoney(-v)}"
    else -> "متوازن 0"
}
private fun holderStatus(v: Long) = when {
    v > 0 -> BalanceStatus.Due
    v < 0 -> BalanceStatus.Owed
    else -> BalanceStatus.Neutral
}
private fun holderTone(type: String) = when (type) {
    CustodyTransactionType.RECEIVED_FROM_ORG -> CustodyOperationTone.ReceiveFromOrganization
    CustodyTransactionType.RETURNED_TO_ORG -> CustodyOperationTone.ReturnToOrganization
    else -> CustodyOperationTone.Neutral
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyHolderOperationsScreen(vm: CustodyViewModel, custodyId: Long, onBack: () -> Unit) {
    val custody by vm.custody(custodyId).collectAsState()
    val accounts by vm.accounts(custodyId).collectAsState()
    val transactions by vm.transactions(custodyId).collectAsState()
    val current = custody ?: return
    var currency by remember { mutableStateOf("YER") }
    var dialogType by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    var deleting by remember { mutableStateOf<CustodyTransactionEntity?>(null) }

    Scaffold(
        modifier = Modifier.semantics { contentDescription = "شاشة عمليات حامل العهدة" },
        topBar = {
            TopAppBar(
                title = { Text("حامل العهدة", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SummaryCard(title = "بطاقة حامل العهدة") {
                    Text(current.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("الجهة: ${current.organizationName}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        holderCurrencies.forEach { code ->
                            val balance = accounts.firstOrNull { it.holderType == "OWNER" && it.personId == null && it.currencyCode == code }?.balanceMinor ?: 0L
                            InformationCard(modifier = Modifier.weight(1f)) {
                                StatusChip(code, MaterialTheme.colorScheme.primary)
                                BalanceAmount(holderSigned(balance), holderStatus(balance), modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    holderCurrencies.forEach { code -> CurrencyChip(currency = code, selected = currency == code, onClick = { currency = code }) }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("استلام من الجهة", { dialogType = CustodyTransactionType.RECEIVED_FROM_ORG }, Modifier.weight(1f).semantics { contentDescription = "استلام من الجهة" })
                    SecondaryButton("مرتجع للجهة / تصفية", { dialogType = CustodyTransactionType.RETURNED_TO_ORG }, Modifier.weight(1f).semantics { contentDescription = "مرتجع للجهة / تصفية" })
                }
            }
            item { Text("عمليات حامل العهدة — $currency", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            val holderTransactions = transactions
                .filter { it.currencyCode == currency && (it.type == CustodyTransactionType.RECEIVED_FROM_ORG || it.type == CustodyTransactionType.RETURNED_TO_ORG) }
                .sortedByDescending { it.transactionDate }
            if (holderTransactions.isEmpty()) {
                item {
                    InformationCard {
                        Text("لا توجد عمليات بهذه العملة حتى الآن.", style = MaterialTheme.typography.bodyLarge)
                        Text("استخدم أزرار العمليات أعلاه لإضافة أول عملية.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(holderTransactions, key = { it.id }) { t ->
                CustodyOperationCard(
                    operationType = holderTypeName(t.type), amount = "${holderMoney(t.amountMinor)} $currency", currency = currency,
                    date = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(t.transactionDate)),
                    description = t.description.takeIf { it.isNotBlank() }, tone = holderTone(t.type),
                    actions = {
                        IconButton(onClick = { editing = t }, modifier = Modifier.semantics { contentDescription = "تعديل" }) { Icon(Icons.Default.Edit, "تعديل") }
                        IconButton(onClick = { deleting = t }, modifier = Modifier.semantics { contentDescription = "حذف" }) { Icon(Icons.Default.Delete, "حذف") }
                    }
                )
            }
        }
    }

    dialogType?.let { type -> CustodyHolderOperationDialog(vm, custodyId, currency, type, null, { dialogType = null }, { dialogType = null }) }
    editing?.let { t -> CustodyHolderOperationDialog(vm, custodyId, t.currencyCode, t.type, t, { editing = null }, { editing = null }) }
    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("حذف العملية") }, text = { Text("سيتم حذف العملية نهائيًا.") },
            confirmButton = { TextButton(onClick = { vm.deleteTransaction(t.id); deleting = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun CustodyHolderOperationDialog(vm: CustodyViewModel, custodyId: Long, defaultCurrency: String, initialType: String, transaction: CustodyTransactionEntity?, onDismiss: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    val calc = LocalCalculatorController.current
    var currency by remember(transaction?.id) { mutableStateOf(transaction?.currencyCode ?: defaultCurrency) }
    var amount by remember(transaction?.id) { mutableStateOf(transaction?.let { holderMoney(it.amountMinor) } ?: "") }
    var details by remember(transaction?.id) { mutableStateOf(transaction?.description ?: "") }
    var date by remember(transaction?.id) { mutableStateOf(transaction?.transactionDate ?: System.currentTimeMillis()) }
    var saving by remember(transaction?.id) { mutableStateOf(false) }
    var error by remember(transaction?.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(calc, transaction?.id) { calc.setResultConsumer { amount = it; error = false }; onDispose { calc.setResultConsumer(null) } }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() }, title = { Text(if (transaction == null) "إضافة عملية حامل العهدة" else "تعديل عملية حامل العهدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(amount, { amount = it; error = false }, Modifier.weight(1.2f), label = { Text("المبلغ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = error, enabled = !saving, trailingIcon = { CalculatorButton(onClick = calc::open) })
                    OutlinedTextField(SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(date)), {}, Modifier.weight(1f), label = { Text("التاريخ") }, readOnly = true, enabled = !saving, singleLine = true, trailingIcon = { IconButton(enabled = !saving, onClick = { val d = Calendar.getInstance().apply { timeInMillis = date }; DatePickerDialog(context, { _, y, m, day -> d.set(y, m, day, 12, 0, 0); date = d.timeInMillis }, d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH)).show() }) { Icon(Icons.Default.CalendarToday, "التاريخ") } })
                }
                if (error) Text("تعذر حفظ العملية. تحقق من المبلغ وحاول مرة أخرى.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth(), label = { Text("التفاصيل") }, singleLine = true, enabled = !saving)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { holderCurrencies.forEach { code -> CurrencyChip(currency = code, selected = currency == code, onClick = { if (!saving) currency = code }) } }
                Text("نوع العملية: ${holderTypeName(initialType)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                val parsed = holderParseAmount(amount)
                if (parsed == null || parsed <= 0L) { error = true; return@TextButton }
                saving = true
                scope.launch {
                    runCatching {
                        if (transaction == null) vm.addTransactionAndWait(custodyId, currency, initialType, null, parsed, details, date)
                        else vm.updateTransactionAndWait(transaction.id, currency, initialType, null, parsed, details, date)
                    }.onSuccess { onFinished() }.onFailure { error = true }
                    saving = false
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("إلغاء") } }
    )
    if (calc.isOpen) Dialog(onDismissRequest = calc::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(modifier = Modifier.fillMaxWidth(.92f).imePadding()) { CalculatorOverlay(expression = calc.expression, result = calc.result.orEmpty(), onKey = calc::press, onClear = calc::clear, onBackspace = calc::backspace, onDismiss = calc::close, onUseResult = calc::useResult) }
    }
}