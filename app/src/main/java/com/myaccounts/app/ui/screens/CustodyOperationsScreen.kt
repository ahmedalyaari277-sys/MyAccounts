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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.custody.*
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal

private val custodyCurrencies = listOf("YER", "SAR", "USD")
private fun money(v: Long): String = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun currencyName(code: String): String = when (code) {
    "YER" -> "ريال يمني"
    "SAR" -> "ريال سعودي"
    else -> "دولار"
}
private fun ownerCustodyLabel(balance: Long): String = when {
    balance > 0 -> "متبقي لديه"
    balance < 0 -> "عجز"
    else -> "متوازن"
}
private fun debtLabel(value: Long, positive: String, negative: String): String = when {
    value > 0 -> positive
    value < 0 -> negative
    else -> "متوازن"
}
private fun settlementDeltaStatus(actual: Long, book: Long): String = when {
    actual > book -> "فائض"
    actual < book -> "عجز"
    else -> "متوازن"
}

private enum class PersonSortOrder { LATEST, ALPHABETICAL }

@Composable
fun CustodyOperationsScreen(
    vm: CustodyViewModel,
    custodyId: Long,
    onBack: () -> Unit,
    onPerson: (Long) -> Unit,
    onOwner: () -> Unit
) {
    val custody by vm.custody(custodyId).collectAsState()
    val people by vm.persons(custodyId).collectAsState()
    val accounts by vm.accounts(custodyId).collectAsState()
    val transactions by vm.transactions(custodyId).collectAsState()
    val current = custody ?: return

    var menu by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var showAddPerson by remember { mutableStateOf(false) }
    var showQuick by remember { mutableStateOf(false) }
    var quickPersonId by remember { mutableStateOf<Long?>(null) }
    var quickOwner by remember { mutableStateOf(false) }
    var showSettlement by remember { mutableStateOf(false) }
    var personSearch by remember { mutableStateOf("") }
    var personSort by remember { mutableStateOf(PersonSortOrder.LATEST) }
    var showPersonSortMenu by remember { mutableStateOf(false) }

    fun openQuick(owner: Boolean, personId: Long? = null) {
        if (current.isClosed) return
        quickOwner = owner
        quickPersonId = personId
        showQuick = true
    }

    val displayedPeople = when (personSort) {
        PersonSortOrder.LATEST -> people
        PersonSortOrder.ALPHABETICAL -> people.sortedBy { it.name.trim().lowercase() }
    }.filter { personSearch.isBlank() || it.name.contains(personSearch.trim(), ignoreCase = true) }

    Scaffold(
        modifier = Modifier.semantics { contentDescription = "شاشة تفاصيل العهدة" },
        topBar = {
            TopAppBar(
                title = { Text(current.name, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "المزيد") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("تقرير العهدة") }, onClick = { menu = false; showReport = true })
                        if (!current.isClosed) {
                            DropdownMenuItem(text = { Text("إغلاق وتسوية العهدة") }, onClick = { menu = false; showSettlement = true })
                        } else {
                            DropdownMenuItem(text = { Text("إعادة فتح العهدة") }, onClick = { menu = false; vm.reopenCustody(custodyId) })
                        }
                        DropdownMenuItem(text = { Text("أرشفة العهدة") }, onClick = { menu = false; vm.archive(custodyId); onBack() })
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (current.isClosed) {
                item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        Text("هذه العهدة مغلقة ومسواة ولا يمكن إضافة أو تعديل عمليات حتى إعادة فتحها.", Modifier.fillMaxWidth().padding(12.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                CustodyOwnerCard(
                    name = current.name,
                    accounts = accounts,
                    transactions = transactions,
                    persons = people,
                    onClick = onOwner,
                    onQuick = { openQuick(true) },
                    enabled = !current.isClosed
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("الأشخاص", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showPersonSortMenu = true }, modifier = Modifier.semantics { contentDescription = "ترتيب الأشخاص" }) { Icon(Icons.Default.Sort, null) }
                        TextButton(enabled = !current.isClosed, onClick = { showAddPerson = true }, modifier = Modifier.semantics { contentDescription = "إضافة شخص" }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("إضافة شخص")
                        }
                        DropdownMenu(expanded = showPersonSortMenu, onDismissRequest = { showPersonSortMenu = false }) {
                            DropdownMenuItem(text = { Text("حسب الأحدث") }, onClick = { personSort = PersonSortOrder.LATEST; showPersonSortMenu = false })
                            DropdownMenuItem(text = { Text("حسب الأبجدية") }, onClick = { personSort = PersonSortOrder.ALPHABETICAL; showPersonSortMenu = false })
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = personSearch,
                    onValueChange = { personSearch = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = "بحث في الأشخاص" },
                    label = { Text("بحث في الأشخاص") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )
            }
            items(displayedPeople, key = { it.id }) { person ->
                CustodyPersonCard(
                    person = person,
                    transactions = transactions,
                    onClick = { onPerson(person.id) },
                    onQuick = { openQuick(false, person.id) },
                    enabled = !current.isClosed
                )
            }
            if (displayedPeople.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (personSearch.isBlank()) "لا يوجد أشخاص في هذه العهدة" else "لا توجد نتائج مطابقة", fontWeight = FontWeight.Bold)
                            if (personSearch.isBlank()) Text("اضغط «إضافة شخص» لإضافة أول شخص", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showAddPerson) {
        CustodyAddPersonDialog(
            custodyId = custodyId,
            existing = people,
            onDismiss = { showAddPerson = false },
            onSave = { person -> vm.addPersonAndWait(person.custodyId, person); showAddPerson = false }
        )
    }
    if (showQuick) {
        CustodyLedgerOperationDialog(
            vm = vm,
            custodyId = custodyId,
            personId = if (quickOwner) null else quickPersonId,
            owner = quickOwner,
            defaultCurrency = "YER",
            initialType = if (quickOwner) CustodyTransactionType.RECEIVED_FROM_ORG else CustodyTransactionType.PAID_TO_PERSON,
            transaction = null,
            dialogWidth = .92f,
            onDismiss = { showQuick = false },
            onFinished = { showQuick = false }
        )
    }
    if (showReport) CustodySummaryReportDialog(current, transactions, people) { showReport = false }
    if (showSettlement) CustodySettlementDialog(vm, current, transactions, people, { showSettlement = false })
}

@Composable
private fun CustodyOwnerCard(
    name: String,
    accounts: List<CustodyAccountEntity>,
    transactions: List<CustodyTransactionEntity>,
    persons: List<CustodyPersonEntity>,
    onClick: () -> Unit,
    onQuick: () -> Unit,
    enabled: Boolean
) {
    Card(Modifier.fillMaxWidth().clickable(enabled = true, onClick = onClick), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = enabled, onClick = onQuick, modifier = Modifier.semantics { contentDescription = "إضافة عملية سريعة لـ $name" }) { Icon(Icons.Default.Add, null) }
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("مستلم العهدة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            custodyCurrencies.forEach { code ->
                val summary = CustodyFinancialSummary.ownerDisplay(transactions, accounts, persons, code)
                Spacer(Modifier.height(7.dp))
                Text(currencyName(code), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryLine("العهدة", money(kotlin.math.abs(summary.custodyMinor)), ownerCustodyLabel(summary.custodyMinor), Modifier.weight(1f))
                    SummaryLine("ذمة الجهة", money(kotlin.math.abs(summary.organizationDebtMinor)), debtLabel(summary.organizationDebtMinor, "مستحق له", "مستحق عليه"), Modifier.weight(1f))
                    SummaryLine("ذمم الأشخاص", money(kotlin.math.abs(summary.peopleDebtMinor)), debtLabel(summary.peopleDebtMinor, "له على الأشخاص", "عليه للأشخاص"), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CustodyPersonCard(
    person: CustodyPersonEntity,
    transactions: List<CustodyTransactionEntity>,
    onClick: () -> Unit,
    onQuick: () -> Unit,
    enabled: Boolean
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = enabled, onClick = onQuick, modifier = Modifier.semantics { contentDescription = "إضافة عملية سريعة لـ ${person.name}" }) { Icon(Icons.Default.Add, null) }
                Column(Modifier.weight(1f)) {
                    Text(person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (person.phone.isNotBlank()) Text(person.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            custodyCurrencies.forEach { code ->
                val custodyBalance = CustodyFinancialSummary.personCustodyBalance(transactions, person.id, code)
                val debt = CustodyFinancialSummary.personDebt(transactions, person.id, code)
                Spacer(Modifier.height(7.dp))
                Text(currencyName(code), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryLine("العهدة", money(kotlin.math.abs(custodyBalance)), when { custodyBalance > 0 -> "لديه"; custodyBalance < 0 -> "مستحق له"; else -> "متوازن" }, Modifier.weight(1f))
                    SummaryLine("الذمة", money(kotlin.math.abs(debt)), debtLabel(debt, "مستحق له", "مستحق عليه"), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(title: String, value: String, status: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CustodyAddPersonDialog(custodyId: Long, existing: List<CustodyPersonEntity>, onDismiss: () -> Unit, onSave: suspend (CustodyPersonEntity) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val matches = existing.filter { name.isNotBlank() && it.name.contains(name.trim(), true) }.take(5)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("إضافة شخص") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(name, { name = it; error = null }, Modifier.fillMaxWidth().semantics { contentDescription = "الاسم" }, label = { Text("الاسم") }, singleLine = true, enabled = !saving)
            matches.forEach { match -> TextButton(onClick = { name = match.name; phone = match.phone; address = match.address; notes = match.notes }) { Text(match.name) } }
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth().semantics { contentDescription = "الهاتف" }, label = { Text("الهاتف") }, singleLine = true, enabled = !saving)
            OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth().semantics { contentDescription = "العنوان" }, label = { Text("العنوان") }, singleLine = true, enabled = !saving)
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth().semantics { contentDescription = "الملاحظات" }, label = { Text("الملاحظات") }, minLines = 2, enabled = !saving)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button(enabled = name.isNotBlank() && !saving, onClick = {
            saving = true
            scope.launch { runCatching { onSave(CustodyPersonEntity(custodyId = custodyId, name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim())) }.onFailure { error = it.message ?: "تعذر حفظ الشخص"; saving = false } }
        }, modifier = Modifier.semantics { contentDescription = "حفظ الشخص" }) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun CustodySummaryReportDialog(custody: CustodyEntity, transactions: List<CustodyTransactionEntity>, people: List<CustodyPersonEntity>, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تقرير العهدة") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            item { Text(custody.name, fontWeight = FontWeight.Bold); Text("الجهة: ${custody.organizationName}") }
            custodyCurrencies.forEach { code ->
                val rows = transactions.filter { it.currencyCode == code }
                item { Text("$code — الرصيد: ${money(kotlin.math.abs(CustodyFinancialSummary.custodyOwnerBalance(transactions, code)))}") }
                items(rows, key = { it.id }) { row ->
                    val personName = row.personId?.let { id -> people.firstOrNull { it.id == id }?.name }
                    val label = when (row.type) {
                        CustodyTransactionType.RECEIVED_FROM_ORG -> "استلام من الجهة"
                        CustodyTransactionType.RETURNED_TO_ORG -> "مرتجع للجهة"
                        CustodyTransactionType.PAID_TO_PERSON -> "صرف${personName?.let { " لـ $it" } ?: ""}"
                        CustodyTransactionType.RETURNED_FROM_PERSON -> "مرتجع${personName?.let { " من $it" } ?: ""}"
                        CustodyTransactionType.ORG_LOAN_FROM_OWNER -> "تسليف الجهة"
                        CustodyTransactionType.ORG_LOAN_REPAYMENT -> "سداد تسليف الجهة"
                        CustodyTransactionType.PERSON_LOAN_TO_OWNER -> "تسليف لحامل العهدة${personName?.let { " من $it" } ?: ""}"
                        CustodyTransactionType.OWNER_REPAY_PERSON_LOAN -> "سداد تسليف للشخص${personName?.let { " لـ $it" } ?: ""}"
                        else -> row.type
                    }
                    Text("$label — ${money(row.amountMinor)} $code", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } })
}

@Composable
private fun CustodySettlementDialog(vm: CustodyViewModel, custody: CustodyEntity, transactions: List<CustodyTransactionEntity>, persons: List<CustodyPersonEntity>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var yer by remember { mutableStateOf("") }
    var sar by remember { mutableStateOf("") }
    var usd by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun parse(text: String): Long? = runCatching { BigDecimal(text.trim()).setScale(2).movePointRight(2).longValueExact() }.getOrNull()
    val actuals = listOf(parse(yer), parse(sar), parse(usd))
    val ownerBalances = custodyCurrencies.map { code -> CustodyFinancialSummary.custodyOwnerBalance(transactions, code) }
    val orgDebts = custodyCurrencies.map { code -> CustodyFinancialSummary.ownerOrganizationDebt(transactions, code) }
    val eligible = actuals.all { it != null } && orgDebts.all { it == 0L }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("إغلاق وتسوية العهدة") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("التسوية للعهدة كلها. لا تمنع أرصدة الأشخاص إغلاق العهدة؛ المعيار هو تسوية ذمة حامل العهدة مع الجهة. الموجود الفعلي يسجل حالة متوازن/عجز/فائض لكل عملة.", style = MaterialTheme.typography.bodySmall) }
                custodyCurrencies.forEachIndexed { i, code ->
                    val actual = actuals[i]
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(currencyName(code), fontWeight = FontWeight.Bold)
                                Text("الرصيد الدفتري لدى الحامل: ${money(ownerBalances[i])}")
                                Text("ذمة الجهة: ${money(orgDebts[i])}")
                                OutlinedTextField(
                                    value = when (code) { "YER" -> yer; "SAR" -> sar; else -> usd },
                                    onValueChange = { when (code) { "YER" -> yer = it; "SAR" -> sar; else -> usd = it } },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("الموجود الفعلي لدى الحامل") },
                                    singleLine = true,
                                    enabled = !saving
                                )
                                if (actual != null) Text("الحالة: ${settlementDeltaStatus(actual, ownerBalances[i])}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item { OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظات التسوية") }, minLines = 2, enabled = !saving) }
                item {
                    Text(
                        when {
                            !actuals.all { it != null } -> "أدخل الموجود الفعلي للعملات الثلاث."
                            orgDebts.any { it != 0L } -> "لا يمكن الإغلاق: ما زالت ذمة حامل العهدة مع الجهة غير مسواة في عملة واحدة على الأقل."
                            else -> "🟢 العهدة مؤهلة للإغلاق. يمكن أن تبقى ذمم الأشخاص قائمة لأنها مستقلة عن مسؤولية الحامل أمام الجهة."
                        },
                        fontWeight = FontWeight.Bold,
                        color = if (eligible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = {
            Button(enabled = eligible && !saving, onClick = {
                saving = true
                scope.launch {
                    runCatching { vm.closeCustodyAndWait(custody.id, actuals[0]!!, actuals[1]!!, actuals[2]!!, notes) }
                        .onSuccess { saving = false; onDismiss() }
                        .onFailure { saving = false; message = it.message ?: "تعذر إغلاق العهدة" }
                }
            }) { Text(if (saving) "جارٍ الحفظ…" else "إغلاق وتسوية العهدة") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("إلغاء") } }
    )
}
