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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myaccounts.app.data.reports.CurrencyReportPersonRow
import com.myaccounts.app.ui.viewmodel.ReportsViewModel
import com.myaccounts.app.util.CurrencyReportExcelExporter
import com.myaccounts.app.util.ReportPdfExporter
import java.math.BigDecimal
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

private enum class ReportPeriod { ALL_TIME, TODAY, THIS_WEEK, THIS_MONTH, CUSTOM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel, onBack: () -> Unit, onPersonClick: (Long, String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.ALL_TIME) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var customStartMillis by remember { mutableStateOf<Long?>(null) }
    var customEndMillis by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) { viewModel.setAllTime() }

    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun endOfDayExclusive(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(millis); add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    fun applyPeriod(period: ReportPeriod) {
        selectedPeriod = period
        val now = System.currentTimeMillis()
        when (period) {
            ReportPeriod.ALL_TIME -> viewModel.setAllTime()
            ReportPeriod.TODAY -> viewModel.setDateRange(startOfDay(now), endOfDayExclusive(now))
            ReportPeriod.THIS_WEEK -> {
                val start = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                }.timeInMillis
                viewModel.setDateRange(start, start + 7L * 24L * 60L * 60L * 1000L)
            }
            ReportPeriod.THIS_MONTH -> {
                val start = Calendar.getInstance().apply {
                    timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.MONTH, 1) }.timeInMillis
                viewModel.setDateRange(start, end)
            }
            ReportPeriod.CUSTOM -> if (customStartMillis != null && customEndMillis != null) {
                viewModel.setDateRange(customStartMillis!!, endOfDayExclusive(customEndMillis!!))
            } else showStartDatePicker = true
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("التقارير", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("تقرير الحسابات", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("يعرض التقرير جميع العمليات افتراضيًا، ويمكنك تحديد فترة زمنية.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                CurrencySelector(uiState.selectedCurrencyCode, viewModel::selectCurrency)
                Spacer(Modifier.height(12.dp))
                Text("الفترة", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ReportPeriodSelector(selectedPeriod, ::applyPeriod)
                if (selectedPeriod == ReportPeriod.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ showStartDatePicker = true }, Modifier.weight(1f)) { Text("من: ${formatDate(customStartMillis)}") }
                        Button({ showEndDatePicker = true }, Modifier.weight(1f)) { Text("إلى: ${formatDate(customEndMillis)}") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(periodDescription(uiState.startDateMillis, uiState.endDateMillisExclusive), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                val summary = uiState.currencySummary
                val exportEnabled = !uiState.isLoading && summary != null
                Button(onClick = {
                    if (summary == null) coroutineScope.launch { snackbarHostState.showSnackbar("لا توجد بيانات كافية لإنشاء التقرير.") }
                    else coroutineScope.launch { CurrencyReportExcelExporter.exportCurrencyReport(context, summary, uiState.people).fold({ snackbarHostState.showSnackbar(it) }, { snackbarHostState.showSnackbar(it.message ?: "حدث خطأ أثناء إنشاء ملف Excel.") }) }
                }, Modifier.fillMaxWidth(), enabled = exportEnabled) {
                    Icon(Icons.Default.TableChart, null); Spacer(Modifier.padding(horizontal = 4.dp)); Text("تصدير التقرير إلى Excel")
                }
                Button(onClick = {
                    if (summary == null) coroutineScope.launch { snackbarHostState.showSnackbar("لا توجد بيانات كافية لإنشاء التقرير.") }
                    else coroutineScope.launch { ReportPdfExporter.exportCurrencyReport(context, summary, uiState.people).fold({ snackbarHostState.showSnackbar(it) }, { snackbarHostState.showSnackbar(it.message ?: "حدث خطأ أثناء إنشاء ملف PDF.") }) }
                }, Modifier.fillMaxWidth(), enabled = exportEnabled) {
                    Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.padding(horizontal = 4.dp)); Text("تصدير التقرير إلى PDF")
                }
            }
            uiState.currencySummary?.let { summary -> item { CurrencySummaryCard(summary.currencyCode, summary.totalReceivableMinor, summary.totalPayableMinor, summary.balanceMinor, summary.transactionCount) } }
            item { Text("الأشخاص", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            when {
                uiState.isLoading -> item { LoadingReportCard() }
                uiState.people.isEmpty() -> item { EmptyReportCard() }
                else -> items(uiState.people, key = { it.personId }) { person -> CurrencyReportPersonCard(person, uiState.selectedCurrencyCode) { onPersonClick(person.personId, uiState.selectedCurrencyCode) } }
            }
            uiState.errorMessage?.let { message -> item { ErrorReportCard(message) } }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (showStartDatePicker) DatePickerDialog({ showStartDatePicker = false }, { TextButton({ showStartDatePicker = false }) { Text("تم") } }) {
        val pickerState = remember { androidx.compose.material3.DatePickerState(initialSelectedDateMillis = customStartMillis, locale = Locale.getDefault()) }
        DatePicker(pickerState)
        LaunchedEffect(pickerState.selectedDateMillis) { pickerState.selectedDateMillis?.let { customStartMillis = it; if (customEndMillis != null) viewModel.setDateRange(it, endOfDayExclusive(customEndMillis!!)) } }
    }
    if (showEndDatePicker) DatePickerDialog({ showEndDatePicker = false }, { TextButton({ showEndDatePicker = false }) { Text("تم") } }) {
        val pickerState = remember { androidx.compose.material3.DatePickerState(initialSelectedDateMillis = customEndMillis, locale = Locale.getDefault()) }
        DatePicker(pickerState)
        LaunchedEffect(pickerState.selectedDateMillis) { pickerState.selectedDateMillis?.let { customEndMillis = it; if (customStartMillis != null) viewModel.setDateRange(customStartMillis!!, endOfDayExclusive(it)) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportPeriodSelector(selectedPeriod: ReportPeriod, onSelected: (ReportPeriod) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PeriodChip("كل الحساب", ReportPeriod.ALL_TIME, selectedPeriod, onSelected)
            PeriodChip("اليوم", ReportPeriod.TODAY, selectedPeriod, onSelected)
            PeriodChip("هذا الأسبوع", ReportPeriod.THIS_WEEK, selectedPeriod, onSelected)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PeriodChip("هذا الشهر", ReportPeriod.THIS_MONTH, selectedPeriod, onSelected)
            PeriodChip("فترة مخصصة", ReportPeriod.CUSTOM, selectedPeriod, onSelected)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodChip(label: String, period: ReportPeriod, selectedPeriod: ReportPeriod, onSelected: (ReportPeriod) -> Unit) {
    FilterChip(selected = selectedPeriod == period, onClick = { onSelected(period) }, label = { Text(label) })
}

private fun formatDate(millis: Long?): String {
    if (millis == null) return "غير محدد"
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    return String.format(Locale.getDefault(), "%02d/%02d/%04d", calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.YEAR))
}

private fun periodDescription(start: Long?, endExclusive: Long?): String = if (start == null && endExclusive == null) "الفترة الحالية: كل الحساب — جميع العمليات" else "الفترة الحالية: ${formatDate(start)} - ${endExclusive?.let { formatDate(it - 1L) } ?: "غير محدد"}"

@Composable
private fun CurrencySelector(selectedCurrency: String, onCurrencySelected: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CurrencyChip("الريال اليمني", "YER", selectedCurrency, onCurrencySelected)
        CurrencyChip("الريال السعودي", "SAR", selectedCurrency, onCurrencySelected)
        CurrencyChip("الدولار", "USD", selectedCurrency, onCurrencySelected)
    }
}

@Composable
private fun CurrencyChip(label: String, code: String, selectedCurrency: String, onSelected: (String) -> Unit) = FilterChip(selectedCurrency == code, { onSelected(code) }, label = { Text(label) })

@Composable
private fun CurrencySummaryCard(currencyCode: String, totalReceivableMinor: Long, totalPayableMinor: Long, balanceMinor: Long, transactionCount: Int) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("ملخص ${currencyName(currencyCode)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportMetricCard(Modifier.weight(1f), "عليه", totalReceivableMinor, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                ReportMetricCard(Modifier.weight(1f), "له", totalPayableMinor, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            }
            Spacer(Modifier.height(8.dp))
            ReportMetricCard(Modifier.fillMaxWidth(), "الرصيد", balanceMinor, balanceContainerColor(balanceMinor), balanceContentColor(balanceMinor))
            Spacer(Modifier.height(10.dp))
            Text("عدد العمليات: $transactionCount", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun ReportMetricCard(modifier: Modifier, title: String, amountMinor: Long, containerColor: Color, contentColor: Color) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = contentColor)
            Spacer(Modifier.height(4.dp))
            Text(formatAmount(amountMinor), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = contentColor)
        }
    }
}

@Composable
private fun CurrencyReportPersonCard(person: CurrencyReportPersonRow, currencyCode: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(person.personName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(currencyName(currencyCode), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatAmount(person.balanceMinor), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = balanceTextColor(person.balanceMinor))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PersonAmountItem(Modifier.weight(1f), "عليه", person.totalReceivableMinor, MaterialTheme.colorScheme.tertiary)
                PersonAmountItem(Modifier.weight(1f), "له", person.totalPayableMinor, MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("عدد العمليات: ${person.transactionCount}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("عرض التقرير", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PersonAmountItem(modifier: Modifier, label: String, amountMinor: Long, color: Color) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(formatAmount(amountMinor), fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LoadingReportCard() = Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("جاري تحميل التقرير...", color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun EmptyReportCard() = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("لا توجد بيانات", fontSize = 18.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text("لا توجد حسابات أو عمليات لهذه العملة حاليًا.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun ErrorReportCard(message: String) = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)) { Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 14.sp) }

@Composable private fun balanceTextColor(balanceMinor: Long): Color = when { balanceMinor > 0L -> MaterialTheme.colorScheme.tertiary; balanceMinor < 0L -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurface }
@Composable private fun balanceContainerColor(balanceMinor: Long): Color = when { balanceMinor > 0L -> MaterialTheme.colorScheme.tertiaryContainer; balanceMinor < 0L -> MaterialTheme.colorScheme.errorContainer; else -> MaterialTheme.colorScheme.surface }
@Composable private fun balanceContentColor(balanceMinor: Long): Color = when { balanceMinor > 0L -> MaterialTheme.colorScheme.onTertiaryContainer; balanceMinor < 0L -> MaterialTheme.colorScheme.onErrorContainer; else -> MaterialTheme.colorScheme.onSurface }
private fun currencyName(currencyCode: String): String = when (currencyCode) { "YER" -> "الريال اليمني"; "SAR" -> "الريال السعودي"; "USD" -> "الدولار الأمريكي"; else -> currencyCode }
private fun formatAmount(amountMinor: Long): String = BigDecimal(amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString()
