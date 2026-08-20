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
import androidx.compose.ui.unit.sp
import com.myaccounts.app.data.reports.MultiCurrencyPersonReport
import com.myaccounts.app.ui.viewmodel.ReportsViewModel
import com.myaccounts.app.util.MultiCurrencyReportExcelExporter
import com.myaccounts.app.util.MultiCurrencyReportPdfExporter
import com.myaccounts.app.util.ReportShareUtil
import kotlinx.coroutines.launch
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

    LaunchedEffect(personId, currencyCode) {
        viewModel.selectPerson(personId)
        if (currencyCode != "ALL") viewModel.selectCurrency(currencyCode) else viewModel.selectCurrency("ALL")
        viewModel.setAllTime()
    }

    fun export(pdf: Boolean) {
        val report = state.selectedPersonMultiCurrencyReport ?: return
        scope.launch {
            val result = if (pdf) {
                MultiCurrencyReportPdfExporter.exportPersonReport(context, report, state.startDateMillis, state.endDateMillisExclusive)
            } else {
                MultiCurrencyReportExcelExporter.exportPersonReport(context, report, state.startDateMillis, state.endDateMillisExclusive)
            }
            result.fold(
                { snackbar.showSnackbar("تعذر إنشاء التقرير.") },
                { snackbar.showSnackbar(it) }
            )
        }
    }

    fun share(pdf: Boolean) {
        val report = state.selectedPersonMultiCurrencyReport ?: return
        val prefix = "MyAccounts_تقرير حساب ${report.personName}"
        val mimeType = if (pdf) "application/pdf" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        scope.launch {
            ReportShareUtil.shareLatestReport(context, prefix, mimeType).fold(
                { snackbar.showSnackbar("تعذر مشاركة التقرير. قم بإصدار التقرير أولاً.") },
                { snackbar.showSnackbar("تم فتح خيارات مشاركة التقرير.") }
            )
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (currencyCode == "ALL") "تقرير حساب الشخص — جميع العملات" else "تقرير حساب الشخص — ${currencyName(currencyCode)}", fontWeight = FontWeight.Bold) }, navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(14.dp)) {
            state.selectedPersonMultiCurrencyReport?.let { report ->
                PersonHeader(report, state.startDateMillis, state.endDateMillisExclusive)
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton({ viewModel.setAllTime() }, Modifier.weight(1f)) { Text("كل الحساب") }
                OutlinedButton({ showStart = true }, Modifier.weight(1f)) { Text("من تاريخ") }
                OutlinedButton({ showEnd = true }, Modifier.weight(1f)) { Text("إلى تاريخ") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ export(true) }, Modifier.weight(1f), enabled = state.selectedPersonMultiCurrencyReport != null && !state.isLoading) { Text("PDF") }
                Button({ export(false) }, Modifier.weight(1f), enabled = state.selectedPersonMultiCurrencyReport != null && !state.isLoading) { Text("Excel") }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ share(true) }, Modifier.weight(1f), enabled = state.selectedPersonMultiCurrencyReport != null && !state.isLoading) { Text("مشاركة PDF") }
                OutlinedButton({ share(false) }, Modifier.weight(1f), enabled = state.selectedPersonMultiCurrencyReport != null && !state.isLoading) { Text("مشاركة Excel") }
            }
            Spacer(Modifier.height(10.dp))
            if (state.isLoading) Text("جاري تحميل التقرير...") else state.selectedPersonMultiCurrencyReport?.let { report ->
                val reports = if (currencyCode == "ALL") report.reports else report.reports.filter { it.currencyCode == currencyCode }
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reports) { currencyReport ->
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(currencyName(currencyReport.currencyCode), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("عليه: ${amount(currencyReport.summary.periodReceivableMinor)}", color = MaterialTheme.colorScheme.error)
                                Text("له: ${amount(currencyReport.summary.periodPayableMinor)}", color = MaterialTheme.colorScheme.secondary)
                                Text("الرصيد: ${balance(currencyReport.summary.periodBalanceMinor)}", fontWeight = FontWeight.Bold)
                                Text("عدد العمليات: ${currencyReport.summary.transactionCount}")
                            }
                        }
                        currencyReport.transactions.forEach { transaction ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(formatDate(transaction.transactionDate), fontWeight = FontWeight.Bold)
                                        Text(transaction.description.ifBlank { "—" })
                                    }
                                    Text(if (transaction.type == "RECEIVABLE") "عليه ${amount(transaction.amountMinor)}" else "له ${amount(transaction.amountMinor)}", color = if (transaction.type == "RECEIVABLE") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                    item { state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                }
            }
        }
    }

    if (showStart) DatePickerDialog(onDismissRequest = { showStart = false }, confirmButton = { TextButton({ showStart = false }) { Text("إغلاق") } }) {
        val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = state.startDateMillis)
        DatePicker(picker)
        LaunchedEffect(picker.selectedDateMillis) { picker.selectedDateMillis?.let { selected -> viewModel.setDateRange(dayStart(selected), state.endDateMillisExclusive ?: addDays(dayStart(selected), 1)); showStart = false } }
    }
    if (showEnd) DatePickerDialog(onDismissRequest = { showEnd = false }, confirmButton = { TextButton({ showEnd = false }) { Text("إغلاق") } }) {
        val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = state.endDateMillisExclusive?.let { addDays(it, -1) })
        DatePicker(picker)
        LaunchedEffect(picker.selectedDateMillis) { picker.selectedDateMillis?.let { selected -> viewModel.setDateRange(state.startDateMillis ?: dayStart(selected), addDays(dayStart(selected), 1)); showEnd = false } }
    }
}

@Composable
private fun PersonHeader(report: MultiCurrencyPersonReport, start: Long?, end: Long?) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text("تقرير حساب ${report.personName}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("الهاتف: ${report.phone.ifBlank { "غير مسجل" }}")
            Text("العنوان: ${report.address.ifBlank { "غير مسجل" }}")
            Text("الفترة: ${range(start, end)}")
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
