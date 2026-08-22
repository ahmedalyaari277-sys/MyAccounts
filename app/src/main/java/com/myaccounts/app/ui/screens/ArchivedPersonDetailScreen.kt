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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedPersonDetailScreen(
    personWithAccounts: PersonWithAccounts,
    archivedTransactions: List<TransactionEntity>,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onRestoreTransaction: (Long) -> Unit,
    onPermanentDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val person = personWithAccounts.person

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(person.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("شخص مؤرشف", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (person.phone.isNotBlank()) Text("الهاتف: ${person.phone}")
                if (person.address.isNotBlank()) Text("العنوان: ${person.address}")
                if (person.notes.isNotBlank()) Text("الملاحظات: ${person.notes}")
                Spacer(Modifier.height(10.dp))
                Text("الحسابات محفوظة مع أرصدتها والعمليات التي كانت بداخلها وقت الأرشفة.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text("العمليات داخل الحساب المؤرشف", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }

            if (archivedTransactions.isEmpty()) {
                item { Text("لا توجد عمليات محفوظة مع هذا الحساب.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(archivedTransactions, key = { it.id }) { transaction ->
                    ArchivedPersonTransactionCard(
                        transaction = transaction,
                        onRestore = { onRestoreTransaction(transaction.id) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRestore, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Text("استعادة الحساب")
                    }
                    Button(onClick = { showDeleteDialog = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Text("حذف نهائي")
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("حذف نهائي") },
            text = { Text("سيتم حذف ${person.name} وجميع حساباته وحركاته نهائيًا. العمليات المحفوظة ضمن أرشفة الحساب لن تكون قابلة للاستعادة بعد الحذف النهائي.") },
            confirmButton = {
                Button(onClick = {
                    showDeleteDialog = false
                    onPermanentDelete()
                }) { Text("حذف نهائي") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun ArchivedPersonTransactionCard(
    transaction: TransactionEntity,
    onRestore: () -> Unit
) {
    val amount = BigDecimal.valueOf(transaction.amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString()
    val date = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(transaction.transactionDate))
    val typeText = if (transaction.type.name == "RECEIVABLE") "له" else "عليه"

    androidx.compose.material3.Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$typeText $amount", fontWeight = FontWeight.Bold)
                    Text("التاريخ: $date", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    if (transaction.description.isNotBlank()) Text(transaction.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                IconButton(onClick = onRestore) {
                    Icon(Icons.Default.Restore, contentDescription = "استعادة العملية", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
