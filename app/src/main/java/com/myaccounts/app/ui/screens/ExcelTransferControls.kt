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
import com.myaccounts.app.ui.components.FeedbackDialog
import com.myaccounts.app.ui.components.FeedbackDialogType
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.PrimaryButton
import com.myaccounts.app.ui.components.SecondaryButton
import com.myaccounts.app.ui.components.TransferModeDialog
import com.myaccounts.app.util.DownloadStorageManager
import com.myaccounts.app.util.GlobalExcelDataManager
import com.myaccounts.app.util.TransferModeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ExcelTransferControls() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<GlobalExcelDataManager.ImportPreview?>(null) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun exportGlobalDirectly() {
        if (busy) return
        busy = true
        scope.launch(Dispatchers.IO) {
            val uri = runCatching { DownloadStorageManager.createUri(context, GlobalExcelDataManager.SUGGESTED_FILE_NAME, GlobalExcelDataManager.MIME_TYPE) }.getOrElse {
                busy = false
                message = "تعذر إنشاء ملف Excel: ${it.message ?: "خطأ غير معروف"}"
                return@launch
            }
            val result = GlobalExcelDataManager.exportActive(context, uri)
            if (result.isSuccess) DownloadStorageManager.finish(context, uri) else DownloadStorageManager.delete(context, uri)
            busy = false
            message = result.fold(
                onSuccess = { s -> "تم تصدير كامل التطبيق إلى Excel.\nالحسابات: ${s.accountPeople} أشخاص، ${s.accountAccounts} حسابات، ${s.accountTransactions} عمليات.\nالعُهَد: ${s.custodyCustodies} عهد، ${s.custodyPeople} أطراف، ${s.custodyTransactions} عمليات.\n\nالملف يحتوي Sheetين فقط: الحسابات والعُهَد." },
                onFailure = { "تعذر تصدير Excel: ${it.message ?: "خطأ غير معروف"}" }
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            busy = true
            scope.launch(Dispatchers.IO) {
                val result = GlobalExcelDataManager.previewImport(context, uri)
                busy = false
                result.fold(onSuccess = { preview = it }, onFailure = { message = "تعذر قراءة ملف Excel العام: ${it.message ?: "الملف غير صالح"}" })
            }
        }
    }

    InformationCard(modifier = Modifier.fillMaxWidth()) {
        Text("استيراد وتصدير Excel", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("الملف العام يشمل بيانات الحسابات والعُهَد معًا، في Sheetين فقط.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryButton(text = "تصدير كامل التطبيق إلى Excel", onClick = { exportGlobalDirectly() }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            SecondaryButton(text = "استيراد كامل التطبيق من Excel", onClick = { importLauncher.launch(arrayOf(GlobalExcelDataManager.MIME_TYPE, "application/zip")) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            if (busy) CircularProgressIndicator()
        }
    }

    preview?.let { data ->
        TransferModeDialog(
            title = "اختيار طريقة استيراد كامل التطبيق",
            message = buildString {
                append("الحسابات — أشخاص: ${data.account.people}، حسابات: ${data.account.accounts}، عمليات: ${data.account.transactions}\n")
                append("العُهَد — عهد: ${data.custody.custodies}، أطراف: ${data.custody.people}، حسابات: ${data.custody.accounts}، عمليات: ${data.custody.transactions}\n")
                if (data.account.errors.isNotEmpty()) { append("\nأخطاء الحسابات:\n"); data.account.errors.take(20).forEach { append("• $it\n") } }
                if (data.custody.errors.isNotEmpty()) { append("\nأخطاء العُهَد:\n"); data.custody.errors.take(20).forEach { append("• $it\n") } }
                if (!data.isValid) append("\nلا يمكن الاستيراد قبل إصلاح الأخطاء الموضحة أعلاه.")
            },
            onDismiss = { preview = null; pendingImportUri = null },
            onModeSelected = { mode ->
                if (!data.isValid) return@TransferModeDialog
                val uri = pendingImportUri ?: return@TransferModeDialog
                preview = null; pendingImportUri = null; busy = true
                scope.launch(Dispatchers.IO) {
                    val result = TransferModeManager.importGlobal(context, uri, mode)
                    busy = false
                    message = result.fold(
                        onSuccess = { s -> "تم استيراد كامل التطبيق بنجاح.\nطريقة الاستيراد: ${mode.title}\nالحسابات: أضيف ${s.account.peopleAdded} أشخاص و${s.account.accountsAdded} حسابات و${s.account.transactionsAdded} عمليات.\nالعُهَد: أضيف ${s.custody.custodiesAdded} عهد و${s.custody.peopleAdded} أطراف و${s.custody.transactionsAdded} عمليات." },
                        onFailure = { "تعذر الاستيراد: ${it.message ?: "الملف غير صالح"}" }
                    )
                }
            }
        )
    }

    message?.let { text -> FeedbackDialog(text = text, type = if (text.startsWith("تم ")) FeedbackDialogType.Success else FeedbackDialogType.Error, onDismiss = { message = null }) }
}
