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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.myaccounts.app.data.custody.CustodyTransactionType
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

private val compactCurrencies = listOf("YER", "SAR", "USD")
private fun compactMoney(v: Long) = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun compactParse(v: String): Long? = runCatching { BigDecimal(v.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }.getOrNull()
private fun status(v: Long, positive: String, negative: String) = when { v > 0 -> positive; v < 0 -> negative; else -> "متوازن" }

@Composable
fun CustodyCompactScreen(vm: CustodyViewModel, custodyId: Long, onBack: () -> Unit, onPerson: (Long) -> Unit, onOwner: () -> Unit) {
    val custody by vm.custody(custodyId).collectAsState()
    val people by vm.persons(custodyId).collectAsState()
    val accounts by vm.accounts(custodyId).collectAsState()
    val transactions by vm.transactions(custodyId).collectAsState()
    val current = custody ?: return
    var search by remember { mutableStateOf("") }
    var sortOpen by remember { mutableStateOf(false) }
    var latestFirst by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var settlementOpen by remember { mutableStateOf(false) }
    var addPerson by remember { mutableStateOf(false) }
    val latestByPerson = remember(transactions) { transactions.groupBy { it.personId }.mapValues { it.value.maxOfOrNull(CustodyTransactionEntity::transactionDate) ?: 0L } }
    val shown = remember(people, search, latestFirst, latestByPerson) {
        people.sortedWith(if (latestFirst) compareByDescending<CustodyPersonEntity> { latestByPerson[it.id] ?: 0L } else compareBy { it.name.trim().lowercase(Locale.getDefault()) })
            .filter { search.isBlank() || it.name.contains(search.trim(), true) || it.phone.contains(search.trim(), true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.name, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "المزيد") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (!current.isClosed) DropdownMenuItem(text = { Text("إغلاق وتسوية العهدة") }, onClick = { menuOpen = false; settlementOpen = true })
                        else DropdownMenuItem(text = { Text("إعادة فتح العهدة") }, onClick = { menuOpen = false; vm.reopenCustody(custodyId) })
                        DropdownMenuItem(text = { Text("أرشفة العهدة") }, onClick = { menuOpen = false; vm.archive(custodyId); onBack() })
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 6.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            contentPadding = PaddingValues(bottom = 14.dp)
        ) {
            item { CompactOwnerCard(current, accounts, transactions, people, !current.isClosed, onOwner) }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("الأشخاص", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { sortOpen = true }, modifier = Modifier.size(36.dp).semantics { contentDescription = "ترتيب الأشخاص" }) { Icon(Icons.Default.Sort, null) }
                    TextButton(enabled = !current.isClosed, onClick = { addPerson = true }, modifier = Modifier.height(36.dp).semantics { contentDescription = "إضافة شخص" }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(2.dp)); Text("إضافة") }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        DropdownMenuItem(text = { Text("أحدث عملية") }, onClick = { latestFirst = true; sortOpen = false })
                        DropdownMenuItem(text = { Text("أبجديًا") }, onClick = { latestFirst = false; sortOpen = false })
                    }
                }
            }
            item {
                OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("بحث في الأشخاص") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.semantics { contentDescription = "بحث في الأشخاص" })
            }
            items(shown, key = { it.id }) { person -> CompactPersonCard(person, transactions, !current.isClosed) { onPerson(person.id) } }
            if (shown.isEmpty()) item { Text(if (search.isBlank()) "لا يوجد أشخاص في هذه العهدة" else "لا توجد نتائج مطابقة") }
        }
    }

    if (addPerson) CompactAddPersonDialog(custodyId, people, { addPerson = false }) { vm.addPersonAndWait(custodyId, it) }
    if (settlementOpen) CompactSettlementDialog(vm, current, transactions) { settlementOpen = false }
}

@Composable
private fun CompactOwnerCard(custody: CustodyEntity, accounts: List<CustodyAccountEntity>, transactions: List<CustodyTransactionEntity>, people: List<CustodyPersonEntity>, enabled: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(custody.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text("حامل العهدة • ${custody.organizationName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Text(if (custody.isClosed) "مغلقة" else "مفتوحة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.fillMaxWidth()) {
                HeaderCell("العملة", .72f); HeaderCell("العهدة", 1.08f); HeaderCell("ذمة الجهة", 1.18f); HeaderCell("ذمم الأشخاص", 1.3f)
            }
            compactCurrencies.forEach { code ->
                val s = CustodyFinancialSummary.ownerDisplay(transactions, accounts, people, code)
                Row(Modifier.fillMaxWidth()) {
                    Cell(code, .72f, bold = true)
                    Metric(compactMoney(kotlin.math.abs(s.custodyMinor)), status(s.custodyMinor, "متبقي", "عجز"), 1.08f)
                    Metric(compactMoney(kotlin.math.abs(s.organizationDebtMinor)), status(s.organizationDebtMinor, "مستحق له", "مستحق عليه"), 1.18f)
                    Metric(compactMoney(kotlin.math.abs(s.peopleDebtMinor)), status(s.peopleDebtMinor, "له على الأشخاص", "عليه للأشخاص"), 1.3f)
                }
            }
        }
    }
}

@Composable private fun HeaderCell(text: String, weight: Float) { Text(text, Modifier.weight(weight).padding(vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1) }
@Composable private fun Cell(text: String, weight: Float, bold: Boolean = false) { Text(text, Modifier.weight(weight).padding(vertical = 2.dp), style = MaterialTheme.typography.bodySmall, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, maxLines = 1) }
@Composable private fun Metric(value: String, label: String, weight: Float) { Column(Modifier.weight(weight).padding(vertical = 1.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) } }

@Composable
private fun CompactPersonCard(person: CustodyPersonEntity, transactions: List<CustodyTransactionEntity>, enabled: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(person.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 1); if (person.phone.isNotBlank()) Text(person.phone, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
            Row(Modifier.fillMaxWidth()) { HeaderCell("العملة", .8f); HeaderCell("العهدة", 1.15f); HeaderCell("الذمة", 1.15f) }
            compactCurrencies.forEach { code ->
                val b = CustodyFinancialSummary.personCustodyBalance(transactions, person.id, code)
                val d = CustodyFinancialSummary.personDebt(transactions, person.id, code)
                Row(Modifier.fillMaxWidth()) { Cell(code, .8f, true); Metric(compactMoney(kotlin.math.abs(b)), status(b, "لديه", "مستحق له"), 1.15f); Metric(compactMoney(kotlin.math.abs(d)), status(d, "مستحق له", "مستحق عليه"), 1.15f) }
            }
        }
    }
}

@Composable
private fun CompactAddPersonDialog(custodyId: Long, existing: List<CustodyPersonEntity>, onDismiss: () -> Unit, onSave: suspend (CustodyPersonEntity) -> Unit) {
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(Modifier.fillMaxWidth(.96f).fillMaxHeight(.82f).imePadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("إضافة شخص", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(name, { name = it; error = null }, Modifier.fillMaxWidth().semantics { contentDescription = "الاسم" }, label = { Text("الاسم") }, singleLine = true, enabled = !saving)
                existing.filter { name.isNotBlank() && it.name.contains(name.trim(), true) }.take(4).forEach { p -> TextButton(enabled = !saving, onClick = { name = p.name; phone = p.phone; address = p.address; notes = p.notes }, Modifier.fillMaxWidth()) { Text(p.name) } }
                OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth().semantics { contentDescription = "الهاتف" }, label = { Text("الهاتف") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, enabled = !saving)
                OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth().semantics { contentDescription = "العنوان" }, label = { Text("العنوان") }, singleLine = true, enabled = !saving)
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth().semantics { contentDescription = "الملاحظات" }, label = { Text("الملاحظات") }, minLines = 2, enabled = !saving)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(enabled = name.isNotBlank() && !saving, onClick = { saving = true; scope.launch { runCatching { onSave(CustodyPersonEntity(custodyId = custodyId, name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim())) }.onSuccess { saving = false; onDismiss() }.onFailure { saving = false; error = it.message ?: "تعذر حفظ الشخص" } } }, Modifier.weight(1f)) { Text("حفظ") }
                    OutlinedButton(enabled = !saving, onClick = onDismiss, Modifier.weight(1f)) { Text("إلغاء") }
                }
            }
        }
    }
}

@Composable
private fun CompactSettlementDialog(vm: CustodyViewModel, custody: CustodyEntity, transactions: List<CustodyTransactionEntity>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val books = remember(transactions) { compactCurrencies.map { CustodyFinancialSummary.custodyOwnerBalance(transactions, it) } }
    var yer by remember { mutableStateOf("") }; var sar by remember { mutableStateOf("") }; var usd by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }; var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val actuals = listOf(compactParse(yer), compactParse(sar), compactParse(usd))
    val differences = books.indices.map { i -> actuals[i]?.let { it - books[i] } }
    val hasDifference = differences.any { it != null && it != 0L }
    val orgDebts = compactCurrencies.map { CustodyFinancialSummary.ownerOrganizationDebt(transactions, it) }
    val eligible = actuals.all { it != null } && orgDebts.all { it == 0L } && (!hasDifference || notes.trim().isNotBlank()) && !saving
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(Modifier.fillMaxWidth(.96f).fillMaxHeight(.86f).imePadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("إغلاق وتسوية العهدة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("أدخل الموجود الفعلي فقط. الرصيد والفرق والعجز والفائض والحالة تُحسب تلقائيًا.", style = MaterialTheme.typography.bodySmall)
                SettlementInput("YER", books[0], yer, { yer = it }, !saving)
                SettlementInput("SAR", books[1], sar, { sar = it }, !saving)
                SettlementInput("USD", books[2], usd, { usd = it }, !saving)
                OutlinedTextField(notes, { notes = it; error = null }, Modifier.fillMaxWidth().semantics { contentDescription = "ملاحظات التسوية" }, label = { Text(if (hasDifference) "الملاحظات / سبب العجز أو الفائض" else "الملاحظات (اختياري)") }, minLines = 2, enabled = !saving)
                if (orgDebts.any { it != 0L }) Text("لا يمكن الإغلاق قبل تسوية ذمة حامل العهدة مع الجهة.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                if (hasDifference && notes.trim().isBlank()) Text("اكتب سبب العجز أو الفائض في الملاحظات.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(enabled = eligible, onClick = { keyboard?.hide(); saving = true; scope.launch { runCatching { vm.closeCustodyAndWait(custody.id, actuals[0]!!, actuals[1]!!, actuals[2]!!, notes.trim()) }.onSuccess { saving = false; onDismiss() }.onFailure { saving = false; error = it.message ?: "تعذر الإغلاق" } } }, Modifier.weight(1f)) { Text(if (saving) "جارٍ الحفظ…" else "إغلاق وتسوية") }
                    OutlinedButton(enabled = !saving, onClick = onDismiss, Modifier.weight(1f)) { Text("إلغاء") }
                }
            }
        }
    }
}

@Composable
private fun SettlementInput(code: String, book: Long, actualText: String, onActual: (String) -> Unit, enabled: Boolean) {
    val focusRequester = remember { FocusRequester() }
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) { }
    val actual = compactParse(actualText)
    val diff = actual?.minus(book)
    val deficit = if (diff != null) maxOf(-diff, 0L) else 0L
    val surplus = if (diff != null) maxOf(diff, 0L) else 0L
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(v3CurrencyName(code), fontWeight = FontWeight.Bold); Text(code, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
            AutoLine("الرصيد الدفتري", compactMoney(kotlin.math.abs(book)), if (book >= 0) "الرصيد المتبقي" else "مستحق عليه")
            OutlinedTextField(
                actualText,
                onActual,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { }
                    .semantics { contentDescription = "الموجود الفعلي $code" },
                label = { Text("الموجود الفعلي") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                enabled = enabled
            )
            if (actual != null) {
                AutoLine("الفرق", compactMoney(kotlin.math.abs(diff ?: 0L)), when { diff!! > 0 -> "فائض"; diff < 0 -> "عجز"; else -> "متوازن" })
                if (deficit > 0) AutoLine("العجز", compactMoney(deficit), "يُذكر السبب في الملاحظات")
                if (surplus > 0) AutoLine("الفائض", compactMoney(surplus), "يُذكر السبب في الملاحظات")
                if (deficit == 0L && surplus == 0L) AutoLine("الحالة", "0", "متوازن")
            }
        }
    }
}

@Composable private fun AutoLine(title: String, value: String, state: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title); Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { Text(value, fontWeight = FontWeight.Bold); Text(state, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
private fun v3CurrencyName(code: String) = when (code) { "YER" -> "ريال يمني"; "SAR" -> "ريال سعودي"; else -> "دولار أمريكي" }
