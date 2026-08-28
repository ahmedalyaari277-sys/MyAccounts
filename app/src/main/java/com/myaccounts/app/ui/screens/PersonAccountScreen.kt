package com.myaccounts.app.ui.screens

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
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.ui.viewmodel.TransactionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonAccountScreen(
    personWithAccounts: PersonWithAccounts,
    onBack: () -> Unit,
    onUpdatePerson: (String, String, String, String) -> Unit,
    onDeletePerson: () -> Unit,
    onReportClick: (String) -> Unit,
    transactionViewModel: TransactionViewModel
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showReportTypeDialog by remember { mutableStateOf(false) }
    val person = personWithAccounts.person
    val initialAccount = personWithAccounts.accounts.firstOrNull()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
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
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        person.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (person.phone.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "الهاتف: ${person.phone}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (person.address.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "العنوان: ${person.address}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (person.notes.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "الملاحظات: ${person.notes}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "الحسابات",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TextButton(onClick = { onReportClick("ALL") }) {
                        Text(
                            "إصدار التقرير الكامل",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = { showReportTypeDialog = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "المزيد")
                    }
                }
            }

            if (initialAccount != null) {
                Spacer(Modifier.height(4.dp))
                TransactionScreen(
                    accountId = initialAccount.id,
                    currencyCode = initialAccount.currencyCode,
                    accounts = personWithAccounts.accounts,
                    onBack = {},
                    transactionViewModel = transactionViewModel,
                    embedded = true,
                    modifier = Modifier.weight(1f),
                    personName = person.name
                )
            }
        }
    }

    if (showReportTypeDialog) {
        AlertDialog(
            onDismissRequest = { showReportTypeDialog = false },
            title = {
                Text(
                    "نوع التقرير",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "اختر نوع التقرير الذي تريد إصداره:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = { showReportTypeDialog = false; onReportClick("ALL") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("جميع العملات") }
                    TextButton(
                        onClick = { showReportTypeDialog = false; onReportClick("YER") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("الريال اليمني") }
                    TextButton(
                        onClick = { showReportTypeDialog = false; onReportClick("SAR") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("الريال السعودي") }
                    TextButton(
                        onClick = { showReportTypeDialog = false; onReportClick("USD") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("الدولار الأمريكي") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReportTypeDialog = false }) { Text("إغلاق") }
            }
        )
    }

    if (showEditDialog) {
        EditPersonDialog(
            person.name,
            person.phone,
            person.address,
            person.notes,
            { showEditDialog = false }
        ) { name, phone, address, notes ->
            onUpdatePerson(name, phone, address, notes)
            showEditDialog = false
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    "أرشفة الشخص",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "هل أنت متأكد من أرشفة هذا الشخص؟ سيتم إخفاؤه من القائمة الرئيسية مع الاحتفاظ بسجله المالي.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { showDeleteDialog = false; onDeletePerson() }) { Text("أرشفة") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("إلغاء") } }
        )
    }
}

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
        title = {
            Text(
                "تعديل بيانات الشخص",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    editedName,
                    { editedName = it; nameError = false },
                    Modifier.fillMaxWidth(),
                    label = { Text("الاسم") },
                    singleLine = true,
                    isError = nameError,
                    shape = MaterialTheme.shapes.small
                )
                if (nameError) {
                    Text(
                        "الاسم مطلوب",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    editedPhone,
                    { editedPhone = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("الهاتف") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    editedAddress,
                    { editedAddress = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("العنوان") },
                    shape = MaterialTheme.shapes.small
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    editedNotes,
                    { editedNotes = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("الملاحظات") },
                    minLines = 2,
                    shape = MaterialTheme.shapes.small
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (editedName.isBlank()) nameError = true
                else onSave(editedName.trim(), editedPhone.trim(), editedAddress.trim(), editedNotes.trim())
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
