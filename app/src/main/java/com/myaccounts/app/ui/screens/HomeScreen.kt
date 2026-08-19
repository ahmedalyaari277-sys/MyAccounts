package com.myaccounts.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.dao.PersonWithAccounts
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
            TopAppBar(
                title = { Text("دفتر الحسابات", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    TextButton(onClick = onReportsClick) { Text("التقارير", fontWeight = FontWeight.Bold) }
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "ترتيب الأشخاص")
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
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "الإعدادات")
                    }
                    IconButton(onClick = onBackupRestoreClick) { Icon(Icons.Default.Backup, contentDescription = "النسخ الاحتياطي والاستعادة") }
                    IconButton(onClick = onArchiveClick) { Icon(Icons.Default.Archive, contentDescription = "الأرشيف") }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("بحث بالاسم أو الهاتف أو العنوان أو الملاحظات") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(16.dp))

                if (displayedList.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(48.dp).width(48.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(if (searchQuery.isBlank()) "لا توجد حسابات مسجلة" else "لا توجد نتائج للبحث", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (searchQuery.isBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text("اضغط (+) لإضافة أول شخص", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) { Icon(Icons.Default.Add, contentDescription = "إضافة شخص") }
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
                shape = RoundedCornerShape(18.dp),
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
private fun PersonCard(personWithAccounts: PersonWithAccounts, onClick: () -> Unit, onQuickTransaction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onQuickTransaction) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة عملية سريعة", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(personWithAccounts.person.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    if (personWithAccounts.person.phone.isNotBlank()) Text(personWithAccounts.person.phone, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            if (personWithAccounts.person.address.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("العنوان: ${personWithAccounts.person.address}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CurrencyLabel("ريال يمني", personWithAccounts.balance("YER"))
                CurrencyLabel("ريال سعودي", personWithAccounts.balance("SAR"))
                CurrencyLabel("دولار", personWithAccounts.balance("USD"))
            }
        }
    }
}

private fun PersonWithAccounts.balance(currencyCode: String): Long = accounts.firstOrNull { it.currencyCode == currencyCode }?.balanceMinor ?: 0L

@Composable
private fun CurrencyLabel(currency: String, balance: Long) {
    val balanceColor = when {
        balance > 0L -> MaterialTheme.colorScheme.error
        balance < 0L -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(currency, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatBalance(balance), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = balanceColor)
    }
}

private fun formatBalance(balance: Long): String = when {
    balance > 0L -> "عليه ${formatAmount(balance)}"
    balance < 0L -> "له ${formatAmount(-balance)}"
    else -> "متوازن 0"
}

private fun formatAmount(amount: Long): String = BigDecimal(amount).movePointLeft(2).stripTrailingZeros().toPlainString()

@Composable
private fun AddPersonDialog(onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة شخص جديد", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(name, { name = it; nameError = false }, Modifier.fillMaxWidth(), label = { Text("اسم الشخص") }, singleLine = true, isError = nameError, shape = RoundedCornerShape(12.dp))
                if (nameError) Text("اسم الشخص مطلوب", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("رقم الهاتف") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text("العنوان") }, minLines = 2, shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("الملاحظات") }, minLines = 2, shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = { Button(onClick = { if (name.isBlank()) nameError = true else onSave(name.trim(), phone.trim(), address.trim(), notes.trim()) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
