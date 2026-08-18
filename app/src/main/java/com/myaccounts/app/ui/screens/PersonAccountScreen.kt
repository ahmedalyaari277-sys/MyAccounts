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
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "أرشفة",
                            tint = MaterialTheme.colorScheme.error
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (person.phone.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "الهاتف: ${person.phone}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (person.address.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "العنوان: ${person.address}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (person.notes.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "الملاحظات: ${person.notes}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "الحسابات",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))

            CurrencyAccountCard(
                account = account(personWithAccounts.accounts, "YER"),
                currencyName = "الريال اليمني",
                onClick = onAccountClick,
                onReportClick = { onReportClick("YER") }
            )
            CurrencyAccountCard(
                account = account(personWithAccounts.accounts, "SAR"),
                currencyName = "الريال السعودي",
                onClick = onAccountClick,
                onReportClick = { onReportClick("SAR") }
            )
            CurrencyAccountCard(
                account = account(personWithAccounts.accounts, "USD"),
                currencyName = "الدولار الأمريكي",
                onClick = onAccountClick,
                onReportClick = { onReportClick("USD") }
            )
        }
    }

    if (showEditDialog) {
        EditPersonDialog(
            name = person.name,
            phone = person.phone,
            address = person.address,
            notes = person.notes,
            onDismiss = { showEditDialog = false },
            onSave = { name, phone, address, notes ->
                onUpdatePerson(name, phone, address, notes)
                showEditDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("أرشفة الشخص") },
            text = {
                Text("هل أنت متأكد من أرشفة هذا الشخص؟ سيتم إخفاؤه من القائمة الرئيسية مع الاحتفاظ بسجله المالي.")
            },
            confirmButton = {
                Button(onClick = {
                    showDeleteDialog = false
                    onDeletePerson()
                }) { Text("أرشفة") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

private fun account(
    accounts: List<CurrencyAccountEntity>,
    currencyCode: String
): CurrencyAccountEntity? = accounts.firstOrNull { it.currencyCode == currencyCode }

@Composable
private fun CurrencyAccountCard(
    account: CurrencyAccountEntity?,
    currencyName: String,
    onClick: (Long) -> Unit,
    onReportClick: () -> Unit
) {
    val balanceMinor = account?.balanceMinor ?: 0L
    val balanceColor = when {
        balanceMinor > 0L -> MaterialTheme.colorScheme.error
        balanceMinor < 0L -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .then(if (account != null) Modifier.clickable { onClick(account.id) } else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currencyName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = account?.currencyCode ?: "غير متاح",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(
                        text = formatBalance(balanceMinor),
                        color = balanceColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    if (balanceMinor != 0L) {
                        Text(
                            text = if (balanceMinor > 0L) "+${formatAmount(balanceMinor)}" else "-${formatAmount(-balanceMinor)}",
                            color = balanceColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onReportClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = account != null
            ) {
                Text("إصدار التقرير", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatBalance(balanceMinor: Long): String = when {
    balanceMinor > 0L -> "عليه"
    balanceMinor < 0L -> "له"
    else -> "متوازن"
}

private fun formatAmount(amountMinor: Long): String = BigDecimal(amountMinor)
    .movePointLeft(2)
    .stripTrailingZeros()
    .toPlainString()

@Composable
private fun EditPersonDialog(
    name: String,
    phone: String,
    address: String,
    notes: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
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
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it; nameError = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("الاسم") },
                    singleLine = true,
                    isError = nameError
                )
                if (nameError) Text(
                    "الاسم مطلوب",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editedPhone,
                    onValueChange = { editedPhone = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("الهاتف") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editedAddress,
                    onValueChange = { editedAddress = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("العنوان") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editedNotes,
                    onValueChange = { editedNotes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("الملاحظات") },
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (editedName.isBlank()) {
                    nameError = true
                } else {
                    onSave(
                        editedName.trim(),
                        editedPhone.trim(),
                        editedAddress.trim(),
                        editedNotes.trim()
                    )
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
