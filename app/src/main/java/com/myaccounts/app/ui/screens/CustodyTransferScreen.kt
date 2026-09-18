package com.myaccounts.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.myaccounts.app.data.custody.CustodyAttachmentStore
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import com.myaccounts.app.ui.components.AppTopBar
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.PrimaryButton
import com.myaccounts.app.ui.components.SecondaryButton
import com.myaccounts.app.util.BackupScope
import com.myaccounts.app.util.CompatibleRestoreManager
import com.myaccounts.app.util.CustodyTwoSheetExcelDataManager
import com.myaccounts.app.util.DownloadStorageManager
import com.myaccounts.app.util.ManualSyncManager
import com.myaccounts.app.util.ScopedBackupManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val BACKUP_PREFS = "myaccounts_backup_preferences"
private const val SYNC_FOLDER_URI = "sync_folder_uri"
private const val BACKUP_EMAIL = "backup_email"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyTransferScreen(vm: CustodyViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val custodies by vm.custodies.collectAsState()
    val preferences = remember { context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var lastBackupUri by remember { mutableStateOf<Uri?>(null) }
    var email by remember { mutableStateOf(preferences.getString(BACKUP_EMAIL, "") ?: "") }
    var syncFolderUri by remember { mutableStateOf(preferences.getString(SYNC_FOLDER_URI, null)?.let(Uri::parse)) }

    fun exportCustodyDirectly() {
        if (busy) return
        busy = true
        scope.launch(Dispatchers.IO) {
            val uri = runCatching { DownloadStorageManager.createUri(context, CustodyTwoSheetExcelDataManager.SUGGESTED_FILE_NAME, CustodyTwoSheetExcelDataManager.MIME_TYPE) }.getOrElse {
                busy = false
                message = "تعذر إنشاء ملف Excel: ${it.message ?: "خطأ غير معروف"}"
                return@launch
            }
            val r = CustodyTwoSheetExcelDataManager.exportActive(context, uri)
            if (r.isSuccess) DownloadStorageManager.finish(context, uri) else DownloadStorageManager.delete(context, uri)
            message = r.fold({ "تم تصدير ${it.custodies} عهدة و${it.transactions} عملية إلى Excel. الملف يحتوي Sheet واحد فقط: بيانات العُهَد، ويشمل التصنيف داخل نفس الورقة." }, { "تعذر تصدير بيانات العُهَد: ${it.message ?: "خطأ غير معروف"}" })
            busy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pendingImport = uri }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pendingRestore = uri }
    val syncFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                syncFolderUri = uri
                preferences.edit().putString(SYNC_FOLDER_URI, uri.toString()).apply()
                message = "تم حفظ مجلد مزامنة العُهَد."
            } catch (e: Exception) { message = "تعذر حفظ مجلد المزامنة: ${e.message ?: "خطأ غير معروف"}" }
        }
    }

    fun createCustodyBackupDirectly() {
        if (busy) return
        busy = true
        scope.launch(Dispatchers.IO) {
            CustodyAttachmentStore.ensureSchema(context)
            val uri = runCatching { DownloadStorageManager.createUri(context, ScopedBackupManager.suggestedFileName(BackupScope.CUSTODY), "application/octet-stream") }.getOrElse {
                busy = false
                message = "تعذر إنشاء ملف النسخة الاحتياطية: ${it.message ?: "خطأ غير معروف"}"
                return@launch
            }
            val r = ScopedBackupManager.createBackup(context, uri, BackupScope.CUSTODY)
            if (r.isSuccess) { DownloadStorageManager.finish(context, uri); lastBackupUri = uri } else DownloadStorageManager.delete(context, uri)
            message = r.fold({ "تم إنشاء النسخة الاحتياطية للعُهَد بنجاح، وتشمل بيانات العُهَد ومرفقاتها." }, { "تعذر إنشاء النسخة الاحتياطية للعُهَد: ${it.message ?: "خطأ غير معروف"}" })
            busy = false
        }
    }

    fun syncNow() {
        val folder = syncFolderUri
        if (folder == null) { message = "اختر مجلد المزامنة أولاً."; return }
        busy = true
        scope.launch(Dispatchers.IO) {
            CustodyAttachmentStore.ensureSchema(context)
            val r = ManualSyncManager.syncToFolder(context, folder, BackupScope.CUSTODY)
            if (r.isSuccess) lastBackupUri = r.getOrNull()
            message = r.fold({ "تم حفظ نسخة مزامنة للعُهَد في المجلد المحدد." }, { "تعذرت مزامنة العُهَد: ${it.message ?: "خطأ غير معروف"}" })
            busy = false
        }
    }

    fun shareBackup() {
        val uri = lastBackupUri ?: run { message = "أنشئ أو نفذ مزامنة لنسخة احتياطية أولاً."; return }
        try {
            val intent = Intent(Intent.ACTION_SEND).apply { type = "application/octet-stream"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية للعُهَد"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            context.startActivity(Intent.createChooser(intent, "مشاركة النسخة الاحتياطية"))
        } catch (e: Exception) { message = "تعذرت مشاركة النسخة الاحتياطية: " + (e.message ?: "خطأ غير معروف") }
    }

    fun sendBackupByEmail() {
        val uri = lastBackupUri ?: run { message = "أنشئ نسخة احتياطية أولاً."; return }
        val normalized = email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) { message = "أدخل عنوان بريد إلكتروني صحيحًا."; return }
        preferences.edit().putString(BACKUP_EMAIL, normalized).apply()
        try {
            val intent = Intent(Intent.ACTION_SEND).apply { type = "application/octet-stream"; putExtra(Intent.EXTRA_EMAIL, arrayOf(normalized)); putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية للعُهَد"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            context.startActivity(Intent.createChooser(intent, "إرسال النسخة الاحتياطية بالبريد"))
        } catch (e: Exception) { message = "تعذر فتح تطبيق البريد أو المشاركة: " + (e.message ?: "خطأ غير معروف") }
    }

    Scaffold(topBar = { AppTopBar(title = "النسخ الاحتياطي والمزامنة", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { InformationCard {
                Text("Excel للعُهَد", style = MaterialTheme.typography.titleMedium)
                Text("تصدير واستيراد بيانات العُهَد دون المساس بدفتر الحسابات.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PrimaryButton("تصدير جميع العُهَد إلى Excel", { exportCustodyDirectly() }, Modifier.fillMaxWidth(), enabled = !busy)
                SecondaryButton("استيراد العُهَد من Excel", { importLauncher.launch(arrayOf(CustodyTwoSheetExcelDataManager.MIME_TYPE)) }, Modifier.fillMaxWidth(), enabled = !busy)
            } }
            item { InformationCard {
                Text("النسخ الاحتياطي", style = MaterialTheme.typography.titleMedium)
                Text("بيانات العُهَد ومرفقاتها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PrimaryButton("إنشاء نسخة احتياطية", { createCustodyBackupDirectly() }, Modifier.fillMaxWidth(), enabled = !busy)
                SecondaryButton("استعادة نسخة احتياطية", { restoreLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) }, Modifier.fillMaxWidth(), enabled = !busy)
            } }
            item { InformationCard {
                Text("المزامنة اليدوية", style = MaterialTheme.typography.titleMedium)
                Text(if (syncFolderUri == null) "اختر مجلدًا للمزامنة." else "تم اختيار مجلد للمزامنة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SecondaryButton("اختيار مجلد المزامنة", { syncFolderLauncher.launch(null) }, Modifier.fillMaxWidth(), enabled = !busy)
                SecondaryButton("مزامنة الآن", { syncNow() }, Modifier.fillMaxWidth(), enabled = !busy && syncFolderUri != null)
            } }
            item { InformationCard {
                Text("إرسال ومشاركة النسخة", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("البريد الإلكتروني (اختياري)") })
                SecondaryButton("إرسال النسخة الاحتياطية بالبريد", { sendBackupByEmail() }, Modifier.fillMaxWidth(), enabled = !busy && lastBackupUri != null)
                SecondaryButton("مشاركة النسخة الاحتياطية", { shareBackup() }, Modifier.fillMaxWidth(), enabled = !busy && lastBackupUri != null)
            } }
            if (custodies.isEmpty()) item { Text("لا توجد عُهَد نشطة.") }
            if (busy) item { CircularProgressIndicator() }
        }
    }

    pendingImport?.let { uri -> AlertDialog(onDismissRequest = { if (!busy) pendingImport = null }, title = { Text("تأكيد استيراد العُهَد") }, text = { Text("سيتم فحص ملف Excel ذي الورقة الواحدة الخاصة بالعُهَد ثم استيراد بياناتها فقط. لن تتأثر بيانات دفتر الحسابات.") }, confirmButton = { TextButton(enabled = !busy, onClick = { pendingImport = null; busy = true; scope.launch(Dispatchers.IO) { val r = runCatching { val p = CustodyTwoSheetExcelDataManager.previewImport(context, uri).getOrThrow(); check(p.isValid) { p.errors.joinToString("\n") }; CustodyAttachmentStore.ensureSchema(context); val snapshot = File.createTempFile("myaccounts-custody-import-snapshot-", ".myaccounts", context.cacheDir); val snapshotUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", snapshot); try { val snapshotResult = ScopedBackupManager.createBackup(context, snapshotUri, BackupScope.CUSTODY); if (snapshotResult.isFailure && snapshotResult.exceptionOrNull()?.message != "لم يتم العثور على أي بيانات لحفظها في النسخة الاحتياطية.") snapshotResult.getOrThrow(); try { CustodyTwoSheetExcelDataManager.import(context, uri).getOrThrow() } catch (failure: Throwable) { val rollback = ScopedBackupManager.restoreBackup(context, snapshotUri, BackupScope.CUSTODY); if (rollback.isFailure) throw IllegalStateException("فشل استيراد بيانات العُهَد وفشلت محاولة التراجع عن التغييرات: ${rollback.exceptionOrNull()?.message ?: "خطأ غير معروف"}", failure); throw failure } } finally { snapshot.delete() } }; message = r.fold({ "تم الاستيراد: ${it.custodiesAdded} عهدة، ${it.peopleAdded} أشخاص، ${it.accountsAdded} حسابات، ${it.transactionsAdded} عمليات." }, { "تعذر استيراد بيانات العُهَد: ${it.message ?: "ملف غير صالح"}" }); busy = false } }) { Text("استيراد") } }, dismissButton = { TextButton(enabled = !busy, onClick = { pendingImport = null }) { Text("إلغاء") } }) }
    pendingRestore?.let { uri -> AlertDialog(onDismissRequest = { if (!busy) pendingRestore = null }, title = { Text("تأكيد استعادة العُهَد") }, text = { Text("سيتم استبدال بيانات العُهَد الحالية بالبيانات الموجودة في النسخة المحددة، مع إعادة المرفقات التابعة لها. كما يدعم التطبيق النسخ القديمة الخاصة بالعُهَد عند الحاجة. لن يتم تعديل بيانات دفتر الحسابات.") }, confirmButton = { TextButton(enabled = !busy, onClick = { pendingRestore = null; busy = true; scope.launch(Dispatchers.IO) { CustodyAttachmentStore.ensureSchema(context); val result = CompatibleRestoreManager.restore(context, uri, BackupScope.CUSTODY); message = result.fold({ "تمت استعادة العُهَد والمرفقات بنجاح." }, { "تعذرت استعادة نسخة العُهَد: ${it.message ?: "الملف غير صالح"}" }); busy = false } }) { Text("استعادة") } }, dismissButton = { TextButton(enabled = !busy, onClick = { pendingRestore = null }) { Text("إلغاء") } }) }
    message?.let { t -> AlertDialog(onDismissRequest = { message = null }, text = { Text(t) }, confirmButton = { TextButton(onClick = { message = null }) { Text("موافق") } }) }
}

