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
import com.myaccounts.app.util.GlobalExcelDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class ExcelFeedbackType { Success, Error }

@Composable
fun ExcelTransferControls() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<GlobalExcelDataManager.ImportPreview?>(null) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var feedbackType by remember { mutableStateOf(ExcelFeedbackType.Success) }

    fun showMessage(text: String, type: ExcelFeedbackType) { message = text; feedbackType = type }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(GlobalExcelDataManager.MIME_TYPE)) { uri ->
        if (uri != null) {
            busy = true
            scope.launch(Dispatchers.IO) {
                val result = GlobalExcelDataManager.exportActive(context, uri)
                busy = false
                result.fold(
                    onSuccess = { summary -> showMessage("تم تصدير كامل التطبيق إلى Excel.\nالحسابات: ${summary.accountPeople} أشخاص، ${summary.accountAccounts} حسابات، ${summary.accountTransactions} عمليات.\nالعُهَد: ${summary.custodyCustodies} عهد، ${summary.custodyPeople} أطراف، ${summary.custodyTransactions} عمليات.\n\nالملف يحتوي Sheetين فقط: الحسابات والعُهَد.", ExcelFeedbackType.Success) },
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
                val result = GlobalExcelDataManager.previewImport(context, uri)
                busy = false
                result.fold(onSuccess = { preview = it }, onFailure = { showMessage("تعذر قراءة ملف Excel العام: ${it.message ?: "الملف غير صالح"}", ExcelFeedbackType.Error) })
            }
        }
    }

    InformationCard(modifier = Modifier.fillMaxWidth()) {
        Text("استيراد وتصدير Excel", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("الملف العام يشمل بيانات الحسابات والعُهَد معًا، في Sheetين فقط.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            PrimaryButton(text = "تصدير كامل التطبيق إلى Excel", onClick = { exportLauncher.launch(GlobalExcelDataManager.SUGGESTED_FILE_NAME) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            SecondaryButton(text = "استيراد كامل التطبيق من Excel", onClick = { importLauncher.launch(arrayOf(GlobalExcelDataManager.MIME_TYPE, "application/zip")) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            if (busy) CircularProgressIndicator()
        }
    }

    preview?.let { data ->
        ConfirmationDialog(
            title = "مراجعة ملف Excel العام",
            message = buildString {
                append("الحسابات — أشخاص: ${data.account.people}، حسابات: ${data.account.accounts}، عمليات: ${data.account.transactions}\n")
                append("العُهَد — عهد: ${data.custody.custodies}، أطراف: ${data.custody.people}، حسابات: ${data.custody.accounts}، عمليات: ${data.custody.transactions}\n")
                if (data.account.errors.isNotEmpty()) { append("\nأخطاء الحسابات:\n"); data.account.errors.take(6).forEach { append("• $it\n") } }
                if (data.custody.errors.isNotEmpty()) { append("\nأخطاء العُهَد:\n"); data.custody.errors.take(6).forEach { append("• $it\n") } }
                if (data.isValid) append("\nسيتم استيراد القسمين بعد اجتياز الفحص.")
            },
            onConfirm = {
                if (!data.isValid) return@ConfirmationDialog
                val uri = pendingImportUri ?: return@ConfirmationDialog
                preview = null; pendingImportUri = null; busy = true
                scope.launch(Dispatchers.IO) {
                    val result = GlobalExcelDataManager.import(context, uri)
                    busy = false
                    result.fold(
                        onSuccess = { summary -> showMessage("تم استيراد كامل التطبيق بنجاح.\nالحسابات: أضيف ${summary.account.peopleAdded} أشخاص و${summary.account.accountsAdded} حسابات و${summary.account.transactionsAdded} عمليات.\nالعُهَد: أضيف ${summary.custody.custodiesAdded} عهد و${summary.custody.peopleAdded} أطراف و${summary.custody.transactionsAdded} عمليات.", ExcelFeedbackType.Success) },
                        onFailure = { showMessage("تعذر الاستيراد: ${it.message ?: "الملف غير صالح"}", ExcelFeedbackType.Error) }
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
        FeedbackDialog(text = text, type = if (feedbackType == ExcelFeedbackType.Success) FeedbackDialogType.Success else FeedbackDialogType.Error, onDismiss = { message = null })
    }
}
