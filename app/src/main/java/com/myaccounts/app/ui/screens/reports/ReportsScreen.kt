package com.myaccounts.app.ui.screens.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myaccounts.app.data.reports.CurrencyReportPersonRow
import com.myaccounts.app.data.reports.GeneralReportTransactionRow
import com.myaccounts.app.data.reports.PersonCurrencySummaryRow
import com.myaccounts.app.ui.viewmodel.ReportsViewModel
import com.myaccounts.app.util.GeneralReportsExcelExporter
import com.myaccounts.app.util.GeneralReportsPdfExporter
import com.myaccounts.app.util.ReportShareUtil
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class ReportType { PEOPLE, DETAILED, SUMMARY }
private enum class Period { ALL, TODAY, WEEK, MONTH, CUSTOM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel, onBack: () -> Unit, onPersonClick: (Long, String) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var reportType by remember { mutableStateOf(ReportType.PEOPLE) }
    var period by remember { mutableStateOf(Period.ALL) }
    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.setAllTime() }

    fun startOfDay(value: Long) = Calendar.getInstance().apply {
        timeInMillis = value
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    fun endOfDay(value: Long) = Calendar.getInstance().apply { timeInMillis = startOfDay(value); add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
    fun selectPeriod(value: Period) {
        period = value
        val now = System.currentTimeMillis()
        when (value) {
            Period.ALL -> viewModel.setAllTime()
            Period.TODAY -> viewModel.setDateRange(startOfDay(now), endOfDay(now))
            Period.WEEK -> {
                val start = Calendar.getInstance().apply { timeInMillis = startOfDay(now); set(Calendar.DAY_OF_WEEK, firstDayOfWeek) }.timeInMillis
                viewModel.setDateRange(start, addDays(start, 7))
            }
            Period.MONTH -> {
                val start = Calendar.getInstance().apply { timeInMillis = startOfDay(now); set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
                viewModel.setDateRange(start, addMonths(start, 1))
            }
            Period.CUSTOM -> showStartPicker = true
        }
    }

    fun export(pdf: Boolean) {
        val start = state.startDateMillis
        val end = state.endDateMillisExclusive
        val currency = state.selectedCurrencyCode
        scope.launch {
            val result = when (reportType) {
                ReportType.PEOPLE -> state.currencySummary?.let { summary ->
                    if (pdf) GeneralReportsPdfExporter.exportPeopleReport(context, currency, summary, state.people, start, end)
                    else GeneralReportsExcelExporter.exportPeopleReport(context, currency, summary, state.people, start, end)
                } ?: Result.failure(IllegalStateException("لا توجد بيانات كافية لإنشاء التقرير."))
                ReportType.DETAILED -> if (pdf) GeneralReportsPdfExporter.exportDetailedReport(context, currency, state.generalTransactions, start, end) else GeneralReportsExcelExporter.exportDetailedReport(context, currency, state.generalTransactions, start, end)
                ReportType.SUMMARY -> if (pdf) GeneralReportsPdfExporter.exportSummaryReport(context, currency, state.personCurrencySummaries, start, end) else GeneralReportsExcelExporter.exportSummaryReport(context, currency, state.personCurrencySummaries, start, end)
            }
            result.fold({ snackbar.showSnackbar(it) }, { snackbar.showSnackbar(it.message ?: "حدث خطأ أثناء التصدير.") })
        }
    }

    fun share() {
        val currency = state.selectedCurrencyCode
        val title = when (reportType) {
            ReportType.PEOPLE -> "تقرير الأشخاص"
            ReportType.DETAILED -> "التقرير التفصيلي"
            ReportType.SUMMARY -> "ملخص تقرير الأشخاص"
        }
        scope.launch {
            val pdfResult = ReportShareUtil.shareLatestReport(context, "MyAccounts_${title}_$currency_", "application/pdf")
            if (pdfResult.isFailure) {
                val excelResult = ReportShareUtil.shareLatestReport(context, "MyAccounts_${title}_$currency_", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                excelResult.fold(
                    onSuccess = { snackbar.showSnackbar("تم فتح خيارات مشاركة التقرير.") },
                    onFailure = { snackbar.showSnackbar("لم يتم العثور على تقرير صادر. صدّر التقرير PDF أو Excel أولاً.") }
                )
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("التقارير العامة", fontWeight = FontWeight.Bold) }, navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item {
                Text("التقارير العامة", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("كل تقرير مستقل حسب العملة، والافتراضي: كل الحساب.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    CurrencyChip("محلي / ريال يمني", "YER", state.selectedCurrencyCode) { viewModel.selectCurrency(it) }
                    CurrencyChip("سعودي / ريال سعودي", "SAR", state.selectedCurrencyCode) { viewModel.selectCurrency(it) }
                    CurrencyChip("دولار", "USD", state.selectedCurrencyCode) { viewModel.selectCurrency(it) }
                }
                Spacer(Modifier.height(6.dp))
                PeriodSelector(period, ::selectPeriod)
                if (period == Period.CUSTOM) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showStartPicker = true }, Modifier.weight(1f)) { Text("من: ${formatDate(customStart)}") }
                        OutlinedButton(onClick = { showEndPicker = true }, Modifier.weight(1f)) { Text("إلى: ${formatDate(customEnd)}") }
                    }
                }
                Text(periodDescription(state.startDateMillis, state.endDateMillisExclusive), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FilterChip(reportType == ReportType.PEOPLE, { reportType = ReportType.PEOPLE }, label = { Text("الأشخاص") })
                    FilterChip(reportType == ReportType.DETAILED, { reportType = ReportType.DETAILED }, label = { Text("التفصيلي") })
                    FilterChip(reportType == ReportType.SUMMARY, { reportType = ReportType.SUMMARY }, label = { Text("الملخص") })
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { export(false) }, Modifier.weight(1f), enabled = !state.isLoading) { Text("Excel") }
                    Button(onClick = { export(true) }, Modifier.weight(1f), enabled = !state.isLoading) { Text("PDF") }
                    OutlinedButton(onClick = { share() }, Modifier.weight(1f), enabled = !state.isLoading) { Text("مشاركة") }
                }
            }
            state.currencySummary?.let { summary -> item { SummaryCard(summary) } }

            when (reportType) {
                ReportType.PEOPLE -> {
                    item { Text("تقرير الأشخاص", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    item { PeopleHeader() }
                    if (state.people.isEmpty()) item { EmptyReport() }
                    else items(state.people, key = { it.personId }) { person -> PeopleRow(person, state.selectedCurrencyCode) { onPersonClick(person.personId, state.selectedCurrencyCode) } }
                }
                ReportType.DETAILED -> {
                    item { Text("التقرير التفصيلي للعمليات", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    item { DetailedHeader() }
                    if (state.generalTransactions.isEmpty()) item { EmptyReport() }
                    else items(state.generalTransactions, key = { it.transactionId }) { DetailedRow(it) }
                }
                ReportType.SUMMARY -> {
                    item { Text("ملخص تقرير الأشخاص", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    item { SummaryHeader() }
                    if (state.personCurrencySummaries.isEmpty()) item { EmptyReport() }
                    else items(state.personCurrencySummaries, key = { "${it.personId}_${it.currencyCode}" }) { SummaryRow(it) }
                }
            }
            state.errorMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
    }

    if (showStartPicker) {
        DatePickerDialog(onDismissRequest = { showStartPicker = false }, confirmButton = { TextButton({ showStartPicker = false }) { Text("إلغاء") } }) {
            val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = customStart)
            DatePicker(picker)
            LaunchedEffect(picker.selectedDateMillis) { picker.selectedDateMillis?.let { customStart = it; if (customEnd != null) viewModel.setDateRange(startOfDay(it), endOfDay(customEnd!!)) } }
        }
    }
    if (showEndPicker) {
        DatePickerDialog(onDismissRequest = { showEndPicker = false }, confirmButton = { TextButton({ showEndPicker = false }) { Text("إلغاء") } }) {
            val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = customEnd)
            DatePicker(picker)
            LaunchedEffect(picker.selectedDateMillis) { picker.selectedDateMillis?.let { customEnd = it; if (customStart != null) viewModel.setDateRange(startOfDay(customStart!!), endOfDay(it)) } }
        }
    }
}

@Composable private fun CurrencyChip(label: String, code: String, selected: String, onSelect: (String) -> Unit) = FilterChip(selected == code, { onSelect(code) }, label = { Text(label) })

@Composable private fun PeriodSelector(selected: Period, onSelect: (Period) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            FilterChip(selected == Period.ALL, { onSelect(Period.ALL) }, label = { Text("كل الحساب") })
            FilterChip(selected == Period.TODAY, { onSelect(Period.TODAY) }, label = { Text("اليوم") })
            FilterChip(selected == Period.WEEK, { onSelect(Period.WEEK) }, label = { Text("هذا الأسبوع") })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            FilterChip(selected == Period.MONTH, { onSelect(Period.MONTH) }, label = { Text("هذا الشهر") })
            FilterChip(selected == Period.CUSTOM, { onSelect(Period.CUSTOM) }, label = { Text("فترة مخصصة") })
        }
    }
}

@Composable private fun SummaryCard(summary: com.myaccounts.app.data.reports.CurrencyReportSummary) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("عليه", summary.totalReceivableMinor, MaterialTheme.colorScheme.error)
            Metric("له", summary.totalPayableMinor, Color(0xFF00854A))
            Metric("الرصيد", summary.balanceMinor, balanceColor(summary.balanceMinor))
            Metric("العمليات", summary.transactionCount.toLong(), MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable private fun Metric(label: String, value: Long, color: Color) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(formatAmount(value), fontWeight = FontWeight.Bold) } }

@Composable private fun PeopleHeader() = TableRow(760.dp, listOf("الشخص", "العملة", "عليه", "له", "الرصيد"), listOf(2.2f, 1.5f, 1.3f, 1.3f, 1.5f), true)
@Composable private fun PeopleRow(p: CurrencyReportPersonRow, currency: String, onClick: () -> Unit) = TableRow(760.dp, listOf(p.personName, currencyName(currency), formatAmount(p.totalReceivableMinor), formatAmount(p.totalPayableMinor), formatAmount(p.balanceMinor)), listOf(2.2f, 1.5f, 1.3f, 1.3f, 1.5f), false, listOf(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.error, Color(0xFF00854A), balanceColor(p.balanceMinor)), onClick)
@Composable private fun DetailedHeader() = TableRow(1000.dp, listOf("التاريخ", "الشخص", "العملة", "البيان", "عليه", "له"), listOf(1.2f, 1.8f, 1.5f, 3f, 1.2f, 1.2f), true)
@Composable private fun DetailedRow(t: GeneralReportTransactionRow) = TableRow(1000.dp, listOf(formatDate(t.transactionDate), t.personName, currencyName(t.currencyCode), t.description.ifBlank { "—" }, if (t.type == "RECEIVABLE") formatAmount(t.amountMinor) else "—", if (t.type == "PAYABLE") formatAmount(t.amountMinor) else "—"), listOf(1.2f, 1.8f, 1.5f, 3f, 1.2f, 1.2f), false, listOf(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurface, if (t.type == "RECEIVABLE") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, if (t.type == "PAYABLE") Color(0xFF00854A) else MaterialTheme.colorScheme.onSurface))
@Composable private fun SummaryHeader() = TableRow(1250.dp, listOf("الشخص", "العملة", "عليه", "له", "الرصيد", "فترة عليه", "فترة له"), listOf(2.1f, 1.4f, 1.2f, 1.2f, 1.3f, 2f, 2f), true)
@Composable private fun SummaryRow(r: PersonCurrencySummaryRow) = TableRow(1250.dp, listOf(r.personName, currencyName(r.currencyCode), formatAmount(r.totalReceivableMinor), formatAmount(r.totalPayableMinor), formatAmount(r.balanceMinor), dateRange(r.firstReceivableDate, r.lastReceivableDate), dateRange(r.firstPayableDate, r.lastPayableDate)), listOf(2.1f, 1.4f, 1.2f, 1.2f, 1.3f, 2f, 2f), false, listOf(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.error, Color(0xFF00854A), balanceColor(r.balanceMinor), MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant))

@Composable private fun TableRow(totalWidth: Dp, values: List<String>, widths: List<Float>, header: Boolean, colors: List<Color> = emptyList(), onClick: (() -> Unit)? = null) {
    val scroll = rememberScrollState()
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)) {
        Row(Modifier.width(totalWidth)) {
            values.forEachIndexed { i, value ->
                Text(value, Modifier.weight(widths[i]).padding(horizontal = 5.dp, vertical = 8.dp), textAlign = TextAlign.Center, fontSize = if (header) 12.sp else 11.sp, fontWeight = if (header) FontWeight.Bold else FontWeight.Normal, color = colors.getOrNull(i) ?: MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable private fun EmptyReport() = Text("لا توجد بيانات ضمن الفترة المحددة.", Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
private fun formatAmount(value: Long) = BigDecimal(value).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun currencyName(code: String) = when (code) { "YER" -> "الريال اليمني"; "SAR" -> "الريال السعودي"; "USD" -> "الدولار الأمريكي"; else -> code }
private fun balanceColor(value: Long) = when { value > 0 -> Color(0xFFB02323); value < 0 -> Color(0xFF00854A); else -> Color.Gray }
private fun formatDate(value: Long?) = value?.let { SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date(it)) } ?: "غير محدد"
private fun periodDescription(start: Long?, end: Long?) = if (start == null && end == null) "الفترة: كل الحساب" else "الفترة: ${formatDate(start)} - ${formatDate(end?.minus(1))}"
private fun dateRange(first: Long?, last: Long?) = if (first == null) "—" else "${formatDate(first)} - ${formatDate(last ?: first)}"
private fun addDays(value: Long, days: Int) = Calendar.getInstance().apply { timeInMillis = value; add(Calendar.DAY_OF_MONTH, days) }.timeInMillis
private fun addMonths(value: Long, months: Int) = Calendar.getInstance().apply { timeInMillis = value; add(Calendar.MONTH, months) }.timeInMillis
