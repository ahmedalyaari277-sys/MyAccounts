@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myaccounts.app.ui.screens

import android.app.DatePickerDialog
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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

private val custodyV2Currencies = listOf("YER", "SAR", "USD")

private fun amount(v: Long): String = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun parseAmountV2(v: String): Long? = runCatching { BigDecimal(v.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }.getOrNull()
private fun signStatus(v: Long, positive: String, negative: String): String = when { v > 0 -> positive; v < 0 -> negative; else -> "متوازن" }
private fun custodyStatusV2(v: Long, owner: Boolean) = when { v > 0 -> if (owner) "متبقي لديه" else "لديه"; v < 0 -> if (owner) "عجز" else "مستحق له"; else -> "متوازن" }
private fun statusColor(v: Long) = when { v > 0 -> Due; v < 0 -> Owed; else -> androidx.compose.ui.graphics.Color.Unspecified }
private fun currencyLabel(code: String): String = when (code) { "YER" -> "ريال يمني"; "SAR" -> "ريال سعودي"; else -> "دولار" }

@Composable
private fun KeyboardAwareField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    singleLine: Boolean = !label.contains("ملاحظات") && !label.contains("سبب"),
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    isError: Boolean = false,
    contentDescriptionValue: String? = null
) {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) { if (focused) requester.bringIntoView() }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .bringIntoViewRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .then(if (contentDescriptionValue != null) Modifier.semantics { contentDescription = contentDescriptionValue } else Modifier),
        label = { Text(label) },
        minLines = minLines,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        enabled = enabled,
        isError = isError
    )
}

@Composable
fun CustodyOperationsScreenV2(
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
    var addPerson by remember { mutableStateOf(false) }
    var quick by remember { mutableStateOf<Pair<Boolean, Long?>?>(null) }
    var settlement by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(false) }
    var order by remember { mutableStateOf(PersonSortV2.LATEST) }
    val latestByPerson = remember(transactions) { transactions.groupBy { it.personId }.mapValues { (_, rows) -> rows.maxOfOrNull { it.transactionDate } ?: 0L } }
    val shownPeople = people
        .let { rows -> if (order == PersonSortV2.ALPHABETICAL) rows.sortedBy { it.name.trim().lowercase() } else rows.sortedByDescending { latestByPerson[it.id] ?: 0L } }
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { if (!current.isClosed) quick = false to null }, modifier = Modifier.semantics { contentDescription = "إضافة عملية" }) { Icon(Icons.Default.Add, null) }
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                CustodyOwnerCardV2(
                    name = current.name,
                    accounts = accounts,
                    transactions = transactions,
                    persons = people,
                    enabled = !current.isClosed,
                    onClick = onOwner,
                    onQuick = { quick = true to null }
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الأشخاص", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { sort = true }, modifier = Modifier.semantics { contentDescription = "ترتيب الأشخاص" }) { Icon(Icons.Default.MoreVert, null) }
                        TextButton(enabled = !current.isClosed, onClick = { addPerson = true }, modifier = Modifier.semantics { contentDescription = "إضافة شخص" }) { Text("إضافة شخص") }
                    }
                    DropdownMenu(expanded = sort, onDismissRequest = { sort = false }) {
                        DropdownMenuItem(text = { Text("حسب الأحدث") }, onClick = { order = PersonSortV2.LATEST; sort = false })
                        DropdownMenuItem(text = { Text("حسب الأبجدية") }, onClick = { order = PersonSortV2.ALPHABETICAL; sort = false })
                    }
                }
            }
            item {
                KeyboardAwareField(search, { search = it }, "بحث في الأشخاص", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Text, contentDescriptionValue = "بحث في الأشخاص")
            }
            items(shownPeople, key = { it.id }) { person ->
                CustodyPersonCardV2(person, transactions, !current.isClosed, { onPerson(person.id) }, { quick = false to person.id })
            }
            if (shownPeople.isEmpty()) item { Text(if (search.isBlank()) "لا يوجد أشخاص في هذه العهدة" else "لا توجد نتائج مطابقة", modifier = Modifier.padding(12.dp)) }
        }
    }

    if (addPerson) CustodyAddPersonDialogV2(custodyId, people, { addPerson = false }) { p -> vm.addPersonAndWait(custodyId, p) }
    quick?.let { (owner, personId) -> CustodyLedgerOperationDialogV2(vm, custodyId, personId, owner, "YER", if (owner) CustodyTransactionType.RECEIVED_FROM_ORG else CustodyTransactionType.PAID_TO_PERSON, null, { quick = null }, { quick = null }) }
    if (settlement) CustodySettlementDialogV2(vm, current, transactions, people) { settlement = false }
}

private enum class PersonSortV2 { LATEST, ALPHABETICAL }

@Composable
private fun CustodyOwnerCardV2(
    name: String,
    accounts: List<CustodyAccountEntity>,
    transactions: List<CustodyTransactionEntity>,
    persons: List<CustodyPersonEntity>,
    enabled: Boolean,
    onClick: () -> Unit,
    onQuick: () -> Unit
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = enabled, onClick = onQuick, modifier = Modifier.semantics { contentDescription = "إضافة عملية سريعة لـ $name" }) { Icon(Icons.Default.Add, null) }
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("حامل العهدة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("العملة", Modifier.weight(.8f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Text("العهدة", Modifier.weight(1.25f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Text("ذمة الجهة", Modifier.weight(1.25f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Text("ذمم الأشخاص", Modifier.weight(1.25f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
            custodyV2Currencies.forEach { code ->
                val s = CustodyFinancialSummary.ownerDisplay(transactions, accounts, persons, code)
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(code, Modifier.weight(.8f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    CompactMetricV2(amount(kotlin.math.abs(s.custodyMinor)), custodyStatusV2(s.custodyMinor, true), Modifier.weight(1.25f))
                    CompactMetricV2(amount(kotlin.math.abs(s.organizationDebtMinor)), signStatus(s.organizationDebtMinor, "مستحق له", "مستحق عليه"), Modifier.weight(1.25f))
                    CompactMetricV2(amount(kotlin.math.abs(s.peopleDebtMinor)), signStatus(s.peopleDebtMinor, "له على الأشخاص", "عليه للأشخاص"), Modifier.weight(1.25f))
                }
            }
        }
    }
}

@Composable
private fun CustodyPersonCardV2(person: CustodyPersonEntity, transactions: List<CustodyTransactionEntity>, enabled: Boolean, onClick: () -> Unit, onQuick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = enabled, onClick = onQuick, modifier = Modifier.semantics { contentDescription = "إضافة عملية سريعة لـ ${person.name}" }) { Icon(Icons.Default.Add, null) }
                Column(Modifier.weight(1f)) {
                    Text(person.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (person.phone.isNotBlank()) Text(person.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("العملة", Modifier.weight(.9f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Text("العهدة", Modifier.weight(1.3f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Text("الذمة", Modifier.weight(1.3f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
            custodyV2Currencies.forEach { code ->
                val balance = CustodyFinancialSummary.personCustodyBalance(transactions, person.id, code)
                val debt = CustodyFinancialSummary.personDebt(transactions, person.id, code)
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(code, Modifier.weight(.9f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    CompactMetricV2(amount(kotlin.math.abs(balance)), signStatus(balance, "لديه", "مستحق له"), Modifier.weight(1.3f))
                    CompactMetricV2(amount(kotlin.math.abs(debt)), signStatus(debt, "مستحق له", "مستحق عليه"), Modifier.weight(1.3f))
                }
            }
        }
    }
}

@Composable
private fun CompactMetricV2(value: String, status: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun CustodyAddPersonDialogV2(custodyId: Long, existing: List<CustodyPersonEntity>, onDismiss: () -> Unit, onSave: suspend (CustodyPersonEntity) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val matches = existing.filter { name.isNotBlank() && it.name.contains(name.trim(), true) }.take(5)
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(Modifier.fillMaxWidth(.94f).imePadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("إضافة شخص", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                KeyboardAwareField(name, { name = it; error = null }, "الاسم", Modifier.fillMaxWidth(), enabled = !saving, contentDescriptionValue = "الاسم")
                matches.forEach { p -> TextButton(onClick = { name = p.name; phone = p.phone; address = p.address; notes = p.notes }, enabled = !saving) { Text(p.name) } }
                KeyboardAwareField(phone, { phone = it }, "الهاتف", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Phone, enabled = !saving, contentDescriptionValue = "الهاتف")
                KeyboardAwareField(address, { address = it }, "العنوان", Modifier.fillMaxWidth(), enabled = !saving, contentDescriptionValue = "العنوان")
                KeyboardAwareField(notes, { notes = it }, "الملاحظات", Modifier.fillMaxWidth(), minLines = 2, singleLine = false, enabled = !saving, contentDescriptionValue = "الملاحظات")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = name.isNotBlank() && !saving, onClick = {
                        saving = true
                        scope.launch { runCatching { onSave(CustodyPersonEntity(custodyId = custodyId, name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim())) }.onSuccess { saving = false; onDismiss() }.onFailure { saving = false; error = it.message ?: "تعذر حفظ الشخص" } }
                    }, Modifier.weight(1f).semantics { contentDescription = "حفظ الشخص" }) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") }
                    OutlinedButton(enabled = !saving, onClick = onDismiss, Modifier.weight(1f)) { Text("إلغاء") }
                }
            }
        }
    }
}

@Composable
private fun CustodySettlementDialogV2(vm: CustodyViewModel, custody: CustodyEntity, transactions: List<CustodyTransactionEntity>, persons: List<CustodyPersonEntity>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val books = remember(transactions) { custodyV2Currencies.map { CustodyFinancialSummary.custodyOwnerBalance(transactions, it) } }
    var yer by remember { mutableStateOf(initialActual(books[0])) }
    var sar by remember { mutableStateOf(initialActual(books[1])) }
    var usd by remember { mutableStateOf(initialActual(books[2])) }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val actuals = listOf(parseAmountV2(yer), parseAmountV2(sar), parseAmountV2(usd))
    val orgDebts = custodyV2Currencies.map { CustodyFinancialSummary.ownerOrganizationDebt(transactions, it) }
    val peopleDebts = custodyV2Currencies.map { CustodyFinancialSummary.ownerPeopleDebt(transactions, it) }
    val deficits = books.indices.map { i -> maxOf(books[i] - (actuals[i] ?: 0L), 0L) }
    val surpluses = books.indices.map { i -> maxOf((actuals[i] ?: 0L) - books[i], 0L) }
    val hasDifference = deficits.any { it != 0L } || surpluses.any { it != 0L }
    val complete = actuals.all { it != null }
    val orgSettled = orgDebts.all { it == 0L }
    val notesValid = !hasDifference || notes.trim().isNotBlank()
    val eligible = complete && orgSettled && notesValid && !saving

    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(Modifier.fillMaxWidth(.95f).imePadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("إغلاق وتسوية العهدة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("الأرقام المحاسبية والفروقات تُحسب تلقائيًا. المطلوب إدخال الموجود الفعلي فقط، وتُكتب أسباب العجز أو الفائض في الملاحظات.", style = MaterialTheme.typography.bodySmall)
                custodyV2Currencies.forEachIndexed { i, code ->
                    SettlementCurrencyCardV2(
                        code = code,
                        book = books[i],
                        orgDebt = orgDebts[i],
                        peopleDebt = peopleDebts[i],
                        actual = when (code) { "YER" -> yer; "SAR" -> sar; else -> usd },
                        onActualChange = { when (code) { "YER" -> yer = it; "SAR" -> sar = it; else -> usd = it }; error = null },
                        enabled = !saving
                    )
                }
                KeyboardAwareField(notes, { notes = it; error = null }, "ملاحظات التسوية / أسباب العجز أو الفائض", Modifier.fillMaxWidth(), minLines = 3, singleLine = false, enabled = !saving, contentDescriptionValue = "ملاحظات التسوية")
                if (!complete) Text("أدخل الموجود الفعلي لكل عملة.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                if (!orgSettled) Text("لا يمكن الإغلاق قبل تسوية ذمة حامل العهدة مع الجهة.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                if (hasDifference && notes.trim().isBlank()) Text("وجود عجز أو فائض يتطلب كتابة السبب في الملاحظات.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = eligible, onClick = {
                        val y = actuals[0] ?: return@Button; val s = actuals[1] ?: return@Button; val u = actuals[2] ?: return@Button
                        saving = true
                        scope.launch { runCatching { vm.closeCustodyAndWait(custody.id, y, s, u, notes.trim()) }.onSuccess { saving = false; onDismiss() }.onFailure { saving = false; error = it.message ?: "تعذر إغلاق العهدة" } }
                    }, Modifier.weight(1f)) { Text(if (saving) "جارٍ الحفظ…" else "إغلاق وتسوية") }
                    OutlinedButton(enabled = !saving, onClick = onDismiss, Modifier.weight(1f)) { Text("إلغاء") }
                }
            }
        }
    }
}

private fun initialActual(book: Long): String = if (book >= 0L) amount(book) else "0"

@Composable
private fun SettlementCurrencyCardV2(
    code: String,
    book: Long,
    orgDebt: Long,
    peopleDebt: Long,
    actual: String,
    onActualChange: (String) -> Unit,
    enabled: Boolean
) {
    val actualMinor = parseAmountV2(actual)
    val diff = actualMinor?.minus(book)
    val deficit = if (diff != null) maxOf(-diff, 0L) else 0L
    val surplus = if (diff != null) maxOf(diff, 0L) else 0L
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(currencyLabel(code), fontWeight = FontWeight.Bold)
                Text(code, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            AutoLineV2("الرصيد الدفتري", amount(kotlin.math.abs(book)), if (book >= 0) "متبقي" else "عجز")
            KeyboardAwareField(actual, onActualChange, "الموجود الفعلي", Modifier.fillMaxWidth().semantics { contentDescription = "الموجود الفعلي $code" }, keyboardType = KeyboardType.Decimal, enabled = enabled, isError = actualMinor == null, contentDescriptionValue = "الموجود الفعلي $code")
            if (actualMinor != null) {
                AutoLineV2("الفارق", amount(kotlin.math.abs(diff ?: 0L)), when { diff!! > 0 -> "فائض"; diff < 0 -> "عجز"; else -> "متوازن" })
                AutoLineV2("العجز", amount(deficit), if (deficit > 0) "يحتاج معالجة" else "لا يوجد")
                AutoLineV2("الفائض", amount(surplus), if (surplus > 0) "فائض" else "لا يوجد")
            }
            AutoLineV2("ذمة الجهة", amount(kotlin.math.abs(orgDebt)), signStatus(orgDebt, "مستحق له", "مستحق عليه"))
            AutoLineV2("ذمم الأشخاص", amount(kotlin.math.abs(peopleDebt)), signStatus(peopleDebt, "له على الأشخاص", "عليه للأشخاص"))
        }
    }
}

@Composable
private fun AutoLineV2(title: String, value: String, status: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { Text(value, fontWeight = FontWeight.Bold); Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun CustodyOwnerLedgerScreenV2(vm: CustodyViewModel, custodyId: Long, onBack: () -> Unit) = CustodyLedgerScreenV2(vm, custodyId, null, onBack)

@Composable
fun CustodyPersonLedgerScreenV2(vm: CustodyViewModel, custodyId: Long, personId: Long, onBack: () -> Unit) = CustodyLedgerScreenV2(vm, custodyId, personId, onBack)

@Composable
private fun CustodyLedgerScreenV2(vm: CustodyViewModel, custodyId: Long, personId: Long?, onBack: () -> Unit) {
    val custody by vm.custody(custodyId).collectAsState()
    val people by vm.persons(custodyId).collectAsState()
    val accounts by vm.accounts(custodyId).collectAsState()
    val transactions by vm.transactions(custodyId).collectAsState()
    val current = custody ?: return
    val owner = personId == null
    val person = people.firstOrNull { it.id == personId }
    if (!owner && person == null) return
    val title = if (owner) current.name else person!!.name
    val ownerAccountIds = accounts.filter { it.holderType == "OWNER" && it.personId == null }.map { it.id }.toSet()
    var currency by remember { mutableStateOf("YER") }
    var add by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    var deleting by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    var transferring by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    var menu by remember { mutableStateOf(false) }
    var editPerson by remember { mutableStateOf(false) }
    var deletePerson by remember { mutableStateOf(false) }
    var editOwner by remember { mutableStateOf(false) }
    var editCustody by remember { mutableStateOf(false) }
    val rows = transactions.filter { t -> t.currencyCode == currency && if (owner) t.accountId in ownerAccountIds else t.personId == personId }.sortedByDescending { it.transactionDate }
    val balance = if (owner) accounts.firstOrNull { it.holderType == "OWNER" && it.personId == null && it.currencyCode == currency }?.balanceMinor ?: 0L else CustodyFinancialSummary.personCustodyBalance(transactions, personId!!, currency)
    val debt = if (owner) CustodyFinancialSummary.ownerPeopleDebt(transactions, currency) else CustodyFinancialSummary.personDebt(transactions, personId!!, currency)
    val orgDebt = if (owner) CustodyFinancialSummary.ownerOrganizationDebt(transactions, currency) else 0L
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = {
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
                }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { if (!current.isClosed) add = true }, modifier = Modifier.semantics { contentDescription = "إضافة عملية" }) { Icon(Icons.Default.Add, null) } }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { custodyV2Currencies.forEach { c -> FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c, fontWeight = FontWeight.Bold) }, modifier = Modifier.weight(1f)) } }
            Spacer(Modifier.height(7.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(currency, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    CompactBalanceLineV2("العهدة", balance, custodyStatusV2(balance, owner))
                    if (owner) CompactBalanceLineV2("ذمة الجهة", orgDebt, signStatus(orgDebt, "مستحق له", "مستحق عليه"))
                    CompactBalanceLineV2(if (owner) "ذمم الأشخاص" else "الذمة", debt, signStatus(debt, if (owner) "له على الأشخاص" else "مستحق له", if (owner) "عليه للأشخاص" else "مستحق عليه"))
                }
            }
            Spacer(Modifier.height(7.dp))
            if (rows.isEmpty()) Text("لا توجد عمليات لهذه العملة", modifier = Modifier.padding(12.dp))
            else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(rows, key = { it.id }) { t ->
                    val personName = t.personId?.let { id -> people.firstOrNull { it.id == id }?.name }.orEmpty()
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(transactionLabelV2(t.type, owner, personName), fontWeight = FontWeight.Bold)
                                Text(SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(t.transactionDate)), style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${amount(t.amountMinor)} $currency", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (!owner && !current.isClosed) IconButton(onClick = { transferring = t }, modifier = Modifier.semantics { contentDescription = "نقل العملية" }) { Icon(Icons.Default.SwapHoriz, "نقل العملية") }
                                IconButton(enabled = !current.isClosed, onClick = { editing = t }, modifier = Modifier.semantics { contentDescription = "تعديل" }) { Icon(Icons.Default.Edit, "تعديل") }
                                IconButton(enabled = !current.isClosed, onClick = { deleting = t }, modifier = Modifier.semantics { contentDescription = "حذف" }) { Icon(Icons.Default.Delete, "حذف") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (add) CustodyLedgerOperationDialogV2(vm, custodyId, personId, owner, currency, if (owner) CustodyTransactionType.RECEIVED_FROM_ORG else CustodyTransactionType.PAID_TO_PERSON, null, { add = false }, { add = false })
    editing?.let { t -> CustodyLedgerOperationDialogV2(vm, custodyId, if (owner) t.personId else personId, owner, t.currencyCode, t.type, t, { editing = null }, { editing = null }) }
    deleting?.let { t -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("حذف العملية") }, text = { Text("سيتم حذف العملية نهائيًا.") }, confirmButton = { TextButton(onClick = { vm.deleteTransaction(t.id); deleting = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } }) }
    transferring?.let { t -> CustodyTransferDialogV2(t, personId!!, people, { transferring = null }) { newId, reason -> vm.transferTransactionAndWait(t.id, newId, reason) } }
    if (!owner && editPerson) CustodyPersonEditDialogV2(vm, person!!, { editPerson = false }) { editPerson = false }
    if (!owner && deletePerson) AlertDialog(onDismissRequest = { deletePerson = false }, title = { Text("حذف الشخص وعملياته") }, text = { Text("سيتم حذف بيانات الشخص وحساباته وجميع عملياته ومرفقاتها نهائيًا. هل تريد المتابعة؟") }, confirmButton = { TextButton(onClick = { deletePerson = false; scope.launch { runCatching { vm.deletePersonAndWait(person!!.id) }.onSuccess { onBack() } } }) { Text("حذف", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deletePerson = false }) { Text("إلغاء") } })
    if (owner && editOwner) CustodyOwnerEditDialogV2(vm, current, { editOwner = false }) { editOwner = false }
    if (owner && editCustody) CustodyDataEditDialogV2(vm, current, { editCustody = false }) { editCustody = false }
}

@Composable
private fun CompactBalanceLineV2(title: String, value: Long, status: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(amount(kotlin.math.abs(value)), fontWeight = FontWeight.Bold, color = statusColor(value)); Text(status, style = MaterialTheme.typography.bodySmall, color = statusColor(value)) }
    }
}

private fun transactionLabelV2(type: String, owner: Boolean, personName: String): String = when (type) {
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
private fun CustodyLedgerOperationDialogV2(vm: CustodyViewModel, custodyId: Long, personId: Long?, owner: Boolean, defaultCurrency: String, initialType: String, transaction: CustodyTransactionEntity?, onDismiss: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val calc = LocalCalculatorController.current
    val scope = rememberCoroutineScope()
    var currency by remember(transaction?.id) { mutableStateOf(transaction?.currencyCode ?: defaultCurrency) }
    var type by remember(transaction?.id) { mutableStateOf(transaction?.type ?: initialType) }
    var amountText by remember(transaction?.id) { mutableStateOf(transaction?.let { amount(it.amountMinor) } ?: "") }
    var details by remember(transaction?.id) { mutableStateOf(transaction?.description ?: "") }
    var date by remember(transaction?.id) { mutableStateOf(transaction?.transactionDate ?: System.currentTimeMillis()) }
    var saving by remember(transaction?.id) { mutableStateOf(false) }
    var error by remember(transaction?.id) { mutableStateOf<String?>(null) }
    var newAttachments by remember(transaction?.id) { mutableStateOf<List<TransactionAttachmentStorage.SelectedAttachment>>(emptyList()) }
    var deleted by remember(transaction?.id) { mutableStateOf<List<CustodyTransactionAttachmentEntity>>(emptyList()) }
    val existing = remember(transaction?.id) { transaction?.let { vm.attachments(it.id) } ?: emptyList() }
    val visibleExisting = existing.filter { a -> deleted.none { it.id == a.id } }
    DisposableEffect(calc, transaction?.id) { calc.setResultConsumer { amountText = it; error = null }; onDispose { calc.setResultConsumer(null) } }
    val kinds = if (owner) listOf(CustodyTransactionType.RECEIVED_FROM_ORG, CustodyTransactionType.RETURNED_TO_ORG, CustodyTransactionType.ORG_LOAN_FROM_OWNER, CustodyTransactionType.ORG_LOAN_REPAYMENT) else listOf(CustodyTransactionType.PAID_TO_PERSON, CustodyTransactionType.RETURNED_FROM_PERSON, CustodyTransactionType.PERSON_LOAN_TO_OWNER, CustodyTransactionType.OWNER_REPAY_PERSON_LOAN)
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(Modifier.fillMaxWidth(.95f).imePadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(if (transaction == null) "إضافة عملية" else "تعديل العملية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyboardAwareField(amountText, { amountText = it; error = null }, "المبلغ", Modifier.weight(1.25f), keyboardType = KeyboardType.Decimal, enabled = !saving, contentDescriptionValue = "المبلغ")
                    OutlinedTextField(SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(date)), {}, Modifier.weight(1f), label = { Text("التاريخ") }, readOnly = true, singleLine = true, trailingIcon = { IconButton(enabled = !saving, onClick = { val d = Calendar.getInstance().apply { timeInMillis = date }; DatePickerDialog(context, { _, y, m, day -> d.set(y, m, day, 12, 0, 0); d.set(Calendar.MILLISECOND, 0); date = d.timeInMillis }, d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH)).show() }) { Icon(Icons.Default.CalendarToday, "اختيار التاريخ") } })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { custodyV2Currencies.forEach { c -> FilterChip(selected = currency == c, onClick = { if (!saving) currency = c }, label = { Text(c) }, modifier = Modifier.weight(1f)) } }
                KeyboardAwareField(details, { details = it }, "التفاصيل", Modifier.fillMaxWidth(), minLines = 2, singleLine = false, enabled = !saving, contentDescriptionValue = "التفاصيل")
                Text("نوع العملية", fontWeight = FontWeight.Bold)
                kinds.forEach { kind -> FilterChip(selected = type == kind, onClick = { if (!saving) type = kind }, label = { Text(transactionLabelV2(kind, owner, "")) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = transactionLabelV2(kind, owner, "") }) }
                if (transaction != null && visibleExisting.isNotEmpty()) {
                    Text("المرفقات الحالية: ${visibleExisting.size}", fontWeight = FontWeight.Bold)
                    visibleExisting.forEach { a -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AttachFile, null); Text(a.fileName, Modifier.weight(1f), maxLines = 1); IconButton(enabled = !saving, onClick = { deleted = deleted + a }) { Icon(Icons.Default.Delete, "حذف المرفق") } } }
                }
                TransactionAttachmentPicker(selectedAttachments = newAttachments, onAttachmentsChanged = { if (!saving) newAttachments = it })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !saving, onClick = {
                        val parsed = parseAmountV2(amountText)
                        if (parsed == null || parsed <= 0L) { error = "أدخل مبلغًا صحيحًا أكبر من صفر."; return@Button }
                        saving = true
                        val selected = newAttachments.map { CustodyAttachmentStorage.Selected(it.uri, it.fileName, it.mimeType) }
                        scope.launch { runCatching { if (transaction == null) vm.addTransactionAndWait(custodyId, currency, type, personId, parsed, details.trim(), date, selected) else vm.updateTransactionAndWait(transaction.id, currency, type, personId, parsed, details.trim(), date, selected, deleted) }.onSuccess { keyboard?.hide(); calc.close(); saving = false; onFinished() }.onFailure { saving = false; error = it.message ?: "تعذر حفظ العملية" } }
                    }, Modifier.weight(1f).semantics { contentDescription = "حفظ العملية" }) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") }
                    OutlinedButton(enabled = !saving, onClick = { keyboard?.hide(); onDismiss() }, Modifier.weight(1f)) { Text("إلغاء") }
                }
            }
        }
    }
    if (calc.isOpen) Dialog(onDismissRequest = calc::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { Card(Modifier.fillMaxWidth(.92f).imePadding()) { CalculatorOverlay(expression = calc.expression, result = calc.result.orEmpty(), onKey = calc::press, onClear = calc::clear, onBackspace = calc::backspace, onDismiss = calc::close, onUseResult = calc::useResult) } }
}

@Composable
private fun CustodyTransferDialogV2(transaction: CustodyTransactionEntity, currentPersonId: Long, people: List<CustodyPersonEntity>, onDismiss: () -> Unit, onTransfer: suspend (Long, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Long?>(null) }
    var reason by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val candidates = people.filter { it.id != currentPersonId && !it.isArchived }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(Modifier.fillMaxWidth(.94f).imePadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("نقل العملية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("العملية الحالية: ${amount(transaction.amountMinor)} ${transaction.currencyCode} — ${transactionLabelV2(transaction.type, false, "")}")
                Text("الشخص الحالي: ${people.firstOrNull { it.id == currentPersonId }?.name.orEmpty()}")
                Text("اختر الشخص الجديد", fontWeight = FontWeight.Bold)
                candidates.forEach { p -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = selected == p.id, onClick = { selected = p.id }, enabled = !saving); Text(p.name) } }
                KeyboardAwareField(reason, { reason = it; error = null }, "سبب النقل", Modifier.fillMaxWidth(), minLines = 3, singleLine = false, enabled = !saving, contentDescriptionValue = "سبب النقل")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = selected != null && reason.trim().isNotBlank() && !saving, onClick = { saving = true; scope.launch { runCatching { onTransfer(selected!!, reason.trim()) }.onSuccess { saving = false; onDismiss() }.onFailure { saving = false; error = it.message ?: "تعذر نقل العملية" } } }, Modifier.weight(1f)) { Text(if (saving) "جارٍ النقل…" else "نقل العملية") }
                    OutlinedButton(enabled = !saving, onClick = onDismiss, Modifier.weight(1f)) { Text("إلغاء") }
                }
            }
        }
    }
}

@Composable
private fun CustodyPersonEditDialogV2(vm: CustodyViewModel, person: CustodyPersonEntity, onDismiss: () -> Unit, onSaved: () -> Unit) {
    EditPersonLikeDialogV2("تعديل بيانات الشخص", person.name, person.phone, person.address, person.notes, onDismiss) { n, p, a, no -> vm.updatePersonAndWait(person.copy(name = n, phone = p, address = a, notes = no)); onSaved() }
}

@Composable
private fun CustodyOwnerEditDialogV2(vm: CustodyViewModel, custody: CustodyEntity, onDismiss: () -> Unit, onSaved: () -> Unit) {
    EditPersonLikeDialogV2("تعديل بيانات حامل العهدة", custody.name, custody.phone, custody.address, custody.notes, onDismiss) { n, p, a, no -> vm.updateCustodyAndWait(custody.copy(name = n, phone = p, address = a, notes = no)); onSaved() }
}

@Composable
private fun CustodyDataEditDialogV2(vm: CustodyViewModel, custody: CustodyEntity, onDismiss: () -> Unit, onSaved: () -> Unit) {
    EditPersonLikeDialogV2("تعديل بيانات العهدة والجهة", custody.organizationName, custody.organizationPhone, custody.organizationAddress, custody.organizationNotes, onDismiss, labels = listOf("اسم الجهة", "هاتف الجهة", "عنوان الجهة", "ملاحظات الجهة")) { n, p, a, no -> vm.updateCustodyAndWait(custody.copy(organizationName = n, organizationPhone = p, organizationAddress = a, organizationNotes = no)); onSaved() }
}

@Composable
private fun EditPersonLikeDialogV2(title: String, initialName: String, initialPhone: String, initialAddress: String, initialNotes: String, onDismiss: () -> Unit, labels: List<String> = listOf("الاسم", "الهاتف", "العنوان", "الملاحظات"), onSave: suspend (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var address by remember { mutableStateOf(initialAddress) }
    var notes by remember { mutableStateOf(initialNotes) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(Modifier.fillMaxWidth(.94f).imePadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                KeyboardAwareField(name, { name = it }, labels[0], Modifier.fillMaxWidth(), enabled = !saving, contentDescriptionValue = labels[0])
                KeyboardAwareField(phone, { phone = it }, labels[1], Modifier.fillMaxWidth(), keyboardType = KeyboardType.Phone, enabled = !saving, contentDescriptionValue = labels[1])
                KeyboardAwareField(address, { address = it }, labels[2], Modifier.fillMaxWidth(), enabled = !saving, contentDescriptionValue = labels[2])
                KeyboardAwareField(notes, { notes = it }, labels[3], Modifier.fillMaxWidth(), minLines = 3, singleLine = false, enabled = !saving, contentDescriptionValue = labels[3])
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = name.trim().isNotBlank() && !saving, onClick = { saving = true; scope.launch { runCatching { onSave(name.trim(), phone.trim(), address.trim(), notes.trim()) }.onSuccess { saving = false }.onFailure { saving = false; error = it.message ?: "تعذر حفظ التعديل" } } }, Modifier.weight(1f)) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") }
                    OutlinedButton(enabled = !saving, onClick = onDismiss, Modifier.weight(1f)) { Text("إلغاء") }
                }
            }
        }
    }
}
