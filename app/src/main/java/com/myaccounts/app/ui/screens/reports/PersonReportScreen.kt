package com.myaccounts.app.ui.screens.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.reports.MultiCurrencyPersonReport
import com.myaccounts.app.data.reports.PersonCurrencyReport
import com.myaccounts.app.ui.components.AppTopBar
import com.myaccounts.app.ui.components.BalanceAmount
import com.myaccounts.app.ui.components.BalanceStatus
import com.myaccounts.app.ui.components.EmptyState
import com.myaccounts.app.ui.components.EmptyStateType
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.PrimaryButton
import com.myaccounts.app.ui.components.SecondaryButton
import com.myaccounts.app.ui.components.SummaryCard
import com.myaccounts.app.ui.components.TransactionCard
import com.myaccounts.app.ui.viewmodel.ReportsViewModel
import com.myaccounts.app.util.MultiCurrencyReportExcelExporter
import com.myaccounts.app.util.MultiCurrencyReportPdfExporter
import com.myaccounts.app.util.PersonReportExcelExporter
import com.myaccounts.app.util.PersonReportPdfExporter
import com.myaccounts.app.util.ReportShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonReportScreen(
    personId: Long,
    currencyCode: String = "ALL",
    viewModel: ReportsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showStart by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    var selectedStart by remember { mutableStateOf<Long?>(null) }
    var selectedEnd by remember { mutableStateOf<Long?>(null) }
    var actionBusy by remember { mutableStateOf(false) }

    LaunchedEffect(personId, currencyCode) {
        viewModel.selectPerson(personId)
        viewModel.selectCurrency(if (currencyCode == "ALL") "ALL" else currencyCode)
        viewModel.setAllTime()
        selectedStart = null
        selectedEnd = null
    }

    fun applyCustomRange() {
        val start = selectedStart?.let(::dayStart)
        val end = selectedEnd?.let(::dayStart)?.let { addDays(it, 1) }
        if (start != null && end != null && end > start) {
            viewModel.setDateRange(start, end)
        }
    }

    suspend fun generateNow(pdf: Boolean): Result<String> {
        val report = viewModel.getFreshMultiCurrencyPersonReport(
            personId = personId,
            startDateMillis = state.startDateMillis,
            endDateMillisExclusive = state.endDateMillisExclusive
        )
        return if (currencyCode == "ALL") {
            if (pdf) {
                MultiCurrencyReportPdfExporter.exportPersonReport(context, report, state.startDateMillis, state.endDateMillisExclusive)
            } else {
                MultiCurrencyReportExcelExporter.exportPersonReport(context, report, state.startDateMillis, state.endDateMillisExclusive)
            }
        } else {
            val currencyReport = report.reports.firstOrNull { it.currencyCode == currencyCode }
                ?: return Result.failure(IllegalStateException("لا توجد بيانات لهذه العملة."))
            if (pdf) {
                PersonReportPdfExporter.exportPersonReport(context, currencyReport.summary, currencyReport.transactions, state.startDateMillis, state.endDateMillisExclusive)
            } else {
                PersonReportExcelExporter.exportPersonReport(context, currencyReport.summary, currencyReport.transactions, state.startDateMillis, state.endDateMillisExclusive)
            }
        }
    }

    fun export(pdf: Boolean) {
        if (actionBusy) return
        actionBusy = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { generateNow(pdf) }
                result.fold(
                    { snackbar.showSnackbar("تم إنشاء التقرير بنجاح.") },
                    { snackbar.showSnackbar(it.message ?: "تعذر إنشاء التقرير.") }
                )
            } finally {
                actionBusy = false
            }
        }
    }

    fun share(pdf: Boolean) {
        if (actionBusy) return
        actionBusy = true
        val personName = state.selectedPersonMultiCurrencyReport?.personName.orEmpty()
        val prefix = "MyAccounts_تقرير_حساب_${safeFileName(personName)}"
        val mime = if (pdf) "application/pdf" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        scope.launch(Dispatchers.IO) {
            try {
                val result = ReportShareUtil.shareGeneratedReport(context, prefix, mime) {
                    runCatching { kotlinx.coroutines.runBlocking { generateNow(pdf).getOrThrow() } }
                }
                withContext(Dispatchers.Main) {
                    result.fold(
                        { snackbar.showSnackbar("تم فتح خيارات مشاركة التقرير.") },
                        { snackbar.showSnackbar(it.message ?: "تعذر مشاركة التقرير.") }
                    )
                }
            } finally {
                withContext(Dispatchers.Main) { actionBusy = false }
            }
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "تقرير حساب الشخص", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.selectedPersonMultiCurrencyReport?.let { report ->
                item { PersonHeader(report, state.startDateMillis, state.endDateMillisExclusive) }
            }

            item {
                InformationCard {
                    Text("العملة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("الكل", { viewModel.selectCurrency("ALL") }, Modifier.weight(1f), enabled = !actionBusy)
                        SecondaryButton("YER", { viewModel.selectCurrency("YER") }, Modifier.weight(1f), enabled = !actionBusy)
                        SecondaryButton("SAR", { viewModel.selectCurrency("SAR") }, Modifier.weight(1f), enabled = !actionBusy)
                        SecondaryButton("USD", { viewModel.selectCurrency("USD") }, Modifier.weight(1f), enabled = !actionBusy)
                    }
                    Text(
                        "العملة الحالية: ${currencyName(state.selectedCurrencyCode)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                InformationCard {
                    Text("الفترة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("كل الحساب", {
                            selectedStart = null
                            selectedEnd = null
                            viewModel.setAllTime()
                        }, Modifier.weight(1f), enabled = !actionBusy)
                        SecondaryButton("من: ${selectedStart?.let(::formatDate) ?: "—"}", { showStart = true }, Modifier.weight(1f), enabled = !actionBusy)
                        SecondaryButton("إلى: ${selectedEnd?.let(::formatDate) ?: "—"}", { showEnd = true }, Modifier.weight(1f), enabled = !actionBusy)
                    }
                    if (selectedStart != null && selectedEnd != null) {
                        Text(
                            "النطاق: ${formatDate(selectedStart!!)} — ${formatDate(selectedEnd!!)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PrimaryButton(
                            "تطبيق الفترة",
                            { applyCustomRange() },
                            Modifier.fillMaxWidth(),
                            enabled = !actionBusy && dayStart(selectedEnd!!) >= dayStart(selectedStart!!)
                        )
                    }
                }
            }

            item {
                InformationCard {
                    Text("التصدير والمشاركة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("PDF", { export(true) }, Modifier.weight(1f), enabled = !actionBusy && !state.isLoading)
                        PrimaryButton("Excel", { export(false) }, Modifier.weight(1f), enabled = !actionBusy && !state.isLoading)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("مشاركة PDF", { share(true) }, Modifier.weight(1f), enabled = !actionBusy && !state.isLoading)
                        SecondaryButton("مشاركة Excel", { share(false) }, Modifier.weight(1f), enabled = !actionBusy && !state.isLoading)
                    }
                }
            }

            if (state.isLoading) {
                item {
                    InformationCard { Text("جاري تحميل التقرير...", style = MaterialTheme.typography.bodyLarge) }
                }
            } else {
                val report = state.selectedPersonMultiCurrencyReport
                if (report == null) {
                    item {
                        EmptyState(
                            type = EmptyStateType.Reports,
                            title = "لا توجد بيانات للتقرير",
                            description = "تعذر تحميل بيانات حساب الشخص حالياً."
                        )
                    }
                } else {
                    val reports = if (currencyCode == "ALL") report.reports else report.reports.filter { it.currencyCode == currencyCode }
                    if (reports.isEmpty()) {
                        item {
                            EmptyState(
                                type = EmptyStateType.Reports,
                                title = "لا توجد بيانات لهذه العملة",
                                description = "اختر عملة أخرى أو اعرض جميع العملات."
                            )
                        }
                    } else {
                        items(reports, key = { it.currencyCode }) { currencyReport ->
                            PersonCurrencySummaryCard(currencyReport)
                        }
                        reports.forEach { currencyReport ->
                            item(key = "transactions-${currencyReport.currencyCode}") {
                                Text(
                                    "عمليات ${currencyName(currencyReport.currencyCode)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            items(currencyReport.transactions, key = { "${currencyReport.currencyCode}-${it.transactionId}" }) { transaction ->
                                TransactionCard(
                                    operationType = if (transaction.type == "RECEIVABLE") "عليه" else "له",
                                    amount = amount(transaction.amountMinor),
                                    description = transaction.description.ifBlank { null },
                                    date = formatDateTime(transaction.transactionDate),
                                    amountStatus = if (transaction.type == "RECEIVABLE") BalanceStatus.Due else BalanceStatus.Owed
                                )
                            }
                        }
                    }
                }
                state.errorMessage?.let { message ->
                    item {
                        InformationCard {
                            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }

    if (showStart) {
        DatePickerDialog(
            onDismissRequest = { showStart = false },
            confirmButton = { TextButton(onClick = { showStart = false }) { Text("إغلاق") } }
        ) {
            val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = selectedStart)
            DatePicker(picker)
            LaunchedEffect(picker.selectedDateMillis) {
                picker.selectedDateMillis?.let { selected ->
                    selectedStart = dayStart(selected)
                    showStart = false
                }
            }
        }
    }

    if (showEnd) {
        DatePickerDialog(
            onDismissRequest = { showEnd = false },
            confirmButton = { TextButton(onClick = { showEnd = false }) { Text("إغلاق") } }
        ) {
            val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = selectedEnd)
            DatePicker(picker)
            LaunchedEffect(picker.selectedDateMillis) {
                picker.selectedDateMillis?.let { selected ->
                    selectedEnd = dayStart(selected)
                    showEnd = false
                }
            }
        }
    }
}

@Composable
private fun PersonHeader(report: MultiCurrencyPersonReport, start: Long?, end: Long?) {
    SummaryCard(title = "حساب الشخص") {
        Text(report.personName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("الهاتف: ${report.phone.ifBlank { "غير مسجل" }}", style = MaterialTheme.typography.bodyLarge)
        Text("العنوان: ${report.address.ifBlank { "غير مسجل" }}", style = MaterialTheme.typography.bodyLarge)
        Text("الفترة: ${range(start, end)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PersonCurrencySummaryCard(report: PersonCurrencyReport) {
    SummaryCard(title = currencyName(report.currencyCode)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BalanceAmount("عليه ${amount(report.summary.periodReceivableMinor)}", BalanceStatus.Due, Modifier.weight(1f))
            BalanceAmount("له ${amount(report.summary.periodPayableMinor)}", BalanceStatus.Owed, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BalanceAmount("الرصيد الافتتاحي: ${balance(report.summary.openingBalanceMinor)}", balanceStatus(report.summary.openingBalanceMinor))
            BalanceAmount("الرصيد الختامي: ${balance(report.summary.closingBalanceMinor)}", balanceStatus(report.summary.closingBalanceMinor))
        }
        Text("عدد العمليات: ${report.summary.transactionCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun amount(value: Long): String = BigDecimal(value).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun balance(value: Long): String = when { value > 0L -> "عليه ${amount(value)}"; value < 0L -> "له ${amount(-value)}"; else -> "متوازن 0" }
private fun balanceStatus(value: Long): BalanceStatus = when { value > 0L -> BalanceStatus.Due; value < 0L -> BalanceStatus.Owed; else -> BalanceStatus.Neutral }
private fun currencyName(code: String): String = when (code) { "ALL" -> "جميع العملات"; "YER" -> "الريال اليمني"; "SAR" -> "الريال السعودي"; "USD" -> "الدولار الأمريكي"; else -> code }
private fun formatDate(value: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date(value))
private fun formatDateTime(value: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ar")).format(Date(value))
private fun range(start: Long?, end: Long?): String = if (start == null && end == null) "كل الحساب" else "${start?.let(::formatDate) ?: "غير محدد"} - ${end?.let { formatDate(it - 1) } ?: "غير محدد"}"
private fun dayStart(value: Long): Long = Calendar.getInstance().apply { timeInMillis = value; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
private fun addDays(value: Long, days: Int): Long = Calendar.getInstance().apply { timeInMillis = value; add(Calendar.DAY_OF_MONTH, days) }.timeInMillis
private fun safeFileName(value: String): String = value.replace(Regex("[\\\\/:*?\"<>|]"), "_").replace(Regex("\\s+"), "_").take(60)
