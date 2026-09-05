package com.myaccounts.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.data.custody.CustodyFinancialSummary
import com.myaccounts.app.ui.theme.Due
import com.myaccounts.app.ui.theme.Neutral
import com.myaccounts.app.ui.theme.Owed
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class CustodySortOrder { LATEST, ALPHABETICAL }
private val custodyCurrencies = listOf("YER", "SAR", "USD")
private fun money(v: Long): String = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun currencyName(code: String): String = when (code) {
    "YER" -> "الريال اليمني"
    "SAR" -> "الريال السعودي"
    else -> "الدولار الأمريكي"
}
private fun status(v: Long): String = when { v > 0 -> "متبقي لديه"; v < 0 -> "عجز"; else -> "متوازن" }
private fun statusColor(label: String): Color = when (label) {
    "عجز" -> Due
    "متبقي لديه" -> Owed
    else -> Neutral
}
private fun closedStatus(custody: CustodyEntity, ownerBalance: Long, actual: Long?): String = if (!custody.isClosed) "مفتوحة" else when { actual == null || actual == ownerBalance -> "مغلقة ومسواة"; actual > ownerBalance -> "مغلقة مع فائض"; else -> "مغلقة مع عجز" }

@Composable
private fun Modifier.keepFocusedFieldVisible(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return bringIntoViewRequester(requester).onFocusEvent { state ->
        if (state.isFocused) scope.launch { requester.bringIntoView() }
    }
}

@Composable
private fun CompactCurrencyHeader(currencies: List<String>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(70.dp))
        currencies.forEach { code ->
            Text(currencyName(code), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun CompactCustodyMetric(value: Long) {
    val label = status(value)
    val color = statusColor(label)
    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(money(kotlin.math.abs(value)), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyHomeWithArchiveScreen(vm: CustodyViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit, onArchive: () -> Unit, onReports: () -> Unit, onTransfer: () -> Unit) {
    val custodies by vm.custodies.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(CustodySortOrder.LATEST) }
    var search by remember { mutableStateOf("") }
    val sortedCustodies = when (sortOrder) {
        CustodySortOrder.LATEST -> custodies
        CustodySortOrder.ALPHABETICAL -> custodies.sortedBy { it.name.trim().lowercase() }
    }
    val displayedCustodies = sortedCustodies.filter {
        search.isBlank() || it.name.contains(search.trim(), true) || it.organizationName.contains(search.trim(), true)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("العُهَد") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = {
                    TextButton(onClick = onReports) { Text("التقارير", fontWeight = FontWeight.Bold) }
                    IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, "ترتيب العُهَد") }
                    IconButton(onClick = onTransfer, modifier = Modifier.semantics { contentDescription = "النسخ الاحتياطي والاستعادة" }) { Icon(Icons.Default.Backup, null) }
                    IconButton(onClick = onArchive) { Icon(Icons.Default.Archive, "الأرشيف") }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(text = { Text("حسب الأحدث") }, onClick = { sortOrder = CustodySortOrder.LATEST; showSortMenu = false })
                        DropdownMenuItem(text = { Text("حسب الأبجدية") }, onClick = { sortOrder = CustodySortOrder.ALPHABETICAL; showSortMenu = false })
                    }
                }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }, modifier = Modifier.semantics { contentDescription = "إضافة عهدة" }) { Icon(Icons.Default.Add, "إضافة عهدة") } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "بحث في العهد" },
                    label = { Text("بحث في العُهَد") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )
            }
            items(displayedCustodies, key = { it.id }) { custody ->
                val transactions by vm.transactions(custody.id).collectAsState()
                Card(Modifier.fillMaxWidth().clickable { onOpen(custody.id) }, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(custody.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("حامل العهدة: ${custody.name}", style = MaterialTheme.typography.bodySmall)
                                Text("الجهة: ${custody.organizationName}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                Text("التاريخ: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(custody.createdAt))}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            val yerActual = custody.settlementYerActualMinor
                            val yerBook = CustodyFinancialSummary.custodyOwnerBalance(transactions, "YER")
                            AssistChip(onClick = {}, enabled = false, label = { Text(closedStatus(custody, yerBook, yerActual)) })
                        }
                        Spacer(Modifier.height(7.dp))
                        CompactCurrencyHeader(custodyCurrencies)
                        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("العهدة", Modifier.width(70.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            custodyCurrencies.forEach { code ->
                                val balance = CustodyFinancialSummary.custodyOwnerBalance(transactions, code)
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { CompactCustodyMetric(balance) }
                            }
                        }
                    }
                }
            }
            if (displayedCustodies.isEmpty()) item { Text(if (search.isBlank()) "لا توجد عُهَد نشطة." else "لا توجد نتائج مطابقة.") }
        }
    }
    if (adding) CustodyCreateDialog(onDismiss = { adding = false }, onSave = { vm.createAndWait(it) })
}

@Composable
private fun CustodyCreateDialog(onDismiss: () -> Unit, onSave: suspend (CustodyEntity) -> Long) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var organizationPhone by remember { mutableStateOf("") }
    var organizationAddress by remember { mutableStateOf("") }
    var organizationNotes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(Modifier.fillMaxWidth(.95f).fillMaxHeight(.9f).imePadding().navigationBarsPadding(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxSize()) {
                Text("إضافة صاحب عهدة", modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    OutlinedTextField(name, { name = it; error = false }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("اسم صاحب العهدة") }, singleLine = true, enabled = !saving)
                    OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("هاتف صاحب العهدة") }, singleLine = true, enabled = !saving)
                    OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("عنوان صاحب العهدة") }, singleLine = true, enabled = !saving)
                    OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("ملاحظات صاحب العهدة") }, singleLine = true, enabled = !saving)
                    Text("بيانات الجهة", fontWeight = FontWeight.Bold)
                    OutlinedTextField(organization, { organization = it; error = false }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("اسم الجهة") }, singleLine = true, enabled = !saving)
                    OutlinedTextField(organizationPhone, { organizationPhone = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("هاتف الجهة") }, singleLine = true, enabled = !saving)
                    OutlinedTextField(organizationAddress, { organizationAddress = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("عنوان الجهة") }, singleLine = true, enabled = !saving)
                    OutlinedTextField(organizationNotes, { organizationNotes = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("ملاحظات الجهة") }, singleLine = true, enabled = !saving)
                    if (error) Text("تعذر حفظ العهدة. تحقق من البيانات وحاول مرة أخرى.", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(4.dp))
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = name.isNotBlank() && organization.isNotBlank() && !saving, onClick = {
                        val custody = CustodyEntity(name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim(), organizationName = organization.trim(), organizationPhone = organizationPhone.trim(), organizationAddress = organizationAddress.trim(), organizationNotes = organizationNotes.trim())
                        saving = true; error = false
                        scope.launch { runCatching { onSave(custody) }.onSuccess { saving = false; onDismiss() }.onFailure { saving = false; error = true } }
                    }, modifier = Modifier.weight(1f)) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") }
                    OutlinedButton(enabled = !saving, onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                }
            }
        }
    }
}