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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myaccounts.app.data.custody.CustodyPersonEntity
import com.myaccounts.app.data.custody.CustodyTransactionEntity
import com.myaccounts.app.data.custody.CustodyTransactionType
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val organizationCurrencies = listOf("YER", "SAR", "USD")
private fun orgMoney(v: Long) = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun orgParse(v: String): Long? = runCatching { BigDecimal(v.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }.getOrNull()

@Composable
private fun Modifier.orgKeepFocusedVisible(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return bringIntoViewRequester(requester).onFocusEvent { if (it.isFocused) scope.launch { requester.bringIntoView() } }
}

@Composable
fun CustodyOrganizationOperationsScreen(vm: CustodyViewModel, custodyId: Long, personId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val custody by vm.custody(custodyId).collectAsState()
    val people by vm.persons(custodyId).collectAsState()
    val transactions by vm.transactions(custodyId).collectAsState()
    val person = people.firstOrNull { it.id == personId && it.partyType == "ENTITY" } ?: return
    val current = custody ?: return
    var currency by remember { mutableStateOf("YER") }
    var add by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    var deleting by remember { mutableStateOf<CustodyTransactionEntity?>(null) }
    val rows = transactions.filter { it.personId == personId && it.currencyCode == currency }.sortedByDescending { it.transactionDate }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("عمليات ${person.name}", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { if (!current.isClosed) add = true }) { Icon(Icons.Default.Add, "إضافة عملية") } }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("الجهة الثابتة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("نوع العملية في هذه الشاشة هو صرف فقط. عمليات استلام/مرتجع/تسليف الجهة تبقى في شاشة حامل العهدة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                organizationCurrencies.forEach { code -> FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(code, fontWeight = FontWeight.Bold) }, modifier = Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
            if (rows.isEmpty()) {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(24.dp)) { Text("لا توجد عمليات صرف لهذه العملة", fontWeight = FontWeight.Bold); Text("استخدم زر + لإضافة عملية صرف للجهة.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(rows, key = { it.id }) { tx ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("صرف", fontWeight = FontWeight.Bold)
                                    Text(SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(tx.transactionDate)), style = MaterialTheme.typography.bodySmall)
                                }
                                Text("${orgMoney(tx.amountMinor)} ${tx.currencyCode}", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                if (tx.categoryName.isNotBlank()) Text("التصنيف: ${tx.categoryName}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                if (tx.description.isNotBlank()) Text(tx.description, style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    IconButton(enabled = !current.isClosed, onClick = { editing = tx }) { Icon(Icons.Default.Edit, "تعديل") }
                                    IconButton(enabled = !current.isClosed, onClick = { deleting = tx }) { Icon(Icons.Default.Delete, "حذف") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (add) OrganizationTransactionDialog(vm, custodyId, personId, currency, null, { add = false }, { add = false })
    editing?.let { tx -> OrganizationTransactionDialog(vm, custodyId, personId, tx.currencyCode, tx, { editing = null }, { editing = null }) }
    deleting?.let { tx ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف العملية") },
            text = { Text("سيتم حذف عملية الصرف نهائيًا.") },
            confirmButton = { TextButton(onClick = { vm.deleteTransaction(tx.id); deleting = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun OrganizationTransactionDialog(
    vm: CustodyViewModel,
    custodyId: Long,
    personId: Long,
    defaultCurrency: String,
    transaction: CustodyTransactionEntity?,
    onDismiss: () -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val transactions by vm.transactions(custodyId).collectAsState()
    var currency by remember(transaction?.id) { mutableStateOf(transaction?.currencyCode ?: defaultCurrency) }
    var amount by remember(transaction?.id) { mutableStateOf(transaction?.let { orgMoney(it.amountMinor) } ?: "") }
    var category by remember(transaction?.id) { mutableStateOf(transaction?.categoryName ?: "") }
    var details by remember(transaction?.id) { mutableStateOf(transaction?.description ?: "") }
    var date by remember(transaction?.id) { mutableStateOf(transaction?.transactionDate ?: System.currentTimeMillis()) }
    var saving by remember(transaction?.id) { mutableStateOf(false) }
    var error by remember(transaction?.id) { mutableStateOf<String?>(null) }
    var categoryMenu by remember { mutableStateOf(false) }
    val categories = remember(transactions) { transactions.map { it.categoryName.trim() }.filter { it.isNotBlank() }.distinct().sorted() }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(Modifier.fillMaxWidth(.94f).fillMaxHeight(.9f).imePadding(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (transaction == null) "إضافة صرف للجهة" else "تعديل صرف الجهة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("نوع العملية: صرف", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    OutlinedTextField(amount, { amount = it; error = null }, Modifier.fillMaxWidth().orgKeepFocusedVisible(), label = { Text("المبلغ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !saving)
                    OutlinedTextField(SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(date)), {}, Modifier.fillMaxWidth(), label = { Text("التاريخ") }, readOnly = true, singleLine = true, enabled = !saving, trailingIcon = { IconButton(enabled = !saving, onClick = { val d = Calendar.getInstance().apply { timeInMillis = date }; DatePickerDialog(context, { _, y, m, day -> d.set(y, m, day, 12, 0, 0); d.set(Calendar.MILLISECOND, 0); date = d.timeInMillis }, d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH)).show() }) { Icon(Icons.Default.CalendarToday, "اختيار التاريخ") } })
                    Text("التصنيف", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedTextField(category, { category = it; categoryMenu = true }, Modifier.fillMaxWidth().orgKeepFocusedVisible(), label = { Text("اختر تصنيفًا أو اكتب تصنيفًا جديدًا") }, singleLine = true, enabled = !saving)
                        DropdownMenu(expanded = categoryMenu && categories.isNotEmpty(), onDismissRequest = { categoryMenu = false }) {
                            categories.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { category = value; categoryMenu = false }) }
                            DropdownMenuItem(text = { Text("جديد") }, onClick = { category = ""; categoryMenu = false })
                        }
                    }
                    if (categories.isNotEmpty()) Text("التصنيفات الموجودة في هذه العهدة: ${categories.joinToString("، ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth().orgKeepFocusedVisible(), label = { Text("التفاصيل") }, minLines = 1, enabled = !saving)
                    Text("العملة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { organizationCurrencies.forEach { code -> FilterChip(selected = currency == code, onClick = { if (!saving) currency = code }, label = { Text(code) }, modifier = Modifier.weight(1f)) } }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                Row(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !saving, onClick = {
                        val parsed = orgParse(amount)
                        if (parsed == null || parsed <= 0L) { error = "أدخل مبلغًا صحيحًا أكبر من صفر."; return@Button }
                        saving = true
                        scope.launch {
                            runCatching {
                                if (transaction == null) vm.addTransactionAndWait(custodyId, currency, CustodyTransactionType.PAID_TO_PERSON, personId, parsed, category.trim(), details.trim(), date)
                                else vm.updateTransactionAndWait(transaction.id, currency, CustodyTransactionType.PAID_TO_PERSON, personId, parsed, category.trim(), details.trim(), date)
                            }.onSuccess { keyboard?.hide(); saving = false; onFinished() }.onFailure { saving = false; error = it.message ?: "تعذر حفظ العملية" }
                        }
                    }, modifier = Modifier.weight(1f)) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") }
                    OutlinedButton(enabled = !saving, onClick = { keyboard?.hide(); onDismiss() }, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                }
            }
        }
    }
}
