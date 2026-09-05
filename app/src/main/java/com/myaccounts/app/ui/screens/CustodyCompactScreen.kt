@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myaccounts.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myaccounts.app.data.custody.CustodyAccountEntity
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.data.custody.CustodyFinancialSummary
import com.myaccounts.app.data.custody.CustodyPersonEntity
import com.myaccounts.app.data.custody.CustodyTransactionEntity
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

private val currencies = listOf("YER", "SAR", "USD")
private fun money(v: Long) = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun parse(v: String): Long? = runCatching { BigDecimal(v.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }.getOrNull()
private fun state(v: Long, positive: String, negative: String) = when { v > 0 -> positive; v < 0 -> negative; else -> "متوازن" }

@Composable
fun CustodyCompactScreen(vm: CustodyViewModel, custodyId: Long, onBack: () -> Unit, onPerson: (Long) -> Unit, onOwner: () -> Unit) {
    val custody by vm.custody(custodyId).collectAsState()
    val people by vm.persons(custodyId).collectAsState()
    val accounts by vm.accounts(custodyId).collectAsState()
    val transactions by vm.transactions(custodyId).collectAsState()
    val current = custody ?: return
    var search by remember { mutableStateOf("") }
    var latestFirst by remember { mutableStateOf(true) }
    var sortOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var settlementOpen by remember { mutableStateOf(false) }
    var addPerson by remember { mutableStateOf(false) }
    val latestByPerson = remember(transactions) { transactions.groupBy { it.personId }.mapValues { it.value.maxOfOrNull(CustodyTransactionEntity::transactionDate) ?: 0L } }
    val shown = remember(people, search, latestFirst, latestByPerson) {
        people.filter { search.isBlank() || it.name.contains(search.trim(), true) || it.phone.contains(search.trim(), true) }
            .sortedWith(if (latestFirst) compareByDescending<CustodyPersonEntity> { latestByPerson[it.id] ?: 0L } else compareBy { it.name.trim().lowercase(Locale.getDefault()) })
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(current.name, fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
            actions = {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "المزيد") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (!current.isClosed) DropdownMenuItem(text = { Text("إغلاق وتسوية العهدة") }, onClick = { menuOpen = false; settlementOpen = true })
                    else DropdownMenuItem(text = { Text("إعادة فتح العهدة") }, onClick = { menuOpen = false; vm.reopenCustody(custodyId) })
                    if (!current.isArchived) DropdownMenuItem(text = { Text("أرشفة العهدة") }, onClick = { menuOpen = false; vm.archive(custodyId); onBack() })
                }
            }
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            item { OwnerCard(current, accounts, transactions, people, onOwner) }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("الأشخاص", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { sortOpen = true }, modifier = Modifier.size(34.dp).semantics { contentDescription = "ترتيب الأشخاص" }) { Icon(Icons.Default.Sort, null) }
                    TextButton(enabled = !current.isClosed, onClick = { addPerson = true }, modifier = Modifier.height(34.dp).semantics { contentDescription = "إضافة شخص" }) { Icon(Icons.Default.Add, null); Text("إضافة") }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        DropdownMenuItem(text = { Text("أحدث عملية") }, onClick = { latestFirst = true; sortOpen = false })
                        DropdownMenuItem(text = { Text("أبجديًا") }, onClick = { latestFirst = false; sortOpen = false })
                    }
                }
            }
            item { OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "بحث في الأشخاص" }, label = { Text("بحث في الأشخاص") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true) }
            items(shown, key = { it.id }) { person -> PersonCard(person, transactions) { onPerson(person.id) } }
            if (shown.isEmpty()) item { Text(if (search.isBlank()) "لا يوجد أشخاص في هذه العهدة" else "لا توجد نتائج مطابقة") }
        }
    }
    if (addPerson) AddPersonDialog(custodyId, people, { addPerson = false }) { vm.addPersonAndWait(custodyId, it) }
    if (settlementOpen) SettlementDialog(vm, current, transactions) { settlementOpen = false }
}

@Composable private fun RowScope.Header(text: String, weight: Float) { Text(text, Modifier.weight(weight).padding(vertical = 1.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1) }
@Composable private fun RowScope.Col(text: String, weight: Float, bold: Boolean = false) { Text(text, Modifier.weight(weight).padding(vertical = 1.dp), style = if (bold) MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodySmall, maxLines = 1) }
@Composable private fun RowScope.Metric(value: String, statusText: String, weight: Float) { Column(Modifier.weight(weight), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1); Text(statusText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) } }

@Composable private fun OwnerCard(c: CustodyEntity, accounts: List<CustodyAccountEntity>, tx: List<CustodyTransactionEntity>, people: List<CustodyPersonEntity>, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(c.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1); Text("حامل العهدة • ${c.organizationName}", style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text(if (c.isClosed) "مغلقة" else "مفتوحة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            Row(Modifier.fillMaxWidth()) { Header("العملة", .7f); Header("العهدة", 1f); Header("ذمة الجهة", 1.15f); Header("ذمم الأشخاص", 1.25f) }
            currencies.forEach { code -> val s = CustodyFinancialSummary.ownerDisplay(tx, accounts, people, code); Row(Modifier.fillMaxWidth()) { Col(code, .7f, true); Metric(money(kotlin.math.abs(s.custodyMinor)), state(s.custodyMinor, "متبقي", "عجز"), 1f); Metric(money(kotlin.math.abs(s.organizationDebtMinor)), state(s.organizationDebtMinor, "مستحق له", "مستحق عليه"), 1.15f); Metric(money(kotlin.math.abs(s.peopleDebtMinor)), state(s.peopleDebtMinor, "له على الأشخاص", "عليه للأشخاص"), 1.25f) } }
        }
    }
}

@Composable private fun PersonCard(p: CustodyPersonEntity, tx: List<CustodyTransactionEntity>, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            if (p.phone.isNotBlank()) Text(p.phone, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth()) { Header("العملة", .7f); Header("العهدة", 1.1f); Header("الذمة", 1.1f) }
            currencies.forEach { code -> val b = CustodyFinancialSummary.personCustodyBalance(tx, p.id, code); val d = CustodyFinancialSummary.personDebt(tx, p.id, code); Row(Modifier.fillMaxWidth()) { Col(code, .7f, true); Metric(money(kotlin.math.abs(b)), state(b, "لديه", "مستحق له"), 1.1f); Metric(money(kotlin.math.abs(d)), state(d, "مستحق له", "مستحق عليه"), 1.1f) } }
        }
    }
}

@Composable private fun AddPersonDialog(custodyId: Long, existing: List<CustodyPersonEntity>, onDismiss: () -> Unit, onSave: suspend (CustodyPersonEntity) -> Unit) {
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(Modifier.fillMaxWidth(.94f).fillMaxHeight(.78f).imePadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("إضافة شخص", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = name, onValueChange = { name = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("الاسم") }, singleLine = true, enabled = !saving)
                existing.filter { name.isNotBlank() && it.name.contains(name.trim(), true) }.take(3).forEach { p -> TextButton(onClick = { name = p.name; phone = p.phone; address = p.address; notes = p.notes }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(p.name) } }
                OutlinedTextField(value = phone, onValueChange = { phone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("الهاتف") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, enabled = !saving)
                OutlinedTextField(value = address, onValueChange = { address = it }, modifier = Modifier.fillMaxWidth(), label = { Text("العنوان") }, singleLine = true, enabled = !saving)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("الملاحظات") }, minLines = 2, enabled = !saving)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Button(onClick = { saving = true; scope.launch { runCatching { onSave(CustodyPersonEntity(custodyId = custodyId, name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim())) }.onSuccess { saving = false; onDismiss() }.onFailure { saving = false; error = it.message ?: "تعذر الحفظ" } } }, enabled = name.isNotBlank() && !saving, modifier = Modifier.weight(1f)) { Text("حفظ") }; OutlinedButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.weight(1f)) { Text("إلغاء") } }
            }
        }
    }
}

@Composable private fun SettlementDialog(vm: CustodyViewModel, custody: CustodyEntity, tx: List<CustodyTransactionEntity>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope(); val keyboard = LocalSoftwareKeyboardController.current
    val books = remember(tx) { currencies.map { CustodyFinancialSummary.custodyOwnerBalance(tx, it) } }
    var yer by remember { mutableStateOf("") }; var sar by remember { mutableStateOf("") }; var usd by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val actuals = listOf(parse(yer), parse(sar), parse(usd)); val diffs = books.indices.map { i -> actuals[i]?.minus(books[i]) }; val hasDiff = diffs.any { it != null && it != 0L }; val orgDebts = currencies.map { CustodyFinancialSummary.ownerOrganizationDebt(tx, it) }
    val canSave = actuals.all { it != null } && orgDebts.all { it == 0L } && (!hasDiff || notes.trim().isNotBlank()) && !saving
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(Modifier.fillMaxWidth(.94f).fillMaxHeight(.86f).imePadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("إغلاق وتسوية العهدة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("أدخل الموجود الفعلي فقط؛ الرصيد والفرق والعجز والفائض والحالة تظهر تلقائيًا.", style = MaterialTheme.typography.bodySmall)
                SettlementRow("YER", books[0], yer, { yer = it }, !saving)
                SettlementRow("SAR", books[1], sar, { sar = it }, !saving)
                SettlementRow("USD", books[2], usd, { usd = it }, !saving)
                OutlinedTextField(value = notes, onValueChange = { notes = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text(if (hasDiff) "الملاحظات / سبب العجز أو الفائض" else "الملاحظات (اختياري)") }, minLines = 2, enabled = !saving)
                if (orgDebts.any { it != 0L }) Text("لا يمكن الإغلاق قبل تسوية ذمة الحامل مع الجهة.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                if (hasDiff && notes.trim().isBlank()) Text("اكتب سبب الفرق في الملاحظات.", color = MaterialTheme.colorScheme.error)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Button(onClick = { keyboard?.hide(); saving = true; scope.launch { runCatching { vm.closeCustodyAndWait(custody.id, actuals[0]!!, actuals[1]!!, actuals[2]!!, notes.trim()) }.onSuccess { saving = false; onDismiss() }.onFailure { saving = false; error = it.message ?: "تعذر الإغلاق" } } }, enabled = canSave, modifier = Modifier.weight(1f)) { Text(if (saving) "جارٍ الحفظ…" else "إغلاق وتسوية") }; OutlinedButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.weight(1f)) { Text("إلغاء") } }
            }
        }
    }
}

@Composable private fun SettlementRow(code: String, book: Long, actual: String, onActual: (String) -> Unit, enabled: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(when(code){"YER"->"ريال يمني";"SAR"->"ريال سعودي";else->"دولار أمريكي"}, fontWeight = FontWeight.Bold); Text(code, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
            AutoLine("الرصيد الدفتري", money(kotlin.math.abs(book)), if (book >= 0) "الرصيد المتبقي" else "مستحق عليه")
            OutlinedTextField(value = actual, onValueChange = onActual, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "الموجود الفعلي $code" }, label = { Text("الموجود الفعلي") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = enabled)
            parse(actual)?.let { a -> val d = a - book; AutoLine("الفرق", money(kotlin.math.abs(d)), when { d > 0 -> "فائض"; d < 0 -> "عجز"; else -> "متوازن" }); if (d != 0L) AutoLine(if (d < 0) "العجز" else "الفائض", money(kotlin.math.abs(d)), "يُذكر السبب في الملاحظات") }
        }
    }
}

@Composable private fun AutoLine(a: String, b: String, c: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(a); Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { Text(b, fontWeight = FontWeight.Bold); Text(c, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
