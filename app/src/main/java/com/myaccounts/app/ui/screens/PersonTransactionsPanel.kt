package com.myaccounts.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.TransactionAttachmentEntity
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.TransactionType
import com.myaccounts.app.ui.viewmodel.TransactionViewModel
import com.myaccounts.app.util.TransactionAttachmentStorage
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun PersonTransactionsPanel(
    accounts: List<CurrencyAccountEntity>,
    transactionViewModel: TransactionViewModel
) {
    val orderedAccounts = listOf("YER", "SAR", "USD").mapNotNull { code -> accounts.firstOrNull { it.currencyCode == code } }
    var selectedCurrency by remember(accounts) { mutableStateOf("YER") }
    val selectedAccount = orderedAccounts.firstOrNull { it.currencyCode == selectedCurrency } ?: orderedAccounts.firstOrNull()
    var transactionToEdit by remember { mutableStateOf<TransactionEntity?>(null) }
    var transactionToDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var transactionForAttachments by remember { mutableStateOf<TransactionEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(selectedAccount?.id) {
        transactionViewModel.selectAccount(selectedAccount?.id)
    }

    val transactions by transactionViewModel.transactions.collectAsState()
    val balance by transactionViewModel.balance.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("العملات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            orderedAccounts.forEach { account ->
                val selected = account.currencyCode == selectedCurrency
                if (selected) {
                    Button(onClick = { selectedCurrency = account.currencyCode }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                        Text(currencyName(account.currencyCode), fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(onClick = { selectedCurrency = account.currencyCode }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                        Text(currencyName(account.currencyCode), fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الرصيد ${currencyName(selectedAccount?.currencyCode)}", fontWeight = FontWeight.Bold)
                    Text(formatSignedBalance(balance), fontWeight = FontWeight.Bold, color = balanceColor(balance))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("العمليات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { showAdd = true }) { Text("+ إضافة عملية") }
        }

        if (transactions.isEmpty()) {
            Text("لا توجد عمليات في ${currencyName(selectedAccount?.currencyCode)}.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions, key = { it.id }) { transaction ->
                    PersonTransactionItem(
                        transaction = transaction,
                        onEdit = { transactionToEdit = transaction },
                        onDelete = { transactionToDelete = transaction },
                        onAttachments = { transactionForAttachments = transaction }
                    )
                }
            }
        }
    }

    transactionToEdit?.let { transaction ->
        EditPersonTransactionDialog(
            transaction = transaction,
            accounts = orderedAccounts,
            transactionViewModel = transactionViewModel,
            onDismiss = { transactionToEdit = null },
            onSave = { updated, newAttachments, removedAttachments ->
                transactionViewModel.updateTransactionWithAttachments(updated, newAttachments, removedAttachments)
                transactionToEdit = null
            }
        )
    }

    if (showAdd && selectedAccount != null) {
        AddPersonTransactionDialog(
            currencyCode = selectedAccount.currencyCode,
            onDismiss = { showAdd = false },
            onSave = { type, amount, description, attachments ->
                transactionViewModel.addTransaction(
                    TransactionEntity(
                        accountId = selectedAccount.id,
                        type = type,
                        amountMinor = amount,
                        description = description,
                        transactionDate = System.currentTimeMillis()
                    ),
                    attachments
                )
                showAdd = false
            }
        )
    }

    transactionForAttachments?.let { transaction ->
        val attachments by transactionViewModel.observeAttachments(transaction.id).collectAsState(initial = emptyList())
        TransactionAttachmentsDialog(
            transactionId = transaction.id,
            attachments = attachments,
            onDismiss = { transactionForAttachments = null },
            onDelete = { transactionViewModel.deleteAttachment(it) }
        )
    }

    transactionToDelete?.let { transaction ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("أرشفة العملية") },
            text = { Text("هل أنت متأكد من أرشفة هذه العملية؟") },
            confirmButton = { TextButton(onClick = { transactionViewModel.archiveTransaction(transaction); transactionToDelete = null }) { Text("أرشفة") } },
            dismissButton = { TextButton(onClick = { transactionToDelete = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun PersonTransactionItem(
    transaction: TransactionEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAttachments: () -> Unit
) {
    val positive = transaction.type == TransactionType.RECEIVABLE
    val color = if (positive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (positive) "عليه" else "له", fontWeight = FontWeight.Bold, color = color)
                Text(formatAmount(transaction.amountMinor), fontWeight = FontWeight.Bold, color = color)
            }
            if (transaction.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(transaction.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(transaction.transactionDate)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onAttachments) { Text("المرفقات") }
                TextButton(onClick = onEdit) { Text("تعديل") }
                TextButton(onClick = onDelete) { Text("أرشفة") }
            }
        }
    }
}

@Composable
private fun EditPersonTransactionDialog(
    transaction: TransactionEntity,
    accounts: List<CurrencyAccountEntity>,
    transactionViewModel: TransactionViewModel,
    onDismiss: () -> Unit,
    onSave: (TransactionEntity, List<TransactionAttachmentStorage.SelectedAttachment>, List<TransactionAttachmentEntity>) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedType by remember(transaction.id) { mutableStateOf(transaction.type) }
    var selectedCurrency by remember(transaction.id) { mutableStateOf(accounts.firstOrNull { it.id == transaction.accountId }?.currencyCode ?: "YER") }
    var amountText by remember(transaction.id) { mutableStateOf(formatAmount(transaction.amountMinor)) }
    var description by remember(transaction.id) { mutableStateOf(transaction.description) }
    var transactionDate by remember(transaction.id) { mutableStateOf(transaction.transactionDate) }
    var newAttachments by remember(transaction.id) { mutableStateOf<List<TransactionAttachmentStorage.SelectedAttachment>>(emptyList()) }
    var removedAttachmentIds by remember(transaction.id) { mutableStateOf<Set<Long>>(emptySet()) }
    var amountError by remember(transaction.id) { mutableStateOf(false) }

    val existingAttachments by transactionViewModel.observeAttachments(transaction.id).collectAsState(initial = emptyList())
    val visibleExistingAttachments = existingAttachments.filterNot { it.id in removedAttachmentIds }
    val currencyLabels = mapOf("YER" to "ريال يمني", "SAR" to "ريال سعودي", "USD" to "دولار")
    val currencyOrder = mapOf("YER" to 0, "SAR" to 1, "USD" to 2)
    val dateText = remember(transactionDate) { SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(transactionDate)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل العملية", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it; amountError = false },
                        modifier = Modifier.weight(1.35f),
                        label = { Text("المبلغ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = amountError
                    )
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = {},
                        modifier = Modifier.weight(1f),
                        label = { Text("التاريخ") },
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                val selected = Calendar.getInstance().apply { timeInMillis = transactionDate }
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        selected.set(year, month, day, 12, 0, 0)
                                        selected.set(Calendar.MILLISECOND, 0)
                                        transactionDate = selected.timeInMillis
                                    },
                                    selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }) { Icon(Icons.Default.CalendarToday, contentDescription = "اختيار التاريخ") }
                        }
                    )
                }
                if (amountError) Text("أدخل مبلغًا صحيحًا أكبر من صفر وبحد أقصى منزلتين عشريتين.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("التفاصيل") },
                    singleLine = true
                )

                Text("العملة", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    accounts.sortedBy { currencyOrder[it.currencyCode] ?: Int.MAX_VALUE }.forEach { account ->
                        FilterChip(
                            selected = selectedCurrency == account.currencyCode,
                            onClick = { selectedCurrency = account.currencyCode },
                            label = { Text(currencyLabels[account.currencyCode] ?: account.currencyCode, fontSize = 12.sp) }
                        )
                    }
                }

                Text("نوع العملية", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    val receivableColors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                    val payableColors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)
                    if (selectedType == TransactionType.RECEIVABLE) Button(onClick = { selectedType = TransactionType.RECEIVABLE }, modifier = Modifier.weight(1f), colors = receivableColors) { Text("✓ عليه") }
                    else OutlinedButton(onClick = { selectedType = TransactionType.RECEIVABLE }, modifier = Modifier.weight(1f)) { Text("عليه") }
                    if (selectedType == TransactionType.PAYABLE) Button(onClick = { selectedType = TransactionType.PAYABLE }, modifier = Modifier.weight(1f), colors = payableColors) { Text("✓ له") }
                    else OutlinedButton(onClick = { selectedType = TransactionType.PAYABLE }, modifier = Modifier.weight(1f)) { Text("له") }
                }

                Text("المرفقات", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                if (visibleExistingAttachments.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        visibleExistingAttachments.forEach { attachment ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(attachment.fileName, modifier = Modifier.weight(1f), maxLines = 2)
                                TextButton(onClick = {
                                    removedAttachmentIds = removedAttachmentIds + attachment.id
                                }) { Text("حذف") }
                            }
                        }
                    }
                }
                TransactionAttachmentPicker(newAttachments) { newAttachments = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = parseAmount(amountText)
                val targetAccount = accounts.firstOrNull { it.currencyCode == selectedCurrency }
                if (amount == null || amount <= 0L || targetAccount == null) {
                    amountError = true
                } else {
                    val updated = transaction.copy(
                        accountId = targetAccount.id,
                        type = selectedType,
                        amountMinor = amount,
                        description = description.trim(),
                        transactionDate = transactionDate
                    )
                    val removed = existingAttachments.filter { it.id in removedAttachmentIds }
                    onSave(updated, newAttachments, removed)
                }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddPersonTransactionDialog(
    currencyCode: String,
    onDismiss: () -> Unit,
    onSave: (TransactionType, Long, String, List<TransactionAttachmentStorage.SelectedAttachment>) -> Unit
) {
    var selectedType by remember { mutableStateOf(TransactionType.RECEIVABLE) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf(false) }
    var attachments by remember { mutableStateOf<List<TransactionAttachmentStorage.SelectedAttachment>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة عملية", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("العملة: ${currencyName(currencyCode)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (selectedType == TransactionType.RECEIVABLE) Button(onClick = { selectedType = TransactionType.RECEIVABLE }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("✓ عليه") }
                    else OutlinedButton(onClick = { selectedType = TransactionType.RECEIVABLE }, modifier = Modifier.weight(1f)) { Text("عليه") }
                    if (selectedType == TransactionType.PAYABLE) Button(onClick = { selectedType = TransactionType.PAYABLE }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("✓ له") }
                    else OutlinedButton(onClick = { selectedType = TransactionType.PAYABLE }, modifier = Modifier.weight(1f)) { Text("له") }
                }
                OutlinedTextField(amountText, { amountText = it; amountError = false }, Modifier.fillMaxWidth(), label = { Text("المبلغ $currencyCode") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = amountError)
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("الوصف") }, minLines = 2, maxLines = 3)
                TransactionAttachmentPicker(attachments) { attachments = it }
            }
        },
        confirmButton = { TextButton(onClick = { val amount = parseAmount(amountText); if (amount == null || amount <= 0L) amountError = true else onSave(selectedType, amount, description.trim(), attachments) }) { Text("حفظ", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun currencyName(code: String?): String = when (code) {
    "YER" -> "اليمني"
    "SAR" -> "السعودي"
    "USD" -> "الدولار"
    else -> "العملة"
}

private fun formatAmount(amountMinor: Long): String = BigDecimal(amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString()

private fun parseAmount(text: String): Long? = runCatching {
    val value = BigDecimal(text.trim()).setScale(2, java.math.RoundingMode.UNNECESSARY)
    value.movePointRight(2).longValueExact()
}.getOrNull()

@Composable
private fun formatSignedBalance(balance: Long): String = when {
    balance > 0L -> "عليه ${formatAmount(balance)}"
    balance < 0L -> "له ${formatAmount(-balance)}"
    else -> "متعادل 0"
}

@Composable
private fun balanceColor(balance: Long) = when {
    balance > 0L -> MaterialTheme.colorScheme.error
    balance < 0L -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}