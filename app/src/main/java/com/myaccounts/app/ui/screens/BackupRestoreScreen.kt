package com.myaccounts.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.custody.CustodyAttachmentStore
import com.myaccounts.app.security.AppSecurityManager
import com.myaccounts.app.ui.components.AppTopBar
import com.myaccounts.app.ui.components.ConfirmationDialog
import com.myaccounts.app.ui.components.DangerButton
import com.myaccounts.app.ui.components.FeedbackDialog
import com.myaccounts.app.ui.components.FeedbackDialogType
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.PrimaryButton
import com.myaccounts.app.ui.components.SecondaryButton
import com.myaccounts.app.ui.components.TransferModeDialog
import com.myaccounts.app.util.BackupScope
import com.myaccounts.app.util.CompatibleRestoreManager
import com.myaccounts.app.util.DownloadStorageManager
import com.myaccounts.app.util.ExcelDataManager
import com.myaccounts.app.util.ManualSyncManager
import com.myaccounts.app.util.ScopedBackupManager
import com.myaccounts.app.util.TransferModeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val BACKUP_PREFS = "myaccounts_backup_preferences"
private const val LAST_BACKUP_URI = "last_backup_uri"
private const val BACKUP_EMAIL = "backup_email"
private const val SYNC_FOLDER_URI = "sync_folder_uri"

enum class BackupFeedbackType { Success, Error, Info }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(onBack: () -> Unit, scope: BackupScope = BackupScope.ALL) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val security = remember { AppSecurityManager(context) }
    val preferences = remember { context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE) }
    val uriKey = "${LAST_BACKUP_URI}_${scope.key}"
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var feedbackType by remember { mutableStateOf(BackupFeedbackType.Info) }
    var email by remember { mutableStateOf(preferences.getString(BACKUP_EMAIL, "") ?: "") }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var lastBackupUri by remember { mutableStateOf(preferences.getString(uriKey, null)?.let(Uri::parse)) }
    var syncFolderUri by remember { mutableStateOf(preferences.getString(SYNC_FOLDER_URI, null)?.let(Uri::parse)) }

    fun showMessage(text: String, type: BackupFeedbackType) { message = text; feedbackType = type }

    fun createBackupDirectly() {
        if (busy) return
        busy = true
        coroutineScope.launch(Dispatchers.IO) {
            val uri = runCatching { DownloadStorageManager.createUri(context, ScopedBackupManager.suggestedFileName(scope), "application/octet-stream") }.getOrElse {
                busy = false
                showMessage("تعذر إنشاء ملف النسخة الاحتياطية: ${it.message ?: "خطأ غير معروف"}", BackupFeedbackType.Error)
                return@launch
            }
            if (scope != BackupScope.ACCOUNTS) CustodyAttachmentStore.ensureSchema(context)
            val result = ScopedBackupManager.createBackup(context, uri, scope)
            if (result.isSuccess) DownloadStorageManager.finish(context, uri) else DownloadStorageManager.delete(context, uri)
            busy = false
            result.fold(
                onSuccess = {
                    lastBackupUri = uri
                    preferences.edit().putString(uriKey, uri.toString()).apply()
                    showMessage("تم إنشاء نسخة ${scope.title} بنجاح، وتشمل البيانات والمرفقات التابعة للنطاق.", BackupFeedbackType.Success)
                },
                onFailure = { showMessage("تعذر إنشاء النسخة الاحتياطية: ${it.message ?: "خطأ غير معروف"}", BackupFeedbackType.Error) }
            )
        }
    }

    val syncFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                syncFolderUri = uri
                preferences.edit().putString(SYNC_FOLDER_URI, uri.toString()).apply()
                showMessage("تم حفظ مجلد المزامنة.", BackupFeedbackType.Success)
            } catch (exception: Exception) {
                showMessage("تعذر حفظ صلاحية مجلد المزامنة: ${exception.message ?: "خطأ غير معروف"}", BackupFeedbackType.Error)
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pendingRestoreUri = uri }

    fun syncNow() {
        val folderUri = syncFolderUri
        if (folderUri == null) { showMessage("اختر مجلد المزامنة أولاً.", BackupFeedbackType.Info); return }
        busy = true
        coroutineScope.launch(Dispatchers.IO) {
            if (scope != BackupScope.ACCOUNTS) CustodyAttachmentStore.ensureSchema(context)
            val result = ManualSyncManager.syncToFolder(context, folderUri, scope)
            busy = false
            result.fold(
                onSuccess = { uri -> lastBackupUri = uri; preferences.edit().putString(uriKey, uri.toString()).apply(); showMessage("تم حفظ نسخة المزامنة لنطاق ${scope.title} بنجاح.", BackupFeedbackType.Success) },
                onFailure = { error -> showMessage("تعذرت المزامنة: ${error.message ?: "خطأ غير معروف"}", BackupFeedbackType.Error) }
            )
        }
    }

    fun shareBackup() {
        val uri = lastBackupUri ?: run { showMessage("أنشئ نسخة احتياطية أولاً.", BackupFeedbackType.Info); return }
        try {
            val intent = Intent(Intent.ACTION_SEND).apply { type = "application/octet-stream"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية — ${scope.title}"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            context.startActivity(Intent.createChooser(intent, "مشاركة النسخة الاحتياطية").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (exception: Exception) { showMessage("تعذر فتح خيارات المشاركة: ${exception.message ?: "خطأ غير معروف"}", BackupFeedbackType.Error) }
    }

    fun sendBackupByEmail() {
        val uri = lastBackupUri ?: run { showMessage("أنشئ نسخة احتياطية أولاً.", BackupFeedbackType.Info); return }
        val normalizedEmail = email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) { showMessage("أدخل عنوان بريد إلكتروني صحيحًا.", BackupFeedbackType.Error); return }
        preferences.edit().putString(BACKUP_EMAIL, normalizedEmail).apply()
        try {
            val intent = Intent(Intent.ACTION_SEND).apply { type = "application/octet-stream"; putExtra(Intent.EXTRA_EMAIL, arrayOf(normalizedEmail)); putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية — ${scope.title}"); putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            context.startActivity(Intent.createChooser(intent, "إرسال النسخة الاحتياطية بالبريد").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (exception: Exception) { showMessage("تعذر فتح تطبيق البريد أو المشاركة: ${exception.message ?: "خطأ غير معروف"}", BackupFeedbackType.Error) }
    }

    LaunchedEffect(scope, lastBackupUri) { if (lastBackupUri != null) showMessage("لديك نسخة ${scope.title} محفوظة ويمكنك مشاركتها.", BackupFeedbackType.Info) }

    Scaffold(topBar = { AppTopBar(title = if (scope == BackupScope.ALL) "النسخ الاحتياطي والمزامنة" else "نسخ واستعادة ${scope.title}", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 4.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            InformationCard(modifier = Modifier.fillMaxWidth()) {
                Text("النسخ الاحتياطي", style = MaterialTheme.typography.titleMedium)
                Text("بيانات النطاق المحدد ومرفقاته.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PrimaryButton(text = "إنشاء نسخة احتياطية", onClick = { createBackupDirectly() }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            }

            if (scope == BackupScope.ALL) ExcelTransferControls()
            if (scope == BackupScope.ACCOUNTS) AccountExcelTransferControls()

            InformationCard(modifier = Modifier.fillMaxWidth()) {
                Text("المزامنة اليدوية", style = MaterialTheme.typography.titleMedium)
                Text(if (syncFolderUri == null) "اختر مجلدًا للمزامنة." else "تم اختيار مجلد للمزامنة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SecondaryButton(text = "اختيار مجلد المزامنة", onClick = { syncFolderLauncher.launch(null) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                SecondaryButton(text = "مزامنة الآن", onClick = { syncNow() }, enabled = !busy && syncFolderUri != null, modifier = Modifier.fillMaxWidth())
            }

            InformationCard(modifier = Modifier.fillMaxWidth()) {
                Text("إرسال ومشاركة النسخة", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("البريد الإلكتروني (اختياري)") })
                Spacer(Modifier.height(2.dp))
                SecondaryButton(text = "إرسال النسخة الاحتياطية بالبريد", onClick = { sendBackupByEmail() }, enabled = !busy && lastBackupUri != null, modifier = Modifier.fillMaxWidth())
                SecondaryButton(text = "مشاركة النسخة الاحتياطية", onClick = { shareBackup() }, enabled = !busy && lastBackupUri != null, modifier = Modifier.fillMaxWidth())
            }

            InformationCard(modifier = Modifier.fillMaxWidth()) {
                Text("استعادة نسخة احتياطية", style = MaterialTheme.typography.titleMedium)
                Text("تستبدل الاستعادة بيانات هذا النطاق فقط.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                DangerButton(text = "استعادة نسخة احتياطية", onClick = { security.markExternalActivityPending(); openDocumentLauncher.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            }

            if (busy) { Spacer(Modifier.height(1.dp)); CircularProgressIndicator() }
        }
    }

    pendingRestoreUri?.let { uri ->
        ConfirmationDialog(
            title = "تأكيد الاستعادة",
            message = "سيتم استبدال بيانات ${scope.title} الحالية بالبيانات الموجودة في النسخة المحددة، مع إبقاء القسم الآخر كما هو. هل تريد المتابعة؟",
            onConfirm = {
                pendingRestoreUri = null
                busy = true
                coroutineScope.launch(Dispatchers.IO) {
                    if (scope != BackupScope.ACCOUNTS) CustodyAttachmentStore.ensureSchema(context)
                    val result = CompatibleRestoreManager.restore(context, uri, scope)
                    busy = false
                    result.fold(
                        onSuccess = { showMessage("تمت استعادة ${scope.title} والمرفقات بنجاح.", BackupFeedbackType.Success) },
                        onFailure = { showMessage("تعذر استعادة النسخة الاحتياطية: ${it.message ?: "الملف غير صالح"}", BackupFeedbackType.Error) }
                    )
                }
            },
            onDismiss = { pendingRestoreUri = null },
            confirmText = "استعادة",
            dismissText = "إلغاء",
            danger = true
        )
    }

    message?.let { text ->
        FeedbackDialog(
            text = text,
            type = when (feedbackType) { BackupFeedbackType.Success -> FeedbackDialogType.Success; BackupFeedbackType.Error -> FeedbackDialogType.Error; BackupFeedbackType.Info -> FeedbackDialogType.Info },
            onDismiss = { message = null }
        )
    }
}

@Composable
private fun AccountExcelTransferControls() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ExcelDataManager.ImportPreview?>(null) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun exportAccountsDirectly() {
        if (busy) return
        busy = true
        coroutineScope.launch(Dispatchers.IO) {
            val uri = runCatching { DownloadStorageManager.createUri(context, ExcelDataManager.SUGGESTED_FILE_NAME, ExcelDataManager.MIME_TYPE) }.getOrElse {
                busy = false
                message = "تعذر إنشاء ملف Excel: ${it.message ?: "خطأ غير معروف"}"
                return@launch
            }
            val result = ExcelDataManager.exportActive(context, uri)
            if (result.isSuccess) DownloadStorageManager.finish(context, uri) else DownloadStorageManager.delete(context, uri)
            busy = false
            message = result.fold(
                onSuccess = { s -> "تم تصدير الحسابات إلى Excel بنجاح.\nالأشخاص: ${s.people}\nالحسابات: ${s.accounts}\nالعمليات: ${s.transactions}" },
                onFailure = { "تعذر تصدير الحسابات إلى Excel: ${it.message ?: "خطأ غير معروف"}" }
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImport = uri
            busy = true
            coroutineScope.launch(Dispatchers.IO) {
                val result = ExcelDataManager.previewImport(context, uri)
                busy = false
                result.fold(
                    onSuccess = { preview = it },
                    onFailure = { message = "تعذر قراءة ملف Excel للحسابات: ${it.message ?: "الملف غير صالح"}" }
                )
            }
        }
    }

    InformationCard(modifier = Modifier.fillMaxWidth()) {
        Text("Excel للحسابات", style = MaterialTheme.typography.titleMedium)
        Text("هذه الوظائف خاصة بالحسابات فقط. ملف Excel للحسابات يحتوي Sheet واحدًا، ولا يتضمن العُهَد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryButton(text = "تصدير الحسابات إلى Excel", onClick = { exportAccountsDirectly() }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        SecondaryButton(text = "استيراد الحسابات من Excel", onClick = { importLauncher.launch(arrayOf(ExcelDataManager.MIME_TYPE)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        if (busy) { Spacer(Modifier.height(2.dp)); CircularProgressIndicator() }
    }

    preview?.let { data ->
        TransferModeDialog(
            title = "اختيار طريقة استيراد الحسابات",
            message = buildString {
                append("الأشخاص: ${data.people}\n")
                append("الحسابات: ${data.accounts}\n")
                append("العمليات: ${data.transactions}\n")
                if (data.errors.isNotEmpty()) { append("\nأخطاء الملف:\n"); data.errors.take(20).forEach { append("• $it\n") } }
                if (!data.isValid) append("\nلا يمكن الاستيراد قبل إصلاح الأخطاء الموضحة أعلاه.")
            },
            onDismiss = { preview = null; pendingImport = null },
            onModeSelected = { mode ->
                if (!data.isValid) return@TransferModeDialog
                val uri = pendingImport ?: return@TransferModeDialog
                preview = null
                pendingImport = null
                busy = true
                coroutineScope.launch(Dispatchers.IO) {
                    val result = TransferModeManager.importAccounts(context, uri, mode)
                    busy = false
                    message = result.fold(
                        onSuccess = { s -> "تم استيراد الحسابات بنجاح.\nطريقة الاستيراد: ${mode.title}\nالأشخاص: ${s.peopleAdded}\nالحسابات: ${s.accountsAdded}\nالعمليات: ${s.transactionsAdded}" },
                        onFailure = { "تعذر استيراد الحسابات: ${it.message ?: "الملف غير صالح"}" }
                    )
                }
            }
        )
    }

    message?.let { text -> FeedbackDialog(text = text, type = if (text.startsWith("تم ")) FeedbackDialogType.Success else FeedbackDialogType.Error, onDismiss = { message = null }) }
}
