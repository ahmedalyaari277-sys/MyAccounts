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
import androidx.compose.material3.Card
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
    LaunchedEffect(accountId) {
        transactionViewModel.selectAccount(accountId)
    }

    val transactions by transactionViewModel.transactions.collectAsState()
    val balance by transactionViewModel.balance.collectAsState()

    var transactionToDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var transactionToEdit by remember { mutableStateOf<TransactionEntity?>(null) }
    var transactionForAttachments by remember { mutableStateOf<TransactionEntity?>(null) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("عمليات $currencyCode") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddTransactionDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "إضافة عملية"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("الرصيد", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = formatBalance(balance),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (transactions.isEmpty()) {
                Text(
                    text = "لا توجد عمليات لهذا الحساب.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
        val attachments by transactionViewModel
            .observeAttachments(transaction.id)
            .collectAsState(initial = emptyList())

        TransactionAttachmentsDialog(
            transactionId = transaction.id,
            attachments = attachments,
            onDismiss = { transactionForAttachments = null },
            onDelete = { attachment ->
                transactionViewModel.deleteAttachment(attachment)
            }
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
                ) { Text("أرشفة") }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun AddTransactionDialog(
    currencyCode: String,
    onDismiss: () -> Unit,
    onSave: (
        TransactionType,
        Long,
        String,
        List<TransactionAttachmentStorage.SelectedAttachment>
    ) -> Unit
) {
    var selectedType by remember { mutableStateOf(TransactionType.RECEIVABLE) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf(false) }
    var attachments by remember {
        mutableStateOf<List<TransactionAttachmentStorage.SelectedAttachment>>(emptyList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة عملية") },
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
                    onAmountChange = {
                        amountText = it
                        amountError = false
                    },
                    onDescriptionChange = { description = it }
                )

                TransactionAttachmentPicker(
                    selectedAttachments = attachments,
                    onAttachmentsChanged = { attachments = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amountMinor = parseAmountToMinor(amountText)
                    if (amountMinor == null || amountMinor <= 0L) {
                        amountError = true
                    } else {
                        onSave(
                            selectedType,
                            amountMinor,
                            description.trim(),
                            attachments
                        )
                    }
                }
            ) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
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
        title = { Text("تعديل العملية") },
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
                    onAmountChange = {
                        amountText = it
                        amountError = false
                    },
                    onDescriptionChange = { description = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amountMinor = parseAmountToMinor(amountText)
                    if (amountMinor == null || amountMinor <= 0L) {
                        amountError = true
                    } else {
                        onSave(
                            transaction.copy(
                                type = selectedType,
                                amountMinor = amountMinor,
                                description = description.trim()
                            )
                        )
                    }
                }
            ) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
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
        Text("نوع العملية", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selectedType == TransactionType.RECEIVABLE) {
                Button(
                    onClick = { onTypeChange(TransactionType.RECEIVABLE) },
                    modifier = Modifier.weight(1f)
                ) { Text("✓ عليه") }
            } else {
                OutlinedButton(
                    onClick = { onTypeChange(TransactionType.RECEIVABLE) },
                    modifier = Modifier.weight(1f)
                ) { Text("عليه") }
            }

            if (selectedType == TransactionType.PAYABLE) {
                Button(
                    onClick = { onTypeChange(TransactionType.PAYABLE) },
                    modifier = Modifier.weight(1f)
                ) { Text("✓ له") }
            } else {
                OutlinedButton(
                    onClick = { onTypeChange(TransactionType.PAYABLE) },
                    modifier = Modifier.weight(1f)
                ) { Text("له") }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = amountText,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("المبلغ $currencyCode") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = amountError
        )

        if (amountError) {
            Text(
                text = "أدخل مبلغًا صحيحًا أكبر من صفر وبحد أقصى منزلتين عشريتين.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(12.dp))

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
    val typeText = when (transaction.type) {
        TransactionType.RECEIVABLE -> "عليه"
        TransactionType.PAYABLE -> "له"
    }

    val formattedDate = remember(transaction.transactionDate) {
        SimpleDateFormat(
            "yyyy-MM-dd HH:mm",
            Locale.getDefault()
        ).format(Date(transaction.transactionDate))
    }

    val attachmentCount by transactionViewModel
        .observeAttachmentCount(transaction.id)
        .collectAsState(initial = 0)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(typeText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                if (transaction.description.isNotBlank()) {
                    Text(
                        transaction.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    formattedDate,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        typeText,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        formatAmount(transaction.amountMinor),
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onAttachments) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = if (attachmentCount == 0) {
                            "إضافة أو عرض المرفقات"
                        } else {
                            "عرض المرفقات ($attachmentCount)"
                        }
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "تعديل العملية"
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "أرشفة العملية"
                    )
                }
            }
        }
    }
}

private fun parseAmountToMinor(value: String): Long? {
    return runCatching {
        BigDecimal(value.trim())
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
    }.getOrNull()
}

private fun formatAmount(amountMinor: Long): String {
    return BigDecimal(amountMinor)
        .movePointLeft(2)
        .setScale(2, RoundingMode.UNNECESSARY)
        .stripTrailingZeros()
        .toPlainString()
}

private fun formatBalance(balanceMinor: Long): String {
    val amount = BigDecimal(balanceMinor)
        .movePointLeft(2)
        .setScale(2, RoundingMode.UNNECESSARY)
        .stripTrailingZeros()
        .toPlainString()
    return if (balanceMinor > 0L) {
        "عليه: $amount"
    } else if (balanceMinor < 0L) {
        "له: ${amount.removePrefix("-")}"
    } else {
        "متوازن: 0"
    }
}
