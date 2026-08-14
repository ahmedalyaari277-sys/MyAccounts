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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myaccounts.app.data.reports.CurrencyReportPersonRow
import com.myaccounts.app.ui.viewmodel.ReportsViewModel
import com.myaccounts.app.util.ReportPdfExporter
import java.math.BigDecimal
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    onBack: () -> Unit,
    onPersonClick: (Long, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()

    val snackbarHostState =
        remember {
            SnackbarHostState()
        }

    LaunchedEffect(Unit) {
        viewModel.loadCurrencyReport()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "التقارير",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState
            )
        }
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            item {

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = "تقرير الحسابات",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text =
                        "اختر العملة لعرض ملخص الحسابات والأشخاص والعمليات.",
                    fontSize = 14.sp,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                CurrencySelector(
                    selectedCurrency =
                        uiState.selectedCurrencyCode,
                    onCurrencySelected = {
                        viewModel.selectCurrency(it)
                    }
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Button(
                    onClick = {

                        val summary =
                            uiState.currencySummary

                        if (summary == null) {

                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    "لا توجد بيانات كافية لإنشاء التقرير."
                                )
                            }

                        } else {

                            val result =
                                ReportPdfExporter
                                    .exportCurrencyReport(
                                        context = context,
                                        summary = summary,
                                        people = uiState.people
                                    )

                            coroutineScope.launch {

                                result.fold(
                                    onSuccess = { message ->
                                        snackbarHostState
                                            .showSnackbar(
                                                message
                                            )
                                    },
                                    onFailure = { exception ->
                                        snackbarHostState
                                            .showSnackbar(
                                                exception.message
                                                    ?: "حدث خطأ أثناء إنشاء ملف PDF."
                                            )
                                    }
                                )
                            }
                        }
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    enabled =
                        !uiState.isLoading &&
                            uiState.currencySummary != null
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.PictureAsPdf,
                        contentDescription = null
                    )

                    Spacer(
                        modifier = Modifier.padding(
                            horizontal = 4.dp
                        )
                    )

                    Text(
                        text = "تصدير التقرير إلى PDF"
                    )
                }
            }

            uiState.currencySummary?.let { summary ->

                item {

                    CurrencySummaryCard(
                        currencyCode =
                            summary.currencyCode,
                        totalReceivableMinor =
                            summary.totalReceivableMinor,
                        totalPayableMinor =
                            summary.totalPayableMinor,
                        balanceMinor =
                            summary.balanceMinor,
                        transactionCount =
                            summary.transactionCount
                    )
                }
            }

            item {

                Text(
                    text = "الأشخاص",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.isLoading) {

                item {

                    LoadingReportCard()
                }

            } else if (uiState.people.isEmpty()) {

                item {

                    EmptyReportCard()
                }

            } else {

                items(
                    items = uiState.people,
                    key = {
                        it.personId
                    }
                ) { person ->

                    CurrencyReportPersonCard(
                        person = person,
                        currencyCode =
                            uiState.selectedCurrencyCode,
                        onClick = {
                            onPersonClick(
                                person.personId,
                                uiState.selectedCurrencyCode
                            )
                        }
                    )
                }
            }

            uiState.errorMessage?.let { message ->

                item {

                    ErrorReportCard(
                        message = message
                    )
                }
            }

            item {

                Spacer(
                    modifier = Modifier.height(8.dp)
                )
            }
        }
    }
}

@Composable
private fun CurrencySelector(
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        CurrencyChip(
            label = "الريال اليمني",
            code = "YER",
            selectedCurrency = selectedCurrency,
            onSelected = onCurrencySelected
        )

        CurrencyChip(
            label = "الريال السعودي",
            code = "SAR",
            selectedCurrency = selectedCurrency,
            onSelected = onCurrencySelected
        )

        CurrencyChip(
            label = "الدولار",
            code = "USD",
            selectedCurrency = selectedCurrency,
            onSelected = onCurrencySelected
        )
    }
}

@Composable
private fun CurrencyChip(
    label: String,
    code: String,
    selectedCurrency: String,
    onSelected: (String) -> Unit
) {
    FilterChip(
        selected = selectedCurrency == code,
        onClick = {
            onSelected(code)
        },
        label = {
            Text(
                text = label
            )
        }
    )
}

@Composable
private fun CurrencySummaryCard(
    currencyCode: String,
    totalReceivableMinor: Long,
    totalPayableMinor: Long,
    balanceMinor: Long,
    transactionCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme
                    .primaryContainer
        )
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text =
                    "ملخص ${currencyName(currencyCode)}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color =
                    MaterialTheme.colorScheme
                        .onPrimaryContainer
            )

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                ReportMetricCard(
                    modifier =
                        Modifier.weight(1f),
                    title = "لك",
                    amountMinor =
                        totalReceivableMinor,
                    containerColor =
                        MaterialTheme.colorScheme
                            .tertiaryContainer,
                    contentColor =
                        MaterialTheme.colorScheme
                            .onTertiaryContainer
                )

                ReportMetricCard(
                    modifier =
                        Modifier.weight(1f),
                    title = "عليك",
                    amountMinor =
                        totalPayableMinor,
                    containerColor =
                        MaterialTheme.colorScheme
                            .errorContainer,
                    contentColor =
                        MaterialTheme.colorScheme
                            .onErrorContainer
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            ReportMetricCard(
                modifier =
                    Modifier.fillMaxWidth(),
                title = "الرصيد",
                amountMinor = balanceMinor,
                containerColor =
                    balanceContainerColor(
                        balanceMinor
                    ),
                contentColor =
                    balanceContentColor(
                        balanceMinor
                    )
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(
                text =
                    "عدد العمليات: $transactionCount",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color =
                    MaterialTheme.colorScheme
                        .onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ReportMetricCard(
    modifier: Modifier,
    title: String,
    amountMinor: Long,
    containerColor: Color,
    contentColor: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {

        Column(
            modifier = Modifier.padding(12.dp)
        ) {

            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = formatAmount(amountMinor),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun CurrencyReportPersonCard(
    person: CurrencyReportPersonRow,
    currencyCode: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = person.personName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(3.dp)
                    )

                    Text(
                        text = currencyName(currencyCode),
                        fontSize = 12.sp,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                    )
                }

                Text(
                    text =
                        formatAmount(
                            person.balanceMinor
                        ),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color =
                        balanceTextColor(
                            person.balanceMinor
                        )
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                PersonAmountItem(
                    modifier =
                        Modifier.weight(1f),
                    label = "لك",
                    amountMinor =
                        person.totalReceivableMinor,
                    color =
                        MaterialTheme.colorScheme
                            .tertiary
                )

                PersonAmountItem(
                    modifier =
                        Modifier.weight(1f),
                    label = "عليك",
                    amountMinor =
                        person.totalPayableMinor,
                    color =
                        MaterialTheme.colorScheme
                            .error
                )
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text =
                        "عدد العمليات: ${person.transactionCount}",
                    fontSize = 13.sp,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )

                Text(
                    text = "عرض التقرير",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color =
                        MaterialTheme.colorScheme
                            .primary
                )
            }
        }
    }
}

@Composable
private fun PersonAmountItem(
    modifier: Modifier,
    label: String,
    amountMinor: Long,
    color: Color
) {
    Column(
        modifier = modifier
    ) {

        Text(
            text = label,
            fontSize = 12.sp,
            color = color,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(2.dp)
        )

        Text(
            text = formatAmount(amountMinor),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LoadingReportCard() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                text = "جاري تحميل التقرير...",
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyReportCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme
                    .surfaceVariant
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                text = "لا توجد بيانات",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text =
                    "لا توجد حسابات أو عمليات لهذه العملة حاليًا.",
                fontSize = 14.sp,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorReportCard(
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme
                    .errorContainer
        )
    ) {

        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color =
                MaterialTheme.colorScheme
                    .onErrorContainer,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun balanceTextColor(
    balanceMinor: Long
): Color {
    return when {
        balanceMinor > 0L ->
            MaterialTheme.colorScheme.tertiary

        balanceMinor < 0L ->
            MaterialTheme.colorScheme.error

        else ->
            MaterialTheme.colorScheme.onSurface
    }
}

@Composable
private fun balanceContainerColor(
    balanceMinor: Long
): Color {
    return when {
        balanceMinor > 0L ->
            MaterialTheme.colorScheme
                .tertiaryContainer

        balanceMinor < 0L ->
            MaterialTheme.colorScheme
                .errorContainer

        else ->
            MaterialTheme.colorScheme.surface
    }
}

@Composable
private fun balanceContentColor(
    balanceMinor: Long
): Color {
    return when {
        balanceMinor > 0L ->
            MaterialTheme.colorScheme
                .onTertiaryContainer

        balanceMinor < 0L ->
            MaterialTheme.colorScheme
                .onErrorContainer

        else ->
            MaterialTheme.colorScheme.onSurface
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
