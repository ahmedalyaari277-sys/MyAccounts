package com.myaccounts.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.custody.CustodyFinancialSummary
import com.myaccounts.app.ui.components.BalanceAmount
import com.myaccounts.app.ui.components.BalanceStatus
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.SummaryCard
import com.myaccounts.app.ui.components.StatusChip
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import java.math.BigDecimal

private val entryCurrencies = listOf("YER", "SAR", "USD")
private fun entryMoney(v: Long): String = BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyEntryScreen(
    vm: CustodyViewModel,
    custodyId: Long,
    onBack: () -> Unit,
    onParties: () -> Unit,
    onHolder: () -> Unit
) {
    val custody by vm.custody(custodyId).collectAsState()
    val people by vm.persons(custodyId).collectAsState()
    val accounts by vm.accounts(custodyId).collectAsState()
    val transactions by vm.transactions(custodyId).collectAsState()
    val current = custody ?: return

    Scaffold(
        modifier = Modifier.semantics { contentDescription = "شاشة العهدة" },
        topBar = {
            TopAppBar(
                title = { Text(current.name, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            item {
                SummaryCard(
                    title = "حامل العهدة",
                    modifier = Modifier.fillMaxWidth().clickable { onHolder() }.semantics { contentDescription = "بطاقة حامل العهدة" }
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(current.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("الجهة: ${current.organizationName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        entryCurrencies.forEach { code ->
                            val balance = accounts.firstOrNull { it.holderType == "OWNER" && it.personId == null && it.currencyCode == code }?.balanceMinor ?: 0L
                            InformationCard(Modifier.weight(1f)) {
                                StatusChip(code, MaterialTheme.colorScheme.primary)
                                BalanceAmount(
                                    amount = when { balance > 0 -> "عليه ${entryMoney(balance)}"; balance < 0 -> "له ${entryMoney(-balance)}"; else -> "متوازن 0" },
                                    status = when { balance > 0 -> BalanceStatus.Due; balance < 0 -> BalanceStatus.Owed; else -> BalanceStatus.Neutral },
                                    label = code
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("فتح عمليات حامل العهدة", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            item {
                SummaryCard(
                    title = "الأطراف",
                    modifier = Modifier.fillMaxWidth().clickable { onParties() }.semantics { contentDescription = "بطاقة الأطراف" }
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("الأطراف", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("عدد الأطراف: ${people.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("فتح قائمة الأطراف وعملياتهم", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (people.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        val summary = entryCurrencies.map { code -> CustodyFinancialSummary.ownerDisplay(transactions, accounts, people, code) }
                        Text("إجمالي ذمم الأطراف", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            entryCurrencies.forEachIndexed { index, code ->
                                val value = summary[index].peopleDebtMinor
                                InformationCard(Modifier.weight(1f)) {
                                    Text(code, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(entryMoney(kotlin.math.abs(value)), fontWeight = FontWeight.Bold)
                                    Text(if (value > 0) "له على الأطراف" else if (value < 0) "عليه للأطراف" else "متوازن", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
