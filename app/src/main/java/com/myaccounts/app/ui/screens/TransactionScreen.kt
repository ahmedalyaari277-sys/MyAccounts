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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.TransactionType
import com.myaccounts.app.ui.viewmodel.TransactionViewModel
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
                        text = balance.toString(),
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
                "+${transaction.amountMinor}"

            TransactionType.PAYABLE ->
                "-${transaction.amountMinor}"
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

                if (transaction.description.isNotBlank()) {

                    Text(
                        text = transaction.description,
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
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف الحركة"
                    )
                }
            }
        }
    }
}
