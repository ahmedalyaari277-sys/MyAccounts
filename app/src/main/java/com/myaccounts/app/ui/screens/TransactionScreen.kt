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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.TransactionType
import com.myaccounts.app.ui.components.AppTopBar
import com.myaccounts.app.ui.components.BalanceAmount
import com.myaccounts.app.ui.components.BalanceStatus
import com.myaccounts.app.ui.components.CurrencyChip
import com.myaccounts.app.ui.components.EmptyState
import com.myaccounts.app.ui.components.StatusChip
import com.myaccounts.app.ui.theme.Due
import com.myaccounts.app.ui.theme.Neutral
import com.myaccounts.app.ui.theme.Owed
import com.myaccounts.app.ui.viewmodel.TransactionViewModel
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var transactionToDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var transactionToEdit by remember { mutableStateOf<TransactionEntity?>(null) }
    var transactionForAttachments by remember { mutableStateOf<TransactionEntity?>(null) }
    var showAddTransaction by remember { mutableStateOf(false) }

    LaunchedEffect(selectedAccountId) { transactionViewModel.selectAccount(selectedAccountId) }
    val transactions by transactionViewModel.transactions.collectAsState()
    val balance by transactionViewModel.balance.collectAsState()

    val balanceStatus = when {
        balance > 0L -> BalanceStatus.Due
        balance < 0L -> BalanceStatus.Owed
        else -> BalanceStatus.Neutral
    }
    val balanceStatusText = when (balanceStatus) {
        BalanceStatus.Due -> "عليه"
        BalanceStatus.Owed -> "له"
        BalanceStatus.Neutral -> "متوازن"
    }
    val balanceStatusColor = when (balanceStatus) {
        BalanceStatus.Due -> Due
        BalanceStatus.Owed -> Owed
        BalanceStatus.Neutral -> Neutral
    }

    val transactionContent: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (accounts.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("YER", "SAR", "USD").forEach { code ->
                        val account = accounts.firstOrNull { it.currencyCode == code } ?: return@forEach
                        CurrencyChip(
                            currency = code,
                            selected = selectedCurrencyCode == code,
                            onClick = { selectedCurrencyCode = code; selectedAccountId = account.id },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("الرصيد الحالي", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BalanceAmount(
                        amount = formatSignedAmount(balance),
                        status = balanceStatus,
                        modifier = Modifier.fillMaxWidth()
                    )
                    StatusChip(text = balanceStatusText, color = balanceStatusColor)
                }
            }

            if (transactions.isEmpty()) {
                EmptyState(
                    title = "لا توجد عمليات حتى الآن",
                    message = "أضف أول عملية لهذا الحساب باستخدام زر إضافة عملية.",
                    icon = Icons.Default.Add,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)
                ) {
                    items(transactions, key = { it.id }) { transaction ->
                        Phase5TransactionCard(
                            transaction = transaction,
                            transactionViewModel = transactionViewModel,
                            onEdit = { transactionToEdit = transaction },
                            onDelete = { transactionToDelete = transaction },
                            onAttachments = { transactionForAttachments = transaction }
                        )
                    }
                }
            }

            if (embedded) {
                androidx.compose.material3.Button(
                    onClick = { showAddTransaction = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("إضافة عملية", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (embedded) {
        transactionContent()
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { AppTopBar(title = "عمليات $selectedCurrencyCode", onBack = onBack) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddTransaction = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) { Icon(Icons.Default.Add, contentDescription = "إضافة عملية") }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) { transactionContent() }
        }
    }

    if (showAddTransaction && accounts.isNotEmpty()) {
        Dialog(
            onDismissRequest = { showAddTransaction = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(Modifier.fillMaxWidth(0.92f), shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                QuickTransactionScreen(
                    personName = personName,
                    accounts = accounts,
                    onSave = { transaction, attachments -> transactionViewModel.addTransaction(transaction, attachments); showAddTransaction = false },
                    onCancel = { showAddTransaction = false }
                )
            }
        }
    }

    transactionToEdit?.let { transaction ->
        val attachments by transactionViewModel.observeAttachments(transaction.id).collectAsState(initial = emptyList())
        var editAccounts by remember(transaction.id) { mutableStateOf<List<CurrencyAccountEntity>>(emptyList()) }
        var editPersonName by remember(transaction.id) { mutableStateOf("") }
        LaunchedEffect(transaction.id) {
            transactionViewModel.getAccount(transaction.accountId)?.let { account ->
                editAccounts = transactionViewModel.getPersonCurrencyAccounts(account.personId)
                editPersonName = transactionViewModel.getPersonNameForAccount(transaction.accountId)
            }
        }
        Dialog(onDismissRequest = { transactionToEdit = null }) {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                if (editAccounts.isNotEmpty()) {
                    QuickTransactionScreen(
                        personName = editPersonName,
                        accounts = editAccounts,
                        initialTransaction = transaction,
                        existingAttachments = attachments,
                        onSave = { _, _ -> },
                        onCancel = { transactionToEdit = null },
                        onEditSave = { updated, newAttachments, deleted -> transactionViewModel.updateTransaction(updated, newAttachments, deleted); transactionToEdit = null }
                    )
                } else {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("جاري تحميل بيانات الحساب...", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
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
            title = { Text("حذف العملية نهائيًا", style = MaterialTheme.typography.titleLarge) },
            text = { Text("سيتم حذف العملية ومرفقاتها نهائيًا من الحساب. لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                TextButton(onClick = { transactionViewModel.deleteTransactionById(transaction.id); transactionToDelete = null }) {
                    Text("حذف نهائي", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { transactionToDelete = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun Phase5TransactionCard(
    transaction: TransactionEntity,
    transactionViewModel: TransactionViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAttachments: () -> Unit
) {
    val attachmentCount by transactionViewModel.observeAttachmentCount(transaction.id).collectAsState(initial = 0)
    val date = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(transaction.transactionDate))
    val amount = BigDecimal(transaction.amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString()
    val isDue = transaction.type == TransactionType.RECEIVABLE
    val status = if (isDue) BalanceStatus.Due else BalanceStatus.Owed
    val statusColor = if (isDue) Due else Owed

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                StatusChip(text = if (isDue) "عليه" else "له", color = statusColor)
                Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BalanceAmount(amount = if (isDue) amount else "-$amount", status = status)
            if (transaction.description.isNotBlank()) Text(transaction.description, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (attachmentCount > 0) IconButton(onClick = onAttachments) { Icon(Icons.Default.AttachFile, contentDescription = "المرفقات") }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "تعديل") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "حذف نهائي") }
            }
        }
    }
}

private fun formatSignedAmount(balance: Long): String {
    val amount = BigDecimal(kotlin.math.abs(balance)).movePointLeft(2).stripTrailingZeros().toPlainString()
    return when {
        balance > 0L -> amount
        balance < 0L -> "-$amount"
        else -> "0"
    }
}
