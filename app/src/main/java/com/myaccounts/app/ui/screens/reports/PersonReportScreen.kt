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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.reports.MultiCurrencyPersonReport
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
fun PersonReportScreen(personId: Long, currencyCode: String = "ALL", viewModel: ReportsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showStart by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    var actionBusy by remember { mutableStateOf(false) }

    LaunchedEffect(personId, currencyCode) {
        viewModel.selectPerson(personId)
        viewModel.selectCurrency(if (currencyCode == "ALL") "ALL" else currencyCode)
        viewModel.loadPersonReport()
        viewModel.setAllTime()
    }

    suspend fun generateNow(pdf: Boolean): Result<String> {
        val report = viewModel.getFreshMultiCurrencyPersonReport(
            personId = personId,
            startDateMillis = state.startDateMillis,
            endDateMillisExclusive = state.endDateMillisExclusive
        )
        return if (currencyCode == "ALL") {
            if (pdf) {
                MultiCurrencyReportPdfExporter.exportPersonReport(
                    context, report, state.startDateMillis, state.endDateMillisExclusive
                )
            } else {
                MultiCurrencyReportExcelExporter.exportPersonReport(
                    context, report, state.startDateMillis, state.endDateMillisExclusive
                )
            }
        } else {
            val currencyReport = report.reports.firstOrNull { it.currencyCode == currencyCode }
                ?: return Result.failure(IllegalStateException("لا توجد بيانات لهذه العملة."))
            if (pdf) {
                PersonReportPdfExporter.exportPersonReport(
                    context,
                    currencyReport.summary,
                    currencyReport.transactions,
                    state.startDateMillis,
                    state.endDateMillisExclusive
                )
            } else {
                PersonReportExcelExporter.exportPersonReport(
                    context,
                    currencyReport.summary,
                    currencyReport.transactions,
                    state.startDateMillis,
                    state.endDateMillisExclusive
                )
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
        val prefix = if (currencyCode == "ALL") {
            "MyAccounts_تقرير_حساب_${safeFileName(personName)}"
        } else {
            "MyAccounts_Person_Report_${safeFileName(personName)}"
        }
        val mime = if (pdf) {
            "application/pdf"
        } else {
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        }
        scope.launch(Dispatchers.IO) {
            try {
                val result = ReportShareUtil.shareGeneratedReport(context, prefix, mime) {
                    runCatching {
                        kotlinx.coroutines.runBlocking { generateNow(pdf).getOrThrow() }
                    }
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (currencyCode == "ALL") "تقرير حساب الشخص — جميع العملات"
                        else "تقرير حساب الشخص — ${currencyName(currencyCode)}",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("رجوع") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            state.selectedPersonMultiCurrencyReport?.let { report ->
                PersonHeader(report, state.startDateMillis, state.endDateMillisExclusive)
                Spacer(Modifier.height(10.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "الفترة والتصدير",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(onClick = { viewModel.setAllTime() }, Modifier.weight(1f), enabled = !actionBusy) { Text("كل الحساب") }
                        OutlinedButton(onClick = { showStart = true }, Modifier.weight(1f), enabled = !actionBusy) { Text("من تاريخ") }
                        OutlinedButton(onClick = { showEnd = true }, Modifier.weight(1f), enabled = !actionBusy) { Text("إلى تاريخ") }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { export(true) }, Modifier.weight(1f), enabled = !actionBusy) { Text("PDF") }
                        Button(onClick = { export(false) }, Modifier.weight(1f), enabled = !actionBusy) { Text("Excel") }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { share(true) }, Modifier.weight(1f), enabled = !actionBusy) { Text("مشاركة PDF") }
                        OutlinedButton(onClick = { share(false) }, Modifier.weight(1f), enabled = !actionBusy) { Text("مشاركة Excel") }
                    }
                    if (actionBusy) {
                        Text(
                            "جارٍ تنفيذ العملية...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            if (state.isLoading) {
                Text("جاري تحميل التقرير...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.selectedPersonMultiCurrencyReport?.let { report ->
                    val reports = if (currencyCode == "ALL") report.reports else report.reports.filter { it.currencyCode == currencyCode }
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(reports) { currencyReport ->
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(currencyName(currencyReport.currencyCode), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("عليه: ${amount(currencyReport.summary.periodReceivableMinor)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                    Text("له: ${amount(currencyReport.summary.periodPayableMinor)}", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
                                    Text("الرصيد: ${balance(currencyReport.summary.periodBalanceMinor)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("عدد العمليات: ${currencyReport.summary.transactionCount}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            currencyReport.transactions.forEach { transaction ->
                                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f)) {
                                            Text(formatDate(transaction.transactionDate), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text(transaction.description.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(
                                            if (transaction.type == "RECEIVABLE") "عليه ${amount(transaction.amountMinor)}" else "له ${amount(transaction.amountMinor)}",
                                            color = if (transaction.type == "RECEIVABLE") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                        item { state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                    }
                }
            }
        }
    }

    if (showStart) {
        DatePickerDialog(
            onDismissRequest = { showStart = false },
            confirmButton = { TextButton({ showStart = false }) { Text("إغلاق") } }
        ) {
            val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = state.startDateMillis)
            DatePicker(picker)
            LaunchedEffect(picker.selectedDateMillis) {
                picker.selectedDateMillis?.let { selected ->
                    viewModel.setDateRange(dayStart(selected), state.endDateMillisExclusive ?: addDays(dayStart(selected), 1))
                    showStart = false
                }
            }
        }
    }

    if (showEnd) {
        DatePickerDialog(
            onDismissRequest = { showEnd = false },
            confirmButton = { TextButton({ showEnd = false }) { Text("إغلاق") } }
        ) {
            val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = state.endDateMillisExclusive?.let { addDays(it, -1) })
            DatePicker(picker)
            LaunchedEffect(picker.selectedDateMillis) {
                picker.selectedDateMillis?.let { selected ->
                    viewModel.setDateRange(state.startDateMillis ?: dayStart(selected), addDays(dayStart(selected), 1))
                    showEnd = false
                }
            }
        }
    }
}

@Composable
private fun PersonHeader(report: MultiCurrencyPersonReport, start: Long?, end: Long?) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.large, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("تقرير حساب ${report.personName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("الهاتف: ${report.phone.ifBlank { "غير مسجل" }}")
            Text("العنوان: ${report.address.ifBlank { "غير مسجل" }}")
            Text("الفترة: ${range(start, end)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun amount(value: Long): String = BigDecimal(value).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun balance(value: Long): String = when { value > 0L -> "عليه ${amount(value)}"; value < 0L -> "له ${amount(-value)}"; else -> "متعادل 0" }
private fun currencyName(code: String): String = when (code) { "YER" -> "الريال اليمني"; "SAR" -> "الريال السعودي"; "USD" -> "الدولار الأمريكي"; else -> code }
private fun formatDate(value: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date(value))
private fun range(start: Long?, end: Long?): String = if (start == null && end == null) "كل الحساب" else "${start?.let(::formatDate) ?: "غير محدد"} - ${end?.let { formatDate(it - 1) } ?: "غير محدد"}"
private fun dayStart(value: Long): Long = Calendar.getInstance().apply { timeInMillis = value; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
private fun addDays(value: Long, days: Int): Long = Calendar.getInstance().apply { timeInMillis = value; add(Calendar.DAY_OF_MONTH, days) }.timeInMillis
private fun safeFileName(value: String): String = value.replace(Regex("[\\\\/:*?\"<>|]"), "_").replace(Regex("\\s+"), "_").take(60)
