package com.myaccounts.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.custody.CustodyBalanceRules
import com.myaccounts.app.ui.components.BalanceAmount
import com.myaccounts.app.ui.components.BalanceStatus
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.SummaryCard
import com.myaccounts.app.ui.viewmodel.CustodyViewModel

private val overviewCurrencies = listOf("YER", "SAR", "USD")

@Composable
fun CustodyOverviewScreen(
    vm: CustodyViewModel,
    custodyId: Long,
    onBack: () -> Unit,
    onHolder: () -> Unit,
    onPerson: (Long) -> Unit
) {
    val custody by vm.custody(custodyId).collectAsState()
    val people by vm.persons(custodyId).collectAsState()
    val accounts by vm.accounts(custodyId).collectAsState()
    val transactions by vm.transactions(custodyId).collectAsState()
    val current = custody ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("العهدة: ${current.name}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SummaryCard(title = "حامل العهدة") {
                    Text(current.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("الجهة: ${current.organizationName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        overviewCurrencies.forEach { currency ->
                            val balance = accounts.firstOrNull {
                                it.holderType == "OWNER" && it.personId == null && it.currencyCode == currency
                            }?.balanceMinor ?: 0L
                            InformationCard(modifier = Modifier.weight(1f)) {
                                Text(currency, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                BalanceAmount(
                                    amount = when {
                                        balance > 0 -> "عليه ${balance / 100.0}"
                                        balance < 0 -> "له ${(-balance) / 100.0}"
                                        else -> "متوازن 0"
                                    },
                                    status = when {
                                        balance > 0 -> BalanceStatus.Due
                                        balance < 0 -> BalanceStatus.Owed
                                        else -> BalanceStatus.Neutral
                                    },
                                    label = currency
                                )
                            }
                        }
                    }
                    Text("فتح شاشة عمليات حامل العهدة", color = MaterialTheme.colorScheme.primary)
                }
            }
            item {
                SummaryCard(title = "الأطراف") {
                    Text("الأطراف المرتبطة بهذه العهدة مستقلة عن حساب حامل العهدة.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (people.isEmpty()) {
                        Text("لا توجد أطراف مضافة حتى الآن.")
                    }
                }
            }
            items(people, key = { it.id }) { person ->
                val balance = transactions.filter { it.personId == person.id && it.currencyCode == "YER" }
                    .sumOf { CustodyBalanceRules.personDelta(it.type, it.amountMinor) }
                InformationCard(
                    modifier = Modifier.fillMaxWidth().clickable { onPerson(person.id) }
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(person.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (person.phone.isNotBlank()) Text(person.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        BalanceAmount(
                            amount = when {
                                balance > 0 -> "عليه ${balance / 100.0}"
                                balance < 0 -> "له ${(-balance) / 100.0}"
                                else -> "متوازن 0"
                            },
                            status = when {
                                balance > 0 -> BalanceStatus.Due
                                balance < 0 -> BalanceStatus.Owed
                                else -> BalanceStatus.Neutral
                            },
                            label = "YER"
                        )
                    }
                }
            }
        }
    }
}
