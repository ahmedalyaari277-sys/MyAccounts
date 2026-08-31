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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.myaccounts.app.data.custody.CustodyBalanceRules
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.data.custody.CustodyPersonEntity
import com.myaccounts.app.data.custody.CustodyTransactionEntity
import com.myaccounts.app.data.custody.CustodyTransactionType
import com.myaccounts.app.ui.theme.Due
import com.myaccounts.app.ui.theme.Owed
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import java.math.BigDecimal

private val custodyCurrenciesForDetails = listOf("YER", "SAR", "USD")

private fun formatCustodyAmount(value: Long): String = BigDecimal(value).movePointLeft(2).stripTrailingZeros().toPlainString()

private fun detailBalanceStatus(value: Long): String = when {
    value > 0L -> "لديه"
    value < 0L -> "عليه"
    else -> "متوازن"
}

@Composable
fun CustodyOperationsScreen(vm: CustodyViewModel, custodyId: Long, onBack: () -> Unit, onPerson: (Long) -> Unit, onOwner: () -> Unit) {
    val custody by vm.custody(custodyId).collectAsState()
    val people by vm.persons(custodyId).collectAsState()
    val accounts by vm.accounts(custodyId).collectAsState()
    val transactions by vm.transactions(custodyId).collectAsState()
    val current = custody ?: run {
        Scaffold(topBar = { TopAppBar(title = { Text("العهدة") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } }) }) { pad -> Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally) { Spacer(Modifier.height(40.dp)); androidx.compose.material3.CircularProgressIndicator() } }
        return
    }
    var menu by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var showAddPerson by remember { mutableStateOf(false) }
    var showQuick by remember { mutableStateOf(false) }
    var quickPersonId by remember { mutableStateOf<Long?>(null) }
    var quickOwner by remember { mutableStateOf(false) }
    val ownerBalances = custodyCurrenciesForDetails.associateWith { code -> accounts.firstOrNull { it.holderType == "OWNER" && it.personId == null && it.currencyCode == code }?.balanceMinor ?: 0L }

    fun openQuick(owner: Boolean, personId: Long? = null) {
        quickOwner = owner
        quickPersonId = personId
        showQuick = true
    }

    Scaffold(
        modifier = Modifier.semantics { contentDescription = "شاشة تفاصيل العهدة" },
        topBar = {
            TopAppBar(
                title = { Text(current.name, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "المزيد") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("تقرير العهدة") }, onClick = { menu = false; showReport = true })
                        DropdownMenuItem(text = { Text("أرشفة العهدة") }, onClick = { menu = false; vm.archive(custodyId); onBack() })
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                CustodyHolderCard(name = current.name, balances = ownerBalances, onClick = onOwner, onQuick = { openQuick(true) })
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("الأشخاص", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { showAddPerson = true }, modifier = Modifier.semantics { contentDescription = "إضافة شخص" }) { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.padding(horizontal = 2.dp)); Text("إضافة شخص") }
                }
            }
            items(people, key = { it.id }) { person ->
                val personBalances = custodyCurrenciesForDetails.associateWith { code -> transactions.filter { it.personId == person.id && it.currencyCode == code }.sumOf { CustodyBalanceRules.personDelta(it.type, it.amountMinor) } }
                CustodyHolderCard(name = person.name, phone = person.phone, balances = personBalances, onClick = { onPerson(person.id) }, onQuick = { openQuick(false, person.id) })
            }
            if (people.isEmpty()) {
                item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("لا يوجد أشخاص في هذه العهدة", fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("اضغط «إضافة شخص» لإضافة أول شخص", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            }
        }
    }

    if (showAddPerson) {
        CustodyAddPersonDialog(custodyId, people, { showAddPerson = false }) { person -> vm.addPersonAndWait(person.custodyId, person); showAddPerson = false }
    }
    if (showQuick) {
        CustodyLedgerOperationDialog(vm, custodyId, if (quickOwner) null else quickPersonId, quickOwner, "YER", if (quickOwner) CustodyTransactionType.RECEIVED_FROM_ORG else CustodyTransactionType.PAID_TO_PERSON, null, { showQuick = false }, { showQuick = false })
    }
    if (showReport) CustodySummaryReportDialog(current, accounts, transactions, { showReport = false })
}

@Composable
private fun CustodyHolderCard(name: String, phone: String = "", balances: Map<String, Long>, onClick: () -> Unit, onQuick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onQuick, modifier = Modifier.semantics { contentDescription = "إضافة عملية سريعة" }) { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                Column(Modifier.weight(1f)) { Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); if (phone.isNotBlank()) Text(phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { custodyCurrencyCell("ريال يمني", balances["YER"] ?: 0L); custodyCurrencyCell("ريال سعودي", balances["SAR"] ?: 0L); custodyCurrencyCell("دولار", balances["USD"] ?: 0L) }
        }
    }
}

@Composable
private fun custodyCurrencyCell(label: String, balance: Long) {
    val color = when { balance > 0L -> Due; balance < 0L -> Owed; else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatCustodyAmount(kotlin.math.abs(balance)), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
        Text(detailBalanceStatus(balance), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun CustodyAddPersonDialog(custodyId: Long, existing: List<CustodyPersonEntity>, onDismiss: () -> Unit, onSave: suspend (CustodyPersonEntity) -> Unit) {
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; var error by remember { mutableStateOf(false) }; var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val matches = existing.filter { name.isNotBlank() && it.name.contains(name.trim(), true) }.take(5)
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("إضافة شخص") }, text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        OutlinedTextField(name, { name = it; error = false }, Modifier.fillMaxWidth(), label = { Text("الاسم") }, singleLine = true, enabled = !saving, isError = error)
        matches.forEach { match -> TextButton(onClick = { if (!saving) { name = match.name; phone = match.phone; address = match.address; notes = match.notes } }, Modifier.fillMaxWidth()) { Text(match.name) } }
        OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("الهاتف") }, singleLine = true, enabled = !saving)
        OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text("العنوان") }, singleLine = true, enabled = !saving)
        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("الملاحظات") }, minLines = 2, enabled = !saving)
        if (error) Text("تعذر حفظ الشخص. تحقق من البيانات وحاول مرة أخرى.", color = MaterialTheme.colorScheme.error)
    } }, confirmButton = { Button(enabled = name.isNotBlank() && !saving, onClick = { saving = true; error = false; scope.launch { runCatching { onSave(CustodyPersonEntity(custodyId = custodyId, name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim())) }.onSuccess { saving = false }.onFailure { saving = false; error = true } } }) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") } }, dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
private fun CustodySummaryReportDialog(custody: CustodyEntity, accounts: List<com.myaccounts.app.data.custody.CustodyAccountEntity>, transactions: List<CustodyTransactionEntity>, onDismiss: () -> Unit) {
    var currency by remember { mutableStateOf("YER") }
    val rows = transactions.filter { it.currencyCode == currency }.sortedBy { it.transactionDate }
    val balance = rows.filter { it.personId == null }.sumOf { CustodyBalanceRules.ownerDelta(it.type, it.amountMinor) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تقرير العهدة") }, text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        item { Text(custody.name, fontWeight = FontWeight.Bold); Text("الجهة: ${custody.organizationName}"); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { custodyCurrenciesForDetails.forEach { code -> androidx.compose.material3.FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(code) }) } }; Text("المتبقي: ${formatCustodyAmount(kotlin.math.abs(balance))}", fontWeight = FontWeight.Bold) }
        items(rows, key = { it.id }) { row -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) { Text(when (row.type) { CustodyTransactionType.RECEIVED_FROM_ORG -> "استلام من الجهة"; CustodyTransactionType.RETURNED_TO_ORG -> "مرتجع للجهة"; CustodyTransactionType.PAID_TO_PERSON -> "صرف"; CustodyTransactionType.RETURNED_FROM_PERSON -> "مرتجع"; else -> row.type }, fontWeight = FontWeight.Bold); Text("${formatCustodyAmount(row.amountMinor)} $currency"); if (row.description.isNotBlank()) Text(row.description, style = MaterialTheme.typography.bodySmall) } } }
    } }, confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } })
}
