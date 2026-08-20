package com.myaccounts.app.ui.screens.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myaccounts.app.ui.viewmodel.ReportsViewModel
import com.myaccounts.app.util.GeneralReportsExcelExporter
import com.myaccounts.app.util.GeneralReportsPdfExporter
import com.myaccounts.app.util.MultiCurrencyReportExcelExporter
import com.myaccounts.app.util.MultiCurrencyReportPdfExporter
import com.myaccounts.app.util.ReportShareUtil
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.net.Uri

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
    var showCurrencyMenu by remember { mutableStateOf(false) }
    var lastPdfUri by remember { mutableStateOf<Uri?>(null) }
    var lastExcelUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) { viewModel.selectCurrency("ALL") }
    LaunchedEffect(reportType, state.selectedCurrencyCode, period, customStart, customEnd) {
        lastPdfUri = null
        lastExcelUri = null
    }

    fun dayStart(v: Long) = Calendar.getInstance().apply {
        timeInMillis = v
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun dayEnd(v: Long) = Calendar.getInstance().apply {
        timeInMillis = dayStart(v)
        add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    fun choose(p: Period) {
        period = p
        val now = System.currentTimeMillis()
        when (p) {
            Period.ALL -> viewModel.setAllTime()
            Period.TODAY -> viewModel.setDateRange(dayStart(now), dayEnd(now))
            Period.WEEK -> {
                val start = Calendar.getInstance().apply {
                    timeInMillis = dayStart(now)
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                }.timeInMillis
                viewModel.setDateRange(start, addDays(start, 7))
            }
            Period.MONTH -> {
                val start = Calendar.getInstance().apply {
                    timeInMillis = dayStart(now)
                    set(Calendar.DAY_OF_MONTH, 1)
                }.timeInMillis
                viewModel.setDateRange(start, addMonths(start, 1))
            }
            Period.CUSTOM -> showStart = true
        }
    }

    fun reportPrefix(): String {
        val allCurrencies = state.selectedCurrencyCode == "ALL"
        return if (allCurrencies) {
            when (reportType) {
                ReportType.PEOPLE -> "MyAccounts_تقرير الأشخاص"
                ReportType.DETAILED -> "MyAccounts_التقرير العام"
                ReportType.SUMMARY -> "MyAccounts_أرصدة الحسابات"
            }
        } else {
            when (reportType) {
                ReportType.PEOPLE -> "MyAccounts_تقرير_الأشخاص"
                ReportType.DETAILED -> "MyAccounts_التقرير_التفصيلي"
                ReportType.SUMMARY -> "MyAccounts_ملخص_الأشخاص"
            }
        }
    }

    fun export(pdf: Boolean) {
        scope.launch {
            lastPdfUri = if (pdf) null else lastPdfUri
            lastExcelUri = if (!pdf) null else lastExcelUri
            val allCurrencies = state.selectedCurrencyCode == "ALL"
            val result = if (allCurrencies) {
                when (reportType) {
                    ReportType.PEOPLE -> if (pdf) MultiCurrencyReportPdfExporter.exportPeopleReport(context, state.allCurrencySummaries, state.allCurrencyPeople, state.startDateMillis, state.endDateMillisExclusive) else MultiCurrencyReportExcelExporter.exportPeopleReport(context, state.allCurrencySummaries, state.allCurrencyPeople, state.startDateMillis, state.endDateMillisExclusive)
                    ReportType.DETAILED -> if (pdf) MultiCurrencyReportPdfExporter.exportDetailedReport(context, state.allCurrencySummaries, state.allCurrencyGeneralTransactions, state.startDateMillis, state.endDateMillisExclusive) else MultiCurrencyReportExcelExporter.exportDetailedReport(context, state.allCurrencySummaries, state.allCurrencyGeneralTransactions, state.startDateMillis, state.endDateMillisExclusive)
                    ReportType.SUMMARY -> if (pdf) MultiCurrencyReportPdfExporter.exportSummaryReport(context, state.allCurrencyPersonSummaries, state.startDateMillis, state.endDateMillisExclusive) else MultiCurrencyReportExcelExporter.exportSummaryReport(context, state.allCurrencyPersonSummaries, state.startDateMillis, state.endDateMillisExclusive)
                }
            } else {
                val currency = state.selectedCurrencyCode
                when (reportType) {
                    ReportType.PEOPLE -> {
                        val summary = state.currencySummary
                        if (summary == null) Result.failure(IllegalStateException("لم تكتمل بيانات التقرير بعد.")) else if (pdf) GeneralReportsPdfExporter.exportPeopleReport(context, currency, summary, state.people, state.startDateMillis, state.endDateMillisExclusive) else GeneralReportsExcelExporter.exportPeopleReport(context, currency, summary, state.people, state.startDateMillis, state.endDateMillisExclusive)
                    }
                    ReportType.DETAILED -> if (pdf) GeneralReportsPdfExporter.exportDetailedReport(context, currency, state.generalTransactions, state.startDateMillis, state.endDateMillisExclusive) else GeneralReportsExcelExporter.exportDetailedReport(context, currency, state.generalTransactions, state.startDateMillis, state.endDateMillisExclusive)
                    ReportType.SUMMARY -> if (pdf) GeneralReportsPdfExporter.exportSummaryReport(context, currency, state.personCurrencySummaries, state.startDateMillis, state.endDateMillisExclusive) else GeneralReportsExcelExporter.exportSummaryReport(context, currency, state.personCurrencySummaries, state.startDateMillis, state.endDateMillisExclusive)
                }
            }
            result.fold(
                { _ ->
                    val mimeType = if (pdf) "application/pdf" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    ReportShareUtil.findLatestReport(context, reportPrefix(), mimeType).fold(
                        { resolvedUri -> if (pdf) lastPdfUri = resolvedUri else lastExcelUri = resolvedUri },
                        { exception -> snackbar.showSnackbar("تم إنشاء التقرير، لكن تعذر ربطه بالمشاركة: ${exception.message ?: "خطأ غير معروف"}") }
                    )
                    snackbar.showSnackbar("تم إنشاء التقرير بنجاح.")
                },
                { exception -> snackbar.showSnackbar("تعذر إنشاء التقرير: ${exception.message ?: "خطأ غير معروف"}") }
            )
        }
    }

    fun share(pdf: Boolean) {
        val mimeType = if (pdf) "application/pdf" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        val uri = if (pdf) lastPdfUri else lastExcelUri
        if (uri == null) {
            scope.launch { snackbar.showSnackbar("أنشئ التقرير المطلوب أولاً قبل مشاركته.") }
            return
        }
        scope.launch {
            ReportShareUtil.shareReport(context, uri, mimeType).fold(
                { _ -> snackbar.showSnackbar("تم فتح خيارات مشاركة التقرير.") },
                { exception -> snackbar.showSnackbar("تعذر مشاركة التقرير: ${exception.message ?: "خطأ غير معروف"}") }
            )
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("التقارير العامة", fontWeight = FontWeight.Bold) }, navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }, actions = { TextButton(onClick = { showCurrencyMenu = true }) { Text("المزيد ⋮") }; DropdownMenu(expanded = showCurrencyMenu, onDismissRequest = { showCurrencyMenu = false }) { DropdownMenuItem(text = { Text("جميع العملات") }, onClick = { showCurrencyMenu = false; viewModel.selectCurrency("ALL") }); DropdownMenuItem(text = { Text("الريال اليمني") }, onClick = { showCurrencyMenu = false; viewModel.selectCurrency("YER") }); DropdownMenuItem(text = { Text("الريال السعودي") }, onClick = { showCurrencyMenu = false; viewModel.selectCurrency("SAR") }); DropdownMenuItem(text = { Text("الدولار الأمريكي") }, onClick = { showCurrencyMenu = false; viewModel.selectCurrency("USD") }) } }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("التقارير العامة", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(if (state.selectedCurrencyCode == "ALL") "التقرير الكامل يعرض العملات الثلاث مستقلة." else "تقرير منفصل: ${currencyName(state.selectedCurrencyCode)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp)); PeriodSelector(period, ::choose)
                if (period == Period.CUSTOM) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { showStart = true }, Modifier.weight(1f)) { Text("من: ${formatDate(customStart)}") }; OutlinedButton(onClick = { showEnd = true }, Modifier.weight(1f)) { Text("إلى: ${formatDate(customEnd)}") } }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(reportType == ReportType.PEOPLE, { reportType = ReportType.PEOPLE }, label = { Text("الأشخاص") }); FilterChip(reportType == ReportType.DETAILED, { reportType = ReportType.DETAILED }, label = { Text("التقرير العام") }); FilterChip(reportType == ReportType.SUMMARY, { reportType = ReportType.SUMMARY }, label = { Text("أرصدة الحسابات") }) }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { export(false) }, Modifier.weight(1f), enabled = !state.isLoading) { Text("Excel") }; Button(onClick = { export(true) }, Modifier.weight(1f), enabled = !state.isLoading) { Text("PDF") } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { share(false) }, Modifier.weight(1f), enabled = !state.isLoading) { Text("مشاركة Excel") }; OutlinedButton(onClick = { share(true) }, Modifier.weight(1f), enabled = !state.isLoading) { Text("مشاركة PDF") } }
            }
            if (state.selectedCurrencyCode == "ALL") {
                item { CurrencyTotals(state.allCurrencySummaries) }
                when (reportType) {
                    ReportType.PEOPLE -> { item { Text("الأشخاص", fontSize = 20.sp, fontWeight = FontWeight.Bold) }; items(state.allCurrencyPeople) { p -> PersonCard(p.personName, p.currencyCode, p.totalReceivableMinor, p.totalPayableMinor, p.balanceMinor, p.transactionCount, null) } }
                    ReportType.DETAILED -> { item { Text("التقرير العام للعمليات", fontSize = 20.sp, fontWeight = FontWeight.Bold) }; items(state.allCurrencyGeneralTransactions) { t -> TransactionCard(t.transactionDate, t.personName, t.description, t.currencyCode, t.type, t.amountMinor) } }
                    ReportType.SUMMARY -> { item { Text("أرصدة الحسابات", fontSize = 20.sp, fontWeight = FontWeight.Bold) }; items(state.allCurrencyPersonSummaries) { r -> PersonCard(r.personName, r.currencyCode, r.totalReceivableMinor, r.totalPayableMinor, r.balanceMinor, r.transactionCount, null) } }
                }
            } else {
                item { SingleCurrencyTotals(state.currencySummary) }
                when (reportType) {
                    ReportType.PEOPLE -> { item { Text("الأشخاص", fontSize = 20.sp, fontWeight = FontWeight.Bold) }; items(state.people) { p -> PersonCard(p.personName, state.selectedCurrencyCode, p.totalReceivableMinor, p.totalPayableMinor, p.balanceMinor, p.transactionCount, null) } }
                    ReportType.DETAILED -> { item { Text("التقرير العام للعمليات", fontSize = 20.sp, fontWeight = FontWeight.Bold) }; items(state.generalTransactions) { t -> TransactionCard(t.transactionDate, t.personName, t.description, t.currencyCode, t.type, t.amountMinor) } }
                    ReportType.SUMMARY -> { item { Text("أرصدة الحسابات", fontSize = 20.sp, fontWeight = FontWeight.Bold) }; items(state.personCurrencySummaries) { r -> PersonCard(r.personName, state.selectedCurrencyCode, r.totalReceivableMinor, r.totalPayableMinor, r.balanceMinor, r.transactionCount, null) } }
                }
            }
            if (state.errorMessage != null) item { Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (showStart) DatePickerDialog(onDismissRequest = { showStart = false }, confirmButton = { TextButton({ showStart = false }) { Text("إغلاق") } }) { val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = customStart); DatePicker(picker); LaunchedEffect(picker.selectedDateMillis) { picker.selectedDateMillis?.let { customStart = it; if (customEnd != null) viewModel.setDateRange(dayStart(it), dayEnd(customEnd!!)) } } }
    if (showEnd) DatePickerDialog(onDismissRequest = { showEnd = false }, confirmButton = { TextButton({ showEnd = false }) { Text("إغلاق") } }) { val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = customEnd); DatePicker(picker); LaunchedEffect(picker.selectedDateMillis) { picker.selectedDateMillis?.let { customEnd = it; if (customStart != null) viewModel.setDateRange(dayStart(customStart!!), dayEnd(it)) } } }
}

@Composable private fun PersonCard(name: String, currency: String, receivable: Long, payable: Long, balanceValue: Long, transactionCount: Int, onClick: (() -> Unit)?) { Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold); Text(currencyName(currency), color = MaterialTheme.colorScheme.onSurfaceVariant) }; Column { Text("عليه ${amount(receivable)}", color = MaterialTheme.colorScheme.error); Text("له ${amount(payable)}", color = MaterialTheme.colorScheme.secondary); Text(balance(balanceValue), fontWeight = FontWeight.Bold); Text("العمليات: $transactionCount") } } } }
@Composable private fun TransactionCard(date: Long, personName: String, description: String, currency: String, type: String, value: Long) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("${formatDate(date)} — $personName", fontWeight = FontWeight.Bold); Text(description.ifBlank { "—" }); Text(currencyName(currency), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(if (type == "RECEIVABLE") "عليه ${amount(value)}" else "له ${amount(value)}", color = if (type == "RECEIVABLE") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary) } } }
@Composable private fun PeriodSelector(selected: Period, onSelect: (Period) -> Unit) { Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { FilterChip(selected == Period.ALL, { onSelect(Period.ALL) }, label = { Text("كل الحساب") }); FilterChip(selected == Period.TODAY, { onSelect(Period.TODAY) }, label = { Text("اليوم") }); FilterChip(selected == Period.WEEK, { onSelect(Period.WEEK) }, label = { Text("هذا الأسبوع") }) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { FilterChip(selected == Period.MONTH, { onSelect(Period.MONTH) }, label = { Text("هذا الشهر") }); FilterChip(selected == Period.CUSTOM, { onSelect(Period.CUSTOM) }, label = { Text("فترة مخصصة") }) } } }
@Composable private fun CurrencyTotals(summaries: List<com.myaccounts.app.data.reports.CurrencyReportSummary>) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { summaries.forEach { s -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(currencyName(s.currencyCode), fontWeight = FontWeight.Bold); Text("عليه ${amount(s.totalReceivableMinor)}", color = MaterialTheme.colorScheme.error); Text("له ${amount(s.totalPayableMinor)}", color = MaterialTheme.colorScheme.secondary); Text(balance(s.balanceMinor), fontWeight = FontWeight.Bold) } } } } }
@Composable private fun SingleCurrencyTotals(summary: com.myaccounts.app.data.reports.CurrencyReportSummary?) { if (summary == null) return; Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(currencyName(summary.currencyCode), fontWeight = FontWeight.Bold); Text("عليه ${amount(summary.totalReceivableMinor)}", color = MaterialTheme.colorScheme.error); Text("له ${amount(summary.totalPayableMinor)}", color = MaterialTheme.colorScheme.secondary); Text(balance(summary.balanceMinor), fontWeight = FontWeight.Bold) } } }
private fun amount(v: Long) = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun balance(v: Long) = when { v > 0L -> "عليه ${amount(v)}"; v < 0L -> "له ${amount(-v)}"; else -> "متعادل 0" }
private fun currencyName(c: String) = when (c) { "YER" -> "الريال اليمني"; "SAR" -> "الريال السعودي"; "USD" -> "الدولار الأمريكي"; else -> c }
private fun formatDate(v: Long?) = v?.let { SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date(it)) } ?: "—"
private fun addDays(v: Long, d: Int) = Calendar.getInstance().apply { timeInMillis = v; add(Calendar.DAY_OF_MONTH, d) }.timeInMillis
private fun addMonths(v: Long, d: Int) = Calendar.getInstance().apply { timeInMillis = v; add(Calendar.MONTH, d) }.timeInMillis
