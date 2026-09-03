package com.myaccounts.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.ui.components.AppTopBar
import com.myaccounts.app.ui.components.BalanceAmount
import com.myaccounts.app.ui.components.BalanceStatus
import com.myaccounts.app.ui.components.DangerButton
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.PrimaryButton
import com.myaccounts.app.ui.components.SecondaryButton
import com.myaccounts.app.ui.components.SummaryCard
import com.myaccounts.app.ui.components.StatusChip
import com.myaccounts.app.ui.viewmodel.TransactionViewModel
import java.math.BigDecimal

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
    val accounts = personWithAccounts.accounts
    val initialAccount = accounts.firstOrNull()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = person.name,
                onBack = onBack,
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(title = "بيانات الشخص") {
                Text(
                    person.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (person.phone.isNotBlank()) {
                    Text(
                        "الهاتف: ${person.phone}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (person.address.isNotBlank()) {
                    Text(
                        "العنوان: ${person.address}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (person.notes.isNotBlank()) {
                    Text(
                        "الملاحظات: ${person.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (accounts.isNotEmpty()) {
                SummaryCard(title = "ملخص الأرصدة") {
                    accounts.sortedBy { currencyOrder(it.currencyCode) }.forEach { account ->
                        BalanceAmount(
                            amount = formatBalance(account.balanceMinor),
                            status = balanceStatus(account.balanceMinor),
                            label = account.currencyCode,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "الحساب والحركات",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip("${accounts.size} عملات")
                    IconButton(onClick = { showReportTypeDialog = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "التقارير")
                    }
                }
            }

            if (initialAccount != null) {
                TransactionScreen(
                    accountId = initialAccount.id,
                    currencyCode = initialAccount.currencyCode,
                    accounts = accounts,
                    onBack = {},
                    transactionViewModel = transactionViewModel,
                    embedded = true,
                    modifier = Modifier.weight(1f),
                    personName = person.name
                )
            } else {
                InformationCard(modifier = Modifier.weight(1f)) {
                    Text(
                        "لا توجد حسابات عملات لهذا الشخص.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "اختر نوع التقرير الذي تريد إصداره:",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { showReportTypeDialog = false; onReportClick("ALL") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("جميع العملات", style = MaterialTheme.typography.labelLarge) }
                    TextButton(
                        onClick = { showReportTypeDialog = false; onReportClick("YER") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("الريال اليمني", style = MaterialTheme.typography.labelLarge) }
                    TextButton(
                        onClick = { showReportTypeDialog = false; onReportClick("SAR") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("الريال السعودي", style = MaterialTheme.typography.labelLarge) }
                    TextButton(
                        onClick = { showReportTypeDialog = false; onReportClick("USD") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("الدولار الأمريكي", style = MaterialTheme.typography.labelLarge) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReportTypeDialog = false }) {
                    Text("إغلاق", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    if (showEditDialog) {
        EditPersonDialog(
            person.name,
            person.phone,
            person.address,
            person.notes,
            onDismiss = { showEditDialog = false }
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
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                DangerButton(
                    text = "أرشفة",
                    onClick = { showDeleteDialog = false; onDeletePerson() }
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = "إلغاء",
                    onClick = { showDeleteDialog = false }
                )
            }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it; nameError = false },
                    modifier = Modifier.fillMaxWidth(),
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
                androidx.compose.material3.OutlinedTextField(
                    value = editedPhone,
                    onValueChange = { editedPhone = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("الهاتف") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small
                )
                androidx.compose.material3.OutlinedTextField(
                    value = editedAddress,
                    onValueChange = { editedAddress = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("العنوان") },
                    shape = MaterialTheme.shapes.small
                )
                androidx.compose.material3.OutlinedTextField(
                    value = editedNotes,
                    onValueChange = { editedNotes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("الملاحظات") },
                    minLines = 2,
                    shape = MaterialTheme.shapes.small
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "حفظ",
                onClick = {
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
                }
            )
        },
        dismissButton = {
            SecondaryButton(text = "إلغاء", onClick = onDismiss)
        }
    )
}

private fun currencyOrder(code: String): Int = when (code) {
    "YER" -> 0
    "SAR" -> 1
    "USD" -> 2
    else -> 3
}

private fun balanceStatus(balanceMinor: Long): BalanceStatus = when {
    balanceMinor > 0L -> BalanceStatus.Due
    balanceMinor < 0L -> BalanceStatus.Owed
    else -> BalanceStatus.Neutral
}

private fun formatBalance(balanceMinor: Long): String {
    val amount = BigDecimal(balanceMinor).movePointLeft(2).stripTrailingZeros().toPlainString()
    return if (balanceMinor > 0L) "+$amount" else amount
}
