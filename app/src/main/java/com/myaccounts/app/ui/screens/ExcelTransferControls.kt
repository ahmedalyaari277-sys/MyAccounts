package com.myaccounts.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myaccounts.app.ui.components.ConfirmationDialog
import com.myaccounts.app.ui.components.FeedbackDialog
import com.myaccounts.app.ui.components.FeedbackDialogType
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.PrimaryButton
import com.myaccounts.app.ui.components.SecondaryButton
import com.myaccounts.app.util.ExcelDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class ExcelFeedbackType { Success, Error }

@Composable
fun ExcelTransferControls() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ExcelDataManager.ImportPreview?>(null) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var feedbackType by remember { mutableStateOf(ExcelFeedbackType.Success) }

    fun showMessage(text: String, type: ExcelFeedbackType) {
        message = text
        feedbackType = type
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExcelDataManager.MIME_TYPE)) { uri ->
        if (uri != null) {
            busy = true
            scope.launch(Dispatchers.IO) {
                val result = ExcelDataManager.exportActive(context, uri)
                busy = false
                result.fold(
                    onSuccess = { summary -> showMessage("تم تصدير البيانات النشطة بنجاح.\nالأشخاص: ${summary.people}\nالحسابات: ${summary.accounts}\nالعمليات: ${summary.transactions}\n\nالأرشيف غير مشمول في الملف.", ExcelFeedbackType.Success) },
                    onFailure = { showMessage("تعذر تصدير Excel: ${it.message ?: "خطأ غير معروف"}", ExcelFeedbackType.Error) }
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
                result.fold(onSuccess = { preview = it }, onFailure = { showMessage("تعذر قراءة ملف Excel: ${it.message ?: "الملف غير صالح"}", ExcelFeedbackType.Error) })
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
        ConfirmationDialog(
            title = "مراجعة ملف Excel",
            message = buildString {
                append("الأشخاص: ${data.people}\n")
                append("الحسابات: ${data.accounts}\n")
                append("العمليات: ${data.transactions}\n")
                if (data.duplicateTransactions > 0) append("تكرارات داخل الملف: ${data.duplicateTransactions}\n")
                if (data.errors.isNotEmpty()) {
                    append("\nلا يمكن الاستيراد قبل إصلاح الأخطاء:\n")
                    data.errors.take(8).forEach { append("• $it\n") }
                } else {
                    append("\nسيتم إدخال البيانات في عملية قاعدة بيانات واحدة. البيانات المؤرشفة لن تُستورد.")
                }
            },
            onConfirm = {
                if (!data.isValid) return@ConfirmationDialog
                val uri = pendingImportUri ?: return@ConfirmationDialog
                preview = null
                pendingImportUri = null
                busy = true
                scope.launch(Dispatchers.IO) {
                    val result = ExcelDataManager.import(context, uri)
                    busy = false
                    result.fold(
                        onSuccess = { summary -> showMessage("تم الاستيراد بنجاح.\nأضيف أشخاص: ${summary.peopleAdded}\nأضيف حسابات: ${summary.accountsAdded}\nأضيف عمليات: ${summary.transactionsAdded}\nتكرارات تم تجاوزها: ${summary.skippedDuplicates}", ExcelFeedbackType.Success) },
                        onFailure = { showMessage("تعذر الاستيراد: ${it.message ?: "خطأ غير معروف"}", ExcelFeedbackType.Error) }
                    )
                }
            },
            onDismiss = { preview = null; pendingImportUri = null },
            confirmText = if (data.isValid) "استيراد" else "غير صالح",
            dismissText = "إلغاء",
            danger = false
        )
    }

    message?.let { text ->
        FeedbackDialog(
            text = text,
            type = if (feedbackType == ExcelFeedbackType.Success) FeedbackDialogType.Success else FeedbackDialogType.Error,
            onDismiss = { message = null }
        )
    }
}
