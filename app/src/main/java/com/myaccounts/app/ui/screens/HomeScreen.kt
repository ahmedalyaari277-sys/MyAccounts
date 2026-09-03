package com.myaccounts.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.ui.components.AppTopBar
import com.myaccounts.app.ui.components.BalanceAmount
import com.myaccounts.app.ui.components.BalanceStatus
import com.myaccounts.app.ui.components.EmptyState
import com.myaccounts.app.ui.components.EmptyStateType
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.SearchField
import com.myaccounts.app.util.TransactionAttachmentStorage
import java.math.BigDecimal

private enum class PersonSortOrder { LATEST_TRANSACTION, ALPHABETICAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    personsList: List<PersonWithAccounts>,
    onAddPerson: (String, String, String, String) -> Unit,
    onPersonClick: (Long) -> Unit,
    onQuickTransactionClick: (Long, String) -> Unit = { personId, _ -> onPersonClick(personId) },
    onQuickTransactionSave: ((TransactionEntity, List<TransactionAttachmentStorage.SelectedAttachment>) -> Unit)? = null,
    onReportsClick: () -> Unit = {},
    onArchiveClick: () -> Unit = {},
    onBackupRestoreClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var quickTransactionPersonId by remember { mutableStateOf<Long?>(null) }
    var sortOrder by remember { mutableStateOf(PersonSortOrder.LATEST_TRANSACTION) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val filteredList = personsList.filter { item ->
        item.person.name.contains(searchQuery, ignoreCase = true) ||
            item.person.phone.contains(searchQuery) ||
            item.person.address.contains(searchQuery, ignoreCase = true) ||
            item.person.notes.contains(searchQuery, ignoreCase = true)
    }

    val displayedList = when (sortOrder) {
        PersonSortOrder.LATEST_TRANSACTION -> filteredList
        PersonSortOrder.ALPHABETICAL -> filteredList.sortedBy { it.person.name.lowercase() }
    }

    val quickPerson = personsList.firstOrNull { it.person.id == quickTransactionPersonId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "حساباتي",
                actions = {
                    IconButton(
                        onClick = onReportsClick,
                        modifier = Modifier.semantics { contentDescription = "التقارير" }
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null)
                    }
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.semantics { contentDescription = "ترتيب الأشخاص" }
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("حسب أحدث عملية") },
                                onClick = {
                                    sortOrder = PersonSortOrder.LATEST_TRANSACTION
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("حسب الأبجدية") },
                                onClick = {
                                    sortOrder = PersonSortOrder.ALPHABETICAL
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.semantics { contentDescription = "المزيد من الخيارات" }
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("الإعدادات") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onSettingsClick()
                                }
                            )
                            DropdownMenuItem(
                                modifier = Modifier.semantics { contentDescription = "فتح النسخ الاحتياطي والاستعادة" },
                                text = { Text("النسخ الاحتياطي والاستعادة") },
                                leadingIcon = { Icon(Icons.Default.Backup, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onBackupRestoreClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("الأرشيف") },
                                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onArchiveClick()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp)
            ) {
                SearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "بحث بالاسم أو الهاتف أو العنوان أو الملاحظات"
                )
                Spacer(Modifier.height(16.dp))

                if (displayedList.isEmpty()) {
                    EmptyState(
                        type = EmptyStateType.People,
                        title = if (searchQuery.isBlank()) "لا توجد حسابات مسجلة" else "لا توجد نتائج للبحث",
                        description = if (searchQuery.isBlank()) "اضغط (+) لإضافة أول شخص" else "جرّب تعديل عبارة البحث"
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayedList, key = { it.person.id }) { item ->
                            PersonCard(
                                item,
                                onClick = { onPersonClick(item.person.id) },
                                onQuickTransaction = {
                                    if (onQuickTransactionSave != null) quickTransactionPersonId = item.person.id
                                    else onQuickTransactionClick(item.person.id, "")
                                }
                            )
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(56.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة شخص")
            }
        }
    }

    if (showAddDialog) {
        AddPersonDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, phone, address, notes ->
                onAddPerson(name, phone, address, notes)
                showAddDialog = false
            }
        )
    }

    if (quickPerson != null && onQuickTransactionSave != null) {
        Dialog(
            onDismissRequest = { quickTransactionPersonId = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .imePadding(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                QuickTransactionScreen(
                    personName = quickPerson.person.name,
                    accounts = quickPerson.accounts,
                    onSave = { transaction, attachments ->
                        onQuickTransactionSave(transaction, attachments)
                        quickTransactionPersonId = null
                    },
                    onCancel = { quickTransactionPersonId = null }
                )
            }
        }
    }
}

@Composable
private fun PersonCard(
    personWithAccounts: PersonWithAccounts,
    onClick: () -> Unit,
    onQuickTransaction: () -> Unit
) {
    InformationCard(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onQuickTransaction) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "إضافة عملية سريعة",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    personWithAccounts.person.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (personWithAccounts.person.phone.isNotBlank()) {
                    Text(
                        personWithAccounts.person.phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        if (personWithAccounts.person.address.isNotBlank()) {
            Text(
                "العنوان: ${personWithAccounts.person.address}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CurrencyBalance(
                modifier = Modifier.weight(1f),
                currency = "ريال يمني",
                balance = personWithAccounts.balance("YER")
            )
            CurrencyBalance(
                modifier = Modifier.weight(1f),
                currency = "ريال سعودي",
                balance = personWithAccounts.balance("SAR")
            )
            CurrencyBalance(
                modifier = Modifier.weight(1f),
                currency = "دولار",
                balance = personWithAccounts.balance("USD")
            )
        }
    }
}

@Composable
private fun CurrencyBalance(
    currency: String,
    balance: Long,
    modifier: Modifier = Modifier
) {
    val status = when {
        balance > 0L -> BalanceStatus.Due
        balance < 0L -> BalanceStatus.Owed
        else -> BalanceStatus.Neutral
    }
    BalanceAmount(
        amount = formatBalance(balance),
        status = status,
        label = currency,
        modifier = modifier
    )
}

private fun PersonWithAccounts.balance(currencyCode: String): Long =
    accounts.firstOrNull { it.currencyCode == currencyCode }?.balanceMinor ?: 0L

private fun formatBalance(balance: Long): String = when {
    balance > 0L -> "عليه ${formatAmount(balance)}"
    balance < 0L -> "له ${formatAmount(-balance)}"
    else -> "متوازن 0"
}

private fun formatAmount(amount: Long): String =
    BigDecimal(amount).movePointLeft(2).stripTrailingZeros().toPlainString()

@Composable
private fun AddPersonDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة شخص جديد", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    name,
                    { name = it; nameError = false },
                    Modifier.fillMaxWidth(),
                    label = { Text("اسم الشخص") },
                    singleLine = true,
                    isError = nameError,
                    shape = MaterialTheme.shapes.small
                )
                if (nameError) {
                    Text(
                        "اسم الشخص مطلوب",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    phone,
                    { phone = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("رقم الهاتف") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    address,
                    { address = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("العنوان") },
                    minLines = 2,
                    shape = MaterialTheme.shapes.small
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    notes,
                    { notes = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("الملاحظات") },
                    minLines = 2,
                    shape = MaterialTheme.shapes.small
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) nameError = true
                    else onSave(name.trim(), phone.trim(), address.trim(), notes.trim())
                }
            ) {
                Text("حفظ", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}
