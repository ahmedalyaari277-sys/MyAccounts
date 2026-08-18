package com.myaccounts.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
    transactionViewModel: TransactionViewModel
) {
    LaunchedEffect(accountId) { transactionViewModel.selectAccount(accountId) }

    val transactions by transactionViewModel.transactions.collectAsState()
    val balance by transactionViewModel.balance.collectAsState()

    var transactionToDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var transactionToEdit by remember { mutableStateOf<TransactionEntity?>(null) }
    var transactionForAttachments by remember { mutableStateOf<TransactionEntity?>(null) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("عمليات $currencyCode", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTransactionDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة عملية")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            BalanceCard(balance = balance, currencyCode = currencyCode)
            Spacer(Modifier.height(14.dp))

            if (transactions.isEmpty()) {
                EmptyTransactionsState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)
                ) {
                    items(transactions, key = { it.id }) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            transactionViewModel = transactionViewModel,
                            onEdit = { transactionToEdit = transaction },
                            onDelete = { transactionToDelete = transaction },
                            onAttachments = { transactionForAttachments = transaction }
                        )
                    }
                }
            }
        }
    }

    if (showAddTransactionDialog) {
        AddTransactionDialog(
            currencyCode = currencyCode,
            onDismiss = { showAddTransactionDialog = false },
            onSave = { type, amountMinor, description, attachments ->
                transactionViewModel.addTransaction(
                    TransactionEntity(
                        accountId = accountId,
                        type = type,
                        amountMinor = amountMinor,
                        description = description,
                        transactionDate = System.currentTimeMillis()
                    ),
                    attachments
                )
                showAddTransactionDialog = false
            }
        )
    }

    transactionToEdit?.let { transaction ->
        EditTransactionDialog(
            currencyCode = currencyCode,
            transaction = transaction,
            onDismiss = { transactionToEdit = null },
            onSave = { updatedTransaction ->
                transactionViewModel.updateTransaction(updatedTransaction)
                transactionToEdit = null
            }
        )
    }

    transactionForAttachments?.let { transaction ->
        val attachments by transactionViewModel.observeAttachments(transaction.id)
            .collectAsState(initial = emptyList())
        TransactionAttachmentsDialog(
            transactionId = transaction.id,
            attachments = attachments,
            onDismiss = { transactionForAttachments = null },
            onDelete = { attachment -> transactionViewModel.deleteAttachment(attachment) }
        )
    }

    transactionToDelete?.let { transaction ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("أرشفة العملية") },
            text = { Text("هل أنت متأكد من أرشفة هذه العملية ومرفقاتها؟ يمكنك استعادتها لاحقًا من الأرشيف.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        transactionViewModel.deleteTransactionById(transaction.id)
                        transactionToDelete = null
                    }
                ) { Text("أرشفة", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { transactionToDelete = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun BalanceCard(balance: Long, currencyCode: String) {
    val balanceColor = when {
        balance > 0L -> MaterialTheme.colorScheme.error
        balance < 0L -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("الرصيد الحالي", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatBalanceWithSign(balance),
                color = balanceColor,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = balanceStatus(balance, currencyCode),
                color = balanceColor,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun EmptyTransactionsState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text("لا توجد عمليات حتى الآن", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "أضف أول عملية لهذا الحساب باستخدام زر +",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AddTransactionDialog(
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransactionFormContent(
                    currencyCode = currencyCode,
                    selectedType = selectedType,
                    amountText = amountText,
                    description = description,
                    amountError = amountError,
                    onTypeChange = { selectedType = it },
                    onAmountChange = { amountText = it; amountError = false },
                    onDescriptionChange = { description = it }
                )
                TransactionAttachmentPicker(
                    selectedAttachments = attachments,
                    onAttachmentsChanged = { attachments = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amountMinor = parseAmountToMinor(amountText)
                if (amountMinor == null || amountMinor <= 0L) amountError = true
                else onSave(selectedType, amountMinor, description.trim(), attachments)
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun EditTransactionDialog(
    currencyCode: String,
    transaction: TransactionEntity,
    onDismiss: () -> Unit,
    onSave: (TransactionEntity) -> Unit
) {
    var selectedType by remember(transaction.id) { mutableStateOf(transaction.type) }
    var amountText by remember(transaction.id) { mutableStateOf(formatAmount(transaction.amountMinor)) }
    var description by remember(transaction.id) { mutableStateOf(transaction.description) }
    var amountError by remember(transaction.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل العملية", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransactionFormContent(
                    currencyCode = currencyCode,
                    selectedType = selectedType,
                    amountText = amountText,
                    description = description,
                    amountError = amountError,
                    onTypeChange = { selectedType = it },
                    onAmountChange = { amountText = it; amountError = false },
                    onDescriptionChange = { description = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amountMinor = parseAmountToMinor(amountText)
                if (amountMinor == null || amountMinor <= 0L) amountError = true
                else onSave(transaction.copy(type = selectedType, amountMinor = amountMinor, description = description.trim()))
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun TransactionFormContent(
    currencyCode: String,
    selectedType: TransactionType,
    amountText: String,
    description: String,
    amountError: Boolean,
    onTypeChange: (TransactionType) -> Unit,
    onAmountChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit
) {
    Column {
        Text("نوع العملية", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val selectedColors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
            if (selectedType == TransactionType.RECEIVABLE) {
                Button(onClick = { onTypeChange(TransactionType.RECEIVABLE) }, modifier = Modifier.weight(1f), colors = selectedColors) {
                    Text("✓ عليه")
                }
            } else {
                OutlinedButton(onClick = { onTypeChange(TransactionType.RECEIVABLE) }, modifier = Modifier.weight(1f)) { Text("عليه") }
            }

            val owedColors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
            if (selectedType == TransactionType.PAYABLE) {
                Button(onClick = { onTypeChange(TransactionType.PAYABLE) }, modifier = Modifier.weight(1f), colors = owedColors) {
                    Text("✓ له")
                }
            } else {
                OutlinedButton(onClick = { onTypeChange(TransactionType.PAYABLE) }, modifier = Modifier.weight(1f)) { Text("له") }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = amountText,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("المبلغ $currencyCode") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = amountError
        )
        if (amountError) Text(
            "أدخل مبلغًا صحيحًا أكبر من صفر وبحد أقصى منزلتين عشريتين.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("الوصف") },
            minLines = 2,
            maxLines = 3
        )
    }
}

@Composable
private fun TransactionItem(
    transaction: TransactionEntity,
    transactionViewModel: TransactionViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAttachments: () -> Unit
) {
    val isReceivable = transaction.type == TransactionType.RECEIVABLE
    val typeText = if (isReceivable) "عليه" else "له"
    val semanticColor = if (isReceivable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    val formattedDate = remember(transaction.transactionDate) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(transaction.transactionDate))
    }
    val attachmentCount by transactionViewModel.observeAttachmentCount(transaction.id).collectAsState(initial = 0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(typeText, fontWeight = FontWeight.Bold, color = semanticColor)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(
                            text = if (isReceivable) "+" else "-",
                            color = semanticColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (transaction.description.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(transaction.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(formattedDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatAmount(transaction.amountMinor),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = semanticColor
                    )
                    Text("${transaction.amountMinor / 100.0}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAttachments) {
                    Icon(Icons.Default.AttachFile, contentDescription = if (attachmentCount == 0) "إضافة أو عرض المرفقات" else "عرض المرفقات ($attachmentCount)")
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل العملية")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "أرشفة العملية", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun parseAmountToMinor(value: String): Long? = runCatching {
    BigDecimal(value.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
}.getOrNull()

private fun formatAmount(amountMinor: Long): String = BigDecimal(amountMinor)
    .movePointLeft(2)
    .setScale(2, RoundingMode.UNNECESSARY)
    .stripTrailingZeros()
    .toPlainString()

private fun formatBalanceWithSign(balanceMinor: Long): String {
    val amount = formatAmount(kotlin.math.abs(balanceMinor))
    return when {
        balanceMinor > 0L -> "+$amount"
        balanceMinor < 0L -> "-$amount"
        else -> "0"
    }
}

private fun balanceStatus(balanceMinor: Long, currencyCode: String): String = when {
    balanceMinor > 0L -> "عليه — مدين لك ($currencyCode)"
    balanceMinor < 0L -> "له — دائن عليك ($currencyCode)"
    else -> "متوازن ($currencyCode)"
}
