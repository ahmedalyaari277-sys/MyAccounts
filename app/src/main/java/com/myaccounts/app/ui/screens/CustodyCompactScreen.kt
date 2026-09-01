@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.data.custody.CustodyFinancialSummary
import com.myaccounts.app.data.custody.CustodyPersonEntity
import com.myaccounts.app.data.custody.CustodyTransactionType
import com.myaccounts.app.data.custody.CustodyAccountEntity
import com.myaccounts.app.data.custody.CustodyTransactionEntity
import com.myaccounts.app.ui.viewmodel.CustodyViewModel

private val compactCurrencies = listOf("YER", "SAR", "USD")
private fun compactMoney(v: Long) = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun compactParse(v: String): Long? = runCatching { BigDecimal(v.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }.getOrNull()
private fun compactStatus(v: Long, positive: String, negative: String) = when { v > 0 -> positive; v < 0 -> negative; else -> "متوازن" }

@Composable
fun CustodyCompactScreen(vm: CustodyViewModel, custodyId: Long, onBack: () -> Unit, onPerson: (Long) -> Unit, onOwner: () -> Unit) {
    val custody by vm.custody(custodyId).collectAsState()
    val people by vm.persons(custodyId).collectAsState()
    val accounts by vm.accounts(custodyId).collectAsState()
    val transactions by vm.transactions(custodyId).collectAsState()
    val current = custody ?: return
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(false) }
    var latest by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var settlement by remember { mutableStateOf(false) }
    var addPerson by remember { mutableStateOf(false) }
    var quickOwner by remember { mutableStateOf(false) }
    var quickPerson by remember { mutableStateOf<Long?>(null) }
    val latestByPerson = remember(transactions) { transactions.groupBy { it.personId }.mapValues { it.value.maxOfOrNull { t -> t.transactionDate } ?: 0L } }
    val shown = people
        .let { list -> if (latest) list.sortedByDescending { latestByPerson[it.id] ?: 0L } else list.sortedBy { it.name.trim().lowercase(Locale.getDefault()) } }
        .filter { search.isBlank() || it.name.contains(search.trim(), true) || it.phone.contains(search.trim(), true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.name, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "المزيد") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!current.isClosed) DropdownMenuItem(text = { Text("إغلاق وتسوية العهدة") }, onClick = { menu = false; settlement = true })
                        else DropdownMenuItem(text = { Text("إعادة فتح العهدة") }, onClick = { menu = false; vm.reopenCustody(custodyId) })
                        DropdownMenuItem(text = { Text("أرشفة العهدة") }, onClick = { menu = false; vm.archive(custodyId); onBack() })
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            item { CompactOwnerCard(current, accounts, transactions, people, !current.isClosed, onOwner) { quickOwner = true; quickPerson = null } }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("الأشخاص", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { sort = true }, modifier = Modifier.semantics { contentDescription = "ترتيب الأشخاص" }) { Icon(Icons.Default.Sort, null) }
                        TextButton(enabled = !current.isClosed, onClick = { addPerson = true }, modifier = Modifier.semantics { contentDescription = "إضافة شخص" }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(2.dp)); Text("إضافة") }
                    }
                    DropdownMenu(expanded = sort, onDismissRequest = { sort = false }) {
                        DropdownMenuItem(text = { Text("أحدث عملية") }, onClick = { latest = true; sort = false })
                        DropdownMenuItem(text = { Text("أبجديًا") }, onClick = { latest = false; sort = false })
                    }
                }
            }
            item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().semantics { contentDescription = "بحث في الأشخاص" }, label = { Text("بحث في الأشخاص") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true) }
            items(shown, key = { it.id }) { p -> CompactPersonCard(p, transactions, !current.isClosed, { onPerson(p.id) }) { quickOwner = false; quickPerson = p.id } }
            if (shown.isEmpty()) item { Text(if (search.isBlank()) "لا يوجد أشخاص في هذه العهدة" else "لا توجد نتائج مطابقة", Modifier.padding(10.dp)) }
        }
    }

    if (addPerson) CompactAddPersonDialog(custodyId, people, { addPerson = false }) { vm.addPersonAndWait(custodyId, it) }
    if (quickOwner || quickPerson != null) {
        val owner = quickOwner
        CustodyLedgerOperationDialog(vm, custodyId, if (owner) null else quickPerson, owner, "YER", if (owner) CustodyTransactionType.RECEIVED_FROM_ORG else CustodyTransactionType.PAID_TO_PERSON, null, .94f, { quickOwner = false; quickPerson = null }, { quickOwner = false; quickPerson = null })
    }
    if (settlement) CompactSettlementDialog(vm, current, transactions) { settlement = false }
}

@Composable
private fun CompactOwnerCard(custody: CustodyEntity, accounts: List<CustodyAccountEntity>, transactions: List<CustodyTransactionEntity>, people: List<CustodyPersonEntity>, enabled: Boolean, onClick: () -> Unit, onQuick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = enabled, onClick = onQuick, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Add, null) }
                Column(Modifier.weight(1f)) { Text(custody.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium); Text("حامل العهدة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text("العملة", Modifier.weight(.8f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold); Text("العهدة", Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold); Text("ذمة الجهة", Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold); Text("ذمم الأشخاص", Modifier.weight(1.3f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
            compactCurrencies.forEach { code ->
                val s = CustodyFinancialSummary.ownerDisplay(transactions, accounts, people, code)
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) { Text(code, Modifier.weight(.8f), fontWeight = FontWeight.Bold); CompactMetric(compactMoney(kotlin.math.abs(s.custodyMinor)), compactStatus(s.custodyMinor, "متبقي لديه", "عجز"), Modifier.weight(1.2f)); CompactMetric(compactMoney(kotlin.math.abs(s.organizationDebtMinor)), compactStatus(s.organizationDebtMinor, "مستحق له", "مستحق عليه"), Modifier.weight(1.2f)); CompactMetric(compactMoney(kotlin.math.abs(s.peopleDebtMinor)), compactStatus(s.peopleDebtMinor, "له على الأشخاص", "عليه للأشخاص"), Modifier.weight(1.3f)) }
            }
        }
    }
}

@Composable
private fun CompactPersonCard(person: CustodyPersonEntity, transactions: List<CustodyTransactionEntity>, enabled: Boolean, onClick: () -> Unit, onQuick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(enabled = enabled, onClick = onQuick, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Add, null) }; Column(Modifier.weight(1f)) { Text(person.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium); if (person.phone.isNotBlank()) Text(person.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text("العملة", Modifier.weight(.9f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold); Text("العهدة", Modifier.weight(1.3f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold); Text("الذمة", Modifier.weight(1.3f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
            compactCurrencies.forEach { code -> val b = CustodyFinancialSummary.personCustodyBalance(transactions, person.id, code); val d = CustodyFinancialSummary.personDebt(transactions, person.id, code); Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) { Text(code, Modifier.weight(.9f), fontWeight = FontWeight.Bold); CompactMetric(compactMoney(kotlin.math.abs(b)), compactStatus(b, "لديه", "مستحق له"), Modifier.weight(1.3f)); CompactMetric(compactMoney(kotlin.math.abs(d)), compactStatus(d, "مستحق له", "مستحق عليه"), Modifier.weight(1.3f)) } }
        }
    }
}

@Composable private fun CompactMetric(value: String, status: String, modifier: Modifier) { Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold); Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) } }

@Composable
private fun CompactAddPersonDialog(custodyId: Long, existing: List<CustodyPersonEntity>, onDismiss: () -> Unit, onSave: suspend (CustodyPersonEntity) -> Unit) {
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(Modifier.fillMaxWidth(.95f).imePadding().navigationBarsPadding()) { Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("إضافة شخص", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it; error = null }, Modifier.fillMaxWidth().semantics { contentDescription = "الاسم" }, label = { Text("الاسم") }, singleLine = true, enabled = !saving)
            existing.filter { name.isNotBlank() && it.name.contains(name.trim(), true) }.take(4).forEach { p -> TextButton(enabled = !saving, onClick = { name = p.name; phone = p.phone; address = p.address; notes = p.notes }) { Text(p.name) } }
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth().semantics { contentDescription = "الهاتف" }, label = { Text("الهاتف") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, enabled = !saving)
            OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth().semantics { contentDescription = "العنوان" }, label = { Text("العنوان") }, singleLine = true, enabled = !saving)
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth().semantics { contentDescription = "الملاحظات" }, label = { Text("الملاحظات") }, minLines = 3, enabled = !saving)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(enabled = name.isNotBlank() && !saving, onClick = { saving = true; scope.launch { runCatching { onSave(CustodyPersonEntity(custodyId = custodyId, name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim())) }.onSuccess { saving = false; onDismiss() }.onFailure { saving = false; error = it.message ?: "تعذر حفظ الشخص" } } }, Modifier.weight(1f)) { Text("حفظ") }; OutlinedButton(enabled = !saving, onClick = onDismiss, Modifier.weight(1f)) { Text("إلغاء") } }
        } }
    }
}

@Composable
private fun CompactSettlementDialog(vm: CustodyViewModel, custody: CustodyEntity, transactions: List<CustodyTransactionEntity>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val books = remember(transactions) { compactCurrencies.map { CustodyFinancialSummary.custodyOwnerBalance(transactions, it) } }
    var yer by remember { mutableStateOf(compactMoney(kotlin.math.max(books[0], 0L))) }
    var sar by remember { mutableStateOf(compactMoney(kotlin.math.max(books[1], 0L))) }
    var usd by remember { mutableStateOf(compactMoney(kotlin.math.max(books[2], 0L))) }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val actuals = listOf(compactParse(yer), compactParse(sar), compactParse(usd))
    val orgDebts = compactCurrencies.map { CustodyFinancialSummary.ownerOrganizationDebt(transactions, it) }
    val hasDifference = books.indices.any { i -> actuals[i]?.let { it != books[i] } == true }
    val eligible = actuals.all { it != null } && orgDebts.all { it == 0L } && (!hasDifference || notes.trim().isNotBlank()) && !saving
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(Modifier.fillMaxWidth(.95f).imePadding().navigationBarsPadding()) { Column(Modifier.fillMaxWidth().heightIn(max = 700.dp).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("إغلاق وتسوية العهدة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("أدخل الموجود الفعلي فقط. الرصيد الدفتري والفرق والعجز والفائض والحالة تظهر تلقائيًا.", style = MaterialTheme.typography.bodySmall)
            SettlementCurrencyRow("YER", books[0], yer, { yer = it }, !saving)
            SettlementCurrencyRow("SAR", books[1], sar, { sar = it }, !saving)
            SettlementCurrencyRow("USD", books[2], usd, { usd = it }, !saving)
            OutlinedTextField(notes, { notes = it; error = null }, Modifier.fillMaxWidth().semantics { contentDescription = "ملاحظات التسوية" }, label = { Text("الملاحظات / سبب العجز أو الفائض") }, minLines = 3, enabled = !saving)
            if (!actuals.all { it != null }) Text("أدخل الموجود الفعلي لكل العملات.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            if (orgDebts.any { it != 0L }) Text("لا يمكن الإغلاق قبل تسوية ذمة حامل العهدة مع الجهة.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            if (hasDifference && notes.trim().isBlank()) Text("اكتب سبب العجز أو الفائض في الملاحظات.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = eligible, onClick = { saving = true; scope.launch { runCatching { vm.closeCustodyAndWait(custody.id, actuals[0]!!, actuals[1]!!, actuals[2]!!, notes.trim()) }.onSuccess { saving = false; onDismiss() }.onFailure { saving = false; error = it.message ?: "تعذر الإغلاق" } } }, Modifier.weight(1f)) { Text(if (saving) "جارٍ الحفظ…" else "إغلاق وتسوية") }
                OutlinedButton(enabled = !saving, onClick = onDismiss, Modifier.weight(1f)) { Text("إلغاء") }
            }
        } }
    }
}

@Composable
private fun SettlementCurrencyRow(code: String, book: Long, actualText: String, onActual: (String) -> Unit, enabled: Boolean) {
    val actual = compactParse(actualText)
    val diff = actual?.minus(book)
    val deficit = if (diff != null) maxOf(-diff, 0L) else 0L
    val surplus = if (diff != null) maxOf(diff, 0L) else 0L
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(v3CurrencyName(code), fontWeight = FontWeight.Bold); Text(code, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        AutoSettlementLine("الرصيد الدفتري", compactMoney(kotlin.math.abs(book)), if (book >= 0) "متبقي" else "عجز")
        OutlinedTextField(actualText, onActual, Modifier.fillMaxWidth().semantics { contentDescription = "الموجود الفعلي $code" }, label = { Text("الموجود الفعلي") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = enabled)
        if (actual != null) {
            AutoSettlementLine("الفرق", compactMoney(kotlin.math.abs(diff ?: 0L)), when { diff!! > 0 -> "فائض"; diff < 0 -> "عجز"; else -> "متوازن" })
            AutoSettlementLine("العجز", compactMoney(deficit), if (deficit > 0) "يحتاج سببًا" else "لا يوجد")
            AutoSettlementLine("الفائض", compactMoney(surplus), if (surplus > 0) "موجود" else "لا يوجد")
        }
    } }
}

@Composable private fun AutoSettlementLine(title: String, value: String, status: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(value, fontWeight = FontWeight.Bold); Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
private fun v3CurrencyName(code: String) = when(code) { "YER" -> "ريال يمني"; "SAR" -> "ريال سعودي"; else -> "دولار أمريكي" }
