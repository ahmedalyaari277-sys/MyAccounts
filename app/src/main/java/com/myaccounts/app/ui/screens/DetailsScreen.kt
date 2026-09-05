package com.myaccounts.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.currency.CurrencyCatalog
import com.myaccounts.app.ui.components.AppTopBar
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.SummaryCard

@Composable
fun DetailsScreen(onBack: () -> Unit) {
    Scaffold(topBar = { AppTopBar(title = "تفاصيل التطبيق", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(title = "حساباتي") {
                Text("MyAccounts — دفتر الحسابات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("تطبيق شخصي لإدارة الحسابات والمعاملات المالية بسهولة، مع إمكانية تفعيل العملات التي تحتاجها من الإعدادات.", style = MaterialTheme.typography.bodyLarge)
            }

            InformationCard {
                Text("العملات المفعلة", style = MaterialTheme.typography.titleMedium)
                Text(CurrencyCatalog.enabledCodes().joinToString("، "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("يمكن إدارة العملات من شاشة الإعدادات.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            InformationCard {
                Text("مزايا التطبيق", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text("• إدارة الأشخاص والحسابات.\n• تسجيل ومتابعة المعاملات.\n• معرفة ما لك وما عليك.\n• التقارير وتصديرها بصيغة PDF وExcel.\n• الأرشفة والاستعادة.\n• النسخ الاحتياطي واستعادة البيانات.\n• حماية التطبيق بالبصمة ورمز الدخول.\n• إضافة عملات جديدة دون تغيير بنية الحسابات.", style = MaterialTheme.typography.bodyLarge)
            }

            InformationCard {
                Text("البيانات والنسخ الاحتياطي", style = MaterialTheme.typography.titleMedium)
                Text("البيانات محفوظة محليًا على الجهاز، مع إمكانية إنشاء نسخ احتياطية للحفاظ عليها.", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
