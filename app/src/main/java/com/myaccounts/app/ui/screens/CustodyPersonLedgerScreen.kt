@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myaccounts.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myaccounts.app.data.custody.*
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val currencies = listOf("YER", "SAR", "USD")
private fun money(v: Long): String = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun typeLabel(type: String): String = when (type) {
    CustodyTransactionType.PAID_TO_PERSON -> "صرف"
    CustodyTransactionType.RETURNED_FROM_PERSON -> "مرتجع"
    CustodyTransactionType.PERSON_LOAN_TO_OWNER -> "تسليف لحامل العهدة"
    CustodyTransactionType.OWNER_REPAY_PERSON_LOAN -> "سداد تسليف للشخص"
    else -> type
}

@Composable
fun CustodyPersonLedgerScreen(vm: CustodyViewModel, custodyId: Long, personId: Long, onBack: () -> Unit) {
    val custody by vm.custody(custodyId).collectAsState()
    val people by vm.persons(custodyId).collectAsState()
    val transactions by vm.transactions(custodyId).collectAsState()
    val person = people.firstOrNull { it.id == personId } ?: return
    val current = custody ?: return
    var currency by remember { mutableStateOf("YER") }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    var deleting by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    var transferring by remember { mutableStateOf<CustodyTransactionEntity?>(null) }

    val rows = transactions.filter { it.personId == personId && it.currencyCode == currency }.sortedByDescending { it.transactionDate }
    val custodyBalance = CustodyFinancialSummary.personCustodyBalance(transactions, personId, currency)
    val debt = CustodyFinancialSummary.personDebt(transactions, personId, currency)

    Scaffold(
        topBar = { TopAppBar(title = { Text(person.name, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }) },
        floatingActionButton = { FloatingActionButton(onClick = { if (!current.isClosed) showAdd = true }, modifier = Modifier.semantics { contentDescription = "إضافة عملية" }) { Icon(Icons.Default.Add, null) } }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                currencies.forEach { code -> FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(code, fontWeight = FontWeight.Bold) }, modifier = Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(currency, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    SummaryRow("العهدة", kotlin.math.abs(custodyBalance), when { custodyBalance > 0 -> "لديه"; custodyBalance < 0 -> "مستحق له"; else -> "متوازن" })
                    SummaryRow("الذمة", kotlin.math.abs(debt), when { debt > 0 -> "مستحق له"; debt < 0 -> "مستحق عليه"; else -> "متوازن" })
                }
            }
            Spacer(Modifier.height(10.dp))
            if (rows.isEmpty()) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("لا توجد عمليات لهذه العملة", fontWeight = FontWeight.Bold)
                        Text("استخدم زر + لإضافة أول عملية", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(rows, key = { it.id }) { transaction ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(typeLabel(transaction.type), fontWeight = FontWeight.Bold)
                                    Text(SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(transaction.transactionDate)), style = MaterialTheme.typography.bodySmall)
                                }
                                Text("${money(transaction.amountMinor)} $currency", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                if (transaction.description.isNotBlank()) Text(transaction.description, style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    IconButton(enabled = !current.isClosed, onClick = { transferring = transaction }, modifier = Modifier.semantics { contentDescription = "نقل العملية" }) { Icon(Icons.Default.SwapHoriz, "نقل العملية") }
                                    IconButton(enabled = !current.isClosed, onClick = { editing = transaction }, modifier = Modifier.semantics { contentDescription = "تعديل" }) { Icon(Icons.Default.Edit, "تعديل") }
                                    IconButton(enabled = !current.isClosed, onClick = { deleting = transaction }, modifier = Modifier.semantics { contentDescription = "حذف" }) { Icon(Icons.Default.Delete, "حذف") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        CustodyLedgerOperationDialog(vm, custodyId, personId, false, currency, CustodyTransactionType.PAID_TO_PERSON, null, onDismiss = { showAdd = false }, onFinished = { showAdd = false })
    }
    editing?.let { transaction ->
        CustodyLedgerOperationDialog(vm, custodyId, personId, false, transaction.currencyCode, transaction.type, transaction, onDismiss = { editing = null }, onFinished = { editing = null })
    }
    deleting?.let { transaction ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف العملية") },
            text = { Text("سيتم حذف العملية نهائيًا.") },
            confirmButton = { TextButton(onClick = { vm.deleteTransaction(transaction.id); deleting = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } }
        )
    }
    transferring?.let { transaction ->
        CustodyTransferDialog(
            transaction = transaction,
            currentPersonId = personId,
            people = people,
            onDismiss = { transferring = null },
            onTransfer = { newPersonId, reason -> vm.transferTransactionAndWait(transaction.id, newPersonId, reason) }
        )
    }
}

@Composable
private fun SummaryRow(title: String, value: Long, status: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(money(value), fontWeight = FontWeight.Bold); Text(status, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun CustodyTransferDialog(
    transaction: CustodyTransactionEntity,
    currentPersonId: Long,
    people: List<CustodyPersonEntity>,
    onDismiss: () -> Unit,
    onTransfer: suspend (Long, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Long?>(null) }
    var reason by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val candidates = people.filter { it.id != currentPersonId && !it.isArchived }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("نقل العملية") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("العملية الحالية: ${money(transaction.amountMinor)} ${transaction.currencyCode} — ${typeLabel(transaction.type)}")
                Text("الشخص الحالي: ${people.firstOrNull { it.id == currentPersonId }?.name.orEmpty()}")
                Text("اختر الشخص الجديد", fontWeight = FontWeight.Bold)
                candidates.forEach { person ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == person.id, onClick = { selected = person.id }, enabled = !saving)
                        Text(person.name)
                    }
                }
                OutlinedTextField(reason, { reason = it; error = null }, Modifier.fillMaxWidth(), label = { Text("سبب النقل") }, minLines = 2, enabled = !saving)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = selected != null && reason.trim().isNotBlank() && !saving, onClick = {
                saving = true
                scope.launch {
                    runCatching { onTransfer(selected!!, reason.trim()) }
                        .onSuccess { saving = false; onDismiss() }
                        .onFailure { saving = false; error = it.message ?: "تعذر نقل العملية" }
                }
            }) { Text(if (saving) "جارٍ النقل…" else "نقل العملية") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("إلغاء") } }
    )
}
