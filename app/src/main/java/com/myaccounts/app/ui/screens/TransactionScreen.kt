package com.myaccounts.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.TransactionType
import com.myaccounts.app.ui.viewmodel.TransactionViewModel
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

    val transactions by transactionViewModel
        .transactions
        .collectAsState()

    val balance by transactionViewModel
        .balance
        .collectAsState()

    var transactionToDelete by remember {
        mutableStateOf<TransactionEntity?>(null)
    }

    var showAddTransactionDialog by remember {
        mutableStateOf(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "حركات $currencyCode"
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                }
            )
        },

        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    showAddTransactionDialog = true
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "إضافة حركة"
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

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {

                    Text(
                        text = "الرصيد",
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(6.dp)
                    )

                    Text(
                        text = formatAmount(balance),
                        style =
                            MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            if (transactions.isEmpty()) {

                Text(
                    text = "لا توجد حركات لهذا الحساب.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )

            } else {

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    items(
                        items = transactions,
                        key = {
                            it.id
                        }
                    ) { transaction ->

                        TransactionItem(
                            transaction = transaction,
                            onDelete = {
                                transactionToDelete = transaction
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddTransactionDialog) {

        AddTransactionDialog(
            currencyCode = currencyCode,

            onDismiss = {
                showAddTransactionDialog = false
            },

            onSave = { type, amountMinor, description ->

                transactionViewModel.addTransaction(
                    TransactionEntity(
                        accountId = accountId,
                        type = type,
                        amountMinor = amountMinor,
                        description = description,
                        transactionDate =
                            System.currentTimeMillis()
                    )
                )

                showAddTransactionDialog = false
            }
        )
    }

    transactionToDelete?.let { transaction ->

        AlertDialog(
            onDismissRequest = {
                transactionToDelete = null
            },

            title = {
                Text("حذف الحركة")
            },

            text = {
                Text(
                    "هل أنت متأكد من حذف هذه الحركة؟"
                )
            },

            confirmButton = {
                TextButton(
                    onClick = {

                        transactionViewModel
                            .deleteTransactionById(
                                transaction.id
                            )

                        transactionToDelete = null
                    }
                ) {
                    Text("حذف")
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        transactionToDelete = null
                    }
                ) {
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
        String
    ) -> Unit
) {

    var selectedType by remember {
        mutableStateOf(
            TransactionType.RECEIVABLE
        )
    }

    var amountText by remember {
        mutableStateOf("")
    }

    var description by remember {
        mutableStateOf("")
    }

    var amountError by remember {
        mutableStateOf(false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text("إضافة حركة")
        },

        text = {

            Column {

                Text(
                    text = "نوع الحركة",
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Button(
                        onClick = {
                            selectedType =
                                TransactionType.RECEIVABLE
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("قبض")
                    }

                    Button(
                        onClick = {
                            selectedType =
                                TransactionType.PAYABLE
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("دفع")
                    }
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                OutlinedTextField(
                    value = amountText,

                    onValueChange = {
                        amountText = it
                        amountError = false
                    },

                    modifier = Modifier.fillMaxWidth(),

                    label = {
                        Text("المبلغ $currencyCode")
                    },

                    singleLine = true,

                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.Decimal
                        ),

                    isError = amountError
                )

                if (amountError) {

                    Text(
                        text =
                            "أدخل مبلغًا صحيحًا أكبر من صفر وبحد أقصى منزلتين عشريتين.",
                        color =
                            MaterialTheme.colorScheme.error,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                OutlinedTextField(
                    value = description,

                    onValueChange = {
                        description = it
                    },

                    modifier = Modifier.fillMaxWidth(),

                    label = {
                        Text("الوصف")
                    },

                    minLines = 2,

                    maxLines = 3
                )
            }
        },

        confirmButton = {

            TextButton(
                onClick = {

                    val amountMinor =
                        parseAmountToMinor(
                            amountText
                        )

                    if (
                        amountMinor == null ||
                        amountMinor <= 0L
                    ) {

                        amountError = true

                    } else {

                        onSave(
                            selectedType,
                            amountMinor,
                            description.trim()
                        )
                    }
                }
            ) {
                Text("حفظ")
            }
        },

        dismissButton = {

            TextButton(
                onClick = onDismiss
            ) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
private fun TransactionItem(
    transaction: TransactionEntity,
    onDelete: () -> Unit
) {

    val typeText =
        when (transaction.type) {

            TransactionType.RECEIVABLE ->
                "قبض"

            TransactionType.PAYABLE ->
                "دفع"
        }

    val amountText =
        when (transaction.type) {

            TransactionType.RECEIVABLE ->
                "+${formatAmount(transaction.amountMinor)}"

            TransactionType.PAYABLE ->
                "-${formatAmount(transaction.amountMinor)}"
        }

    val formattedDate =
        remember(transaction.transactionDate) {
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                Locale.getDefault()
            ).format(
                Date(transaction.transactionDate)
            )
        }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = typeText,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                if (
                    transaction.description
                        .isNotBlank()
                ) {

                    Text(
                        text =
                            transaction.description,

                        style =
                            MaterialTheme.typography.bodyMedium
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )
                }

                Text(
                    text = formattedDate,
                    style =
                        MaterialTheme.typography.bodySmall
                )
            }

            Row {

                Text(
                    text = amountText,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onDelete
                ) {
                    Icon(
                        imageVector =
                            Icons.Default.Delete,
                        contentDescription =
                            "حذف الحركة"
                    )
                }
            }
        }
    }
}

private fun parseAmountToMinor(
    input: String
): Long? {

    val normalized =
        input
            .trim()
            .replace(',', '.')

    if (normalized.isEmpty()) {
        return null
    }

    return try {

        val decimal =
            BigDecimal(normalized)

        if (decimal <= BigDecimal.ZERO) {
            return null
        }

        decimal
            .setScale(
                2,
                RoundingMode.UNNECESSARY
            )
            .movePointRight(2)
            .longValueExact()

    } catch (
        exception: NumberFormatException
    ) {

        null

    } catch (
        exception: ArithmeticException
    ) {

        null
    }
}

private fun formatAmount(
    amountMinor: Long
): String {

    val decimal =
        BigDecimal(amountMinor)
            .movePointLeft(2)
            .stripTrailingZeros()

    return decimal.toPlainString()
}
