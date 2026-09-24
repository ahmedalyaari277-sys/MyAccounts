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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.foundation.isSystemInDarkTheme
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.data.local.dao.LedgerSearchResult
import com.myaccounts.app.ui.components.AppTopBar
import com.myaccounts.app.ui.components.BalanceStatus
import com.myaccounts.app.ui.components.EmptyState
import com.myaccounts.app.ui.components.EmptyStateType
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.SearchField
import com.myaccounts.app.ui.components.currencyDisplayName
import com.myaccounts.app.ui.theme.EntityName
import com.myaccounts.app.ui.theme.EntityNameDark
import com.myaccounts.app.util.TransactionAttachmentStorage
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PersonSortOrder { LATEST_TRANSACTION, ALPHABETICAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(personsList: List<PersonWithAccounts>, onAddPerson: (String, String, String, String) -> Unit, onPersonClick: (Long) -> Unit, onQuickTransactionClick: (Long, String) -> Unit = { personId, _ -> onPersonClick(personId) }, onQuickTransactionSave: ((TransactionEntity, List<TransactionAttachmentStorage.SelectedAttachment>) -> Unit)? = null, onReportsClick: () -> Unit = {}, onArchiveClick: () -> Unit = {}, onBackupRestoreClick: () -> Unit = {}, searchMatchedPersonIds: Set<Long> = emptySet(), searchResults: List<LedgerSearchResult> = emptyList(), onSearchQueryChange: (String) -> Unit = {}, onSearchResultClick: (LedgerSearchResult) -> Unit = {}) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var quickTransactionPersonId by remember { mutableStateOf<Long?>(null) }
    var sortOrder by remember { mutableStateOf(PersonSortOrder.LATEST_TRANSACTION) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { onSearchQueryChange("") } }
    val filteredList = personsList.filter { item ->
        searchQuery.isBlank() || item.person.id in searchMatchedPersonIds
    }
    val displayedList = when (sortOrder) { PersonSortOrder.LATEST_TRANSACTION -> filteredList; PersonSortOrder.ALPHABETICAL -> filteredList.sortedBy { it.person.name.lowercase() } }
    val quickPerson = personsList.firstOrNull { it.person.id == quickTransactionPersonId }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        AppTopBar(title = "حساباتي", actions = {
            IconButton(onClick = onReportsClick) { Icon(Icons.Default.Assessment, contentDescription = "التقارير") }
            Box {
                IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, contentDescription = "ترتيب الأشخاص") }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    DropdownMenuItem(text = { Text("حسب أحدث عملية") }, onClick = { sortOrder = PersonSortOrder.LATEST_TRANSACTION; showSortMenu = false })
                    DropdownMenuItem(text = { Text("حسب الأبجدية") }, onClick = { sortOrder = PersonSortOrder.ALPHABETICAL; showSortMenu = false })
                }
            }
            Box {
                IconButton(onClick = { showMoreMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "المزيد من الخيارات") }
                DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                    DropdownMenuItem(modifier = Modifier.semantics { contentDescription = "فتح النسخ الاحتياطي والاستعادة" }, text = { Text("النسخ الاحتياطي والاستعادة") }, leadingIcon = { Icon(Icons.Default.Backup, contentDescription = null) }, onClick = { showMoreMenu = false; onBackupRestoreClick() })
                    DropdownMenuItem(text = { Text("الأرشيف") }, leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) }, onClick = { showMoreMenu = false; onArchiveClick() })
                }
            }
        })
    }) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 88.dp)) {
                SearchField(query = searchQuery, onQueryChange = { searchQuery = it; onSearchQueryChange(it) }, placeholder = "بحث في الحسابات: الاسم، المبلغ، التاريخ، التفاصيل أو أي بيانات")
                Spacer(Modifier.height(8.dp))
                if (searchQuery.isNotBlank()) {
                    Text("نتائج البحث: " + searchResults.size, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    if (searchResults.isEmpty()) {
                        EmptyState(type = EmptyStateType.People, title = "لا توجد نتائج للبحث", description = "جرّب الاسم أو الهاتف أو التفاصيل أو المبلغ أو التاريخ.")
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(searchResults, key = { it.kind + "-" + (it.transactionId ?: it.personId) }) { result ->
                                LedgerSearchResultCard(result, onClick = { onSearchResultClick(result) })
                            }
                        }
                    }
                } else if (displayedList.isEmpty()) {
                    EmptyState(type = EmptyStateType.People, title = "لا توجد حسابات مسجلة", description = "اضغط (+) لإضافة أول شخص")
                } else {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(displayedList, key = { it.person.id }) { item -> PersonCard(item, onClick = { onPersonClick(item.person.id) }, onQuickTransaction = { if (onQuickTransactionSave != null) quickTransactionPersonId = item.person.id else onQuickTransactionClick(item.person.id, "") }) }
                    }
                }
            }
            FloatingActionButton(onClick = { showAddDialog = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(56.dp), containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = MaterialTheme.shapes.large) { Icon(Icons.Default.Add, contentDescription = "إضافة شخص") }
        }
    }
    if (showAddDialog) AddPersonDialog(onDismiss = { showAddDialog = false }, onSave = { name, phone, address, notes -> onAddPerson(name, phone, address, notes); showAddDialog = false })
    if (quickPerson != null && onQuickTransactionSave != null) {
        Dialog(onDismissRequest = { quickTransactionPersonId = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Card(Modifier.fillMaxWidth(0.72f).imePadding(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                QuickTransactionScreen(personName = quickPerson.person.name, accounts = quickPerson.accounts, onSave = { transaction, attachments -> onQuickTransactionSave(transaction, attachments); quickTransactionPersonId = null }, onCancel = { quickTransactionPersonId = null })
            }
        }
    }
}

@Composable
private fun PersonCard(personWithAccounts: PersonWithAccounts, onClick: () -> Unit, onQuickTransaction: () -> Unit) {
    val entityNameColor = MaterialTheme.colorScheme.primary
    InformationCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onQuickTransaction) { Icon(Icons.Default.Add, contentDescription = "إضافة عملية سريعة", tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(personWithAccounts.person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = entityNameColor)
                if (personWithAccounts.person.phone.isNotBlank()) Text(personWithAccounts.person.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
        if (personWithAccounts.person.address.isNotBlank()) Text("العنوان: ${personWithAccounts.person.address}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CurrencyBalance(Modifier.weight(1f), "ريال يمني", personWithAccounts.balance("YER"))
            CurrencyBalance(Modifier.weight(1f), "ريال سعودي", personWithAccounts.balance("SAR"))
            CurrencyBalance(Modifier.weight(1f), currencyDisplayName("USD"), personWithAccounts.balance("USD"))
        }
    }
}

@Composable
private fun CurrencyBalance(modifier: Modifier, currency: String, balance: Long) {
    val status = when { balance > 0L -> BalanceStatus.Due; balance < 0L -> BalanceStatus.Owed; else -> BalanceStatus.Neutral }
    val color = when (status) { BalanceStatus.Due -> com.myaccounts.app.ui.theme.Due; BalanceStatus.Owed -> com.myaccounts.app.ui.theme.Owed; BalanceStatus.Neutral -> com.myaccounts.app.ui.theme.Neutral }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(currencyDisplayName(currency), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatBalance(balance), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}

private fun PersonWithAccounts.balance(currencyCode: String): Long = accounts.firstOrNull { it.currencyCode == currencyCode }?.balanceMinor ?: 0L
private fun formatBalance(balance: Long): String = when { balance > 0L -> "عليه ${formatAmount(balance)}"; balance < 0L -> "له ${formatAmount(-balance)}"; else -> "متوازن 0" }
private fun formatAmount(amount: Long): String = BigDecimal(amount).movePointLeft(2).stripTrailingZeros().toPlainString()

@Composable
private fun AddPersonDialog(onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إضافة شخص جديد", style = MaterialTheme.typography.titleLarge) }, text = {
        Column {
            OutlinedTextField(name, { name = it; nameError = false }, Modifier.fillMaxWidth(), label = { Text("اسم الشخص") }, singleLine = true, isError = nameError, shape = MaterialTheme.shapes.small)
            if (nameError) Text("اسم الشخص مطلوب", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("رقم الهاتف") }, singleLine = true, shape = MaterialTheme.shapes.small)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text("العنوان") }, minLines = 2, shape = MaterialTheme.shapes.small)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("الملاحظات") }, minLines = 2, shape = MaterialTheme.shapes.small)
        }
    }, confirmButton = { Button(onClick = { if (name.isBlank()) nameError = true else onSave(name.trim(), phone.trim(), address.trim(), notes.trim()) }) { Text("حفظ", style = MaterialTheme.typography.labelLarge) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", style = MaterialTheme.typography.labelLarge) } })
}


@Composable
private fun LedgerSearchResultCard(result: LedgerSearchResult, onClick: () -> Unit) {
    InformationCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(if (result.kind == "PERSON") result.title else result.title.ifBlank { "عملية بدون تفاصيل" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (result.kind == "PERSON") "شخص" else "عملية — " + result.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (result.kind == "TRANSACTION") {
                    val amount = result.amountMinor?.let { BigDecimal(it).movePointLeft(2).stripTrailingZeros().toPlainString() }.orEmpty()
                    val date = result.transactionDate?.let { SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(it)) }.orEmpty()
                    Text(amount + " " + result.currencyCode.orEmpty() + "  •  " + date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
