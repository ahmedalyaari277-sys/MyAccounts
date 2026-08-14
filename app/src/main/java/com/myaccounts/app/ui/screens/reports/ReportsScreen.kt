package com.myaccounts.app.ui.screens.reports

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myaccounts.app.data.reports.CurrencyReportPersonRow
import com.myaccounts.app.ui.viewmodel.ReportsViewModel
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    onBack: () -> Unit,
    onPersonClick: (Long, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

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
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

            CurrencySelector(
                selectedCurrency = uiState.selectedCurrencyCode,
                onCurrencySelected = {
                    viewModel.selectCurrency(it)
                }
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            uiState.currencySummary?.let { summary ->

                CurrencySummaryCard(
                    currencyCode = summary.currencyCode,
                    totalReceivableMinor =
                        summary.totalReceivableMinor,
                    totalPayableMinor =
                        summary.totalPayableMinor,
                    balanceMinor =
                        summary.balanceMinor,
                    transactionCount =
                        summary.transactionCount
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )
            }

            Text(
                text = "الأشخاص",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            if (uiState.isLoading) {

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("جاري تحميل التقرير...")
                }

            } else if (uiState.people.isEmpty()) {

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("لا توجد بيانات لهذه العملة")
                }

            } else {

                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {

                    items(
                        items = uiState.people,
                        key = {
                            it.personId
                        }
                    ) { person ->

                        CurrencyReportPersonCard(
                            person = person,
                            onClick = {
                                onPersonClick(
                                    person.personId,
                                    uiState.selectedCurrencyCode
                                )
                            }
                        )
                    }
                }
            }

            uiState.errorMessage?.let { message ->

                Text(
                    text = message,
                    modifier = Modifier.padding(
                        top = 8.dp
                    ),
                    color = MaterialTheme.colorScheme.error
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
            Text(label)
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
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = currencyName(currencyCode),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            ReportAmountRow(
                label = "إجمالي لك",
                amountMinor = totalReceivableMinor
            )

            ReportAmountRow(
                label = "إجمالي عليك",
                amountMinor = totalPayableMinor
            )

            ReportAmountRow(
                label = "الرصيد",
                amountMinor = balanceMinor
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = "عدد العمليات: $transactionCount",
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun CurrencyReportPersonCard(
    person: CurrencyReportPersonRow,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = person.personName,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            ReportAmountRow(
                label = "لك",
                amountMinor =
                    person.totalReceivableMinor
            )

            ReportAmountRow(
                label = "عليك",
                amountMinor =
                    person.totalPayableMinor
            )

            ReportAmountRow(
                label = "الرصيد",
                amountMinor =
                    person.balanceMinor
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text =
                    "عدد العمليات: ${person.transactionCount}",
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ReportAmountRow(
    label: String,
    amountMinor: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically
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

    Spacer(
        modifier = Modifier.height(4.dp)
    )
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
