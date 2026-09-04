package com.myaccounts.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.PrimaryButton
import com.myaccounts.app.ui.components.SecondaryButton
import com.myaccounts.app.util.ExcelDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ExcelTransferControls() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ExcelDataManager.ImportPreview?>(null) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExcelDataManager.MIME_TYPE)) { uri ->
        if (uri != null) {
            busy = true
            scope.launch(Dispatchers.IO) {
                val result = ExcelDataManager.exportActive(context, uri)
                busy = false
                result.fold(
                    onSuccess = { summary -> message = "تم تصدير البيانات النشطة بنجاح.\nالأشخاص: ${summary.people}\nالحسابات: ${summary.accounts}\nالعمليات: ${summary.transactions}\n\nالأرشيف غير مشمول في الملف." },
                    onFailure = { message = "تعذر تصدير Excel: ${it.message ?: "خطأ غير معروف"}" }
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            busy = true
            scope.launch(Dispatchers.IO) {
                val result = ExcelDataManager.previewImport(context, uri)
                busy = false
                result.fold(onSuccess = { preview = it }, onFailure = { message = "تعذر قراءة ملف Excel: ${it.message ?: "الملف غير صالح"}" })
            }
        }
    }

    InformationCard(modifier = Modifier.fillMaxWidth()) {
        Text("استيراد وتصدير Excel", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ملف واحد وSheet واحد. يتم التعامل مع البيانات النشطة فقط، ولا يدخل الأرشيف في الاستيراد أو التصدير.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            PrimaryButton(text = "تصدير البيانات إلى Excel", onClick = { exportLauncher.launch(ExcelDataManager.SUGGESTED_FILE_NAME) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            SecondaryButton(text = "استيراد البيانات من Excel", onClick = { importLauncher.launch(arrayOf(ExcelDataManager.MIME_TYPE, "application/zip")) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            if (busy) CircularProgressIndicator()
        }
    }

    preview?.let { data ->
        AlertDialog(
            onDismissRequest = { preview = null; pendingImportUri = null },
            title = { Text("مراجعة ملف Excel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الأشخاص: ${data.people}")
                    Text("الحسابات: ${data.accounts}")
                    Text("العمليات: ${data.transactions}")
                    if (data.duplicateTransactions > 0) Text("تكرارات داخل الملف: ${data.duplicateTransactions}", color = MaterialTheme.colorScheme.error)
                    if (data.errors.isNotEmpty()) {
                        Text("لا يمكن الاستيراد قبل إصلاح الأخطاء:", color = MaterialTheme.colorScheme.error)
                        data.errors.take(8).forEach { Text("• $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    } else {
                        Text("سيتم إدخال البيانات في عملية قاعدة بيانات واحدة. البيانات المؤرشفة لن تُستورد.", color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                if (data.isValid) TextButton(onClick = {
                    val uri = pendingImportUri ?: return@TextButton
                    preview = null
                    pendingImportUri = null
                    busy = true
                    scope.launch(Dispatchers.IO) {
                        val result = ExcelDataManager.import(context, uri)
                        busy = false
                        result.fold(
                            onSuccess = { summary -> message = "تم الاستيراد بنجاح.\nأضيف أشخاص: ${summary.peopleAdded}\nأضيف حسابات: ${summary.accountsAdded}\nأضيف عمليات: ${summary.transactionsAdded}\nتكرارات تم تجاوزها: ${summary.skippedDuplicates}" },
                            onFailure = { message = "تعذر الاستيراد: ${it.message ?: "خطأ غير معروف"}" }
                        )
                    }
                }) { Text("استيراد") }
            },
            dismissButton = { TextButton(onClick = { preview = null; pendingImportUri = null }) { Text("إلغاء") } }
        )
    }

    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, text = { Text(text) }, confirmButton = { TextButton(onClick = { message = null }) { Text("موافق") } }) }
}
