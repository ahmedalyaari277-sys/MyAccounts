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

@Composable
fun DetailsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        TextButton(onClick = onBack) { Text("رجوع") }
        Spacer(Modifier.height(8.dp))
        Text("تفاصيل التطبيق", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Text("MyAccounts — دفتر الحسابات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "تطبيق شخصي لإدارة الحسابات والمعاملات المالية بسهولة، مع دعم الريال اليمني والريال السعودي والدولار الأمريكي.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        Text("يوفر التطبيق:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "• إدارة الأشخاص والحسابات.\n" +
                "• تسجيل ومتابعة المعاملات.\n" +
                "• معرفة ما لك وما عليك.\n" +
                "• التقارير وتصديرها بصيغة PDF وExcel.\n" +
                "• الأرشفة والاستعادة.\n" +
                "• النسخ الاحتياطي واستعادة البيانات.\n" +
                "• حماية التطبيق بالبصمة ورمز PIN.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "البيانات محفوظة محليًا على الجهاز، مع إمكانية إنشاء نسخ احتياطية للحفاظ عليها.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        Text("المطور: أحمد محمد اليعري", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("هاتف: +967-773034454", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text("البريد الإلكتروني: Ahmedalyaari277@gmail.com", style = MaterialTheme.typography.bodyMedium)
    }
}
