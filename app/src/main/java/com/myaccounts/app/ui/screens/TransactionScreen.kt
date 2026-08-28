package com.myaccounts.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.TransactionType
import com.myaccounts.app.ui.viewmodel.TransactionViewModel
import com.myaccounts.app.util.TransactionAttachmentStorage
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    accountId: Long,
    currencyCode: String,
    onBack: () -> Unit,
    transactionViewModel: TransactionViewModel,
    accounts: List<CurrencyAccountEntity> = emptyList(),
    embedded: Boolean = false,
    modifier: Modifier = Modifier,
    personName: String = ""
) {
    var selectedAccountId by remember(accountId) { mutableStateOf(accountId) }
    var selectedCurrencyCode by remember(currencyCode) { mutableStateOf(currencyCode) }
    LaunchedEffect(selectedAccountId) { transactionViewModel.selectAccount(selectedAccountId) }

    val transactions by transactionViewModel.transactions.collectAsState()
    val balance by transactionViewModel.balance.collectAsState()
    var transactionToDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var transactionToEdit by remember { mutableStateOf<TransactionEntity?>(null) }
    var transactionForAttachments by remember { mutableStateOf<TransactionEntity?>(null) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }

    val availableAccounts = accounts

    val currencySelector: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("YER", "SAR", "USD").forEach { code ->
                val account = availableAccounts.firstOrNull { it.currencyCode == code }
                if (account != null) {
                    if (selectedCurrencyCode == code) {
                        Button(
                            onClick = {},
                            Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) { Text(code, fontWeight = FontWeight.Bold) }
                    } else {
                        OutlinedButton(
                            onClick = {
                                selectedCurrencyCode = code
                                selectedAccountId = account.id
                            },
                            Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) { Text(code, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    val transactionContent: @Composable (Modifier) -> Unit = { contentModifier ->
        Column(contentModifier.fillMaxSize()) {
            if (availableAccounts.isNotEmpty()) {
                currencySelector()
                Spacer(Modifier.height(10.dp))
            }
            BalanceCard(balance = balance, currencyCode = selectedCurrencyCode)
            Spacer(Modifier.height(14.dp))
            if (transactions.isEmpty()) {
                EmptyTransactionsState()
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)
                ) {
                    items(transactions, key = { it.id }) { transaction ->
                        TransactionItem(
                            transaction,
                            transactionViewModel,
                            onEdit = { transactionToEdit = transaction },
                            onDelete = { transactionToDelete = transaction },
                            onAttachments = { transactionForAttachments = transaction }
                        )
                    }
                }
            }
            if (embedded) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { showAddTransactionDialog = true }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("إضافة عملية")
                }
            }
        }
    }

    if (embedded) {
        transactionContent(modifier)
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                androidx.compose.material3.TopAppBar(
                    title = { Text("عمليات $selectedCurrencyCode", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } }
                )
            },
            floatingActionButton = { FloatingActionButton(onClick = { showAddTransactionDialog = true }) { Icon(Icons.Default.Add, contentDescription = "إضافة عملية") } }
        ) { paddingValues ->
            Column(Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)) {
                transactionContent(modifier.weight(1f))
            }
        }
    }

    if (showAddTransactionDialog && embedded && availableAccounts.isNotEmpty()) {
        Dialog(
            onDismissRequest = { showAddTransactionDialog = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(Modifier.fillMaxWidth(0.92f), shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                QuickTransactionScreen(
                    personName = personName,
                    accounts = availableAccounts,
                    onSave = { transaction, attachments ->
                        transactionViewModel.addTransaction(transaction, attachments)
                        showAddTransactionDialog = false
                    },
                    onCancel = { showAddTransactionDialog = false }
                )
            }
        }
    } else if (showAddTransactionDialog) {
        AddTransactionDialog(selectedCurrencyCode, onDismiss = { showAddTransactionDialog = false }, onSave = { type, amountMinor, description, attachments ->
            transactionViewModel.addTransaction(
                TransactionEntity(
                    accountId = selectedAccountId,
                    type = type,
                    amountMinor = amountMinor,
                    description = description,
                    transactionDate = System.currentTimeMillis()
                ),
                attachments
            )
            showAddTransactionDialog = false
        })
    }

    transactionToEdit?.let { transaction ->
        val attachments by transactionViewModel.observeAttachments(transaction.id).collectAsState(initial = emptyList())
        var editAccounts by remember(transaction.id) { mutableStateOf<List<CurrencyAccountEntity>>(emptyList()) }
        var editPersonName by remember(transaction.id) { mutableStateOf("") }
        LaunchedEffect(transaction.id) {
            val account = transactionViewModel.getAccount(transaction.accountId)
            if (account != null) {
                editAccounts = transactionViewModel.getPersonCurrencyAccounts(account.personId)
                editPersonName = transactionViewModel.getPersonNameForAccount(transaction.accountId)
            }
        }
        Dialog(onDismissRequest = { transactionToEdit = null }) {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                if (editAccounts.isNotEmpty()) QuickTransactionScreen(
                    personName = editPersonName,
                    accounts = editAccounts,
                    initialTransaction = transaction,
                    existingAttachments = attachments,
                    onSave = { _, _ -> },
                    onCancel = { transactionToEdit = null },
                    onEditSave = { updatedTransaction, newAttachments, deletedAttachments ->
                        transactionViewModel.updateTransaction(updatedTransaction, newAttachments, deletedAttachments)
                        transactionToEdit = null
                    }
                ) else Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("جاري تحميل بيانات الحساب...", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    transactionForAttachments?.let { transaction ->
        val attachments by transactionViewModel.observeAttachments(transaction.id).collectAsState(initial = emptyList())
        TransactionAttachmentsDialog(transactionId = transaction.id, attachments = attachments, onDismiss = { transactionForAttachments = null }, onDelete = { transactionViewModel.deleteAttachment(it) })
    }

    transactionToDelete?.let { transaction ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("حذف العملية نهائيًا") },
            text = { Text("سيتم حذف العملية ومرفقاتها نهائيًا من الحساب. لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = { TextButton(onClick = { transactionViewModel.deleteTransactionById(transaction.id); transactionToDelete = null }) { Text("حذف نهائي", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { transactionToDelete = null }) { Text("إلغاء") } }
        )
    }
}

@Composable private fun BalanceCard(balance: Long, currencyCode: String) {
    val balanceColor = when {
        balance < 0L -> MaterialTheme.colorScheme.secondary
        balance > 0L -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(16.dp)) { Text("الرصيد الحالي", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(4.dp)); Text(formatBalanceWithSign(balance), color = balanceColor, fontSize = 21.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(2.dp)); Text(balanceStatus(balance, currencyCode), color = balanceColor, style = MaterialTheme.typography.bodySmall) } }
}

@Composable private fun EmptyTransactionsState() { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp)); Text("لا توجد عمليات حتى الآن", fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("أضف أول عملية لهذا الحساب باستخدام زر +", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } } }

@Composable private fun AddTransactionDialog(currencyCode: String, onDismiss: () -> Unit, onSave: (TransactionType, Long, String, List<TransactionAttachmentStorage.SelectedAttachment>) -> Unit) {
    var selectedType by remember { mutableStateOf(TransactionType.RECEIVABLE) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<TransactionAttachmentStorage.SelectedAttachment>>(emptyList()) }
    var amountError by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إضافة عملية", fontWeight = FontWeight.Bold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("نوع العملية", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selectedType == TransactionType.RECEIVABLE) Button(onClick = { selectedType = TransactionType.RECEIVABLE }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("✓ عليه") } else OutlinedButton(onClick = { selectedType = TransactionType.RECEIVABLE }, Modifier.weight(1f)) { Text("عليه") }
            if (selectedType == TransactionType.PAYABLE) Button(onClick = { selectedType = TransactionType.PAYABLE }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("✓ له") } else OutlinedButton(onClick = { selectedType = TransactionType.PAYABLE }, Modifier.weight(1f)) { Text("له") }
        }
        OutlinedTextField(value = amountText, onValueChange = { amountText = it; amountError = false }, Modifier.fillMaxWidth(), label = { Text("المبلغ $currencyCode") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = amountError)
        OutlinedTextField(value = description, onValueChange = { description = it }, Modifier.fillMaxWidth(), label = { Text("الوصف") }, minLines = 2)
        TransactionAttachmentPicker(selectedAttachments = attachments, onAttachmentsChanged = { attachments = it })
    } }, confirmButton = { TextButton(onClick = {
        val amountMinor = runCatching { BigDecimal(amountText.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }.getOrNull()
        if (amountMinor == null || amountMinor <= 0L) amountError = true else onSave(selectedType, amountMinor, description.trim(), attachments)
    }) { Text("حفظ", fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable private fun TransactionItem(transaction: TransactionEntity, transactionViewModel: TransactionViewModel, onEdit: () -> Unit, onDelete: () -> Unit, onAttachments: () -> Unit) {
    val attachmentsCount by transactionViewModel.observeAttachmentCount(transaction.id).collectAsState(initial = 0)
    val date = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(transaction.transactionDate))
    val amountText = BigDecimal(transaction.amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString()
    val isReceivable = transaction.type == TransactionType.RECEIVABLE
    Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(if (isReceivable) "عليه" else "له", fontWeight = FontWeight.Bold, color = if (isReceivable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary); Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(4.dp)); Text(amountText, fontWeight = FontWeight.Bold, fontSize = 19.sp); if (transaction.description.isNotBlank()) Text(transaction.description, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (attachmentsCount > 0) IconButton(onClick = onAttachments) { Icon(Icons.Default.AttachFile, contentDescription = "المرفقات") }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "تعديل") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "حذف نهائي") }
        }
    } }
}

private fun formatAmount(amountMinor: Long): String = BigDecimal(amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun formatBalanceWithSign(balance: Long): String { val amount = formatAmount(kotlin.math.abs(balance)); return if (balance < 0) "-$amount" else "+$amount" }
private fun balanceStatus(balance: Long, currencyCode: String): String = when { balance > 0L -> "عليه $currencyCode"; balance < 0L -> "له $currencyCode"; else -> "متوازن" }
