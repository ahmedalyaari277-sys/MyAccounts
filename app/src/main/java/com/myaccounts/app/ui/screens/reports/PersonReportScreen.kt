package com.myaccounts.app.ui.screens.reports

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myaccounts.app.data.reports.PersonReportSummary
import com.myaccounts.app.data.reports.PersonReportTransactionRow
import com.myaccounts.app.ui.viewmodel.ReportsViewModel
import com.myaccounts.app.ui.viewmodel.TransactionViewModel
import com.myaccounts.app.util.PersonReportExcelExporter
import com.myaccounts.app.util.PersonReportPdfExporter
import com.myaccounts.app.util.ReportShareUtil
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class PersonReportRangePreset { ALL, TODAY, WEEK, MONTH, CUSTOM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonReportScreen(
    personId: Long,
    currencyCode: String,
    viewModel: ReportsViewModel,
    transactionViewModel: TransactionViewModel? = null,
    onTransactionClick: (Long, String) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var selectedRangePreset by remember { mutableStateOf(PersonReportRangePreset.ALL) }
    val todayStartMillis = remember { startOfDayMillis(System.currentTimeMillis()) }

    LaunchedEffect(personId, currencyCode) {
        selectedRangePreset = PersonReportRangePreset.ALL
        viewModel.selectCurrency(currencyCode)
        viewModel.selectPerson(personId)
        viewModel.setAllTime()
    }

    val startDateMillis = uiState.startDateMillis
    val endDateMillisExclusive = uiState.endDateMillisExclusive

    fun sharePersonReport() {
        val summary = uiState.selectedPersonSummary ?: return
        val prefix = "MyAccounts_Person_Report_${safeFileName(summary.personName)}_"
        coroutineScope.launch {
            val pdfResult = ReportShareUtil.shareLatestReport(context, prefix, "application/pdf")
            if (pdfResult.isFailure) {
                val excelResult = ReportShareUtil.shareLatestReport(context, prefix, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                excelResult.fold(
                    onSuccess = { snackbarHostState.showSnackbar("تم فتح خيارات مشاركة التقرير.") },
                    onFailure = { snackbarHostState.showSnackbar("لم يتم العثور على تقرير صادر. صدّر التقرير PDF أو Excel أولاً.") }
                )
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("تقرير حساب", fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)
        ) {
            uiState.selectedPersonSummary?.let { summary ->
                PersonReportHeader(summary, startDateMillis, endDateMillisExclusive)
                Spacer(Modifier.height(12.dp))
            }

            PersonReportRangePresetSection(
                selectedPreset = selectedRangePreset,
                onAllTime = { selectedRangePreset = PersonReportRangePreset.ALL; viewModel.setAllTime() },
                onToday = {
                    selectedRangePreset = PersonReportRangePreset.TODAY
                    val start = startOfDayMillis(System.currentTimeMillis())
                    viewModel.setDateRange(start, addDays(start, 1))
                },
                onWeek = {
                    selectedRangePreset = PersonReportRangePreset.WEEK
                    val start = startOfWeekMillis(System.currentTimeMillis())
                    viewModel.setDateRange(start, addDays(start, 7))
                },
                onMonth = {
                    selectedRangePreset = PersonReportRangePreset.MONTH
                    val start = startOfMonthMillis(System.currentTimeMillis())
                    viewModel.setDateRange(start, addMonths(start, 1))
                },
                onCustom = { selectedRangePreset = PersonReportRangePreset.CUSTOM; showStartDatePicker = true }
            )

            Spacer(Modifier.height(10.dp))
            DateRangeSection(
                startDateMillis,
                endDateMillisExclusive,
                onStartDateClick = { selectedRangePreset = PersonReportRangePreset.CUSTOM; showStartDatePicker = true },
                onEndDateClick = { selectedRangePreset = PersonReportRangePreset.CUSTOM; showEndDatePicker = true }
            )
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val summary = uiState.selectedPersonSummary
                        if (summary == null) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("لا توجد بيانات كافية لإنشاء التقرير.") }
                        } else {
                            val result = PersonReportPdfExporter.exportPersonReport(
                                context = context,
                                summary = summary,
                                transactions = uiState.selectedPersonTransactions,
                                startDateMillis = startDateMillis,
                                endDateMillisExclusive = endDateMillisExclusive
                            )
                            coroutineScope.launch {
                                result.fold(
                                    onSuccess = { snackbarHostState.showSnackbar(it) },
                                    onFailure = { snackbarHostState.showSnackbar(it.message ?: "حدث خطأ أثناء إنشاء تقرير PDF.") }
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isLoading && uiState.selectedPersonSummary != null
                ) { Text("PDF") }

                Button(
                    onClick = {
                        val summary = uiState.selectedPersonSummary
                        if (summary == null) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("لا توجد بيانات كافية لإنشاء التقرير.") }
                        } else {
                            val result = PersonReportExcelExporter.exportPersonReport(
                                context = context,
                                summary = summary,
                                transactions = uiState.selectedPersonTransactions,
                                startDateMillis = startDateMillis,
                                endDateMillisExclusive = endDateMillisExclusive
                            )
                            coroutineScope.launch {
                                result.fold(
                                    onSuccess = { snackbarHostState.showSnackbar(it) },
                                    onFailure = { snackbarHostState.showSnackbar(it.message ?: "حدث خطأ أثناء إنشاء تقرير Excel.") }
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isLoading && uiState.selectedPersonSummary != null
                ) { Text("Excel") }

                OutlinedButton(
                    onClick = { sharePersonReport() },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isLoading && uiState.selectedPersonSummary != null
                ) { Text("مشاركة") }
            }

            Spacer(Modifier.height(12.dp))

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("جاري تحميل التقرير...") }
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.selectedPersonSummary?.let { summary -> item { PersonReportSummaryCard(summary) } }
                    item {
                        Text("كشف الحساب", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    }
                    item { PersonReportTableHeader() }

                    if (uiState.selectedPersonTransactionRows.isEmpty()) {
                        item {
                            Text(
                                "لا توجد عمليات خلال الفترة المحددة.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        items(uiState.selectedPersonTransactionRows, key = { it.transactionId }) { row ->
                            PersonReportTransactionRowCard(
                                row = row,
                                currencyCode = currencyCode,
                                onClick = {
                                    coroutineScope.launch {
                                        transactionViewModel?.getTransaction(row.transactionId)?.let {
                                            onTransactionClick(it.accountId, currencyCode)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    uiState.errorMessage?.let { message ->
                        item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("إغلاق التقرير") }
                    }
                }
            }
        }
    }

    if (showStartDatePicker) {
        ReportDatePickerDialog(
            initialDateMillis = startDateMillis ?: todayStartMillis,
            title = "اختر تاريخ البداية",
            onDismiss = { showStartDatePicker = false },
            onDateSelected = { selectedMillis ->
                val selectedStart = startOfDayMillis(selectedMillis)
                val currentEnd = endDateMillisExclusive ?: addDays(selectedStart, 1)
                val safeEnd = if (currentEnd <= selectedStart) addDays(selectedStart, 1) else currentEnd
                viewModel.setDateRange(selectedStart, safeEnd)
                showStartDatePicker = false
            }
        )
    }

    if (showEndDatePicker) {
        val currentStart = startDateMillis ?: todayStartMillis
        ReportDatePickerDialog(
            initialDateMillis = endDateMillisExclusive?.let { addDays(it, -1) } ?: currentStart,
            title = "اختر تاريخ النهاية",
            onDismiss = { showEndDatePicker = false },
            onDateSelected = { selectedMillis ->
                val endExclusive = addDays(startOfDayMillis(selectedMillis), 1)
                if (endExclusive > currentStart) {
                    viewModel.setDateRange(currentStart, endExclusive)
                    showEndDatePicker = false
                }
            }
        )
    }
}

@Composable
private fun PersonReportHeader(summary: PersonReportSummary, startDateMillis: Long?, endDateMillisExclusive: Long?) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Text("تقرير حساب", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(summary.personName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("العملة: ${currencyName(summary.currencyCode)}")
            Text("الفترة: ${if (startDateMillis == null && endDateMillisExclusive == null) "كل الحساب" else "${formatDate(startDateMillis ?: 0L)} إلى ${formatDate(addDays(endDateMillisExclusive ?: 0L, -1))}"}")
            Text("تاريخ إصدار التقرير: ${formatDate(System.currentTimeMillis())}")
            Text("عدد العمليات: ${summary.transactionCount}")
        }
    }
}

@Composable
private fun PersonReportRangePresetSection(
    selectedPreset: PersonReportRangePreset,
    onAllTime: () -> Unit,
    onToday: () -> Unit,
    onWeek: () -> Unit,
    onMonth: () -> Unit,
    onCustom: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Text("الفترة الزمنية", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PresetButton("كل الحساب", selectedPreset == PersonReportRangePreset.ALL, onAllTime, Modifier.weight(1f))
                PresetButton("اليوم", selectedPreset == PersonReportRangePreset.TODAY, onToday, Modifier.weight(1f))
                PresetButton("هذا الأسبوع", selectedPreset == PersonReportRangePreset.WEEK, onWeek, Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PresetButton("هذا الشهر", selectedPreset == PersonReportRangePreset.MONTH, onMonth, Modifier.weight(1f))
                PresetButton("فترة مخصصة", selectedPreset == PersonReportRangePreset.CUSTOM, onCustom, Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PresetButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(text) }
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
}

@Composable
private fun DateRangeSection(startDateMillis: Long?, endDateMillisExclusive: Long?, onStartDateClick: () -> Unit, onEndDateClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Text(if (startDateMillis == null && endDateMillisExclusive == null) "الفترة: كل الحساب" else "الفترة المحددة", fontWeight = FontWeight.Bold)
            if (startDateMillis != null || endDateMillisExclusive != null) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateButton("من", startDateMillis, onStartDateClick, Modifier.weight(1f))
                    DateButton("إلى", endDateMillisExclusive?.let { addDays(it, -1) }, onEndDateClick, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DateButton(label: String, dateMillis: Long?, onClick: () -> Unit, modifier: Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 12.sp)
            Text(dateMillis?.let(::formatDate) ?: "اختيار التاريخ")
        }
    }
}

@Composable
private fun PersonReportSummaryCard(summary: PersonReportSummary) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("ملخص الحساب", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            ReportSummaryRow("الرصيد الافتتاحي", summary.openingBalanceMinor)
            ReportSummaryRow("إجمالي عليه خلال الفترة", summary.periodReceivableMinor, true)
            ReportSummaryRow("إجمالي له خلال الفترة", summary.periodPayableMinor, false)
            ReportSummaryRow("الرصيد الختامي", summary.closingBalanceMinor, amountIsPositive = summary.closingBalanceMinor > 0L, neutralWhenZero = true)
            Text("عدد العمليات: ${summary.transactionCount}", fontSize = 14.sp)
        }
    }
}

@Composable
private fun ReportSummaryRow(label: String, amountMinor: Long, amountIsPositive: Boolean? = null, neutralWhenZero: Boolean = false) {
    val color = when {
        amountMinor == 0L && neutralWhenZero -> MaterialTheme.colorScheme.onSurface
        amountIsPositive == true -> MaterialTheme.colorScheme.error
        amountIsPositive == false -> MaterialTheme.colorScheme.primary
        amountMinor > 0L -> MaterialTheme.colorScheme.error
        amountMinor < 0L -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp)
        Text(formatAmount(amountMinor), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun PersonReportTableHeader() {
    Row(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReportCell("التاريخ", 1.2f, true)
        ReportCell("البيان", 2.1f, true)
        ReportCell("عليه", 1.1f, true, MaterialTheme.colorScheme.error)
        ReportCell("له", 1.1f, true, MaterialTheme.colorScheme.primary)
        ReportCell("الرصيد", 1.2f, true)
    }
}

@Composable
private fun PersonReportTransactionRowCard(row: PersonReportTransactionRow, currencyCode: String, onClick: () -> Unit) {
    val typeColor = when (row.type) {
        "RECEIVABLE" -> MaterialTheme.colorScheme.error
        "PAYABLE" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReportCell(formatDate(row.transactionDate), 1.2f)
        ReportCell(row.description.ifBlank { "—" }, 2.1f)
        ReportCell(if (row.type == "RECEIVABLE") formatAmount(row.amountMinor) else "—", 1.1f, color = if (row.type == "RECEIVABLE") typeColor else MaterialTheme.colorScheme.onSurface)
        ReportCell(if (row.type == "PAYABLE") formatAmount(row.amountMinor) else "—", 1.1f, color = if (row.type == "PAYABLE") typeColor else MaterialTheme.colorScheme.onSurface)
        val balanceColor = when {
            row.balanceMinor > 0L -> MaterialTheme.colorScheme.error
            row.balanceMinor < 0L -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        }
        ReportCell("${formatAmount(row.balanceMinor)} ${currencyName(currencyCode)}", 1.2f, color = balanceColor)
    }
}

@Composable
private fun RowScope.ReportCell(text: String, weight: Float, header: Boolean = false, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        modifier = Modifier.weight(weight).padding(horizontal = 4.dp),
        textAlign = TextAlign.Center,
        fontSize = if (header) 12.sp else 11.sp,
        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
        color = color
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDatePickerDialog(initialDateMillis: Long, title: String, onDismiss: () -> Unit, onDateSelected: (Long) -> Unit) {
    val datePickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let(onDateSelected) }) { Text("اختيار") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    ) {
        Column {
            Text(title, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp), fontWeight = FontWeight.Bold)
            DatePicker(state = datePickerState)
        }
    }
}

private fun currencyName(currencyCode: String): String = when (currencyCode) {
    "YER" -> "الريال اليمني"
    "SAR" -> "الريال السعودي"
    "USD" -> "الدولار الأمريكي"
    else -> currencyCode
}

private fun formatAmount(amountMinor: Long): String = BigDecimal(amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun formatDate(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date(millis))
private fun safeFileName(value: String): String = value.replace(Regex("[\\\\/:*?\"<>|]"), "_").replace(Regex("\\s+"), "_").take(60).ifBlank { "Person" }

private fun startOfDayMillis(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfWeekMillis(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDayMillis(millis)
    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
}.timeInMillis

private fun startOfMonthMillis(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDayMillis(millis)
    set(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

private fun addDays(millis: Long, days: Int): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    add(Calendar.DAY_OF_MONTH, days)
}.timeInMillis

private fun addMonths(millis: Long, months: Int): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    add(Calendar.MONTH, months)
}.timeInMillis
