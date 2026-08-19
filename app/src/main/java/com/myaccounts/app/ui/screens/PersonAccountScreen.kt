package com.myaccounts.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonAccountScreen(
    personWithAccounts: PersonWithAccounts,
    onBack: () -> Unit,
    onUpdatePerson: (String, String, String, String) -> Unit,
    onDeletePerson: () -> Unit,
    onAccountClick: (Long) -> Unit,
    onReportClick: (String) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showReportTypeDialog by remember { mutableStateOf(false) }
    val person = personWithAccounts.person

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = person.name, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "أرشفة", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(person.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (person.phone.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("الهاتف: ${person.phone}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (person.address.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text("العنوان: ${person.address}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (person.notes.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text("الملاحظات: ${person.notes}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("الحسابات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row {
                    TextButton(onClick = { onReportClick("ALL") }) { Text("إصدار التقرير الكامل", fontWeight = FontWeight.Bold) }
                    IconButton(onClick = { showReportTypeDialog = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "المزيد")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            CurrencyAccountCard(account(personWithAccounts.accounts, "YER"), "الريال اليمني", onAccountClick)
            CurrencyAccountCard(account(personWithAccounts.accounts, "SAR"), "الريال السعودي", onAccountClick)
            CurrencyAccountCard(account(personWithAccounts.accounts, "USD"), "الدولار الأمريكي", onAccountClick)
        }
    }

    if (showReportTypeDialog) {
        AlertDialog(
            onDismissRequest = { showReportTypeDialog = false },
            title = { Text("نوع التقرير") },
            text = {
                Column {
                    Text("اختر نوع التقرير الذي تريد إصداره:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { showReportTypeDialog = false; onReportClick("ALL") }, Modifier.fillMaxWidth()) { Text("جميع العملات") }
                    TextButton(onClick = { showReportTypeDialog = false; onReportClick("YER") }, Modifier.fillMaxWidth()) { Text("الريال اليمني") }
                    TextButton(onClick = { showReportTypeDialog = false; onReportClick("SAR") }, Modifier.fillMaxWidth()) { Text("الريال السعودي") }
                    TextButton(onClick = { showReportTypeDialog = false; onReportClick("USD") }, Modifier.fillMaxWidth()) { Text("الدولار الأمريكي") }
                }
            },
            confirmButton = { TextButton(onClick = { showReportTypeDialog = false }) { Text("إغلاق") } }
        )
    }

    if (showEditDialog) {
        EditPersonDialog(person.name, person.phone, person.address, person.notes, { showEditDialog = false }) { name, phone, address, notes ->
            onUpdatePerson(name, phone, address, notes)
            showEditDialog = false
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("أرشفة الشخص") },
            text = { Text("هل أنت متأكد من أرشفة هذا الشخص؟ سيتم إخفاؤه من القائمة الرئيسية مع الاحتفاظ بسجله المالي.") },
            confirmButton = { Button(onClick = { showDeleteDialog = false; onDeletePerson() }) { Text("أرشفة") } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("إلغاء") } }
        )
    }
}

private fun account(accounts: List<CurrencyAccountEntity>, currencyCode: String): CurrencyAccountEntity? = accounts.firstOrNull { it.currencyCode == currencyCode }

@Composable
private fun CurrencyAccountCard(account: CurrencyAccountEntity?, currencyName: String, onClick: (Long) -> Unit) {
    val balanceMinor = account?.balanceMinor ?: 0L
    val balanceColor = when {
        balanceMinor > 0L -> MaterialTheme.colorScheme.error
        balanceMinor < 0L -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).then(if (account != null) Modifier.clickable { onClick(account.id) } else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(currencyName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(account?.currencyCode ?: "غير متاح", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(if (balanceMinor > 0L) "عليه ${formatAmount(balanceMinor)}" else if (balanceMinor < 0L) "له ${formatAmount(-balanceMinor)}" else "متعادل 0", color = balanceColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
    }
}

private fun formatAmount(amountMinor: Long): String = BigDecimal(amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString()

@Composable
private fun EditPersonDialog(name: String, phone: String, address: String, notes: String, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var editedName by remember { mutableStateOf(name) }
    var editedPhone by remember { mutableStateOf(phone) }
    var editedAddress by remember { mutableStateOf(address) }
    var editedNotes by remember { mutableStateOf(notes) }
    var nameError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل بيانات الشخص") },
        text = {
            Column {
                OutlinedTextField(editedName, { editedName = it; nameError = false }, Modifier.fillMaxWidth(), label = { Text("الاسم") }, singleLine = true, isError = nameError)
                if (nameError) Text("الاسم مطلوب", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(editedPhone, { editedPhone = it }, Modifier.fillMaxWidth(), label = { Text("الهاتف") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(editedAddress, { editedAddress = it }, Modifier.fillMaxWidth(), label = { Text("العنوان") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(editedNotes, { editedNotes = it }, Modifier.fillMaxWidth(), label = { Text("الملاحظات") }, minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { if (editedName.isBlank()) nameError = true else onSave(editedName.trim(), editedPhone.trim(), editedAddress.trim(), editedNotes.trim()) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
