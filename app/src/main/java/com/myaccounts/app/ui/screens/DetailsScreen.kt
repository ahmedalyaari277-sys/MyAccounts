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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.currency.CurrencyCatalog

@Composable
fun DetailsScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.Top) {
        TextButton(onClick = onBack) { Text("رجوع") }
        Spacer(Modifier.height(8.dp))
        Text("تفاصيل التطبيق", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("MyAccounts — دفتر الحسابات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("تطبيق شخصي لإدارة الحسابات والمعاملات المالية بسهولة، مع إمكانية تفعيل العملات التي تحتاجها من الإعدادات.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Text("العملات المفعلة حاليًا: ${CurrencyCatalog.codes.joinToString("، ")}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Text("يوفر التطبيق:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("• إدارة الأشخاص والحسابات.\n• تسجيل ومتابعة المعاملات.\n• معرفة ما لك وما عليك.\n• التقارير وتصديرها بصيغة PDF وExcel.\n• الأرشفة والاستعادة.\n• النسخ الاحتياطي واستعادة البيانات.\n• حماية التطبيق بالبصمة ورمز الدخول.\n• إضافة عملات جديدة دون تغيير بنية الحسابات.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Text("البيانات محفوظة محليًا على الجهاز، مع إمكانية إنشاء نسخ احتياطية للحفاظ عليها.", style = MaterialTheme.typography.bodyMedium)
    }
}
