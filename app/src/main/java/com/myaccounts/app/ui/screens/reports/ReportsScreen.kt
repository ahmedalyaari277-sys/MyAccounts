package com.myaccounts.app.ui.screens.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.myaccounts.app.ui.viewmodel.ReportsViewModel
import com.myaccounts.app.util.MultiCurrencyReportExcelExporter
import com.myaccounts.app.util.MultiCurrencyReportPdfExporter
import kotlinx.coroutines.launch
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

    LaunchedEffect(Unit) { viewModel.setAllTime() }

    fun dayStart(v: Long) = Calendar.getInstance().apply { timeInMillis = v; set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0) }.timeInMillis
    fun dayEnd(v: Long) = Calendar.getInstance().apply { timeInMillis = dayStart(v); add(Calendar.DAY_OF_MONTH,1) }.timeInMillis
    fun choose(p: Period) {
        period = p
        val now = System.currentTimeMillis()
        when(p) {
            Period.ALL -> viewModel.setAllTime()
            Period.TODAY -> viewModel.setDateRange(dayStart(now), dayEnd(now))
            Period.WEEK -> { val s=Calendar.getInstance().apply{timeInMillis=dayStart(now);set(Calendar.DAY_OF_WEEK,firstDayOfWeek)}.timeInMillis;viewModel.setDateRange(s,addDays(s,7)) }
            Period.MONTH -> { val s=Calendar.getInstance().apply{timeInMillis=dayStart(now);set(Calendar.DAY_OF_MONTH,1)}.timeInMillis;viewModel.setDateRange(s,addMonths(s,1)) }
            Period.CUSTOM -> showStart=true
        }
    }
    fun export(pdf:Boolean) {
        scope.launch {
            val result=when(reportType){
                ReportType.PEOPLE -> if(pdf) MultiCurrencyReportPdfExporter.exportPeopleReport(context,state.allCurrencySummaries,state.allCurrencyPeople,state.startDateMillis,state.endDateMillisExclusive) else MultiCurrencyReportExcelExporter.exportPeopleReport(context,state.allCurrencySummaries,state.allCurrencyPeople,state.startDateMillis,state.endDateMillisExclusive)
                ReportType.DETAILED -> if(pdf) MultiCurrencyReportPdfExporter.exportDetailedReport(context,state.allCurrencySummaries,state.allCurrencyGeneralTransactions,state.startDateMillis,state.endDateMillisExclusive) else MultiCurrencyReportExcelExporter.exportDetailedReport(context,state.allCurrencySummaries,state.allCurrencyGeneralTransactions,state.startDateMillis,state.endDateMillisExclusive)
                ReportType.SUMMARY -> if(pdf) MultiCurrencyReportPdfExporter.exportSummaryReport(context,state.allCurrencyPersonSummaries,state.startDateMillis,state.endDateMillisExclusive) else MultiCurrencyReportExcelExporter.exportSummaryReport(context,state.allCurrencyPersonSummaries,state.startDateMillis,state.endDateMillisExclusive)
            }
            result.fold({snackbar.showSnackbar(it)},{snackbar.showSnackbar(it.message?:"تعذر إنشاء التقرير.")})
        }
    }

    Scaffold(topBar={TopAppBar(title={Text("التقارير العامة",fontWeight=FontWeight.Bold)},navigationIcon={TextButton(onClick=onBack){Text("رجوع")}})},snackbarHost={SnackbarHost(snackbar)}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            item{
                Text("التقارير العامة",fontSize=24.sp,fontWeight=FontWeight.Bold)
                Text("التقرير الشامل يعرض العملات الثلاث مستقلة، ويمكن اختيار عملة منفصلة من «المزيد». ",color=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                PeriodSelector(period,::choose)
                if(period==Period.CUSTOM) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick={showStart=true},Modifier.weight(1f)){Text("من: ${formatDate(customStart)}")}
                    OutlinedButton(onClick={showEnd=true},Modifier.weight(1f)){Text("إلى: ${formatDate(customEnd)}")}
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    FilterChip(reportType==ReportType.PEOPLE,{reportType=ReportType.PEOPLE},label={Text("الأشخاص")})
                    FilterChip(reportType==ReportType.DETAILED,{reportType=ReportType.DETAILED},label={Text("التقرير العام")})
                    FilterChip(reportType==ReportType.SUMMARY,{reportType=ReportType.SUMMARY},label={Text("أرصدة الحسابات")})
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Button(onClick={export(false)},Modifier.weight(1f),enabled=!state.isLoading){Text("Excel")}
                    Button(onClick={export(true)},Modifier.weight(1f),enabled=!state.isLoading){Text("PDF")}
                }
            }
            item{CurrencyTotals(state.allCurrencySummaries)}
            when(reportType){
                ReportType.PEOPLE -> { item{Text("الأشخاص",fontSize=20.sp,fontWeight=FontWeight.Bold)};items(state.allCurrencyPeople){p->Card(Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(p.personName,fontWeight=FontWeight.Bold);Text(currencyName(p.currencyCode),color=MaterialTheme.colorScheme.onSurfaceVariant)};Column{Text("عليه ${amount(p.totalReceivableMinor)}",color=MaterialTheme.colorScheme.error);Text("له ${amount(p.totalPayableMinor)}",color=MaterialTheme.colorScheme.secondary);Text(balance(p.balanceMinor),fontWeight=FontWeight.Bold)}}}} }
                ReportType.DETAILED -> { item{Text("التقرير العام للعمليات",fontSize=20.sp,fontWeight=FontWeight.Bold)};items(state.allCurrencyGeneralTransactions){t->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("${formatDate(t.transactionDate)} — ${t.personName}",fontWeight=FontWeight.Bold);Text(t.description.ifBlank{"—"});Text(currencyName(t.currencyCode),color=MaterialTheme.colorScheme.onSurfaceVariant);Text(if(t.type=="RECEIVABLE")"عليه ${amount(t.amountMinor)}" else "له ${amount(t.amountMinor)}",color=if(t.type=="RECEIVABLE")MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)}}} }
                ReportType.SUMMARY -> { item{Text("أرصدة الحسابات",fontSize=20.sp,fontWeight=FontWeight.Bold)};items(state.allCurrencyPersonSummaries){r->Card(Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){Column{Text(r.personName,fontWeight=FontWeight.Bold);Text(currencyName(r.currencyCode))};Column{Text("عليه ${amount(r.totalReceivableMinor)}",color=MaterialTheme.colorScheme.error);Text("له ${amount(r.totalPayableMinor)}",color=MaterialTheme.colorScheme.secondary);Text(balance(r.balanceMinor),fontWeight=FontWeight.Bold);Text("العمليات: ${r.transactionCount}")}}}} }
            }
            if(state.errorMessage!=null)item{Text(state.errorMessage!!,color=MaterialTheme.colorScheme.error)}
        }
    }
    if(showStart)DatePickerDialog(onDismissRequest={showStart=false},confirmButton={TextButton({showStart=false}){Text("إغلاق")}}){val picker=androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis=customStart);DatePicker(picker);LaunchedEffect(picker.selectedDateMillis){picker.selectedDateMillis?.let{customStart=it;if(customEnd!=null)viewModel.setDateRange(dayStart(it),dayEnd(customEnd!!))}}}
    if(showEnd)DatePickerDialog(onDismissRequest={showEnd=false},confirmButton={TextButton({showEnd=false}){Text("إغلاق")}}){val picker=androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis=customEnd);DatePicker(picker);LaunchedEffect(picker.selectedDateMillis){picker.selectedDateMillis?.let{customEnd=it;if(customStart!=null)viewModel.setDateRange(dayStart(customStart!!),dayEnd(it))}}}
}

@Composable private fun PeriodSelector(selected:Period,onSelect:(Period)->Unit){Column{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){FilterChip(selected==Period.ALL,{onSelect(Period.ALL)},label={Text("كل الحساب")});FilterChip(selected==Period.TODAY,{onSelect(Period.TODAY)},label={Text("اليوم")});FilterChip(selected==Period.WEEK,{onSelect(Period.WEEK)},label={Text("هذا الأسبوع")})};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){FilterChip(selected==Period.MONTH,{onSelect(Period.MONTH)},label={Text("هذا الشهر")});FilterChip(selected==Period.CUSTOM,{onSelect(Period.CUSTOM)},label={Text("فترة مخصصة")})}}}
@Composable private fun CurrencyTotals(summaries:List<com.myaccounts.app.data.reports.CurrencyReportSummary>){Column(verticalArrangement=Arrangement.spacedBy(6.dp)){summaries.forEach{s->Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(currencyName(s.currencyCode),fontWeight=FontWeight.Bold);Text("عليه ${amount(s.totalReceivableMinor)}",color=MaterialTheme.colorScheme.error);Text("له ${amount(s.totalPayableMinor)}",color=MaterialTheme.colorScheme.secondary);Text(balance(s.balanceMinor),fontWeight=FontWeight.Bold)}}}}}
private fun amount(v:Long)=java.math.BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun balance(v:Long)=when{v>0L->"عليه ${amount(v)}";v<0L->"له ${amount(-v)}";else->"متعادل 0"}
private fun currencyName(c:String)=when(c){"YER"->"الريال اليمني";"SAR"->"الريال السعودي";"USD"->"الدولار الأمريكي";else->c}
private fun formatDate(v:Long?)=v?.let{SimpleDateFormat("dd/MM/yyyy",Locale("ar")).format(Date(it))}?:"—"
private fun addDays(v:Long,d:Int)=Calendar.getInstance().apply{timeInMillis=v;add(Calendar.DAY_OF_MONTH,d)}.timeInMillis
private fun addMonths(v:Long,d:Int)=Calendar.getInstance().apply{timeInMillis=v;add(Calendar.MONTH,d)}.timeInMillis
