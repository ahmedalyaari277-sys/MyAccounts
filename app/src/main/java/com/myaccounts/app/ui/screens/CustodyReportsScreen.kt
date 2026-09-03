package com.myaccounts.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.myaccounts.app.data.custody.CustodyBalanceRules
import com.myaccounts.app.data.custody.CustodyPersonEntity
import com.myaccounts.app.data.custody.CustodyTransactionEntity
import com.myaccounts.app.data.custody.CustodyTransactionType
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.ui.components.AppTopBar
import com.myaccounts.app.ui.components.BalanceAmount
import com.myaccounts.app.ui.components.BalanceStatus
import com.myaccounts.app.ui.components.CustodyOperationCard
import com.myaccounts.app.ui.components.CustodyOperationTone
import com.myaccounts.app.ui.components.EmptyState
import com.myaccounts.app.ui.components.EmptyStateType
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.PrimaryButton
import com.myaccounts.app.ui.components.SecondaryButton
import com.myaccounts.app.ui.components.SummaryCard
import com.myaccounts.app.ui.components.StatusChip
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import com.myaccounts.app.util.CustodyReportExporter
import com.myaccounts.app.util.ReportShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val reportCurrencies = listOf("ALL", "YER", "SAR", "USD")
private val reportTypes = listOf(
    "ALL" to "كل العمليات",
    CustodyTransactionType.RECEIVED_FROM_ORG to "استلام من الجهة",
    CustodyTransactionType.PAID_TO_PERSON to "صرف للشخص",
    CustodyTransactionType.RETURNED_FROM_PERSON to "مرتجع من الشخص",
    CustodyTransactionType.RETURNED_TO_ORG to "مرتجع للجهة"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyReportsScreen(vm: CustodyViewModel, onBack: () -> Unit) {
    val custodies by vm.custodies.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var currency by remember { mutableStateOf("ALL") }
    var type by remember { mutableStateOf("ALL") }
    var personId by remember { mutableStateOf<Long?>(null) }
    var period by remember { mutableStateOf(0) }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun inPeriod(date: Long): Boolean {
        val start = startDate ?: return true
        val end = endDate ?: return date >= start
        return date >= start && date < end
    }

    fun filteredTransactions(transactions: List<CustodyTransactionEntity>): List<CustodyTransactionEntity> = transactions
        .asSequence()
        .filter { currency == "ALL" || it.currencyCode == currency }
        .filter { type == "ALL" || it.type == type }
        .filter { personId == null || it.personId == personId }
        .filter(::inPeriod)
        .sortedByDescending { it.transactionDate }
        .toList()

    fun toneFor(value: String): CustodyOperationTone = when (value) {
        CustodyTransactionType.RECEIVED_FROM_ORG -> CustodyOperationTone.ReceiveFromOrganization
        CustodyTransactionType.PAID_TO_PERSON -> CustodyOperationTone.PayToPerson
        CustodyTransactionType.RETURNED_FROM_PERSON -> CustodyOperationTone.ReturnFromPerson
        CustodyTransactionType.RETURNED_TO_ORG -> CustodyOperationTone.ReturnToOrganization
        else -> CustodyOperationTone.Neutral
    }

    fun typeName(value: String): String = reportTypes.firstOrNull { it.first == value }?.second ?: value

    fun export(custody: CustodyEntity, transactions: List<CustodyTransactionEntity>, pdf: Boolean) {
        if (busy) return
        busy = true
        scope.launch(Dispatchers.IO) {
            val result = if (pdf) CustodyReportExporter.exportPdf(context, custody, transactions, currency) else CustodyReportExporter.exportExcel(context, custody, transactions, currency)
            withContext(Dispatchers.Main) {
                message = result.fold({ it }, { it.message ?: "تعذر إنشاء التقرير." })
                busy = false
            }
        }
    }

    fun share(custody: CustodyEntity, transactions: List<CustodyTransactionEntity>, pdf: Boolean) {
        if (busy) return
        busy = true
        val mime = if (pdf) "application/pdf" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        val prefix = "MyAccounts_تقرير_عهدة_${safeFileName(custody.name)}"
        scope.launch(Dispatchers.IO) {
            val result = ReportShareUtil.shareGeneratedReport(context, prefix, mime) {
                if (pdf) CustodyReportExporter.exportPdf(context, custody, transactions, currency) else CustodyReportExporter.exportExcel(context, custody, transactions, currency)
            }
            withContext(Dispatchers.Main) {
                message = result.fold({ "تم فتح خيارات مشاركة التقرير." }, { it.message ?: "تعذر مشاركة التقرير." })
                busy = false
            }
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "تقارير العُهَد", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SummaryCard(title = "مركز تقارير العُهَد") {
                    Text("التقارير مستقلة عن دفتر الحسابات وتقرأ من بيانات العهد فقط.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        reportCurrencies.forEach { code ->
                            SecondaryButton(code, { currency = code }, Modifier.weight(1f), enabled = !busy)
                        }
                    }
                    Text("العملة الحالية: ${currencyName(currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            items(custodies, key = { it.id }) { custody ->
                val people by vm.persons(custody.id).collectAsState(initial = emptyList())
                val transactions by vm.transactions(custody.id).collectAsState(initial = emptyList())
                val filtered = filteredTransactions(transactions)
                val currencies = if (currency == "ALL") listOf("YER", "SAR", "USD") else listOf(currency)

                itemContent(
                    custody = custody,
                    people = people,
                    filtered = filtered,
                    currencies = currencies,
                    currentType = type,
                    currentPersonId = personId,
                    period = period,
                    busy = busy,
                    onType = { type = it },
                    onPerson = { personId = it },
                    onPeriod = {
                        period = it
                        when (it) {
                            0 -> { startDate = null; endDate = null }
                            1 -> { startDate = dayStart(System.currentTimeMillis()); endDate = dayEnd(System.currentTimeMillis()) }
                            2 -> {
                                val now = dayStart(System.currentTimeMillis())
                                val cal = java.util.Calendar.getInstance().apply { timeInMillis = now; set(java.util.Calendar.DAY_OF_WEEK, firstDayOfWeek) }
                                startDate = cal.timeInMillis
                                endDate = addDays(startDate!!, 7)
                            }
                            3 -> {
                                val now = java.util.Calendar.getInstance().apply { timeInMillis = dayStart(System.currentTimeMillis()); set(java.util.Calendar.DAY_OF_MONTH, 1) }
                                startDate = now.timeInMillis
                                endDate = java.util.Calendar.getInstance().apply { timeInMillis = now.timeInMillis; add(java.util.Calendar.MONTH, 1) }.timeInMillis
                            }
                        }
                    },
                    onExportPdf = { export(custody, filtered, true) },
                    onExportExcel = { export(custody, filtered, false) },
                    onSharePdf = { share(custody, filtered, true) },
                    onShareExcel = { share(custody, filtered, false) },
                    toneFor = ::toneFor,
                    typeName = ::typeName
                )
            }

            if (custodies.isEmpty()) {
                item {
                    EmptyState(
                        type = EmptyStateType.Custody,
                        title = "لا توجد عُهَد",
                        description = "لا توجد بيانات عهد متاحة لإصدار التقارير."
                    )
                }
            }
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("موافق") } }
        )
    }
}

@Composable
private fun itemContent(
    custody: CustodyEntity,
    people: List<CustodyPersonEntity>,
    filtered: List<CustodyTransactionEntity>,
    currencies: List<String>,
    currentType: String,
    currentPersonId: Long?,
    period: Int,
    busy: Boolean,
    onType: (String) -> Unit,
    onPerson: (Long?) -> Unit,
    onPeriod: (Int) -> Unit,
    onExportPdf: () -> Unit,
    onExportExcel: () -> Unit,
    onSharePdf: () -> Unit,
    onShareExcel: () -> Unit,
    toneFor: (String) -> CustodyOperationTone,
    typeName: (String) -> String
) {
    SummaryCard(title = custody.name) {
        Text("الجهة: ${custody.organizationName}", style = MaterialTheme.typography.bodyLarge)
        Text("عدد الأشخاص: ${people.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        InformationCard {
            Text("الفترة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("كل الحساب", { onPeriod(0) }, Modifier.weight(1f), enabled = !busy)
                SecondaryButton("اليوم", { onPeriod(1) }, Modifier.weight(1f), enabled = !busy)
                SecondaryButton("الأسبوع", { onPeriod(2) }, Modifier.weight(1f), enabled = !busy)
                SecondaryButton("الشهر", { onPeriod(3) }, Modifier.weight(1f), enabled = !busy)
            }
            Text(
                when (period) { 1 -> "اليوم"; 2 -> "هذا الأسبوع"; 3 -> "هذا الشهر"; else -> "كل الحساب" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        InformationCard {
            Text("نوع الحركة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reportTypes.take(3).forEach { (code, label) ->
                    SecondaryButton(if (code == "ALL") "الكل" else label, { onType(code) }, Modifier.weight(1f), enabled = !busy)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reportTypes.drop(3).forEach { (code, label) ->
                    SecondaryButton(label, { onType(code) }, Modifier.weight(1f), enabled = !busy)
                }
            }
            StatusChip(typeName(currentType))
        }

        InformationCard {
            Text("الشخص / الطرف", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("الكل", { onPerson(null) }, Modifier.weight(1f), enabled = !busy)
                people.take(3).forEach { person ->
                    SecondaryButton(person.name, { onPerson(person.id) }, Modifier.weight(1f), enabled = !busy)
                }
            }
            if (people.size > 3) Text("يمكن تطبيق تقرير شخص محدد من شاشة عمليات الشخص.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            currentPersonId?.let { id ->
                people.firstOrNull { it.id == id }?.let { StatusChip(it.name) }
            }
        }

        SummaryCard(title = "الملخص المالي") {
            currencies.forEach { code ->
                val rows = filtered.filter { it.currencyCode == code }
                val received = rows.filter { it.type == CustodyTransactionType.RECEIVED_FROM_ORG }.sumOf { it.amountMinor }
                val paid = rows.filter { it.type == CustodyTransactionType.PAID_TO_PERSON }.sumOf { it.amountMinor }
                val returnedFromPerson = rows.filter { it.type == CustodyTransactionType.RETURNED_FROM_PERSON }.sumOf { it.amountMinor }
                val returnedToOrg = rows.filter { it.type == CustodyTransactionType.RETURNED_TO_ORG }.sumOf { it.amountMinor }
                val balanceValue = rows.sumOf { CustodyBalanceRules.ownerDelta(it.type, it.amountMinor) }
                InformationCard {
                    Text(currencyName(code), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BalanceAmount("استلام ${amount(received)}", BalanceStatus.Owed, Modifier.weight(1f))
                        BalanceAmount("صرف ${amount(paid)}", BalanceStatus.Due, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BalanceAmount("مرتجع أشخاص ${amount(returnedFromPerson)}", BalanceStatus.Owed, Modifier.weight(1f))
                        BalanceAmount("مرتجع جهة ${amount(returnedToOrg)}", BalanceStatus.Neutral, Modifier.weight(1f))
                    }
                    BalanceAmount(
                        balance(balanceValue),
                        if (balanceValue > 0L) BalanceStatus.Due else if (balanceValue < 0L) BalanceStatus.Owed else BalanceStatus.Neutral,
                        label = "الرصيد النهائي"
                    )
                }
            }
            Text("عدد العمليات: ${filtered.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        InformationCard {
            Text("التصدير والمشاركة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("PDF", onExportPdf, Modifier.weight(1f), enabled = !busy)
                PrimaryButton("Excel", onExportExcel, Modifier.weight(1f), enabled = !busy)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("مشاركة PDF", onSharePdf, Modifier.weight(1f), enabled = !busy)
                SecondaryButton("مشاركة Excel", onShareExcel, Modifier.weight(1f), enabled = !busy)
            }
        }

        if (filtered.isEmpty()) {
            EmptyState(
                type = EmptyStateType.Reports,
                title = "لا توجد عمليات ضمن الفلاتر",
                description = "غيّر العملة أو الفترة أو نوع الحركة أو الشخص."
            )
        } else {
            Text("تفاصيل العمليات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            filtered.forEach { transaction ->
                val personName = transaction.personId?.let { id -> people.firstOrNull { it.id == id }?.name }
                CustodyOperationCard(
                    operationType = typeName(transaction.type),
                    amount = "${amount(transaction.amountMinor)} ${transaction.currencyCode}",
                    currency = transaction.currencyCode,
                    date = formatDateTime(transaction.transactionDate),
                    description = listOfNotNull(personName, transaction.description.ifBlank { null }).joinToString(" — ").ifBlank { null },
                    tone = toneFor(transaction.type)
                )
            }
        }
    }
}

private fun amount(value: Long): String = BigDecimal(value).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun balance(value: Long): String = when { value > 0L -> "عليه ${amount(value)}"; value < 0L -> "له ${amount(-value)}"; else -> "متوازن 0" }
private fun currencyName(value: String): String = when (value) { "YER" -> "الريال اليمني"; "SAR" -> "الريال السعودي"; "USD" -> "الدولار الأمريكي"; "ALL" -> "جميع العملات"; else -> value }
private fun formatDateTime(value: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ar")).format(Date(value))
private fun dayStart(value: Long): Long = java.util.Calendar.getInstance().apply { timeInMillis = value; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
private fun dayEnd(value: Long): Long = addDays(dayStart(value), 1)
private fun addDays(value: Long, days: Int): Long = java.util.Calendar.getInstance().apply { timeInMillis = value; add(java.util.Calendar.DAY_OF_MONTH, days) }.timeInMillis
private fun safeFileName(value: String): String = value.replace(Regex("[\\\\/:*?\"<>|]"), "_").replace(Regex("\\s+"), "_").take(60)
