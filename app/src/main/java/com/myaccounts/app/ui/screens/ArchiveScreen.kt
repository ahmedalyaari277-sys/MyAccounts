package com.myaccounts.app.ui.screens

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*
import com.myaccounts.app.data.local.entity.PersonWithAccounts
import com.myaccounts.app.data.local.dao.ArchivedTransactionRow
import com.myaccounts.app.data.local.dao.RestoreTransactionResult

@Composable
fun ArchiveScreen(
    archivedPersons: List<PersonWithAccounts>,
    archivedTransactions: List<ArchivedTransactionRow>,
    onOpenPerson: (Long) -> Unit,
    onRestorePerson: (Long) -> Unit,
    onPermanentDelete: (Long) -> Unit,
    onRestoreTransaction: (Long) -> Unit,
    onPermanentDeleteTransaction: (Long) -> Unit,
    onClearArchive: () -> Unit,
    restoreTransactionResult: RestoreTransactionResult? = null,
    onDismissRestoreResult: () -> Unit = {}
) {
    var showClearArchiveDialog by remember { mutableStateOf(false) }
    var personToDelete by remember { mutableStateOf<PersonWithAccounts?>(null) }
    var transactionToDelete by remember { mutableStateOf<ArchivedTransactionRow?>(null) }
    val hasArchive = archivedPersons.isNotEmpty() || archivedTransactions.isNotEmpty()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("الأرشيف", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (hasArchive) {
                IconButton(onClick = { showClearArchiveDialog = true }) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "إفراغ الأرشيف", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!hasArchive) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("الأرشيف فارغ")
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(archivedPersons.size) { index ->
                    val person = archivedPersons[index]
                    ArchivedPersonCard(
                        person = person,
                        onOpen = { onOpenPerson(person.person.id) },
                        onRestore = { onRestorePerson(person.person.id) },
                        onDelete = { personToDelete = person }
                    )
                }
                items(archivedTransactions.size) { index ->
                    val transaction = archivedTransactions[index]
                    ArchivedTransactionCard(
                        transaction = transaction,
                        onRestore = { onRestoreTransaction(transaction.transactionId) },
                        onDelete = { transactionToDelete = transaction }
                    )
                }
            }
        }
    }

    if (showClearArchiveDialog) AlertDialog(
        onDismissRequest = { showClearArchiveDialog = false },
        title = { Text("إفراغ الأرشيف") },
        text = { Text("سيتم حذف جميع الأشخاص والعمليات المؤرشفة نهائيًا، بما في ذلك الحسابات التابعة والمرفقات. لا يمكن التراجع عن هذا الإجراء.") },
        confirmButton = { Button(onClick = { showClearArchiveDialog = false; onClearArchive() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("إفراغ الأرشيف") } },
        dismissButton = { TextButton(onClick = { showClearArchiveDialog = false }) { Text("إلغاء") } }
    )
    if (restoreTransactionResult != null) {
        val result = restoreTransactionResult
        val message = when (result) {
            RestoreTransactionResult.RESTORED -> "تمت استعادة العملية بنجاح."
            RestoreTransactionResult.ACCOUNT_ARCHIVED -> "لا يمكن استعادة العملية لأن الحساب المرتبط بها مؤرشف. استعد الحساب أولًا ثم استعد العملية."
            RestoreTransactionResult.ACCOUNT_DELETED -> "لا يمكن استعادة العملية لأن الحساب الأصلي حُذف نهائيًا. لا يتم ربط العملية بحساب آخر تلقائيًا."
            RestoreTransactionResult.ACCOUNT_REPLACED -> "لا يمكن استعادة العملية لأن الحساب الأصلي غير موجود ويوجد حساب آخر بديل. لن يتم نقل العملية إليه تلقائيًا."
            RestoreTransactionResult.OWNER_DELETED -> "لا يمكن استعادة العملية لأن الشخص الأصلي حُذف نهائيًا."
        }
        AlertDialog(
            onDismissRequest = onDismissRestoreResult,
            title = { Text(if (result == RestoreTransactionResult.RESTORED) "استعادة العملية" else "تعذر الاستعادة") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismissRestoreResult) { Text("حسنًا") } }
        )
    }
    personToDelete?.let { person ->
        AlertDialog(
            onDismissRequest = { personToDelete = null },
            title = { Text("حذف نهائي") },
            text = { Text("سيتم حذف ${person.person.name} وجميع حساباته وحركاته نهائيًا. لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = { Button(onClick = { onPermanentDelete(person.person.id); personToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("حذف نهائي") } },
            dismissButton = { TextButton(onClick = { personToDelete = null }) { Text("إلغاء") } }
        )
    }
    transactionToDelete?.let { transaction ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("حذف العملية نهائيًا") },
            text = { Text("سيتم حذف العملية من الأرشيف ومرفقاتها نهائيًا. لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = { Button(onClick = { onPermanentDeleteTransaction(transaction.transactionId); transactionToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("حذف نهائي") } },
            dismissButton = { TextButton(onClick = { transactionToDelete = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun ArchivedPersonCard(person: PersonWithAccounts, onOpen: () -> Unit, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(person.person.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (person.person.phone.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(person.person.phone, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (person.person.address.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("العنوان: ${person.person.address}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRestore, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Restore, contentDescription = null); Spacer(Modifier.padding(horizontal = 2.dp)); Text("استعادة") }
                Button(onClick = onDelete, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.DeleteForever, contentDescription = null); Spacer(Modifier.padding(horizontal = 2.dp)); Text("حذف نهائي") }
            }
        }
    }
}

@Composable
private fun ArchivedTransactionCard(transaction: ArchivedTransactionRow, onRestore: () -> Unit, onDelete: () -> Unit) {
    val amount = BigDecimal.valueOf(transaction.amountMinor).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()
    val date = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(transaction.transactionDate))
    val typeText = if (transaction.type == "RECEIVABLE") "له" else "عليه"
    val typeColor = if (transaction.type == "RECEIVABLE") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(transaction.personName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text("العملة: ${transaction.currencyCode}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(typeText, color = typeColor, fontWeight = FontWeight.Bold)
            Text("المبلغ: $amount", fontWeight = FontWeight.Bold, color = typeColor)
            Text("التاريخ: $date", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (transaction.description.isNotBlank()) Text("البيان: ${transaction.description}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRestore, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Restore, contentDescription = null); Spacer(Modifier.padding(horizontal = 2.dp)); Text("استعادة") }
                Button(onClick = onDelete, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.DeleteForever, contentDescription = null); Spacer(Modifier.padding(horizontal = 2.dp)); Text("حذف نهائي") }
            }
        }
    }
}