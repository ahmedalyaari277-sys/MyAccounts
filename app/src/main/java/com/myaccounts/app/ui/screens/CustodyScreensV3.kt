@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myaccounts.app.ui.screens

import android.app.DatePickerDialog
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myaccounts.app.data.custody.*
import com.myaccounts.app.ui.components.CalculatorButton
import com.myaccounts.app.ui.components.CalculatorOverlay
import com.myaccounts.app.ui.components.LocalCalculatorController
import com.myaccounts.app.ui.theme.Due
import com.myaccounts.app.ui.theme.Owed
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import com.myaccounts.app.util.CustodyAttachmentStorage
import com.myaccounts.app.util.TransactionAttachmentStorage
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val V3_CURRENCIES = listOf("YER", "SAR", "USD")
private fun v3Money(v: Long): String = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun v3Parse(v: String): Long? = runCatching { BigDecimal(v.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }.getOrNull()
private fun v3CurrencyName(c: String) = when (c) { "YER" -> "ريال يمني"; "SAR" -> "ريال سعودي"; else -> "دولار أمريكي" }
private fun v3Status(v: Long, positive: String, negative: String) = when { v > 0 -> positive; v < 0 -> negative; else -> "متوازن" }
private fun v3Color(v: Long) = when { v > 0 -> Due; v < 0 -> Owed; else -> MaterialTheme.colorScheme.onSurfaceVariant }

@Composable
private fun V3Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = !label.contains("ملاحظات") && !label.contains("سبب"),
    minLines: Int = 1,
    enabled: Boolean = true,
    contentDescriptionValue: String = label
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics { contentDescription = contentDescriptionValue },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        minLines = minLines,
        enabled = enabled
    )
}

private enum class V3PersonSort { LATEST, ALPHABETICAL }

@Composable
fun CustodyOperationsScreenV3(vm: CustodyViewModel, custodyId: Long, onBack: () -> Unit, onPerson: (Long) -> Unit, onOwner: () -> Unit) {
    val custody by vm.custody(custodyId).collectAsState()
    val people by vm.persons(custodyId).collectAsState()
    val accounts by vm.accounts(custodyId).collectAsState()
    val transactions by vm.transactions(custodyId).collectAsState()
    val current = custody ?: return
    var menu by remember { mutableStateOf(false) }
    var settlement by remember { mutableStateOf(false) }
    var addPerson by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(V3PersonSort.LATEST) }
    var quickOwner by remember { mutableStateOf<Boolean?>(null) }
    var quickPerson by remember { mutableStateOf<Long?>(null) }
    val latestByPerson = remember(transactions) { transactions.groupBy { it.personId }.mapValues { it.value.maxOfOrNull { t -> t.transactionDate } ?: 0L } }
    val shown = when (sort) {
        V3PersonSort.LATEST -> people.sortedByDescending { latestByPerson[it.id] ?: 0L }
        V3PersonSort.ALPHABETICAL -> people.sortedBy { it.name.trim().lowercase(Locale.getDefault()) }
    }.filter { search.isBlank() || it.name.contains(search.trim(), true) || it.phone.contains(search.trim(), true) }
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
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 8.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            item { V3OwnerCard(current.name, accounts, transactions, people, !current.isClosed, onOwner) { quickOwner = true; quickPerson = null } }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الأشخاص", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { sortMenu = true }, modifier = Modifier.semantics { contentDescription = "ترتيب الأشخاص" }) { Icon(Icons.Default.Sort, null) }
                        TextButton(enabled = !current.isClosed, onClick = { addPerson = true }, modifier = Modifier.semantics { contentDescription = "إضافة شخص" }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(3.dp)); Text("إضافة") }
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        DropdownMenuItem(text = { Text("حسب الأحدث") }, onClick = { sort = V3PersonSort.LATEST; sortMenu = false })
                        DropdownMenuItem(text = { Text("حسب الأبجدية") }, onClick = { sort = V3PersonSort.ALPHABETICAL; sortMenu = false })
                    }
                }
            }
            item { V3Field(search, { search = it }, "بحث في الأشخاص", Modifier.fillMaxWidth(), contentDescriptionValue = "بحث في الأشخاص") }
            items(shown, key = { it.id }) { p -> V3PersonCard(p, transactions, !current.isClosed, { onPerson(p.id) }) { quickOwner = false; quickPerson = p.id } }
            if (shown.isEmpty()) item { Text(if (search.isBlank()) "لا يوجد أشخاص في هذه العهدة" else "لا توجد نتائج مطابقة", modifier = Modifier.padding(10.dp)) }
        }
    }
    if (addPerson) V3AddPersonDialog(custodyId, people, { addPerson = false }) { vm.addPersonAndWait(custodyId, it) }
    if (quickOwner != null) V3OperationDialog(vm, custodyId, quickPerson, quickOwner == true, "YER", if (quickOwner == true) CustodyTransactionType.RECEIVED_FROM_ORG else CustodyTransactionType.PAID_TO_PERSON, null, { quickOwner = null }, { quickOwner = null })
    if (settlement) V3SettlementDialog(vm, current, transactions) { settlement = false }
}

@Composable
private fun V3OwnerCard(name: String, accounts: List<CustodyAccountEntity>, transactions: List<CustodyTransactionEntity>, people: List<CustodyPersonEntity>, enabled: Boolean, onClick: () -> Unit, onQuick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = enabled, onClick = onQuick, modifier = Modifier.size(40.dp).semantics { contentDescription = "إضافة عملية سريعة" }) { Icon(Icons.Default.Add, null) }
                Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium); Text("حامل العهدة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("العملة", Modifier.weight(.75f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("العهدة", Modifier.weight(1.25f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("ذمة الجهة", Modifier.weight(1.25f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("ذمم الأشخاص", Modifier.weight(1.35f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            V3_CURRENCIES.forEach { code ->
                val s = CustodyFinancialSummary.ownerDisplay(transactions, accounts, people, code)
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(code, Modifier.weight(.75f), fontWeight = FontWeight.Bold)
                    V3Metric(v3Money(kotlin.math.abs(s.custodyMinor)), v3Status(s.custodyMinor, "متبقي لديه", "عجز"), Modifier.weight(1.25f))
                    V3Metric(v3Money(kotlin.math.abs(s.organizationDebtMinor)), v3Status(s.organizationDebtMinor, "مستحق له", "مستحق عليه"), Modifier.weight(1.25f))
                    V3Metric(v3Money(kotlin.math.abs(s.peopleDebtMinor)), v3Status(s.peopleDebtMinor, "له على الأشخاص", "عليه للأشخاص"), Modifier.weight(1.35f))
                }
            }
        }
    }
}

@Composable
private fun V3PersonCard(person: CustodyPersonEntity, transactions: List<CustodyTransactionEntity>, enabled: Boolean, onClick: () -> Unit, onQuick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = enabled, onClick = onQuick, modifier = Modifier.size(40.dp).semantics { contentDescription = "إضافة عملية سريعة لـ ${person.name}" }) { Icon(Icons.Default.Add, null) }
                Column(Modifier.weight(1f)) { Text(person.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium); if (person.phone.isNotBlank()) Text(person.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("العملة", Modifier.weight(.9f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("العهدة", Modifier.weight(1.3f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("الذمة", Modifier.weight(1.3f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            V3_CURRENCIES.forEach { code ->
                val custodyBalance = CustodyFinancialSummary.personCustodyBalance(transactions, person.id, code)
                val debt = CustodyFinancialSummary.personDebt(transactions, person.id, code)
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(code, Modifier.weight(.9f), fontWeight = FontWeight.Bold)
                    V3Metric(v3Money(kotlin.math.abs(custodyBalance)), v3Status(custodyBalance, "لديه", "مستحق له"), Modifier.weight(1.3f))
                    V3Metric(v3Money(kotlin.math.abs(debt)), v3Status(debt, "مستحق له", "مستحق عليه"), Modifier.weight(1.3f))
                }
            }
        }
    }
}

@Composable private fun V3Metric(value: String, status: String, modifier: Modifier) { Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold); Text(status, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) } }

@Composable
private fun V3AddPersonDialog(custodyId: Long, existing: List<CustodyPersonEntity>, onDismiss: () -> Unit, onSave: suspend (CustodyPersonEntity) -> Unit) {
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val matches = existing.filter { name.isNotBlank() && it.name.contains(name.trim(), true) }.take(5)
    V3DialogShell("إضافة شخص", onDismiss, saving) {
        V3Field(name, { name = it; error = null }, "الاسم", Modifier.fillMaxWidth(), enabled = !saving)
        matches.forEach { p -> TextButton(enabled = !saving, onClick = { name = p.name; phone = p.phone; address = p.address; notes = p.notes }) { Text(p.name) } }
        V3Field(phone, { phone = it }, "الهاتف", Modifier.fillMaxWidth(), KeyboardType.Phone, enabled = !saving)
        V3Field(address, { address = it }, "العنوان", Modifier.fillMaxWidth(), enabled = !saving)
        V3Field(notes, { notes = it }, "الملاحظات", Modifier.fillMaxWidth(), singleLine = false, minLines = 3, enabled = !saving)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = name.isNotBlank() && !saving, onClick = { saving = true; scope.launch { runCatching { onSave(CustodyPersonEntity(custodyId = custodyId, name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim())) }.onSuccess { saving = false; onDismiss() }.onFailure { saving = false; error = it.message ?: "تعذر حفظ الشخص" } } }, Modifier.weight(1f).semantics { contentDescription = "حفظ الشخص" }) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") }
            OutlinedButton(enabled = !saving, onClick = onDismiss, Modifier.weight(1f)) { Text("إلغاء") }
        }
    }
}

@Composable
private fun V3DialogShell(title: String, onDismiss: () -> Unit, busy: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(Modifier.fillMaxWidth(.95f).imePadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().heightIn(max = 690.dp).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); content() })
        }
    }
}

@Composable
private fun V3SettlementDialog(vm: CustodyViewModel, custody: CustodyEntity, transactions: List<CustodyTransactionEntity>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val books = remember(transactions) { V3_CURRENCIES.map { CustodyFinancialSummary.custodyOwnerBalance(transactions, it) } }
    var yer by remember { mutableStateOf(v3Money(kotlin.math.max(books[0], 0L))) }
    var sar by remember { mutableStateOf(v3Money(kotlin.math.max(books[1], 0L))) }
    var usd by remember { mutableStateOf(v3Money(kotlin.math.max(books[2], 0L))) }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val actual = listOf(v3Parse(yer), v3Parse(sar), v3Parse(usd))
    val orgDebts = V3_CURRENCIES.map { CustodyFinancialSummary.ownerOrganizationDebt(transactions, it) }
    val hasDiff = books.indices.any { i -> actual[i]?.let { it != books[i] } == true }
    val complete = actual.all { it != null }
    val orgSettled = orgDebts.all { it == 0L }
    val notesRequired = hasDiff
    val eligible = complete && orgSettled && (!notesRequired || notes.trim().isNotBlank()) && !saving
    V3DialogShell("إغلاق وتسوية العهدة", onDismiss, saving) {
        Text("أدخل فقط الموجود الفعلي. الرصيد الدفتري، العجز، الفائض، الفرق، وحالة كل عملة تظهر وتحسب تلقائيًا.", style = MaterialTheme.typography.bodySmall)
        SettlementRowV3("YER", books[0], yer, { yer = it }, !saving)
        SettlementRowV3("SAR", books[1], sar, { sar = it }, !saving)
        SettlementRowV3("USD", books[2], usd, { usd = it }, !saving)
        V3Field(notes, { notes = it; error = null }, "ملاحظات التسوية / أسباب العجز أو الفائض", Modifier.fillMaxWidth(), singleLine = false, minLines = 3, enabled = !saving, contentDescriptionValue = "ملاحظات التسوية")
        if (!complete) Text("أدخل الموجود الفعلي بشكل صحيح للعملات الثلاث.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        if (!orgSettled) Text("لا يمكن الإغلاق لأن ذمة حامل العهدة مع الجهة غير مسواة.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        if (notesRequired && notes.trim().isBlank()) Text("العجز أو الفائض يتطلب تسجيل السبب في الملاحظات.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = eligible, onClick = { saving = true; scope.launch { runCatching { vm.closeCustodyAndWait(custody.id, actual[0]!!, actual[1]!!, actual[2]!!, notes.trim()) }.onSuccess { saving = false; onDismiss() }.onFailure { saving = false; error = it.message ?: "تعذر إغلاق العهدة" } } }, Modifier.weight(1f).semantics { contentDescription = "إغلاق وتسوية العهدة" }) { Text(if (saving) "جارٍ الحفظ…" else "إغلاق وتسوية") }
            OutlinedButton(enabled = !saving, onClick = onDismiss, Modifier.weight(1f)) { Text("إلغاء") }
        }
    }
}

@Composable
private fun SettlementRowV3(code: String, book: Long, actualText: String, onActual: (String) -> Unit, enabled: Boolean) {
    val actual = v3Parse(actualText)
    val diff = actual?.minus(book)
    val deficit = if (diff != null) maxOf(-diff, 0L) else 0L
    val surplus = if (diff != null) maxOf(diff, 0L) else 0L
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(v3CurrencyName(code), fontWeight = FontWeight.Bold); Text(code, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
            V3AutoLine("الرصيد الدفتري", v3Money(kotlin.math.abs(book)), if (book >= 0) "متبقي" else "عجز")
            V3Field(actualText, onActual, "الموجود الفعلي", Modifier.fillMaxWidth(), KeyboardType.Decimal, enabled = enabled, contentDescriptionValue = "الموجود الفعلي $code")
            if (actual != null) {
                V3AutoLine("الفرق", v3Money(kotlin.math.abs(diff ?: 0L)), when { diff!! > 0 -> "فائض"; diff < 0 -> "عجز"; else -> "متوازن" })
                V3AutoLine("العجز", v3Money(deficit), if (deficit > 0) "يحتاج سببًا" else "لا يوجد")
                V3AutoLine("الفائض", v3Money(surplus), if (surplus > 0) "موجود" else "لا يوجد")
            }
        }
    }
}

@Composable private fun V3AutoLine(title: String, value: String, status: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(value, fontWeight = FontWeight.Bold); Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }

@Composable
fun CustodyOwnerLedgerScreenV3(vm: CustodyViewModel, custodyId: Long, onBack: () -> Unit) = V3LedgerScreen(vm, custodyId, null, onBack)

@Composable
fun CustodyPersonLedgerScreenV3(vm: CustodyViewModel, custodyId: Long, personId: Long, onBack: () -> Unit) = V3LedgerScreen(vm, custodyId, personId, onBack)

@Composable
private fun V3LedgerScreen(vm: CustodyViewModel, custodyId: Long, personId: Long?, onBack: () -> Unit) {
    val custody by vm.custody(custodyId).collectAsState()
    val people by vm.persons(custodyId).collectAsState()
    val transactions by vm.transactions(custodyId).collectAsState()
    val accounts by vm.accounts(custodyId).collectAsState()
    val current = custody ?: return
    val owner = personId == null
    val person = people.firstOrNull { it.id == personId }
    if (!owner && person == null) return
    val title = if (owner) current.name else person!!.name
    val ownerIds = accounts.filter { it.holderType == "OWNER" && it.personId == null }.map { it.id }.toSet()
    var currency by remember { mutableStateOf("YER") }
    var add by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    var deleting by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    var transferring by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    var menu by remember { mutableStateOf(false) }
    var editPerson by remember { mutableStateOf(false) }
    var editOwner by remember { mutableStateOf(false) }
    var editCustody by remember { mutableStateOf(false) }
    var deletePerson by remember { mutableStateOf(false) }
    val rows = transactions.filter { t -> t.currencyCode == currency && if (owner) t.accountId in ownerIds else t.personId == personId }.sortedByDescending { it.transactionDate }
    val balance = if (owner) accounts.firstOrNull { it.holderType == "OWNER" && it.personId == null && it.currencyCode == currency }?.balanceMinor ?: 0L else CustodyFinancialSummary.personCustodyBalance(transactions, personId!!, currency)
    val debt = if (owner) CustodyFinancialSummary.ownerPeopleDebt(transactions, currency) else CustodyFinancialSummary.personDebt(transactions, personId!!, currency)
    val orgDebt = if (owner) CustodyFinancialSummary.ownerOrganizationDebt(transactions, currency) else 0L
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(title, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }, actions = {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "المزيد") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (owner) {
                        if (!current.isClosed) {
                            DropdownMenuItem(text = { Text("تعديل بيانات حامل العهدة") }, onClick = { menu = false; editOwner = true })
                            DropdownMenuItem(text = { Text("تعديل بيانات العهدة والجهة") }, onClick = { menu = false; editCustody = true })
                        }
                    } else {
                        DropdownMenuItem(text = { Text("تعديل بيانات الشخص") }, onClick = { menu = false; editPerson = true })
                        if (!current.isClosed) DropdownMenuItem(text = { Text("حذف الشخص وعملياته") }, onClick = { menu = false; deletePerson = true })
                    }
                }
            })
        },
        floatingActionButton = { FloatingActionButton(onClick = { if (!current.isClosed) add = true }, modifier = Modifier.semantics { contentDescription = "إضافة عملية" }) { Icon(Icons.Default.Add, null) } }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 10.dp, vertical = 7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { V3_CURRENCIES.forEach { c -> FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c, fontWeight = FontWeight.Bold) }, modifier = Modifier.weight(1f)) } }
            Spacer(Modifier.height(6.dp))
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(currency, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); V3BalanceLine("العهدة", balance, v3Status(balance, if (owner) "متبقي لديه" else "لديه", if (owner) "عجز" else "مستحق له")); if (owner) V3BalanceLine("ذمة الجهة", orgDebt, v3Status(orgDebt, "مستحق له", "مستحق عليه")); V3BalanceLine(if (owner) "ذمم الأشخاص" else "الذمة", debt, v3Status(debt, if (owner) "له على الأشخاص" else "مستحق له", if (owner) "عليه للأشخاص" else "مستحق عليه")) } }
            Spacer(Modifier.height(6.dp))
            if (rows.isEmpty()) Text("لا توجد عمليات لهذه العملة", modifier = Modifier.padding(10.dp)) else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(rows, key = { it.id }) { t ->
                    val pn = t.personId?.let { id -> people.firstOrNull { it.id == id }?.name }.orEmpty()
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(9.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(v3TransactionLabel(t.type, owner, pn), fontWeight = FontWeight.Bold); Text(SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(t.transactionDate)), style = MaterialTheme.typography.bodySmall) }
                        Text("${v3Money(t.amountMinor)} ${t.currencyCode}", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (!owner && !current.isClosed) IconButton(onClick = { transferring = t }, modifier = Modifier.semantics { contentDescription = "نقل العملية" }) { Icon(Icons.Default.SwapHoriz, "نقل العملية") }
                            IconButton(enabled = !current.isClosed, onClick = { editing = t }, modifier = Modifier.semantics { contentDescription = "تعديل" }) { Icon(Icons.Default.Edit, "تعديل") }
                            IconButton(enabled = !current.isClosed, onClick = { deleting = t }, modifier = Modifier.semantics { contentDescription = "حذف" }) { Icon(Icons.Default.Delete, "حذف") }
                        }
                    } }
                }
            }
        }
    }
    if (add) V3OperationDialog(vm, custodyId, personId, owner, currency, if (owner) CustodyTransactionType.RECEIVED_FROM_ORG else CustodyTransactionType.PAID_TO_PERSON, null, { add = false }, { add = false })
    editing?.let { V3OperationDialog(vm, custodyId, if (owner) it.personId else personId, owner, it.currencyCode, it.type, it, { editing = null }, { editing = null }) }
    deleting?.let { t -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("حذف العملية") }, text = { Text("سيتم حذف العملية نهائيًا.") }, confirmButton = { TextButton(onClick = { vm.deleteTransaction(t.id); deleting = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } }) }
    transferring?.let { t -> V3TransferDialog(t, personId!!, people, { transferring = null }) { newId, reason -> vm.transferTransactionAndWait(t.id, newId, reason) } }
    if (!owner && editPerson) V3PersonEditDialog(vm, person!!, { editPerson = false }) { editPerson = false }
    if (owner && editOwner) V3OwnerEditDialog(vm, current, { editOwner = false }) { editOwner = false }
    if (owner && editCustody) V3CustodyEditDialog(vm, current, { editCustody = false }) { editCustody = false }
    if (!owner && deletePerson) AlertDialog(onDismissRequest = { deletePerson = false }, title = { Text("حذف الشخص وعملياته") }, text = { Text("سيتم حذف بيانات الشخص وحساباته وجميع عملياته ومرفقاتها نهائيًا. هل تريد المتابعة؟") }, confirmButton = { TextButton(onClick = { deletePerson = false; scope.launch { runCatching { vm.deletePersonAndWait(person!!.id) }.onSuccess { onBack() } } }) { Text("حذف", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deletePerson = false }) { Text("إلغاء") } })
}

@Composable private fun V3BalanceLine(title: String, value: Long, status: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(v3Money(kotlin.math.abs(value)), fontWeight = FontWeight.Bold, color = v3Color(value)); Text(status, style = MaterialTheme.typography.bodySmall, color = v3Color(value)) } } }

private fun v3TransactionLabel(type: String, owner: Boolean, personName: String): String = when(type) {
    CustodyTransactionType.RECEIVED_FROM_ORG -> "استلام من الجهة"
    CustodyTransactionType.RETURNED_TO_ORG -> "مرتجع للجهة"
    CustodyTransactionType.PAID_TO_PERSON -> if (owner && personName.isNotBlank()) "صرف لـ $personName" else "صرف"
    CustodyTransactionType.RETURNED_FROM_PERSON -> if (owner && personName.isNotBlank()) "مرتجع من $personName" else "مرتجع"
    CustodyTransactionType.ORG_LOAN_FROM_OWNER -> "تسليف الجهة"
    CustodyTransactionType.ORG_LOAN_REPAYMENT -> "سداد تسليف الجهة"
    CustodyTransactionType.PERSON_LOAN_TO_OWNER -> if (owner && personName.isNotBlank()) "تسليف من $personName" else "تسليف لحامل العهدة"
    CustodyTransactionType.OWNER_REPAY_PERSON_LOAN -> if (owner && personName.isNotBlank()) "سداد تسليف لـ $personName" else "سداد تسليف للشخص"
    else -> type
}

@Composable
private fun V3OperationDialog(vm: CustodyViewModel, custodyId: Long, personId: Long?, owner: Boolean, defaultCurrency: String, initialType: String, transaction: CustodyTransactionEntity?, onDismiss: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val calc = LocalCalculatorController.current
    val scope = rememberCoroutineScope()
    var currency by remember(transaction?.id) { mutableStateOf(transaction?.currencyCode ?: defaultCurrency) }
    var type by remember(transaction?.id) { mutableStateOf(transaction?.type ?: initialType) }
    var amountText by remember(transaction?.id) { mutableStateOf(transaction?.let { v3Money(it.amountMinor) } ?: "") }
    var details by remember(transaction?.id) { mutableStateOf(transaction?.description ?: "") }
    var date by remember(transaction?.id) { mutableStateOf(transaction?.transactionDate ?: System.currentTimeMillis()) }
    var saving by remember(transaction?.id) { mutableStateOf(false) }
    var error by remember(transaction?.id) { mutableStateOf<String?>(null) }
    var newAttachments by remember(transaction?.id) { mutableStateOf<List<TransactionAttachmentStorage.SelectedAttachment>>(emptyList()) }
    var deletedAttachments by remember(transaction?.id) { mutableStateOf<List<CustodyTransactionAttachmentEntity>>(emptyList()) }
    val existing = remember(transaction?.id) { transaction?.let { vm.attachments(it.id) } ?: emptyList() }
    val visibleExisting = existing.filter { a -> deletedAttachments.none { it.id == a.id } }
    DisposableEffect(calc, transaction?.id) { calc.setResultConsumer { amountText = it; error = null }; onDispose { calc.setResultConsumer(null) } }
    val kinds = if (owner) listOf(CustodyTransactionType.RECEIVED_FROM_ORG, CustodyTransactionType.RETURNED_TO_ORG, CustodyTransactionType.ORG_LOAN_FROM_OWNER, CustodyTransactionType.ORG_LOAN_REPAYMENT) else listOf(CustodyTransactionType.PAID_TO_PERSON, CustodyTransactionType.RETURNED_FROM_PERSON, CustodyTransactionType.PERSON_LOAN_TO_OWNER, CustodyTransactionType.OWNER_REPAY_PERSON_LOAN)
    V3DialogShell(if (transaction == null) "إضافة عملية" else "تعديل العملية", onDismiss, saving) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            V3Field(amountText, { amountText = it; error = null }, "المبلغ", Modifier.weight(1.25f), KeyboardType.Decimal, enabled = !saving, contentDescriptionValue = "المبلغ")
            OutlinedTextField(SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(date)), {}, Modifier.weight(1f), label = { Text("التاريخ") }, readOnly = true, singleLine = true, enabled = !saving, trailingIcon = { IconButton(onClick = { val d = Calendar.getInstance().apply { timeInMillis = date }; DatePickerDialog(context, { _, y, m, day -> d.set(y,m,day,12,0,0); d.set(Calendar.MILLISECOND,0); date = d.timeInMillis }, d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH)).show() }) { Icon(Icons.Default.CalendarToday, "اختيار التاريخ") } })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { V3_CURRENCIES.forEach { c -> FilterChip(selected = currency == c, onClick = { if (!saving) currency = c }, label = { Text(c) }, modifier = Modifier.weight(1f)) } }
        V3Field(details, { details = it }, "التفاصيل", Modifier.fillMaxWidth(), singleLine = false, minLines = 2, enabled = !saving, contentDescriptionValue = "التفاصيل")
        Text("نوع العملية", fontWeight = FontWeight.Bold)
        kinds.forEach { kind -> FilterChip(selected = type == kind, onClick = { if (!saving) type = kind }, label = { Text(v3TransactionLabel(kind, owner, "")) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = v3TransactionLabel(kind, owner, "") }) }
        if (transaction != null && visibleExisting.isNotEmpty()) {
            Text("المرفقات الحالية: ${visibleExisting.size}", fontWeight = FontWeight.Bold)
            visibleExisting.forEach { a -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AttachFile, null); Text(a.fileName, Modifier.weight(1f), maxLines = 1); IconButton(enabled = !saving, onClick = { deletedAttachments = deletedAttachments + a }) { Icon(Icons.Default.Delete, "حذف المرفق") } } }
        }
        TransactionAttachmentPicker(selectedAttachments = newAttachments, onAttachmentsChanged = { if (!saving) newAttachments = it })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !saving, onClick = { val parsed = v3Parse(amountText); if (parsed == null || parsed <= 0L) { error = "أدخل مبلغًا صحيحًا أكبر من صفر."; return@Button }; saving = true; val selected = newAttachments.map { CustodyAttachmentStorage.Selected(it.uri, it.fileName, it.mimeType) }; scope.launch { runCatching { if (transaction == null) vm.addTransactionAndWait(custodyId, currency, type, personId, parsed, details.trim(), date, selected) else vm.updateTransactionAndWait(transaction.id, currency, type, personId, parsed, details.trim(), date, selected, deletedAttachments) }.onSuccess { keyboard?.hide(); calc.close(); saving = false; onFinished() }.onFailure { saving = false; error = it.message ?: "تعذر حفظ العملية" } } }, Modifier.weight(1f).semantics { contentDescription = "حفظ العملية" }) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") }
            OutlinedButton(enabled = !saving, onClick = { keyboard?.hide(); onDismiss() }, Modifier.weight(1f)) { Text("إلغاء") }
        }
    }
    if (calc.isOpen) Dialog(onDismissRequest = calc::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { Card(Modifier.fillMaxWidth(.92f).imePadding().navigationBarsPadding()) { CalculatorOverlay(expression = calc.expression, result = calc.result.orEmpty(), onKey = calc::press, onClear = calc::clear, onBackspace = calc::backspace, onDismiss = calc::close, onUseResult = calc::useResult) } }
}

@Composable
private fun V3TransferDialog(transaction: CustodyTransactionEntity, currentPersonId: Long, people: List<CustodyPersonEntity>, onDismiss: () -> Unit, onTransfer: suspend (Long, String) -> Unit) {
    var selected by remember { mutableStateOf<Long?>(null) }; var reason by remember { mutableStateOf("") }; var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope(); val candidates = people.filter { it.id != currentPersonId && !it.isArchived }
    V3DialogShell("نقل العملية", onDismiss, saving) {
        Text("العملية: ${v3Money(transaction.amountMinor)} ${transaction.currencyCode} — ${v3TransactionLabel(transaction.type, false, "")}")
        Text("الشخص الحالي: ${people.firstOrNull { it.id == currentPersonId }?.name.orEmpty()}")
        Text("الشخص الجديد", fontWeight = FontWeight.Bold)
        candidates.forEach { p -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = selected == p.id, onClick = { selected = p.id }, enabled = !saving); Text(p.name) } }
        V3Field(reason, { reason = it; error = null }, "سبب النقل", Modifier.fillMaxWidth(), singleLine = false, minLines = 3, enabled = !saving, contentDescriptionValue = "سبب النقل")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = selected != null && reason.trim().isNotBlank() && !saving, onClick = { saving = true; scope.launch { runCatching { onTransfer(selected!!, reason.trim()) }.onSuccess { saving = false; onDismiss() }.onFailure { saving = false; error = it.message ?: "تعذر نقل العملية" } } }, Modifier.weight(1f)) { Text(if (saving) "جارٍ النقل…" else "نقل العملية") }
            OutlinedButton(enabled = !saving, onClick = onDismiss, Modifier.weight(1f)) { Text("إلغاء") }
        }
    }
}

@Composable private fun V3PersonEditDialog(vm: CustodyViewModel, person: CustodyPersonEntity, onDismiss: () -> Unit, onSaved: () -> Unit) = V3SimpleEditDialog("تعديل بيانات الشخص", person.name, person.phone, person.address, person.notes, onDismiss, listOf("الاسم", "الهاتف", "العنوان", "الملاحظات")) { n,p,a,no -> vm.updatePersonAndWait(person.copy(name=n, phone=p, address=a, notes=no)); onSaved() }
@Composable private fun V3OwnerEditDialog(vm: CustodyViewModel, custody: CustodyEntity, onDismiss: () -> Unit, onSaved: () -> Unit) = V3SimpleEditDialog("تعديل بيانات حامل العهدة", custody.name, custody.phone, custody.address, custody.notes, onDismiss, listOf("اسم حامل العهدة", "الهاتف", "العنوان", "الملاحظات")) { n,p,a,no -> vm.updateCustodyAndWait(custody.copy(name=n, phone=p, address=a, notes=no)); onSaved() }
@Composable private fun V3CustodyEditDialog(vm: CustodyViewModel, custody: CustodyEntity, onDismiss: () -> Unit, onSaved: () -> Unit) = V3SimpleEditDialog("تعديل بيانات العهدة والجهة", custody.organizationName, custody.organizationPhone, custody.organizationAddress, custody.organizationNotes, onDismiss, listOf("اسم الجهة", "هاتف الجهة", "عنوان الجهة", "ملاحظات الجهة")) { n,p,a,no -> vm.updateCustodyAndWait(custody.copy(organizationName=n, organizationPhone=p, organizationAddress=a, organizationNotes=no)); onSaved() }

@Composable
private fun V3SimpleEditDialog(title: String, n0: String, p0: String, a0: String, no0: String, onDismiss: () -> Unit, labels: List<String>, onSave: suspend (String,String,String,String) -> Unit) {
    var name by remember { mutableStateOf(n0) }; var phone by remember { mutableStateOf(p0) }; var address by remember { mutableStateOf(a0) }; var notes by remember { mutableStateOf(no0) }; var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    V3DialogShell(title, onDismiss, saving) {
        V3Field(name, { name = it }, labels[0], Modifier.fillMaxWidth(), enabled = !saving)
        V3Field(phone, { phone = it }, labels[1], Modifier.fillMaxWidth(), KeyboardType.Phone, enabled = !saving)
        V3Field(address, { address = it }, labels[2], Modifier.fillMaxWidth(), enabled = !saving)
        V3Field(notes, { notes = it }, labels[3], Modifier.fillMaxWidth(), singleLine = false, minLines = 3, enabled = !saving)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = name.trim().isNotBlank() && !saving, onClick = { saving = true; scope.launch { runCatching { onSave(name.trim(), phone.trim(), address.trim(), notes.trim()) }.onSuccess { saving = false }.onFailure { saving = false; error = it.message ?: "تعذر حفظ التعديل" } } }, Modifier.weight(1f)) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") }
            OutlinedButton(enabled = !saving, onClick = onDismiss, Modifier.weight(1f)) { Text("إلغاء") }
        }
    }
}
