package com.myaccounts.app.ui.screens

import android.app.DatePickerDialog
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myaccounts.app.data.custody.*
import com.myaccounts.app.ui.components.BalanceAmount
import com.myaccounts.app.ui.components.BalanceStatus
import com.myaccounts.app.ui.components.CalculatorButton
import com.myaccounts.app.ui.components.CalculatorOverlay
import com.myaccounts.app.ui.components.CustodyOperationCard
import com.myaccounts.app.ui.components.CustodyOperationTone
import com.myaccounts.app.ui.components.CurrencyChip
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.LocalCalculatorController
import com.myaccounts.app.ui.components.PrimaryButton
import com.myaccounts.app.ui.components.SecondaryButton
import com.myaccounts.app.ui.components.SummaryCard
import com.myaccounts.app.ui.components.StatusChip
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

private val custodyCurrencies = listOf("YER", "SAR", "USD")
private fun money(v: Long) = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun parseAmount(v: String): Long? = runCatching { BigDecimal(v.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }.getOrNull()
private fun typeName(t: String) = when (t) {
    CustodyTransactionType.RECEIVED_FROM_ORG -> "استلام من الجهة"
    CustodyTransactionType.PAID_TO_PERSON -> "صرف للشخص"
    CustodyTransactionType.RETURNED_FROM_PERSON -> "مرتجع من الشخص"
    CustodyTransactionType.RETURNED_TO_ORG -> "مرتجع للجهة / تصفية"
    else -> t
}
private fun signed(v: Long) = when { v > 0 -> "عليه ${money(v)}"; v < 0 -> "له ${money(-v)}"; else -> "متوازن 0" }
private fun balanceStatus(v: Long) = when { v > 0 -> BalanceStatus.Due; v < 0 -> BalanceStatus.Owed; else -> BalanceStatus.Neutral }
private fun custodyTone(type: String) = when (type) {
    CustodyTransactionType.RECEIVED_FROM_ORG -> CustodyOperationTone.ReceiveFromOrganization
    CustodyTransactionType.PAID_TO_PERSON -> CustodyOperationTone.PayToPerson
    CustodyTransactionType.RETURNED_FROM_PERSON -> CustodyOperationTone.ReturnFromPerson
    CustodyTransactionType.RETURNED_TO_ORG -> CustodyOperationTone.ReturnToOrganization
    else -> CustodyOperationTone.Neutral
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyOperationsScreen(vm: CustodyViewModel, custodyId: Long, onBack: () -> Unit, onPerson: (Long) -> Unit) {
    val custody by vm.custody(custodyId).collectAsState()
    val people by vm.persons(custodyId).collectAsState()
    val accounts by vm.accounts(custodyId).collectAsState()
    val transactions by vm.transactions(custodyId).collectAsState()
    val current = custody ?: return
    var currency by remember { mutableStateOf("YER") }
    var dialogType by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    var deleting by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    var addPerson by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    var personSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.semantics { contentDescription = "شاشة تفاصيل العهدة" },
        topBar = {
            TopAppBar(
                title = { Text(current.name, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "المزيد") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("تقرير العهدة") }, onClick = { menu = false; report = true })
                        DropdownMenuItem(text = { Text("أرشفة العهدة") }, onClick = { menu = false; vm.archive(custodyId); onBack() })
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SummaryCard(title = "ملخص العهدة") {
                    Text(current.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("الجهة: ${current.organizationName}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        custodyCurrencies.forEach { code ->
                            val b = accounts.firstOrNull { it.holderType == "OWNER" && it.personId == null && it.currencyCode == code }?.balanceMinor ?: 0L
                            InformationCard(modifier = Modifier.weight(1f)) {
                                StatusChip(code, MaterialTheme.colorScheme.primary)
                                BalanceAmount(
                                    amount = signed(b),
                                    status = balanceStatus(b),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    custodyCurrencies.forEach { code ->
                        CurrencyChip(currency = code, selected = currency == code, onClick = { currency = code })
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = "استلام من الجهة",
                        onClick = { dialogType = CustodyTransactionType.RECEIVED_FROM_ORG },
                        modifier = Modifier.weight(1f).semantics { contentDescription = "استلام من الجهة" }
                    )
                    SecondaryButton(
                        text = "مرتجع للجهة / تصفية",
                        onClick = { dialogType = CustodyTransactionType.RETURNED_TO_ORG },
                        modifier = Modifier.weight(1f).semantics { contentDescription = "مرتجع للجهة / تصفية" }
                    )
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الأشخاص", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    SecondaryButton(text = "إضافة شخص", onClick = { addPerson = true }, modifier = Modifier.semantics { contentDescription = "إضافة شخص" })
                }
            }
            if (people.isEmpty()) {
                item {
                    InformationCard {
                        Text("لا يوجد أشخاص في هذه العهدة بعد.", style = MaterialTheme.typography.bodyLarge)
                        Text("أضف شخصًا لبدء تسجيل عمليات الصرف والمرتجعات الخاصة به.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(people, key = { it.id }) { person ->
                val b = transactions.filter { it.personId == person.id && it.currencyCode == currency }.sumOf { CustodyBalanceRules.personDelta(it.type, it.amountMinor) }
                InformationCard(modifier = Modifier.fillMaxWidth().clickable { onPerson(person.id) }) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(person.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (person.phone.isNotBlank()) Text(person.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        BalanceAmount(amount = signed(b), status = balanceStatus(b), label = currency)
                    }
                }
            }
            item { Text("العمليات — $currency", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            val visibleTransactions = transactions.filter { it.currencyCode == currency }.sortedByDescending { it.transactionDate }
            if (visibleTransactions.isEmpty()) {
                item {
                    InformationCard {
                        Text("لا توجد عمليات بهذه العملة حتى الآن.", style = MaterialTheme.typography.bodyLarge)
                        Text("استخدم أزرار العمليات أعلاه لإضافة أول عملية.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(visibleTransactions, key = { it.id }) { t ->
                CustodyOperationCard(
                    operationType = typeName(t.type),
                    amount = "${money(t.amountMinor)} $currency",
                    currency = currency,
                    date = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(t.transactionDate)),
                    description = t.description.takeIf { it.isNotBlank() },
                    tone = custodyTone(t.type),
                    actions = {
                        IconButton(onClick = { editing = t }, modifier = Modifier.semantics { contentDescription = "تعديل" }) { Icon(Icons.Default.Edit, "تعديل") }
                        IconButton(onClick = { deleting = t }, modifier = Modifier.semantics { contentDescription = "حذف" }) { Icon(Icons.Default.Delete, "حذف") }
                    }
                )
            }
        }
    }

    if (addPerson) CustodyPersonDialog(
        existing = people,
        onDismiss = { if (!personSaving) addPerson = false },
        onSave = { person ->
            scope.launch {
                personSaving = true
                runCatching { vm.addPersonAndWait(custodyId, person) }.onSuccess { addPerson = false }
                personSaving = false
            }
        }
    )
    dialogType?.let { type -> CustodyOperationDialog(vm, custodyId, people, currency, type, null, onDismiss = { dialogType = null }, onFinished = { dialogType = null }) }
    editing?.let { t -> CustodyOperationDialog(vm, custodyId, people, t.currencyCode, t.type, t, onDismiss = { editing = null }, onFinished = { editing = null }) }
    deleting?.let { t -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("حذف العملية") }, text = { Text("سيتم حذف العملية نهائيًا.") }, confirmButton = { TextButton(onClick = { vm.deleteTransaction(t.id); deleting = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } }) }
    if (report) CustodyReportDialog(current, accounts, transactions, onDismiss = { report = false })
}

@Composable
private fun AutomationMarker(label: String, focusRequester: FocusRequester? = null) {
    AndroidView(
        factory = { context ->
            View(context).apply {
                contentDescription = label
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                isFocusable = true
                if (focusRequester != null) setOnClickListener { focusRequester.requestFocus() }
            }
        },
        modifier = Modifier.size(1.dp)
    )
}

@Composable
private fun CustodyPersonDialog(existing: List<CustodyPersonEntity>, onDismiss: () -> Unit, onSave: suspend (CustodyPersonEntity) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val matches = existing.filter { name.isNotBlank() && it.name.contains(name.trim(), true) }.take(5)
    val nameFocus = remember { FocusRequester() }
    val phoneFocus = remember { FocusRequester() }
    val addressFocus = remember { FocusRequester() }
    val notesFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("إضافة شخص", modifier = Modifier.semantics { contentDescription = "حوار إضافة شخص" }) },
        text = { Column(modifier = Modifier.semantics { contentDescription = "حقول إضافة شخص" }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth()) { AutomationMarker("الاسم", nameFocus); OutlinedTextField(name, { name = it; error = false }, modifier = Modifier.weight(1f).focusRequester(nameFocus).semantics { contentDescription = "الاسم" }, label = { Text("الاسم") }, singleLine = true, enabled = !saving) }
            matches.forEach { s -> TextButton(onClick = { if (!saving) { name = s.name; phone = s.phone; address = s.address; notes = s.notes } }, modifier = Modifier.fillMaxWidth()) { Text(s.name) } }
            Row(Modifier.fillMaxWidth()) { AutomationMarker("الهاتف", phoneFocus); OutlinedTextField(phone, { phone = it }, modifier = Modifier.weight(1f).focusRequester(phoneFocus).semantics { contentDescription = "الهاتف" }, label = { Text("الهاتف") }, singleLine = true, enabled = !saving) }
            Row(Modifier.fillMaxWidth()) { AutomationMarker("العنوان", addressFocus); OutlinedTextField(address, { address = it }, modifier = Modifier.weight(1f).focusRequester(addressFocus).semantics { contentDescription = "العنوان" }, label = { Text("العنوان") }, singleLine = true, enabled = !saving) }
            Row(Modifier.fillMaxWidth()) { AutomationMarker("الملاحظات", notesFocus); OutlinedTextField(notes, { notes = it }, modifier = Modifier.weight(1f).focusRequester(notesFocus).semantics { contentDescription = "الملاحظات" }, label = { Text("الملاحظات") }, minLines = 2, enabled = !saving) }
            if (error) Text("تعذر حفظ الشخص. تحقق من البيانات وحاول مرة أخرى.", color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { Button(enabled = name.isNotBlank() && !saving, onClick = { val person = CustodyPersonEntity(custodyId = 0, name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim()); saving = true; error = false; scope.launch { runCatching { onSave(person) }.onFailure { saving = false; error = true } } }, modifier = Modifier.semantics { contentDescription = "حفظ الشخص" }) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun CustodyOperationDialog(vm: CustodyViewModel, custodyId: Long, people: List<CustodyPersonEntity>, defaultCurrency: String, initialType: String, transaction: CustodyTransactionEntity?, onDismiss: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current; val keyboard = LocalSoftwareKeyboardController.current; val calc = LocalCalculatorController.current; val scope = rememberCoroutineScope()
    var currency by remember(transaction?.id) { mutableStateOf(transaction?.currencyCode ?: defaultCurrency) }; var type by remember(transaction?.id) { mutableStateOf(transaction?.type ?: initialType) }; var personId by remember(transaction?.id) { mutableStateOf(transaction?.personId) }; var amount by remember(transaction?.id) { mutableStateOf(transaction?.let { money(it.amountMinor) } ?: "") }; var details by remember(transaction?.id) { mutableStateOf(transaction?.description ?: "") }; var date by remember(transaction?.id) { mutableStateOf(transaction?.transactionDate ?: System.currentTimeMillis()) }; var saving by remember(transaction?.id) { mutableStateOf(false) }; var error by remember(transaction?.id) { mutableStateOf(false) }; var attachments by remember(transaction?.id) { mutableStateOf<List<TransactionAttachmentStorage.SelectedAttachment>>(emptyList()) }; var deletedAttachments by remember(transaction?.id) { mutableStateOf<List<CustodyTransactionAttachmentEntity>>(emptyList()) }
    val needsPerson = type == CustodyTransactionType.PAID_TO_PERSON || type == CustodyTransactionType.RETURNED_FROM_PERSON
    val existing = remember(transaction?.id) { transaction?.let { vm.attachments(it.id) } ?: emptyList() }
    val visibleExisting = existing.filter { saved -> deletedAttachments.none { it.id == saved.id } }
    val amountFocus = remember(transaction?.id) { FocusRequester() }
    val detailsFocus = remember(transaction?.id) { FocusRequester() }
    DisposableEffect(calc, transaction?.id) { calc.setResultConsumer { amount = it; error = false }; onDispose { calc.setResultConsumer(null) } }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxWidth(.95f).semantics { contentDescription = "حوار العملية" }, shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AutomationMarker("حوار العملية")
                Text(if (transaction == null) "إضافة عملية" else "تعديل العملية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { contentDescription = "حوار العملية" })
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.weight(1.2f)) {
                        AutomationMarker("المبلغ للعملية", amountFocus)
                        OutlinedTextField(amount, { amount = it; error = false }, modifier = Modifier.fillMaxWidth().focusRequester(amountFocus).semantics { contentDescription = "المبلغ للعملية" }, label = { Text("المبلغ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = error, enabled = !saving, trailingIcon = { CalculatorButton(onClick = calc::open) })
                    }
                    OutlinedTextField(SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(date)), {}, modifier = Modifier.weight(1f).semantics { contentDescription = "تاريخ العملية" }, label = { Text("التاريخ") }, readOnly = true, enabled = !saving, singleLine = true, trailingIcon = { IconButton(enabled = !saving, onClick = { val d = Calendar.getInstance().apply { timeInMillis = date }; DatePickerDialog(context, { _, y, m, day -> d.set(y, m, day, 12, 0, 0); date = d.timeInMillis }, d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH)).show() }) { Icon(Icons.Default.CalendarToday, "التاريخ") } })
                }
                if (error) Text("تعذر حفظ العملية. تحقق من البيانات وحاول مرة أخرى.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth()) { AutomationMarker("بيان العملية", detailsFocus); OutlinedTextField(details, { details = it }, modifier = Modifier.weight(1f).focusRequester(detailsFocus).semantics { contentDescription = "بيان العملية" }, label = { Text("التفاصيل") }, singleLine = true, enabled = !saving) }
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { custodyCurrencies.forEach { code -> CurrencyChip(currency = code, selected = currency == code, onClick = { if (!saving) currency = code }) } }
                listOf(CustodyTransactionType.RECEIVED_FROM_ORG, CustodyTransactionType.PAID_TO_PERSON, CustodyTransactionType.RETURNED_FROM_PERSON, CustodyTransactionType.RETURNED_TO_ORG).forEach { k ->
                    val selectType = { if (!saving) { type = k; if (k == CustodyTransactionType.RECEIVED_FROM_ORG || k == CustodyTransactionType.RETURNED_TO_ORG) personId = null } }
                    Row(modifier = Modifier.fillMaxWidth().clickable(enabled = !saving) { selectType() }) { RadioButton(selected = type == k, onClick = { selectType() }); Text(typeName(k), modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge) }
                }
                if (needsPerson) people.forEach { p -> Row(modifier = Modifier.fillMaxWidth().clickable(enabled = !saving) { personId = p.id }) { RadioButton(selected = personId == p.id, onClick = { if (!saving) personId = p.id }); Text(p.name, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge) } }
                if (transaction != null && visibleExisting.isNotEmpty()) {
                    Text("المرفقات الحالية: ${visibleExisting.size}", style = MaterialTheme.typography.labelLarge)
                    visibleExisting.forEach { a -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(a.fileName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge); TextButton(enabled = !saving, onClick = { deletedAttachments = deletedAttachments + a }) { Text("حذف") } } }
                }
                TransactionAttachmentPicker(selectedAttachments = attachments, onAttachmentsChanged = { if (!saving) attachments = it })
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(text = if (saving) "جارٍ الحفظ…" else "حفظ", onClick = {
                        val m = parseAmount(amount); if (m == null || m <= 0 || (needsPerson && personId == null)) { error = true; return@PrimaryButton }
                        val selected = attachments.map { CustodyAttachmentStorage.Selected(it.uri, it.fileName, it.mimeType) }; saving = true
                        scope.launch { runCatching { if (transaction == null) vm.addTransactionAndWait(custodyId, currency, type, personId, m, details, date, selected) else vm.updateTransactionAndWait(transaction.id, currency, type, personId, m, details, date, selected, deletedAttachments) }.onSuccess { keyboard?.hide(); saving = false; onFinished() }.onFailure { saving = false; error = true } }
                    }, modifier = Modifier.weight(1f).semantics { contentDescription = "حفظ العملية" })
                    SecondaryButton(text = "إلغاء", onClick = { keyboard?.hide(); onDismiss() }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    if (calc.isOpen) Dialog(onDismissRequest = calc::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { Card(modifier = Modifier.fillMaxWidth(.92f).imePadding()) { CalculatorOverlay(expression = calc.expression, result = calc.result.orEmpty(), onKey = calc::press, onClear = calc::clear, onBackspace = calc::backspace, onDismiss = calc::close, onUseResult = calc::useResult) } }
}

@Composable
private fun CustodyReportDialog(custody: CustodyEntity, accounts: List<CustodyAccountEntity>, transactions: List<CustodyTransactionEntity>, onDismiss: () -> Unit) {
    var currency by remember { mutableStateOf("YER") }
    val owner = accounts.firstOrNull { it.holderType == "OWNER" && it.personId == null && it.currencyCode == currency }
    val rows = transactions.filter { it.currencyCode == currency }.sortedBy { it.transactionDate }
    val balance = rows.filter { it.accountId == owner?.id }.sumOf { CustodyBalanceRules.ownerDelta(it.type, it.amountMinor) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تقرير العهدة", style = MaterialTheme.typography.titleLarge) }, text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SummaryCard {
                Text(custody.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("الجهة: ${custody.organizationName}", style = MaterialTheme.typography.bodyLarge)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { custodyCurrencies.forEach { code -> CurrencyChip(currency = code, selected = currency == code, onClick = { currency = code }) } }
                BalanceAmount(amount = signed(balance), status = balanceStatus(balance), label = currency)
            }
        }
        items(rows, key = { it.id }) { row ->
            CustodyOperationCard(operationType = typeName(row.type), amount = "${money(row.amountMinor)} $currency", currency = currency, date = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(row.transactionDate)), description = row.description.takeIf { it.isNotBlank() }, tone = custodyTone(row.type))
        }
    } }, confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } })
}
