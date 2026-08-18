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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.myaccounts.app.data.local.dao.ArchivedTransactionRow
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    archivedPersons: List<PersonWithAccounts>,
    archivedTransactions: List<ArchivedTransactionRow>,
    onBack: () -> Unit,
    onRestore: (Long) -> Unit,
    onPermanentDelete: (Long) -> Unit,
    onPersonClick: (Long) -> Unit,
    onRestoreTransaction: (Long) -> Unit,
    onPermanentDeleteTransaction: (Long) -> Unit
) {
    var personToDelete by remember { mutableStateOf<PersonWithAccounts?>(null) }
    var transactionToDelete by remember { mutableStateOf<ArchivedTransactionRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الأرشيف", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (archivedPersons.isEmpty() && archivedTransactions.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("الأرشيف فارغ", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("الأشخاص والعمليات التي تتم أرشفتها ستظهر هنا.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (archivedPersons.isNotEmpty()) {
                    item { Text("الأشخاص المؤرشفون", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                    items(archivedPersons, key = { "person_${it.person.id}" }) { person ->
                        ArchivedPersonCard(
                            person = person,
                            onOpen = { onPersonClick(person.person.id) },
                            onRestore = { onRestore(person.person.id) },
                            onDelete = { personToDelete = person }
                        )
                    }
                }

                if (archivedTransactions.isNotEmpty()) {
                    item { Text("العمليات المؤرشفة", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
                    items(archivedTransactions, key = { "transaction_${it.transactionId}" }) { transaction ->
                        ArchivedTransactionCard(
                            transaction = transaction,
                            onRestore = { onRestoreTransaction(transaction.transactionId) },
                            onDelete = { transactionToDelete = transaction }
                        )
                    }
                }
            }
        }
    }

    personToDelete?.let { person ->
        AlertDialog(
            onDismissRequest = { personToDelete = null },
            title = { Text("حذف نهائي") },
            text = { Text("سيتم حذف ${person.person.name} وجميع حساباته وحركاته نهائيًا. لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                Button(onClick = { onPermanentDelete(person.person.id); personToDelete = null }) { Text("حذف نهائي") }
            },
            dismissButton = { TextButton(onClick = { personToDelete = null }) { Text("إلغاء") } }
        )
    }

    transactionToDelete?.let { transaction ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("حذف العملية نهائيًا") },
            text = { Text("سيتم حذف العملية من الأرشيف ومرفقاتها نهائيًا. لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                Button(onClick = { onPermanentDeleteTransaction(transaction.transactionId); transactionToDelete = null }) { Text("حذف نهائي") }
            },
            dismissButton = { TextButton(onClick = { transactionToDelete = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun ArchivedPersonCard(person: PersonWithAccounts, onOpen: () -> Unit, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(person.person.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (person.person.phone.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(person.person.phone, fontSize = 13.sp) }
            if (person.person.address.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("العنوان: ${person.person.address}", fontSize = 13.sp) }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Restore, contentDescription = null); Spacer(Modifier.padding(horizontal = 2.dp)); Text("استعادة")
                }
                Button(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null); Spacer(Modifier.padding(horizontal = 2.dp)); Text("حذف نهائي")
                }
            }
        }
    }
}

@Composable
private fun ArchivedTransactionCard(transaction: ArchivedTransactionRow, onRestore: () -> Unit, onDelete: () -> Unit) {
    val amount = BigDecimal.valueOf(transaction.amountMinor).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()
    val date = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(transaction.transactionDate))
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Text(transaction.personName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("العملة: ${transaction.currencyCode} • ${if (transaction.type == "RECEIVABLE") "له" else "عليه"}")
            Text("المبلغ: $amount")
            Text("التاريخ: $date")
            if (transaction.description.isNotBlank()) Text("البيان: ${transaction.description}")
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Restore, contentDescription = null); Spacer(Modifier.padding(horizontal = 2.dp)); Text("استعادة")
                }
                Button(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null); Spacer(Modifier.padding(horizontal = 2.dp)); Text("حذف نهائي")
                }
            }
        }
    }
}
