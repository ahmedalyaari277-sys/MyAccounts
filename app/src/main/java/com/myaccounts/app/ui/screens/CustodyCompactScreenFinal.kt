@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myaccounts.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myaccounts.app.data.custody.CustodyAccountEntity
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.data.custody.CustodyFinancialSummary
import com.myaccounts.app.data.custody.CustodyPersonEntity
import com.myaccounts.app.data.custody.CustodyTransactionEntity
import com.myaccounts.app.data.custody.CustodyTransactionType
import com.myaccounts.app.ui.theme.Due
import com.myaccounts.app.ui.theme.Neutral
import com.myaccounts.app.ui.theme.Owed
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

private val finalCurrencies = listOf("YER", "SAR", "USD")
private fun finalMoney(v: Long): String = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun finalParse(v: String): Long? = runCatching { BigDecimal(v.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }.getOrNull()
private fun finalStatus(v: Long, positive: String, negative: String) = when { v > 0 -> positive; v < 0 -> negative; else -> "متوازن" }
private fun finalCurrencyName(code: String): String = when (code) {
    "YER" -> "الريال اليمني"
    "SAR" -> "الريال السعودي"
    else -> "الدولار الأمريكي"
}
private fun finalStatusColor(status: String): Color = when (status) {
    "عجز", "مستحق عليه", "عليه للأشخاص" -> Due
    "متبقي لديه", "لديه", "مستحق له", "له على الأطراف" -> Owed
    else -> Neutral
}

@Composable
private fun Modifier.keepFocusedFieldVisible(): Modifier {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusEvent { state ->
            if (state.isFocused) {
                scope.launch { bringIntoViewRequester.bringIntoView() }
            }
        }
}

@Composable
fun CustodyCompactScreenFinal(vm: CustodyViewModel, custodyId: Long, onBack: () -> Unit, onPerson: (Long) -> Unit, onOwner: () -> Unit) {
    val custody by vm.custody(custodyId).collectAsState()
    val people by vm.persons(custodyId).collectAsState()
    val accounts by vm.accounts(custodyId).collectAsState()
    val transactions by vm.transactions(custodyId).collectAsState()
    val current = custody ?: return
    var search by remember { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    var latestFirst by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var settlement by remember { mutableStateOf(false) }
    var addPerson by remember { mutableStateOf(false) }
    var quickOwner by remember { mutableStateOf(false) }
    var quickPerson by remember { mutableStateOf<Long?>(null) }
    val latestByPerson = remember(transactions) { transactions.groupBy { it.personId }.mapValues { it.value.maxOfOrNull { t -> t.transactionDate } ?: 0L } }
    val shown = people.let { rows -> if (latestFirst) rows.sortedByDescending { latestByPerson[it.id] ?: 0L } else rows.sortedBy { it.name.trim().lowercase(Locale.getDefault()) } }
        .filter { search.isBlank() || it.name.contains(search.trim(), true) || it.phone.contains(search.trim(), true) }

    Scaffold(
        modifier = Modifier.semantics { contentDescription = "شاشة تفاصيل العهدة" },
        topBar = {
            TopAppBar(
                title = { Text(current.name, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = {
                    if (!current.isClosed) {
                        IconButton(
                            onClick = { addPerson = true },
                            modifier = Modifier.semantics { contentDescription = "إضافة طرف" }
                        ) { Icon(Icons.Default.Add, contentDescription = "إضافة طرف") }
                    }
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item { FinalOwnerCard(current, accounts, transactions, people, !current.isClosed, onOwner) { quickOwner = true; quickPerson = null } }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("الأطراف", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { sortMenu = true }, modifier = Modifier.semantics { contentDescription = "ترتيب الأطراف" }) { Icon(Icons.Default.Sort, null) }
                        TextButton(enabled = !current.isClosed, onClick = { addPerson = true }, modifier = Modifier.semantics { contentDescription = "إضافة طرف" }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(2.dp)); Text("إضافة") }
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        DropdownMenuItem(text = { Text("أحدث عملية") }, onClick = { latestFirst = true; sortMenu = false })
                        DropdownMenuItem(text = { Text("أبجديًا") }, onClick = { latestFirst = false; sortMenu = false })
                    }
                }
            }
            item { OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "بحث في الأطراف" }, label = { Text("بحث في الأطراف") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true) }
            item {
                val categories = transactions.map { it.categoryName.trim() }.filter { it.isNotBlank() }.distinct().sorted()
                if (categories.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("بنود العهدة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            categories.forEach { category -> SuggestionChip(onClick = {}, label = { Text(category) }) }
                        }
                    }
                }
            }
            items(shown, key = { it.id }) { person -> FinalPersonCard(person, transactions, !current.isClosed, { onPerson(person.id) }) { quickOwner = false; quickPerson = person.id } }
            if (shown.isEmpty()) item { Text(if (search.isBlank()) "لا يوجد أطراف في هذه العهدة" else "لا توجد نتائج مطابقة", Modifier.padding(10.dp)) }
        }
    }

    if (addPerson) FinalAddPersonDialog(custodyId, people, { addPerson = false }) { vm.addPersonAndWait(custodyId, it) }
    if (quickOwner || quickPerson != null) {
        val owner = quickOwner
        CustodyLedgerOperationDialog(
            vm = vm,
            custodyId = custodyId,
            personId = if (owner) null else quickPerson,
            owner = owner,
            defaultCurrency = "YER",
            initialType = if (owner) CustodyTransactionType.RECEIVED_FROM_ORG else CustodyTransactionType.PAID_TO_PERSON,
            transaction = null,
            dialogWidth = .94f,
            onDismiss = { quickOwner = false; quickPerson = null },
            onFinished = { quickOwner = false; quickPerson = null }
        )
    }
    if (settlement) FinalSettlementDialog(vm, current, transactions) { settlement = false }
}

@Composable
private fun FinalOwnerCard(custody: CustodyEntity, accounts: List<CustodyAccountEntity>, transactions: List<CustodyTransactionEntity>, people: List<CustodyPersonEntity>, enabled: Boolean, onClick: () -> Unit, onQuick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = enabled, onClick = onQuick, modifier = Modifier.size(40.dp).semantics { contentDescription = "إضافة عملية سريعة" }) { Icon(Icons.Default.Add, null) }
                Column(Modifier.weight(1f)) { Text(custody.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium); Text("حامل العهدة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            HorizontalDivider()
            CompactCurrencyHeader(finalCurrencies)
            finalCurrencies.forEach { code ->
                val s = CustodyFinancialSummary.ownerDisplay(transactions, accounts, people, code)
                CompactMetricRow(
                    label = "العهدة",
                    cells = listOf(
                        if (code == "YER") CompactMetricData(finalMoney(kotlin.math.abs(s.custodyMinor)), finalStatus(s.custodyMinor, "متبقي لديه", "عجز")) else null,
                        if (code == "SAR") CompactMetricData(finalMoney(kotlin.math.abs(s.custodyMinor)), finalStatus(s.custodyMinor, "متبقي لديه", "عجز")) else null,
                        if (code == "USD") CompactMetricData(finalMoney(kotlin.math.abs(s.custodyMinor)), finalStatus(s.custodyMinor, "متبقي لديه", "عجز")) else null
                    ),
                    visibleOnlyForCurrency = code
                )
            }
            CompactOwnerRows(transactions, accounts, people)
        }
    }
}

private data class CompactMetricData(val value: String, val status: String)

@Composable
private fun CompactCurrencyHeader(currencies: List<String>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(70.dp))
        currencies.forEach { code ->
            Text(
                finalCurrencyName(code),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CompactMetricRow(label: String, cells: List<CompactMetricData?>, visibleOnlyForCurrency: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(70.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        cells.forEach { cell ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (cell != null) FinalMetric(cell.value, cell.status, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CompactOwnerRows(custodyTransactions: List<CustodyTransactionEntity>, accounts: List<CustodyAccountEntity>, people: List<CustodyPersonEntity>) {
    val summaries = finalCurrencies.map { code -> CustodyFinancialSummary.ownerDisplay(custodyTransactions, accounts, people, code) }
    val organization = summaries.map { CompactMetricData(finalMoney(kotlin.math.abs(it.organizationDebtMinor)), finalStatus(it.organizationDebtMinor, "مستحق له", "مستحق عليه")) }
    val parties = summaries.map { CompactMetricData(finalMoney(kotlin.math.abs(it.peopleDebtMinor)), finalStatus(it.peopleDebtMinor, "له على الأطراف", "عليه للأشخاص")) }
    CompactMetricRow("ذمة الجهة", organization)
    CompactMetricRow("ذمم الأطراف", parties)
}

@Composable
private fun FinalPersonCard(person: CustodyPersonEntity, transactions: List<CustodyTransactionEntity>, enabled: Boolean, onClick: () -> Unit, onQuick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = enabled, onClick = onQuick, modifier = Modifier.size(40.dp).semantics { contentDescription = "إضافة عملية سريعة" }) { Icon(Icons.Default.Add, null) }
                Column(Modifier.weight(1f)) {
                    Text(person.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(if (person.partyType == "ENTITY") "جهة" else "شخص", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (person.phone.isNotBlank()) Text(person.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            CompactCurrencyHeader(finalCurrencies)
            val custodyCells = finalCurrencies.map { code ->
                val balance = CustodyFinancialSummary.personCustodyBalance(transactions, person.id, code)
                CompactMetricData(finalMoney(kotlin.math.abs(balance)), finalStatus(balance, "لديه", "مستحق له"))
            }
            val debtCells = finalCurrencies.map { code ->
                val debt = CustodyFinancialSummary.personDebt(transactions, person.id, code)
                CompactMetricData(finalMoney(kotlin.math.abs(debt)), finalStatus(debt, "مستحق له", "مستحق عليه"))
            }
            CompactMetricRow("العهدة", custodyCells)
            CompactMetricRow("الذمة", debtCells)
        }
    }
}

@Composable
private fun FinalMetric(value: String, status: String, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = finalStatusColor(status), maxLines = 1)
        Spacer(Modifier.width(3.dp))
        Text(status, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = finalStatusColor(status), maxLines = 1)
    }
}

@Composable
private fun FinalAddPersonDialog(custodyId: Long, existing: List<CustodyPersonEntity>, onDismiss: () -> Unit, onSave: suspend (CustodyPersonEntity) -> Unit) {
    var name by remember { mutableStateOf("") }; var partyType by remember { mutableStateOf("PERSON") }; var phone by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(Modifier.fillMaxWidth(.95f).imePadding().navigationBarsPadding()) { Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("إضافة طرف", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = name, onValueChange = { name = it; error = null }, modifier = Modifier.fillMaxWidth().keepFocusedFieldVisible().semantics { contentDescription = "الاسم" }, label = { Text("الاسم") }, singleLine = true, enabled = !saving)
            existing.filter { name.isNotBlank() && it.name.contains(name.trim(), true) }.take(4).forEach { p -> TextButton(enabled = !saving, onClick = { name = p.name; phone = p.phone; address = p.address; notes = p.notes }) { Text(p.name) } }
            OutlinedTextField(value = phone, onValueChange = { phone = it }, modifier = Modifier.fillMaxWidth().keepFocusedFieldVisible().semantics { contentDescription = "الهاتف" }, label = { Text("الهاتف") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, enabled = !saving)
            OutlinedTextField(value = address, onValueChange = { address = it }, modifier = Modifier.fillMaxWidth().keepFocusedFieldVisible().semantics { contentDescription = "العنوان" }, label = { Text("العنوان") }, singleLine = true, enabled = !saving)
            OutlinedTextField(value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth().keepFocusedFieldVisible().semantics { contentDescription = "الملاحظات" }, label = { Text("الملاحظات") }, minLines = 3, enabled = !saving)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = name.isNotBlank() && !saving, onClick = { saving = true; scope.launch { runCatching { onSave(CustodyPersonEntity(custodyId = custodyId, name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim(), partyType = partyType)) }.onSuccess { saving = false; onDismiss() }.onFailure { saving = false; error = it.message ?: "تعذر حفظ الشخص" } } }, modifier = Modifier.weight(1f).semantics { contentDescription = "حفظ الشخص" }) { Text("حفظ") }
                OutlinedButton(enabled = !saving, onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("إلغاء") }
            }
        } }
    }
}

@Composable
private fun FinalSettlementDialog(vm: CustodyViewModel, custody: CustodyEntity, transactions: List<CustodyTransactionEntity>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val books = remember(transactions) { finalCurrencies.map { CustodyFinancialSummary.custodyOwnerBalance(transactions, it) } }
    var yer by remember { mutableStateOf(finalMoney(kotlin.math.max(books[0], 0L))) }; var sar by remember { mutableStateOf(finalMoney(kotlin.math.max(books[1], 0L))) }; var usd by remember { mutableStateOf(finalMoney(kotlin.math.max(books[2], 0L))) }
    var notes by remember { mutableStateOf("") }; var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val actuals = listOf(finalParse(yer), finalParse(sar), finalParse(usd)); val orgDebts = finalCurrencies.map { CustodyFinancialSummary.ownerOrganizationDebt(transactions, it) }
    val hasDifference = books.indices.any { i -> actuals[i]?.let { it != books[i] } == true }
    val canClose = actuals.all { it != null } && orgDebts.all { it == 0L } && (!hasDifference || notes.trim().isNotBlank()) && !saving
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(Modifier.fillMaxWidth(.96f).imePadding().navigationBarsPadding()) { Column(Modifier.fillMaxWidth().heightIn(max = 700.dp).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("إغلاق وتسوية العهدة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("أدخل الموجود الفعلي فقط. الرصيد الدفتري والفرق والعجز والفائض وحالة التسوية تظهر تلقائيًا. ذمم الأطراف لا تمنع إغلاق العهدة.", style = MaterialTheme.typography.bodySmall)
            FinalSettlementCurrency("YER", books[0], yer, { yer = it }, !saving)
            FinalSettlementCurrency("SAR", books[1], sar, { sar = it }, !saving)
            FinalSettlementCurrency("USD", books[2], usd, { usd = it }, !saving)
            OutlinedTextField(value = notes, onValueChange = { notes = it; error = null }, modifier = Modifier.fillMaxWidth().keepFocusedFieldVisible().semantics { contentDescription = "ملاحظات التسوية" }, label = { Text("الملاحظات / سبب العجز أو الفائض") }, minLines = 3, enabled = !saving)
            if (!actuals.all { it != null }) Text("أدخل الموجود الفعلي للعملات الثلاث.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            if (orgDebts.any { it != 0L }) Text("لا يمكن الإغلاق قبل تسوية ذمة حامل العهدة مع الجهة.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            if (hasDifference && notes.trim().isBlank()) Text("اكتب سبب العجز أو الفائض في الملاحظات.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = canClose, onClick = { saving = true; scope.launch { runCatching { vm.closeCustodyAndWait(custody.id, actuals[0]!!, actuals[1]!!, actuals[2]!!, notes.trim()) }.onSuccess { saving = false; onDismiss() }.onFailure { saving = false; error = it.message ?: "تعذر الإغلاق" } } }, modifier = Modifier.weight(1f)) { Text(if (saving) "جارٍ الحفظ…" else "إغلاق وتسوية") }
                OutlinedButton(enabled = !saving, onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("إلغاء") }
            }
        } }
    }
}

@Composable
private fun FinalSettlementCurrency(code: String, book: Long, actualText: String, onActual: (String) -> Unit, enabled: Boolean) {
    val actual = finalParse(actualText); val diff = actual?.minus(book); val deficit = if (diff != null) maxOf(-diff, 0L) else 0L; val surplus = if (diff != null) maxOf(diff, 0L) else 0L
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(finalCurrencyName(code), fontWeight = FontWeight.Bold); Text(code, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        AutoSettlement("الرصيد الدفتري", finalMoney(kotlin.math.abs(book)), if (book >= 0) "متبقي" else "عجز")
        OutlinedTextField(value = actualText, onValueChange = onActual, modifier = Modifier.fillMaxWidth().keepFocusedFieldVisible().semantics { contentDescription = "الموجود الفعلي $code" }, label = { Text("الموجود الفعلي") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = enabled)
        if (actual != null) { AutoSettlement("الفرق", finalMoney(kotlin.math.abs(diff ?: 0L)), when { diff!! > 0 -> "فائض"; diff < 0 -> "عجز"; else -> "متوازن" }); AutoSettlement("العجز", finalMoney(deficit), if (deficit > 0) "يحتاج سببًا" else "لا يوجد"); AutoSettlement("الفائض", finalMoney(surplus), if (surplus > 0) "موجود" else "لا يوجد") }
    } }
}

@Composable private fun AutoSettlement(title: String, value: String, status: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(value, fontWeight = FontWeight.Bold); Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }