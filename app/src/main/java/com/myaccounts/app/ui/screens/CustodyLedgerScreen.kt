@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myaccounts.app.ui.screens

import android.app.DatePickerDialog
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
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

private val custodyLedgerCurrencies = listOf("YER", "SAR", "USD")
private fun money(v: Long): String = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun parseAmount(v: String): Long? = runCatching { BigDecimal(v.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }.getOrNull()
private fun typeLabel(type: String, owner: Boolean, personName: String = ""): String = when (type) {
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
private fun custodyStatus(v: Long, owner: Boolean) = when { v > 0 -> if (owner) "متبقي لديه" else "لديه"; v < 0 -> if (owner) "عجز" else "مستحق له"; else -> "متوازن" }
private fun debtStatus(v: Long, positive: String, negative: String) = when { v > 0 -> positive; v < 0 -> negative; else -> "متوازن" }
private fun balanceColor(v: Long) = if (v > 0) Due else if (v < 0) Owed else androidx.compose.ui.graphics.Color.Unspecified

@Composable
private fun Modifier.keepFocusedFieldVisible(): Modifier {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusEvent { state ->
            if (state.isFocused) scope.launch { bringIntoViewRequester.bringIntoView() }
        }
}

@Composable
fun CustodyLedgerScreen(vm: CustodyViewModel, custodyId: Long, personId: Long?, onBack: () -> Unit, dialogWidth: Float = .92f) {
    val people by vm.persons(custodyId).collectAsState()
    val accounts by vm.accounts(custodyId).collectAsState()
    val transactions by vm.transactions(custodyId).collectAsState()
    val custody by vm.custody(custodyId).collectAsState()
    val owner = personId == null
    val person = people.firstOrNull { it.id == personId }
    if (!owner && person == null) return
    val current = custody ?: return
    val title = if (owner) current.name else person!!.name
    val ownerAccountIds = accounts.filter { it.holderType == "OWNER" && it.personId == null }.map { it.id }.toSet()
    var selectedCurrency by remember { mutableStateOf("YER") }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    var deleting by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    var showOwnerMenu by remember { mutableStateOf(false) }
    var showEditOwner by remember { mutableStateOf(false) }
    var showEditCustody by remember { mutableStateOf(false) }
    val rows = transactions.filter { t -> t.currencyCode == selectedCurrency && if (owner) t.accountId in ownerAccountIds else t.personId == personId }.sortedByDescending { it.transactionDate }
    val custodyBalance = if (owner) accounts.firstOrNull { it.holderType == "OWNER" && it.personId == null && it.currencyCode == selectedCurrency }?.balanceMinor ?: 0L else CustodyFinancialSummary.personCustodyBalance(transactions, personId!!, selectedCurrency)
    val orgDebt = if (owner) CustodyFinancialSummary.ownerOrganizationDebt(transactions, selectedCurrency) else 0L
    val peopleDebt = if (owner) CustodyFinancialSummary.ownerPeopleDebt(transactions, selectedCurrency) else CustodyFinancialSummary.personDebt(transactions, personId!!, selectedCurrency)

    Scaffold(
        topBar = { TopAppBar(title = { Text("عمليات $title", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }, actions = {
            if (owner) {
                IconButton(onClick = { showOwnerMenu = true }, modifier = Modifier.semantics { contentDescription = "خيارات حامل العهدة" }) { Icon(Icons.Default.MoreVert, "المزيد") }
                DropdownMenu(expanded = showOwnerMenu, onDismissRequest = { showOwnerMenu = false }) {
                    if (!current.isClosed) {
                        DropdownMenuItem(text = { Text("تعديل بيانات حامل العهدة") }, onClick = { showOwnerMenu = false; showEditOwner = true })
                        DropdownMenuItem(text = { Text("تعديل بيانات العهدة والجهة") }, onClick = { showOwnerMenu = false; showEditCustody = true })
                    }
                }
            }
        } ) },
        floatingActionButton = { FloatingActionButton(onClick = { if (!current.isClosed) showAdd = true }, modifier = Modifier.semantics { contentDescription = "إضافة عملية" }) { Icon(Icons.Default.Add, null) } }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) { custodyLedgerCurrencies.forEach { code -> FilterChip(selected = selectedCurrency == code, onClick = { selectedCurrency = code }, label = { Text(code, fontWeight = FontWeight.Bold) }, modifier = Modifier.weight(1f)) } }
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(selectedCurrency, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    SummaryBalance("العهدة", custodyBalance, custodyStatus(custodyBalance, owner))
                    if (owner) {
                        SummaryBalance("ذمة الجهة", orgDebt, debtStatus(orgDebt, "مستحق له", "مستحق عليه"))
                        SummaryBalance("ذمم الأطراف", peopleDebt, debtStatus(peopleDebt, "له على الأطراف", "عليه للأشخاص"))
                    } else SummaryBalance("الذمة", peopleDebt, debtStatus(peopleDebt, "مستحق له", "مستحق عليه"))
                }
            }
            Spacer(Modifier.height(10.dp))
            if (rows.isEmpty()) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("لا توجد عمليات لهذه العملة", fontWeight = FontWeight.Bold)
                        Text("استخدم زر + لإضافة أول عملية", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(rows, key = { it.id }) { t ->
                        val pName = t.personId?.let { id -> people.firstOrNull { it.id == id }?.name }.orEmpty()
                        val attachCount = remember(t.id) { vm.attachments(t.id).size }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(typeLabel(t.type, owner, pName), fontWeight = FontWeight.Bold)
                                    Text(SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(t.transactionDate)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${money(t.amountMinor)} $selectedCurrency", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                if (t.categoryName.isNotBlank()) Text("بند: ${t.categoryName}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    if (attachCount > 0) Icon(Icons.Default.AttachFile, "المرفقات")
                                    IconButton(enabled = !current.isClosed, onClick = { editing = t }, modifier = Modifier.semantics { contentDescription = "تعديل" }) { Icon(Icons.Default.Edit, "تعديل") }
                                    IconButton(enabled = !current.isClosed, onClick = { deleting = t }, modifier = Modifier.semantics { contentDescription = "حذف" }) { Icon(Icons.Default.Delete, "حذف") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) CustodyLedgerOperationDialog(vm = vm, custodyId = custodyId, personId = personId, owner = owner, defaultCurrency = selectedCurrency, initialType = if (owner) CustodyTransactionType.RECEIVED_FROM_ORG else CustodyTransactionType.PAID_TO_PERSON, transaction = null, dialogWidth = dialogWidth, onDismiss = { showAdd = false }, onFinished = { showAdd = false })
    editing?.let { t -> CustodyLedgerOperationDialog(vm = vm, custodyId = custodyId, personId = if (owner) t.personId else personId, owner = owner, defaultCurrency = t.currencyCode, initialType = t.type, transaction = t, dialogWidth = dialogWidth, onDismiss = { editing = null }, onFinished = { editing = null }) }
    deleting?.let { t -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("حذف العملية") }, text = { Text("سيتم حذف العملية نهائيًا.") }, confirmButton = { TextButton(onClick = { vm.deleteTransaction(t.id); deleting = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } }) }
    if (showEditOwner) CustodyOwnerEditDialog(vm, current, onDismiss = { showEditOwner = false }, onSaved = { showEditOwner = false })
    if (showEditCustody) CustodyDataEditDialog(vm, current, onDismiss = { showEditCustody = false }, onSaved = { showEditCustody = false })
}

@Composable
private fun SummaryBalance(title: String, value: Long, status: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(money(kotlin.math.abs(value)), fontWeight = FontWeight.Bold, color = balanceColor(value)); Text(status, style = MaterialTheme.typography.bodySmall, color = balanceColor(value)) }
    }
}

@Composable
fun CustodyLedgerOperationDialog(vm: CustodyViewModel, custodyId: Long, personId: Long?, owner: Boolean, defaultCurrency: String, initialType: String, transaction: CustodyTransactionEntity?, dialogWidth: Float = .92f, onDismiss: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val calc = LocalCalculatorController.current
    val scope = rememberCoroutineScope()
    var currency by remember(transaction?.id) { mutableStateOf(transaction?.currencyCode ?: defaultCurrency) }
    var type by remember(transaction?.id) { mutableStateOf(transaction?.type ?: initialType) }
    var amount by remember(transaction?.id) { mutableStateOf(transaction?.let { money(it.amountMinor) } ?: "") }
    var categoryName by remember(transaction?.id) { mutableStateOf(transaction?.categoryName ?: "") }
    var details by remember(transaction?.id) { mutableStateOf(transaction?.description ?: "") }
    var date by remember(transaction?.id) { mutableStateOf(transaction?.transactionDate ?: System.currentTimeMillis()) }
    var saving by remember(transaction?.id) { mutableStateOf(false) }
    var error by remember(transaction?.id) { mutableStateOf<String?>(null) }
    var attachments by remember(transaction?.id) { mutableStateOf<List<TransactionAttachmentStorage.SelectedAttachment>>(emptyList()) }
    var deletedAttachments by remember(transaction?.id) { mutableStateOf<List<CustodyTransactionAttachmentEntity>>(emptyList()) }
    val existing = remember(transaction?.id) { transaction?.let { vm.attachments(it.id) } ?: emptyList() }
    val visibleExisting = existing.filter { a -> deletedAttachments.none { it.id == a.id } }
    DisposableEffect(calc, transaction?.id) { calc.setResultConsumer { amount = it; error = null }; onDispose { calc.setResultConsumer(null) } }
    val availableTypes = if (owner) listOf(CustodyTransactionType.RECEIVED_FROM_ORG, CustodyTransactionType.RETURNED_TO_ORG, CustodyTransactionType.ORG_LOAN_FROM_OWNER, CustodyTransactionType.ORG_LOAN_REPAYMENT) else listOf(CustodyTransactionType.PAID_TO_PERSON, CustodyTransactionType.RETURNED_FROM_PERSON, CustodyTransactionType.PERSON_LOAN_TO_OWNER, CustodyTransactionType.OWNER_REPAY_PERSON_LOAN)
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(Modifier.fillMaxWidth(dialogWidth).imePadding(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (transaction == null) "إضافة عملية" else "تعديل العملية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(amount, { amount = it; error = null }, Modifier.weight(1.35f).keepFocusedFieldVisible(), label = { Text("المبلغ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !saving, isError = error != null, trailingIcon = { CalculatorButton(onClick = calc::open) })
                    OutlinedTextField(SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(date)), {}, Modifier.weight(1f), label = { Text("التاريخ") }, readOnly = true, singleLine = true, enabled = !saving, trailingIcon = { IconButton(enabled = !saving, onClick = { val d = Calendar.getInstance().apply { timeInMillis = date }; DatePickerDialog(context, { _, y, m, day -> d.set(y, m, day, 12, 0, 0); d.set(Calendar.MILLISECOND, 0); date = d.timeInMillis }, d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH)).show() }) { Icon(Icons.Default.CalendarToday, "اختيار التاريخ") } })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(categoryName, { categoryName = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible().semantics { contentDescription = "بند العهدة" }, label = { Text("بند العهدة") }, singleLine = true, enabled = !saving)
                OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("التفاصيل") }, minLines = 1, enabled = !saving)
                Text("العملة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { custodyLedgerCurrencies.forEach { code -> FilterChip(selected = currency == code, onClick = { if (!saving) currency = code }, label = { Text(code) }) } }
                Text("نوع العملية", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { availableTypes.forEach { kind -> FilterChip(selected = type == kind, onClick = { if (!saving) type = kind }, label = { Text(typeLabel(kind, owner)) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = typeLabel(kind, owner) }) } }
                if (transaction != null && visibleExisting.isNotEmpty()) {
                    Text("المرفقات الحالية: ${visibleExisting.size}", fontWeight = FontWeight.Bold)
                    visibleExisting.forEach { a -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AttachFile, null); Text(a.fileName, Modifier.weight(1f), maxLines = 1); IconButton(enabled = !saving, onClick = { deletedAttachments = deletedAttachments + a }) { Icon(Icons.Default.Delete, "حذف المرفق") } } }
                }
                TransactionAttachmentPicker(selectedAttachments = attachments, onAttachmentsChanged = { if (!saving) attachments = it })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !saving, onClick = {
                        val parsed = parseAmount(amount)
                        if (parsed == null || parsed <= 0L) { error = "أدخل مبلغًا صحيحًا أكبر من صفر."; return@Button }
                        saving = true; error = null
                        val selected = attachments.map { CustodyAttachmentStorage.Selected(it.uri, it.fileName, it.mimeType) }
                        scope.launch {
                            runCatching { if (transaction == null) vm.addTransactionAndWait(custodyId, currency, type, personId, parsed, categoryName.trim(), details.trim(), date, selected) else vm.updateTransactionAndWait(transaction.id, currency, type, personId, parsed, categoryName.trim(), details.trim(), date, selected, deletedAttachments) }
                                .onSuccess { keyboard?.hide(); calc.close(); saving = false; onFinished() }
                                .onFailure { saving = false; error = it.message ?: "تعذر حفظ العملية" }
                        }
                    }, modifier = Modifier.weight(1f).semantics { contentDescription = "حفظ العملية" }) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") }
                    OutlinedButton(enabled = !saving, onClick = { keyboard?.hide(); onDismiss() }, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                }
            }
        }
    }
    if (calc.isOpen) Dialog(onDismissRequest = calc::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { Card(Modifier.fillMaxWidth(.92f).imePadding()) { CalculatorOverlay(expression = calc.expression, result = calc.result.orEmpty(), onKey = calc::press, onClear = calc::clear, onBackspace = calc::backspace, onDismiss = calc::close, onUseResult = calc::useResult) } }
}