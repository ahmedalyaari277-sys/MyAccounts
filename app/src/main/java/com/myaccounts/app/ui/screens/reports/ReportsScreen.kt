package com.myaccounts.app.ui.screens.reports

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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myaccounts.app.data.reports.CurrencyReportPersonRow
import com.myaccounts.app.data.reports.GeneralReportTransactionRow
import com.myaccounts.app.data.reports.PersonCurrencySummaryRow
import com.myaccounts.app.ui.viewmodel.ReportsViewModel
import com.myaccounts.app.util.GeneralReportsExcelExporter
import com.myaccounts.app.util.GeneralReportsPdfExporter
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class GeneralReportType { PEOPLE, DETAILED, SUMMARY }
private enum class ReportPeriod { ALL, TODAY, WEEK, MONTH, CUSTOM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    onBack: () -> Unit,
    onPersonClick: (Long, String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var reportType by remember { mutableStateOf(GeneralReportType.PEOPLE) }
    var period by remember { mutableStateOf(ReportPeriod.ALL) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) { viewModel.setAllTime() }

    fun startOfDay(value: Long): Long = Calendar.getInstance().apply {
        timeInMillis = value
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun endOfDay(value: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(value); add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    fun choosePeriod(selected: ReportPeriod) {
        period = selected
        val now = System.currentTimeMillis()
        when (selected) {
            ReportPeriod.ALL -> viewModel.setAllTime()
            ReportPeriod.TODAY -> viewModel.setDateRange(startOfDay(now), endOfDay(now))
            ReportPeriod.WEEK -> {
                val start = Calendar.getInstance().apply {
                    timeInMillis = startOfDay(now)
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                }.timeInMillis
                viewModel.setDateRange(start, addDays(start, 7))
            }
            ReportPeriod.MONTH -> {
                val start = Calendar.getInstance().apply {
                    timeInMillis = startOfDay(now); set(Calendar.DAY_OF_MONTH, 1)
                }.timeInMillis
                viewModel.setDateRange(start, addMonths(start, 1))
            }
            ReportPeriod.CUSTOM -> showStartPicker = true
        }
    }

    fun exportCurrent(pdf: Boolean) {
        val start = state.startDateMillis
        val end = state.endDateMillisExclusive
        val currency = state.selectedCurrencyCode
        scope.launch {
            val result = when (reportType) {
                GeneralReportType.PEOPLE -> {
                    val summary = state.currencySummary
                    if (summary == null) Result.failure<String>(IllegalStateException("لا توجد بيانات كافية لإنشاء التقرير."))
                    else if (pdf) GeneralReportsPdfExporter.exportPeopleReport(LocalContextHolder.context, currency, summary, state.people, start, end)
                    else GeneralReportsExcelExporter.exportPeopleReport(LocalContextHolder.context, currency, summary, state.people, start, end)
                }
                GeneralReportType.DETAILED -> {
                    if (pdf) GeneralReportsPdfExporter.exportDetailedReport(LocalContextHolder.context, currency, state.generalTransactions, start, end)
                    else GeneralReportsExcelExporter.exportDetailedReport(LocalContextHolder.context, currency, state.generalTransactions, start, end)
                }
                GeneralReportType.SUMMARY -> {
                    if (pdf) GeneralReportsPdfExporter.exportSummaryReport(LocalContextHolder.context, currency, state.personCurrencySummaries, start, end)
                    else GeneralReportsExcelExporter.exportSummaryReport(LocalContextHolder.context, currency, state.personCurrencySummaries, start, end)
                }
            }
            result.fold({ snackbar.showSnackbar(it) }, { snackbar.showSnackbar(it.message ?: "حدث خطأ أثناء التصدير.") })
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    LocalContextHolder.context = context

    Scaffold(
        topBar = { TopAppBar(title = { Text("التقارير", fontWeight = FontWeight.Bold) }, navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("التقارير العامة", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("كل تقرير منفصل حسب العملة، والافتراضي هو كل الحساب.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                CurrencySelector(state.selectedCurrencyCode) { viewModel.selectCurrency(it) }
                Spacer(Modifier.height(8.dp))
                PeriodSelector(period, ::choosePeriod)
                if (period == ReportPeriod.CUSTOM) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) { Text("من: ${formatDate(customStart)}") }
                        OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) { Text("إلى: ${formatDate(customEnd)}") }
                    }
                }
                Text(periodDescription(state.startDateMillis, state.endDateMillisExclusive), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                ReportTypeSelector(reportType) { reportType = it }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportCurrent(false) }, modifier = Modifier.weight(1f), enabled = !state.isLoading) { Text("Excel") }
                    Button(onClick = { exportCurrent(true) }, modifier = Modifier.weight(1f), enabled = !state.isLoading) { Text("PDF") }
                }
            }

            item { GeneralSummaryCard(state.selectedCurrencyCode, state.currencySummary) }

            when (reportType) {
                GeneralReportType.PEOPLE -> {
                    item { Text("تقرير الأشخاص", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    item { PeopleTableHeader() }
                    if (state.people.isEmpty()) item { EmptyReport() }
                    else items(state.people, key = { it.personId }) { person -> PeopleTableRow(person, state.selectedCurrencyCode) { onPersonClick(person.personId, state.selectedCurrencyCode) } }
                }
                GeneralReportType.DETAILED -> {
                    item { Text("التقرير التفصيلي للعمليات", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    item { DetailedTableHeader() }
                    if (state.generalTransactions.isEmpty()) item { EmptyReport() }
                    else items(state.generalTransactions, key = { it.transactionId }) { DetailedTableRow(it) }
                }
                GeneralReportType.SUMMARY -> {
                    item { Text("ملخص تقرير الأشخاص", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    item { SummaryTableHeader() }
                    if (state.personCurrencySummaries.isEmpty()) item { EmptyReport() }
                    else items(state.personCurrencySummaries, key = { "${it.personId}_${it.currencyCode}" }) { SummaryTableRow(it) }
                }
            }

            state.errorMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showStartPicker) {
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = { TextButton(onClick = { showStartPicker = false }) { Text("تم") } },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("إلغاء") } }
        ) {
            val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = customStart)
            DatePicker(picker)
            LaunchedEffect(picker.selectedDateMillis) { picker.selectedDateMillis?.let { customStart = it; if (customEnd != null) viewModel.setDateRange(startOfDay(it), endOfDay(customEnd!!)) } }
        }
    }
    if (showEndPicker) {
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = { TextButton(onClick = { showEndPicker = false }) { Text("تم") } },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("إلغاء") } }
        ) {
            val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = customEnd)
            DatePicker(picker)
            LaunchedEffect(picker.selectedDateMillis) { picker.selectedDateMillis?.let { customEnd = it; if (customStart != null) viewModel.setDateRange(startOfDay(customStart!!), endOfDay(it)) } }
        }
    }
}

private object LocalContextHolder { lateinit var context: android.content.Context }

@Composable
private fun CurrencySelector(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CurrencyChip("محلي / ريال يمني", "YER", selected, onSelect)
        CurrencyChip("سعودي / ريال سعودي", "SAR", selected, onSelect)
        CurrencyChip("دولار", "USD", selected, onSelect)
    }
}

@Composable
private fun CurrencyChip(label: String, code: String, selected: String, onSelect: (String) -> Unit) {
    FilterChip(selected = selected == code, onClick = { onSelect(code) }, label = { Text(label) })
}

@Composable
private fun PeriodSelector(selected: ReportPeriod, onSelect: (ReportPeriod) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            PeriodChip("كل الحساب", ReportPeriod.ALL, selected, onSelect)
            PeriodChip("اليوم", ReportPeriod.TODAY, selected, onSelect)
            PeriodChip("هذا الأسبوع", ReportPeriod.WEEK, selected, onSelect)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            PeriodChip("هذا الشهر", ReportPeriod.MONTH, selected, onSelect)
            PeriodChip("فترة مخصصة", ReportPeriod.CUSTOM, selected, onSelect)
        }
    }
}

@Composable
private fun PeriodChip(label: String, period: ReportPeriod, selected: ReportPeriod, onSelect: (ReportPeriod) -> Unit) {
    FilterChip(selected = selected == period, onClick = { onSelect(period) }, label = { Text(label) })
}

@Composable
private fun ReportTypeSelector(selected: GeneralReportType, onSelect: (GeneralReportType) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        FilterChip(selected == GeneralReportType.PEOPLE, { onSelect(GeneralReportType.PEOPLE) }, label = { Text("الأشخاص") })
        FilterChip(selected == GeneralReportType.DETAILED, { onSelect(GeneralReportType.DETAILED) }, label = { Text("التفصيلي") })
        FilterChip(selected == GeneralReportType.SUMMARY, { onSelect(GeneralReportType.SUMMARY) }, label = { Text("الملخص") })
    }
}

@Composable
private fun GeneralSummaryCard(currency: String, summary: com.myaccounts.app.data.reports.CurrencyReportSummary?) {
    if (summary == null) return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("عليه", summary.totalReceivableMinor, MaterialTheme.colorScheme.error)
            Metric("له", summary.totalPayableMinor, Color(0xFF00854A))
            Metric("الرصيد", summary.balanceMinor, balanceColor(summary.balanceMinor))
            Metric("العمليات", summary.transactionCount.toLong(), MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun Metric(label: String, amount: Long, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
        Text(formatAmount(amount), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PeopleTableHeader() = TableRow(Modifier.width(760.dp), listOf("الشخص", "العملة", "عليه", "له", "الرصيد"), header = true, widths = listOf(2.2f, 1.5f, 1.3f, 1.3f, 1.5f))

@Composable
private fun PeopleTableRow(person: CurrencyReportPersonRow, currency: String, onClick: () -> Unit) {
    TableRow(Modifier.width(760.dp), listOf(person.personName, currencyName(currency), formatAmount(person.totalReceivableMinor), formatAmount(person.totalPayableMinor), formatAmount(person.balanceMinor)), colors = listOf(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.error, Color(0xFF00854A), balanceColor(person.balanceMinor)), widths = listOf(2.2f, 1.5f, 1.3f, 1.3f, 1.5f), onClick = onClick)
}

@Composable
private fun DetailedTableHeader() = TableRow(Modifier.width(1000.dp), listOf("التاريخ", "الشخص", "العملة", "البيان", "عليه", "له"), header = true, widths = listOf(1.2f, 1.8f, 1.5f, 3f, 1.2f, 1.2f))

@Composable
private fun DetailedTableRow(t: GeneralReportTransactionRow) = TableRow(Modifier.width(1000.dp), listOf(formatDate(t.transactionDate), t.personName, currencyName(t.currencyCode), t.description.ifBlank { "—" }, if (t.type == "RECEIVABLE") formatAmount(t.amountMinor) else "—", if (t.type == "PAYABLE") formatAmount(t.amountMinor) else "—"), colors = listOf(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurface, if (t.type == "RECEIVABLE") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, if (t.type == "PAYABLE") Color(0xFF00854A) else MaterialTheme.colorScheme.onSurface), widths = listOf(1.2f, 1.8f, 1.5f, 3f, 1.2f, 1.2f))

@Composable
private fun SummaryTableHeader() = TableRow(Modifier.width(1250.dp), listOf("الشخص", "العملة", "عليه", "له", "الرصيد", "فترة عليه", "فترة له"), header = true, widths = listOf(2.1f, 1.4f, 1.2f, 1.2f, 1.3f, 2.0f, 2.0f))

@Composable
private fun SummaryTableRow(r: PersonCurrencySummaryRow) = TableRow(Modifier.width(1250.dp), listOf(r.personName, currencyName(r.currencyCode), formatAmount(r.totalReceivableMinor), formatAmount(r.totalPayableMinor), formatAmount(r.balanceMinor), dateRange(r.firstReceivableDate, r.lastReceivableDate), dateRange(r.firstPayableDate, r.lastPayableDate)), colors = listOf(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.error, Color(0xFF00854A), balanceColor(r.balanceMinor), MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant), widths = listOf(2.1f, 1.4f, 1.2f, 1.2f, 1.3f, 2.0f, 2.0f))

@Composable
private fun TableRow(modifier: Modifier, values: List<String>, header: Boolean = false, colors: List<Color> = emptyList(), widths: List<Float>, onClick: (() -> Unit)? = null) {
    Row(modifier = modifier.then(if (onClick != null) Modifier else Modifier), verticalAlignment = Alignment.CenterVertically) {
        values.forEachIndexed { index, value ->
            Text(
                text = value,
                modifier = Modifier.weight(widths[index]).padding(horizontal = 6.dp, vertical = 8.dp),
                textAlign = TextAlign.Center,
                fontSize = if (header) 12.sp else 11.sp,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                color = colors.getOrNull(index) ?: MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EmptyReport() = Text("لا توجد بيانات ضمن الفترة المحددة.", modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)

private fun formatAmount(value: Long) = BigDecimal(value).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun currencyName(code: String) = when (code) { "YER" -> "الريال اليمني"; "SAR" -> "الريال السعودي"; "USD" -> "الدولار الأمريكي"; else -> code }
private fun balanceColor(value: Long) = when { value > 0 -> Color(0xFFB02323); value < 0 -> Color(0xFF00854A); else -> Color.Gray }
private fun formatDate(value: Long?) = value?.let { SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date(it)) } ?: "غير محدد"
private fun periodDescription(start: Long?, end: Long?) = if (start == null && end == null) "الفترة: كل الحساب" else "الفترة: ${formatDate(start)} - ${formatDate(end?.minus(1))}"
private fun dateRange(first: Long?, last: Long?) = if (first == null) "—" else "${formatDate(first)} - ${formatDate(last ?: first)}"
private fun addDays(value: Long, days: Int) = Calendar.getInstance().apply { timeInMillis = value; add(Calendar.DAY_OF_MONTH, days) }.timeInMillis
private fun addMonths(value: Long, months: Int) = Calendar.getInstance().apply { timeInMillis = value; add(Calendar.MONTH, months) }.timeInMillis
