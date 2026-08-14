package com.myaccounts.app.ui.screens.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myaccounts.app.data.reports.PersonReportSummary
import com.myaccounts.app.data.reports.PersonReportTransaction
import com.myaccounts.app.ui.viewmodel.ReportsViewModel
import com.myaccounts.app.ui.viewmodel.TransactionViewModel
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

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

    val coroutineScope = rememberCoroutineScope()

    var showStartDatePicker by remember {
        mutableStateOf(false)
    }

    var showEndDatePicker by remember {
        mutableStateOf(false)
    }

    val todayStartMillis = remember {
        startOfDayMillis(
            System.currentTimeMillis()
        )
    }

    LaunchedEffect(
        personId,
        currencyCode
    ) {
        viewModel.selectCurrency(currencyCode)
        viewModel.selectPerson(personId)

        val startDate = startOfDayMillis(
            todayStartMillis
        )

        val endDateExclusive =
            addDays(
                startDate,
                1
            )

        viewModel.setDateRange(
            startDateMillis = startDate,
            endDateMillisExclusive = endDateExclusive
        )
    }

    val startDateMillis =
        uiState.startDateMillis

    val endDateMillisExclusive =
        uiState.endDateMillisExclusive

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "تقرير الشخص",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

            uiState.selectedPersonSummary?.let { summary ->

                Text(
                    text = summary.personName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = currencyName(
                        summary.currencyCode
                    ),
                    fontSize = 14.sp,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )
            }

            DateRangeSection(
                startDateMillis = startDateMillis,
                endDateMillisExclusive =
                    endDateMillisExclusive,
                onStartDateClick = {
                    showStartDatePicker = true
                },
                onEndDateClick = {
                    showEndDatePicker = true
                }
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            if (uiState.isLoading) {

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        text = "جاري تحميل التقرير..."
                    )
                }

            } else {

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {

                    uiState.selectedPersonSummary?.let {
                        summary ->

                        item {

                            PersonReportSummaryCard(
                                summary = summary
                            )
                        }
                    }

                    item {

                        Text(
                            text = "العمليات",
                            fontSize = 20.sp,
                            fontWeight =
                                FontWeight.Bold,
                            modifier =
                                Modifier.padding(
                                    top = 8.dp
                                )
                        )
                    }

                    if (
                        uiState
                            .selectedPersonTransactions
                            .isEmpty()
                    ) {

                        item {

                            Text(
                                text =
                                    "لا توجد عمليات خلال الفترة المحددة.",
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                            )
                        }

                    } else {

                        items(
                            items =
                                uiState
                                    .selectedPersonTransactions,
                            key = {
                                it.transactionId
                            }
                        ) { transaction ->

                            PersonReportTransactionCard(
                                transaction =
                                    transaction,
                                currencyCode =
                                    currencyCode,
                                onClick = {

                                    coroutineScope.launch {

                                        val selectedTransaction =
                                            transactionViewModel
                                                ?.getTransaction(
                                                    transaction.transactionId
                                                )

                                        selectedTransaction?.let {

                                            onTransactionClick(
                                                it.accountId,
                                                currencyCode
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }

                    item {

                        Spacer(
                            modifier =
                                Modifier.height(8.dp)
                        )

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.SpaceBetween
                        ) {

                            Text(
                                text = "رجوع",
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Button(
                                onClick = onBack
                            ) {
                                Text("إغلاق التقرير")
                            }
                        }
                    }

                    uiState.errorMessage?.let {
                        message ->

                        item {

                            Text(
                                text = message,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showStartDatePicker) {

        ReportDatePickerDialog(
            initialDateMillis =
                startDateMillis
                    ?: todayStartMillis,

            title = "اختر تاريخ البداية",

            onDismiss = {
                showStartDatePicker = false
            },

            onDateSelected = { selectedMillis ->

                val selectedStart =
                    startOfDayMillis(
                        selectedMillis
                    )

                val currentEnd =
                    endDateMillisExclusive
                        ?: addDays(
                            selectedStart,
                            1
                        )

                val safeEnd =
                    if (currentEnd <= selectedStart) {
                        addDays(
                            selectedStart,
                            1
                        )
                    } else {
                        currentEnd
                    }

                viewModel.setDateRange(
                    startDateMillis =
                        selectedStart,
                    endDateMillisExclusive =
                        safeEnd
                )

                showStartDatePicker = false
            }
        )
    }

    if (showEndDatePicker) {

        val currentStart =
            startDateMillis
                ?: todayStartMillis

        ReportDatePickerDialog(
            initialDateMillis =
                endDateMillisExclusive
                    ?.let {
                        addDays(
                            it,
                            -1
                        )
                    }
                    ?: currentStart,

            title = "اختر تاريخ النهاية",

            onDismiss = {
                showEndDatePicker = false
            },

            onDateSelected = { selectedMillis ->

                val selectedEndDate =
                    startOfDayMillis(
                        selectedMillis
                    )

                val endExclusive =
                    addDays(
                        selectedEndDate,
                        1
                    )

                if (
                    endExclusive > currentStart
                ) {

                    viewModel.setDateRange(
                        startDateMillis =
                            currentStart,
                        endDateMillisExclusive =
                            endExclusive
                    )

                    showEndDatePicker = false
                }
            }
        )
    }
}

@Composable
private fun DateRangeSection(
    startDateMillis: Long?,
    endDateMillisExclusive: Long?,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme
                    .colorScheme
                    .surfaceVariant
        )
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = "الفترة الزمنية",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                DateButton(
                    label = "من",
                    dateMillis = startDateMillis,
                    onClick = onStartDateClick,
                    modifier =
                        Modifier.weight(1f)
                )

                DateButton(
                    label = "إلى",
                    dateMillis =
                        endDateMillisExclusive
                            ?.let {
                                addDays(
                                    it,
                                    -1
                                )
                            },
                    onClick = onEndDateClick,
                    modifier =
                        Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DateButton(
    label: String,
    dateMillis: Long?,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
    ) {

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                text = label,
                fontSize = 12.sp
            )

            Text(
                text =
                    dateMillis?.let {
                        formatDate(it)
                    } ?: "اختيار التاريخ"
            )
        }
    }
}

@Composable
private fun PersonReportSummaryCard(
    summary: PersonReportSummary
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = "ملخص الحساب",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            ReportSummaryRow(
                label = "الرصيد الافتتاحي",
                amountMinor =
                    summary.openingBalanceMinor
            )

            ReportSummaryRow(
                label = "إجمالي لك خلال الفترة",
                amountMinor =
                    summary.periodReceivableMinor
            )

            ReportSummaryRow(
                label = "إجمالي عليك خلال الفترة",
                amountMinor =
                    summary.periodPayableMinor
            )

            ReportSummaryRow(
                label = "صافي حركة الفترة",
                amountMinor =
                    summary.periodBalanceMinor
            )

            ReportSummaryRow(
                label = "الرصيد الختامي",
                amountMinor =
                    summary.closingBalanceMinor
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text =
                    "عدد العمليات: ${summary.transactionCount}",
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ReportSummaryRow(
    label: String,
    amountMinor: Long
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {

        Text(
            text = label,
            fontSize = 14.sp
        )

        Text(
            text = formatAmount(amountMinor),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PersonReportTransactionCard(
    transaction: PersonReportTransaction,
    currencyCode: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick
            )
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    text =
                        formatDate(
                            transaction.transactionDate
                        ),
                    fontSize = 13.sp,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )

                Text(
                    text =
                        transactionTypeName(
                            transaction.type
                        ),
                    fontSize = 14.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text =
                    "${formatAmount(transaction.amountMinor)} ${currencyName(currencyCode)}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            if (
                transaction.description.isNotBlank()
            ) {

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                Text(
                    text =
                        transaction.description,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDatePickerDialog(
    initialDateMillis: Long,
    title: String,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val datePickerState =
        androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis =
                initialDateMillis
        )

    DatePickerDialog(
        onDismissRequest = onDismiss,

        confirmButton = {

            TextButton(
                onClick = {

                    datePickerState
                        .selectedDateMillis
                        ?.let(onDateSelected)
                }
            ) {
                Text("اختيار")
            }
        },

        dismissButton = {

            TextButton(
                onClick = onDismiss
            ) {
                Text("إلغاء")
            }
        }
    ) {

        Column {

            Text(
                text = title,
                modifier = Modifier.padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 16.dp
                ),
                fontWeight =
                    FontWeight.Bold
            )

            DatePicker(
                state = datePickerState
            )
        }
    }
}

private fun transactionTypeName(
    type: String
): String {
    return when (type) {
        "RECEIVABLE" -> "لك"
        "PAYABLE" -> "عليك"
        else -> type
    }
}

private fun currencyName(
    currencyCode: String
): String {
    return when (currencyCode) {
        "YER" -> "الريال اليمني"
        "SAR" -> "الريال السعودي"
        "USD" -> "الدولار الأمريكي"
        else -> currencyCode
    }
}

private fun formatAmount(
    amountMinor: Long
): String {
    return BigDecimal(amountMinor)
        .movePointLeft(2)
        .stripTrailingZeros()
        .toPlainString()
}

private fun formatDate(
    millis: Long
): String {
    return SimpleDateFormat(
        "dd/MM/yyyy",
        Locale("ar")
    ).format(
        Date(millis)
    )
}

private fun startOfDayMillis(
    millis: Long
): Long {
    val calendar =
        Calendar.getInstance().apply {
            timeInMillis = millis

            set(
                Calendar.HOUR_OF_DAY,
                0
            )

            set(
                Calendar.MINUTE,
                0
            )

            set(
                Calendar.SECOND,
                0
            )

            set(
                Calendar.MILLISECOND,
                0
            )
        }

    return calendar.timeInMillis
}

private fun addDays(
    millis: Long,
    days: Int
): Long {
    return Calendar.getInstance().apply {
        timeInMillis = millis
        add(
            Calendar.DAY_OF_MONTH,
            days
        )
    }.timeInMillis
}
