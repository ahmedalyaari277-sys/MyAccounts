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
import com.myaccounts.app.util.CurrencyReportExcelExporter
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadCurrencyReport()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التقارير", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("تقرير الحسابات", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "اختر العملة لعرض ملخص الحسابات والأشخاص والعمليات.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                CurrencySelector(
                    selectedCurrency = uiState.selectedCurrencyCode,
                    onCurrencySelected = viewModel::selectCurrency
                )
                Spacer(Modifier.height(12.dp))

                val summary = uiState.currencySummary
                val exportEnabled = !uiState.isLoading && summary != null

                Button(
                    onClick = {
                        if (summary == null) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("لا توجد بيانات كافية لإنشاء التقرير.")
                            }
                        } else {
                            val result = CurrencyReportExcelExporter.exportCurrencyReport(
                                context = context,
                                summary = summary,
                                people = uiState.people
                            )
                            coroutineScope.launch {
                                result.fold(
                                    onSuccess = { snackbarHostState.showSnackbar(it) },
                                    onFailure = {
                                        snackbarHostState.showSnackbar(
                                            it.message ?: "حدث خطأ أثناء إنشاء ملف Excel."
                                        )
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = exportEnabled
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("تصدير التقرير إلى Excel")
                }

                Button(
                    onClick = {
                        if (summary == null) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("لا توجد بيانات كافية لإنشاء التقرير.")
                            }
                        } else {
                            val result = ReportPdfExporter.exportCurrencyReport(
                                context = context,
                                summary = summary,
                                people = uiState.people
                            )
                            coroutineScope.launch {
                                result.fold(
                                    onSuccess = { snackbarHostState.showSnackbar(it) },
                                    onFailure = {
                                        snackbarHostState.showSnackbar(
                                            it.message ?: "حدث خطأ أثناء إنشاء ملف PDF."
                                        )
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = exportEnabled
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("تصدير التقرير إلى PDF")
                }
            }

            uiState.currencySummary?.let { summary ->
                item {
                    CurrencySummaryCard(
                        currencyCode = summary.currencyCode,
                        totalReceivableMinor = summary.totalReceivableMinor,
                        totalPayableMinor = summary.totalPayableMinor,
                        balanceMinor = summary.balanceMinor,
                        transactionCount = summary.transactionCount
                    )
                }
            }

            item {
                Text("الأشخاص", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            when {
                uiState.isLoading -> item { LoadingReportCard() }
                uiState.people.isEmpty() -> item { EmptyReportCard() }
                else -> items(uiState.people, key = { it.personId }) { person ->
                    CurrencyReportPersonCard(
                        person = person,
                        currencyCode = uiState.selectedCurrencyCode,
                        onClick = {
                            onPersonClick(person.personId, uiState.selectedCurrencyCode)
                        }
                    )
                }
            }

            uiState.errorMessage?.let { message ->
                item { ErrorReportCard(message) }
            }

            item { Spacer(Modifier.height(8.dp)) }
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CurrencyChip("الريال اليمني", "YER", selectedCurrency, onCurrencySelected)
        CurrencyChip("الريال السعودي", "SAR", selectedCurrency, onCurrencySelected)
        CurrencyChip("الدولار", "USD", selectedCurrency, onCurrencySelected)
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
        onClick = { onSelected(code) },
        label = { Text(label) }
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
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "ملخص ${currencyName(currencyCode)}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportMetricCard(
                    Modifier.weight(1f), "عليه", totalReceivableMinor,
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer
                )
                ReportMetricCard(
                    Modifier.weight(1f), "له", totalPayableMinor,
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(8.dp))
            ReportMetricCard(
                Modifier.fillMaxWidth(), "الرصيد", balanceMinor,
                balanceContainerColor(balanceMinor),
                balanceContentColor(balanceMinor)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "عدد العمليات: $transactionCount",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
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
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = contentColor)
            Spacer(Modifier.height(4.dp))
            Text(formatAmount(amountMinor), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = contentColor)
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
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(person.personName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        currencyName(currencyCode),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    formatAmount(person.balanceMinor),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = balanceTextColor(person.balanceMinor)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PersonAmountItem(
                    Modifier.weight(1f), "عليه", person.totalReceivableMinor,
                    MaterialTheme.colorScheme.tertiary
                )
                PersonAmountItem(
                    Modifier.weight(1f), "له", person.totalPayableMinor,
                    MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "عدد العمليات: ${person.transactionCount}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "عرض التقرير",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
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
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(formatAmount(amountMinor), fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LoadingReportCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("جاري تحميل التقرير...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyReportCard() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("لا توجد بيانات", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "لا توجد حسابات أو عمليات لهذه العملة حاليًا.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorReportCard(message: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(
            message,
            Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun balanceTextColor(balanceMinor: Long): Color = when {
    balanceMinor > 0L -> MaterialTheme.colorScheme.tertiary
    balanceMinor < 0L -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun balanceContainerColor(balanceMinor: Long): Color = when {
    balanceMinor > 0L -> MaterialTheme.colorScheme.tertiaryContainer
    balanceMinor < 0L -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.surface
}

@Composable
private fun balanceContentColor(balanceMinor: Long): Color = when {
    balanceMinor > 0L -> MaterialTheme.colorScheme.onTertiaryContainer
    balanceMinor < 0L -> MaterialTheme.colorScheme.onErrorContainer
    else -> MaterialTheme.colorScheme.onSurface
}

private fun currencyName(currencyCode: String): String = when (currencyCode) {
    "YER" -> "الريال اليمني"
    "SAR" -> "الريال السعودي"
    "USD" -> "الدولار الأمريكي"
    else -> currencyCode
}

private fun formatAmount(amountMinor: Long): String =
    BigDecimal(amountMinor)
        .movePointLeft(2)
        .stripTrailingZeros()
        .toPlainString()
