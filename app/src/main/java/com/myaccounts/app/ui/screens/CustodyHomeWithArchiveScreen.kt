package com.myaccounts.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.data.custody.CustodyFinancialSummary
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal

private enum class CustodySortOrder { LATEST, ALPHABETICAL }
private val custodyCurrencies = listOf("YER", "SAR", "USD")
private fun money(v: Long): String = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun status(v: Long): String = when { v > 0 -> "متبقي"; v < 0 -> "عجز"; else -> "متوازن" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyHomeWithArchiveScreen(vm: CustodyViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit, onArchive: () -> Unit, onReports: () -> Unit, onTransfer: () -> Unit) {
    val custodies by vm.custodies.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(CustodySortOrder.LATEST) }
    val displayedCustodies = when (sortOrder) { CustodySortOrder.LATEST -> custodies; CustodySortOrder.ALPHABETICAL -> custodies.sortedBy { it.name.trim().lowercase() } }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("العُهَد") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }, actions = {
                TextButton(onClick = onReports) { Text("التقارير", fontWeight = FontWeight.Bold) }
                IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, "ترتيب العُهَد") }
                IconButton(onClick = onTransfer, modifier = Modifier.semantics { contentDescription = "النسخ الاحتياطي والاستعادة" }) { Icon(Icons.Default.Backup, null) }
                IconButton(onClick = onArchive) { Icon(Icons.Default.Archive, "الأرشيف") }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    DropdownMenuItem(text = { Text("حسب الأحدث") }, onClick = { sortOrder = CustodySortOrder.LATEST; showSortMenu = false })
                    DropdownMenuItem(text = { Text("حسب الأبجدية") }, onClick = { sortOrder = CustodySortOrder.ALPHABETICAL; showSortMenu = false })
                }
            })
        },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "إضافة عهدة") } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(displayedCustodies, key = { it.id }) { custody ->
                val accounts by vm.accounts(custody.id).collectAsState()
                val transactions by vm.transactions(custody.id).collectAsState()
                val people by vm.persons(custody.id).collectAsState()
                val totalBalances = custodyCurrencies.associateWith { code -> CustodyFinancialSummary.custodyTotalBalance(transactions, accounts, code, people) }
                Card(Modifier.fillMaxWidth().clickable { onOpen(custody.id) }, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(custody.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("الجهة: ${custody.organizationName}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            if (custody.isClosed) AssistChip(onClick = {}, enabled = false, label = { Text("مغلقة ومسواة") })
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth()) {
                            custodyCurrencies.forEach { code ->
                                val balance = totalBalances[code] ?: 0L
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text(code, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(money(kotlin.math.abs(balance)), fontWeight = FontWeight.Bold, color = when { balance > 0 -> MaterialTheme.colorScheme.primary; balance < 0 -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                                    Text(status(balance), style = MaterialTheme.typography.labelSmall, color = when { balance > 0 -> MaterialTheme.colorScheme.primary; balance < 0 -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                                }
                            }
                        }
                    }
                }
            }
            if (displayedCustodies.isEmpty()) item { Text("لا توجد عُهَد نشطة.") }
        }
    }
    if (adding) CustodyCreateDialog(onDismiss = { adding = false }, onSave = { vm.createAndWait(it) })
}

@Composable
private fun CustodyCreateDialog(onDismiss: () -> Unit, onSave: suspend (CustodyEntity) -> Long) {
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }; var organizationPhone by remember { mutableStateOf("") }; var organizationAddress by remember { mutableStateOf("") }; var organizationNotes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("إضافة صاحب عهدة") }, text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(name, { name = it; error = false }, Modifier.fillMaxWidth(), label = { Text("اسم صاحب العهدة") }, singleLine = true, enabled = !saving)
        OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("هاتف صاحب العهدة") }, singleLine = true, enabled = !saving)
        OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text("عنوان صاحب العهدة") }, singleLine = true, enabled = !saving)
        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظات صاحب العهدة") }, singleLine = true, enabled = !saving)
        Text("بيانات الجهة", fontWeight = FontWeight.Bold)
        OutlinedTextField(organization, { organization = it; error = false }, Modifier.fillMaxWidth(), label = { Text("اسم الجهة") }, singleLine = true, enabled = !saving)
        OutlinedTextField(organizationPhone, { organizationPhone = it }, Modifier.fillMaxWidth(), label = { Text("هاتف الجهة") }, singleLine = true, enabled = !saving)
        OutlinedTextField(organizationAddress, { organizationAddress = it }, Modifier.fillMaxWidth(), label = { Text("عنوان الجهة") }, singleLine = true, enabled = !saving)
        OutlinedTextField(organizationNotes, { organizationNotes = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظات الجهة") }, singleLine = true, enabled = !saving)
        if (error) Text("تعذر حفظ العهدة. تحقق من البيانات وحاول مرة أخرى.", color = MaterialTheme.colorScheme.error)
    } }, confirmButton = { Button(enabled = name.isNotBlank() && organization.isNotBlank() && !saving, onClick = {
        val custody = CustodyEntity(name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim(), organizationName = organization.trim(), organizationPhone = organizationPhone.trim(), organizationAddress = organizationAddress.trim(), organizationNotes = organizationNotes.trim())
        saving = true; error = false
        scope.launch { runCatching { onSave(custody) }.onSuccess { saving = false; onDismiss() }.onFailure { saving = false; error = true } }
    }) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") } }, dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("إلغاء") } })
}
