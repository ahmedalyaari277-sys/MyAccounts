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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.reports.CurrencyReportSummary
import com.myaccounts.app.data.reports.GeneralReportTransactionRow
import com.myaccounts.app.ui.components.AppTopBar
import com.myaccounts.app.ui.components.BalanceAmount
import com.myaccounts.app.ui.components.BalanceStatus
import com.myaccounts.app.ui.components.CurrencyChip
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.PrimaryButton
import com.myaccounts.app.ui.components.SecondaryButton
import com.myaccounts.app.ui.components.SummaryCard
import com.myaccounts.app.ui.components.StatusChip
import com.myaccounts.app.ui.viewmodel.ReportsViewModel
import com.myaccounts.app.util.GeneralReportsExcelExporter
import com.myaccounts.app.util.GeneralReportsPdfExporter
import com.myaccounts.app.util.MultiCurrencyReportExcelExporter
import com.myaccounts.app.util.MultiCurrencyReportPdfExporter
import com.myaccounts.app.util.ReportShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class ReportType { PEOPLE, DETAILED, SUMMARY }
private enum class Period { ALL, TODAY, WEEK, MONTH, CUSTOM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel, onBack: () -> Unit, onPersonClick: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var reportType by remember { mutableStateOf(ReportType.PEOPLE) }
    var period by remember { mutableStateOf(Period.ALL) }
    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }
    var showStart by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.selectCurrency("ALL") }

    fun dayStart(value: Long): Long = Calendar.getInstance().apply {
        timeInMillis = value
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    fun dayEnd(value: Long): Long = Calendar.getInstance().apply { timeInMillis = dayStart(value); add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis

    fun selectPeriod(value: Period) {
        period = value
        val now = System.currentTimeMillis()
        when (value) {
            Period.ALL -> viewModel.setAllTime()
            Period.TODAY -> viewModel.setDateRange(dayStart(now), dayEnd(now))
            Period.WEEK -> {
                val start = Calendar.getInstance().apply { timeInMillis = dayStart(now); set(Calendar.DAY_OF_WEEK, firstDayOfWeek) }.timeInMillis
                viewModel.setDateRange(start, addDays(start, 7))
            }
            Period.MONTH -> {
                val start = Calendar.getInstance().apply { timeInMillis = dayStart(now); set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
                viewModel.setDateRange(start, addMonths(start, 1))
            }
            Period.CUSTOM -> showStart = true
        }
    }

    fun generate(pdf: Boolean): Result<String> {
        val all = state.selectedCurrencyCode == "ALL"
        return if (all) when (reportType) {
            ReportType.PEOPLE -> if (pdf) MultiCurrencyReportPdfExporter.exportPeopleReport(context, state.allCurrencySummaries, state.allCurrencyPeople, state.startDateMillis, state.endDateMillisExclusive) else MultiCurrencyReportExcelExporter.exportPeopleReport(context, state.allCurrencySummaries, state.allCurrencyPeople, state.startDateMillis, state.endDateMillisExclusive)
            ReportType.DETAILED -> if (pdf) MultiCurrencyReportPdfExporter.exportDetailedReport(context, state.allCurrencySummaries, state.allCurrencyGeneralTransactions, state.startDateMillis, state.endDateMillisExclusive) else MultiCurrencyReportExcelExporter.exportDetailedReport(context, state.allCurrencySummaries, state.allCurrencyGeneralTransactions, state.startDateMillis, state.endDateMillisExclusive)
            ReportType.SUMMARY -> if (pdf) MultiCurrencyReportPdfExporter.exportSummaryReport(context, state.allCurrencyPersonSummaries, state.startDateMillis, state.endDateMillisExclusive) else MultiCurrencyReportExcelExporter.exportSummaryReport(context, state.allCurrencyPersonSummaries, state.startDateMillis, state.endDateMillisExclusive)
        } else {
            val currency = state.selectedCurrencyCode
            when (reportType) {
                ReportType.PEOPLE -> state.currencySummary?.let { summary -> if (pdf) GeneralReportsPdfExporter.exportPeopleReport(context, currency, summary, state.people, state.startDateMillis, state.endDateMillisExclusive) else GeneralReportsExcelExporter.exportPeopleReport(context, currency, summary, state.people, state.startDateMillis, state.endDateMillisExclusive) } ?: Result.failure(IllegalStateException("لم تكتمل بيانات التقرير بعد."))
                ReportType.DETAILED -> if (pdf) GeneralReportsPdfExporter.exportDetailedReport(context, currency, state.generalTransactions, state.startDateMillis, state.endDateMillisExclusive) else GeneralReportsExcelExporter.exportDetailedReport(context, currency, state.generalTransactions, state.startDateMillis, state.endDateMillisExclusive)
                ReportType.SUMMARY -> if (pdf) GeneralReportsPdfExporter.exportSummaryReport(context, currency, state.personCurrencySummaries, state.startDateMillis, state.endDateMillisExclusive) else GeneralReportsExcelExporter.exportSummaryReport(context, currency, state.personCurrencySummaries, state.startDateMillis, state.endDateMillisExclusive)
            }
        }
    }

    fun export(pdf: Boolean) {
        if (busy || state.isLoading) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { generate(pdf) }
            result.fold({ snackbar.showSnackbar("تم إنشاء التقرير بنجاح.") }, { snackbar.showSnackbar(it.message ?: "تعذر إنشاء التقرير.") })
            busy = false
        }
    }

    fun share(pdf: Boolean) {
        if (busy || state.isLoading) return
        busy = true
        val prefix = when (reportType) { ReportType.PEOPLE -> "MyAccounts_تقرير_الأشخاص"; ReportType.DETAILED -> "MyAccounts_التقرير_العام"; ReportType.SUMMARY -> "MyAccounts_أرصدة_الحسابات" }
        val mime = if (pdf) "application/pdf" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        scope.launch(Dispatchers.IO) {
            val result = ReportShareUtil.shareGeneratedReport(context, prefix, mime) { generate(pdf) }
            withContext(Dispatchers.Main) { result.fold({ snackbar.showSnackbar("تم فتح خيارات مشاركة التقرير.") }, { snackbar.showSnackbar(it.message ?: "تعذر مشاركة التقرير.") }); busy = false }
        }
    }

    Scaffold(topBar = { AppTopBar(title = "التقارير العامة", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SummaryCard(title = "مركز التقارير") {
                    Text(if (state.selectedCurrencyCode == "ALL") "عرض العملات الثلاث بشكل مستقل دون جمعها." else "عملة التقرير: ${currencyName(state.selectedCurrencyCode)}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CurrencyChip("الكل", state.selectedCurrencyCode == "ALL", { viewModel.selectCurrency("ALL") }, Modifier.weight(1f))
                        CurrencyChip("YER", state.selectedCurrencyCode == "YER", { viewModel.selectCurrency("YER") }, Modifier.weight(1f))
                        CurrencyChip("SAR", state.selectedCurrencyCode == "SAR", { viewModel.selectCurrency("SAR") }, Modifier.weight(1f))
                        CurrencyChip("USD", state.selectedCurrencyCode == "USD", { viewModel.selectCurrency("USD") }, Modifier.weight(1f))
                    }
                }
            }
            item {
                InformationCard {
                    Text("الفترة", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(period == Period.ALL, { selectPeriod(Period.ALL) }, label = { Text("كل الحساب") })
                        FilterChip(period == Period.TODAY, { selectPeriod(Period.TODAY) }, label = { Text("اليوم") })
                        FilterChip(period == Period.WEEK, { selectPeriod(Period.WEEK) }, label = { Text("الأسبوع") })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(period == Period.MONTH, { selectPeriod(Period.MONTH) }, label = { Text("الشهر") })
                        FilterChip(period == Period.CUSTOM, { selectPeriod(Period.CUSTOM) }, label = { Text("مخصصة") })
                    }
                    if (period == Period.CUSTOM) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("من: ${formatDate(customStart)}", { showStart = true }, Modifier.weight(1f))
                        SecondaryButton("إلى: ${formatDate(customEnd)}", { showEnd = true }, Modifier.weight(1f))
                    }
                }
            }
            item {
                InformationCard {
                    Text("نوع التقرير", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(reportType == ReportType.PEOPLE, { reportType = ReportType.PEOPLE }, label = { Text("الأشخاص") })
                        FilterChip(reportType == ReportType.DETAILED, { reportType = ReportType.DETAILED }, label = { Text("العمليات") })
                        FilterChip(reportType == ReportType.SUMMARY, { reportType = ReportType.SUMMARY }, label = { Text("الأرصدة") })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("Excel", { export(false) }, Modifier.weight(1f), enabled = !busy && !state.isLoading)
                        PrimaryButton("PDF", { export(true) }, Modifier.weight(1f), enabled = !busy && !state.isLoading)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("مشاركة Excel", { share(false) }, Modifier.weight(1f), enabled = !busy && !state.isLoading)
                        SecondaryButton("مشاركة PDF", { share(true) }, Modifier.weight(1f), enabled = !busy && !state.isLoading)
                    }
                    if (busy) Text("جارٍ تنفيذ العملية...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.isLoading) {
                item { InformationCard { Text("جاري تحميل بيانات التقرير...", style = MaterialTheme.typography.bodyLarge) } }
            } else {
                if (state.selectedCurrencyCode == "ALL") {
                    item { CurrencyTotals(state.allCurrencySummaries) }
                    when (reportType) {
                        ReportType.PEOPLE -> items(state.allCurrencyPeople) { p -> ReportPersonCard(p.personName, p.currencyCode, p.totalReceivableMinor, p.totalPayableMinor, p.balanceMinor, p.transactionCount) { onPersonClick(p.personId) } }
                        ReportType.DETAILED -> items(state.allCurrencyGeneralTransactions) { t -> ReportTransactionCard(t) }
                        ReportType.SUMMARY -> items(state.allCurrencyPersonSummaries) { p -> ReportPersonCard(p.personName, p.currencyCode, p.totalReceivableMinor, p.totalPayableMinor, p.balanceMinor, p.transactionCount) { onPersonClick(p.personId) } }
                    }
                } else {
                    item { state.currencySummary?.let { CurrencyTotals(listOf(it)) } }
                    when (reportType) {
                        ReportType.PEOPLE -> items(state.people) { p -> ReportPersonCard(p.personName, state.selectedCurrencyCode, p.totalReceivableMinor, p.totalPayableMinor, p.balanceMinor, p.transactionCount) { onPersonClick(p.personId) } }
                        ReportType.DETAILED -> items(state.generalTransactions) { t -> ReportTransactionCard(t) }
                        ReportType.SUMMARY -> items(state.personCurrencySummaries) { p -> ReportPersonCard(p.personName, p.currencyCode, p.totalReceivableMinor, p.totalPayableMinor, p.balanceMinor, p.transactionCount) { onPersonClick(p.personId) } }
                    }
                }
                if (state.errorMessage != null) item { StatusChip(state.errorMessage!!, MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (showStart) DatePickerDialog(onDismissRequest = { showStart = false }, confirmButton = { TextButton({ showStart = false }) { Text("إغلاق") } }) {
        val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = customStart)
        DatePicker(picker)
        LaunchedEffect(picker.selectedDateMillis) { picker.selectedDateMillis?.let { selected -> customStart = selected; if (customEnd != null && dayStart(selected) <= dayStart(customEnd!!)) viewModel.setDateRange(dayStart(selected), dayEnd(customEnd!!)) } }
    }
    if (showEnd) DatePickerDialog(onDismissRequest = { showEnd = false }, confirmButton = { TextButton({ showEnd = false }) { Text("إغلاق") } }) {
        val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = customEnd)
        DatePicker(picker)
        LaunchedEffect(picker.selectedDateMillis) { picker.selectedDateMillis?.let { selected -> customEnd = selected; if (customStart != null && dayStart(customStart!!) <= dayStart(selected)) viewModel.setDateRange(dayStart(customStart!!), dayEnd(selected)) } }
    }
}

@Composable private fun CurrencyTotals(summaries: List<CurrencyReportSummary>) {
    SummaryCard(title = "ملخص الأرصدة") {
        summaries.forEach { summary ->
            InformationCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(currencyName(summary.currencyCode), style = MaterialTheme.typography.titleMedium)
                        Text("عدد العمليات: ${summary.transactionCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        BalanceAmount("عليه ${amount(summary.totalReceivableMinor)}", BalanceStatus.Due)
                        BalanceAmount("له ${amount(summary.totalPayableMinor)}", BalanceStatus.Owed)
                        BalanceAmount(balance(summary.balanceMinor), balanceStatus(summary.balanceMinor))
                    }
                }
            }
        }
    }
}

@Composable private fun ReportPersonCard(name: String, currency: String, receivable: Long, payable: Long, balanceValue: Long, transactionCount: Int, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(currencyName(currency), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("عدد العمليات: $transactionCount", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                BalanceAmount("عليه ${amount(receivable)}", BalanceStatus.Due)
                BalanceAmount("له ${amount(payable)}", BalanceStatus.Owed)
                BalanceAmount(balance(balanceValue), balanceStatus(balanceValue))
            }
        }
    }
}

@Composable private fun ReportTransactionCard(row: GeneralReportTransactionRow) {
    InformationCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(row.personName, style = MaterialTheme.typography.titleMedium)
                Text(formatDate(row.transactionDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(row.description.ifBlank { "بدون وصف" }, style = MaterialTheme.typography.bodyLarge)
                Text(currencyName(row.currencyCode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BalanceAmount(if (row.type == "RECEIVABLE") "عليه ${amount(row.amountMinor)}" else "له ${amount(row.amountMinor)}", if (row.type == "RECEIVABLE") BalanceStatus.Due else BalanceStatus.Owed)
        }
    }
}

private fun balanceStatus(value: Long): BalanceStatus = when { value > 0L -> BalanceStatus.Due; value < 0L -> BalanceStatus.Owed; else -> BalanceStatus.Neutral }
private fun amount(value: Long): String = BigDecimal(value).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun balance(value: Long): String = when { value > 0L -> "عليه ${amount(value)}"; value < 0L -> "له ${amount(-value)}"; else -> "متوازن 0" }
private fun currencyName(code: String): String = when (code) { "YER" -> "الريال اليمني"; "SAR" -> "الريال السعودي"; "USD" -> "الدولار الأمريكي"; else -> code }
private fun formatDate(value: Long?): String = value?.let { SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date(it)) } ?: "—"
private fun addDays(value: Long, days: Int): Long = Calendar.getInstance().apply { timeInMillis = value; add(Calendar.DAY_OF_MONTH, days) }.timeInMillis
private fun addMonths(value: Long, months: Int): Long = Calendar.getInstance().apply { timeInMillis = value; add(Calendar.MONTH, months) }.timeInMillis
