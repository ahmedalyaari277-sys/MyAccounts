package com.myaccounts.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.myaccounts.app.util.BackupScope
import com.myaccounts.app.util.CustodyBackupManager
import com.myaccounts.app.util.CustodyReportExporter
import com.myaccounts.app.util.CustodyTwoSheetExcelDataManager
import com.myaccounts.app.util.ManualSyncManager
import com.myaccounts.app.util.ReportShareUtil
import com.myaccounts.app.util.ScopedBackupManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val PDF_MIME = "application/pdf"
private const val BACKUP_PREFS = "myaccounts_backup_preferences"
private const val SYNC_FOLDER_URI = "sync_folder_uri"

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
    var syncFolderUri by remember { mutableStateOf(preferences.getString(SYNC_FOLDER_URI, null)?.let(Uri::parse)) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CustodyTwoSheetExcelDataManager.MIME_TYPE)) { uri ->
        if (uri != null) {
            busy = true
            scope.launch(Dispatchers.IO) {
                val r = CustodyTwoSheetExcelDataManager.exportActive(context, uri)
                message = r.fold({ "تم تصدير ${it.custodies} عهدة و${it.transactions} عملية إلى Excel. الملف يحتوي Sheet واحد فقط: بيانات العُهَد، ويشمل التصنيف داخل نفس الورقة." }, { "تعذر تصدير بيانات العُهَد: ${it.message ?: "خطأ غير معروف"}" })
                busy = false
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pendingImport = uri }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            busy = true
            scope.launch(Dispatchers.IO) {
                CustodyAttachmentStore.ensureSchema(context)
                val r = ScopedBackupManager.createBackup(context, uri, BackupScope.CUSTODY)
                if (r.isSuccess) lastBackupUri = uri
                message = r.fold({ "تم إنشاء النسخة الاحتياطية للعُهَد بنجاح، وتشمل بيانات العُهَد ومرفقاتها." }, { "تعذر إنشاء النسخة الاحتياطية للعُهَد: ${it.message ?: "خطأ غير معروف"}" })
                busy = false
            }
        }
    }
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

    Scaffold(topBar = { TopAppBar(title = { Text("النسخ الاحتياطي و الاستعادة") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "رجوع") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("النسخ الاحتياطي و الاستعادة", style = MaterialTheme.typography.titleLarge) }
            item { Text("هذه الشاشة خاصة بالعُهَد فقط. لا تتعامل مع بيانات دفتر الحسابات، والنسخة الاحتياطية تشمل مرفقات العُهَد.") }
            item { Button(enabled = !busy, onClick = { exportLauncher.launch(CustodyTwoSheetExcelDataManager.SUGGESTED_FILE_NAME) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, null); Text("تصدير جميع العُهَد إلى Excel") } }
            item { OutlinedButton(enabled = !busy, onClick = { importLauncher.launch(arrayOf(CustodyTwoSheetExcelDataManager.MIME_TYPE)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileUpload, null); Text("استيراد العُهَد من Excel") } }
            item { Button(enabled = !busy, onClick = { backupLauncher.launch(ScopedBackupManager.suggestedFileName(BackupScope.CUSTODY)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Backup, null); Text("نسخ احتياطي للعُهَد فقط") } }
            item { OutlinedButton(enabled = !busy, onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Restore, null); Text("استعادة نسخة العُهَد") } }
            item {
                Text("المزامنة اليدوية للعُهَد", style = MaterialTheme.typography.titleMedium)
                Text(if (syncFolderUri == null) "اختر مجلدًا لحفظ نسخة مزامنة للعُهَد." else "تم اختيار مجلد مزامنة للعُهَد.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(enabled = !busy, onClick = { syncFolderLauncher.launch(null) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Folder, null); Text("اختيار مجلد المزامنة") }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(enabled = !busy && syncFolderUri != null, onClick = { syncNow() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Sync, null); Text("مزامنة العُهَد الآن") }
            }
            item { OutlinedButton(enabled = !busy && lastBackupUri != null, onClick = { val uri = lastBackupUri ?: return@OutlinedButton; try { val intent = Intent(Intent.ACTION_SEND).apply { type = "application/octet-stream"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية للعُهَد"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; context.startActivity(Intent.createChooser(intent, "مشاركة نسخة العُهَد")) } catch (e: Exception) { message = "تعذرت مشاركة نسخة العُهَد: ${e.message ?: "خطأ غير معروف"}" } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null); Text("مشاركة آخر نسخة للعُهَد") } }
            item { Text("التقارير والمشاركة", style = MaterialTheme.typography.titleMedium) }
            items(custodies, key = { it.id }) { c -> CustodyTransferCard(c, vm, { message = it }, { busy = it }) }
            if (custodies.isEmpty()) item { Text("لا توجد عُهَد نشطة.") }
            if (busy) item { CircularProgressIndicator() }
        }
    }

    pendingImport?.let { uri -> AlertDialog(onDismissRequest = { if (!busy) pendingImport = null }, title = { Text("تأكيد استيراد العُهَد") }, text = { Text("سيتم فحص ملف Excel ذي الورقة الواحدة الخاصة بالعُهَد ثم استيراد بياناتها فقط. لن تتأثر بيانات دفتر الحسابات.") }, confirmButton = { TextButton(enabled = !busy, onClick = { pendingImport = null; busy = true; scope.launch(Dispatchers.IO) { val r = runCatching { val p = CustodyTwoSheetExcelDataManager.previewImport(context, uri).getOrThrow(); check(p.isValid) { p.errors.joinToString("\n") }; CustodyAttachmentStore.ensureSchema(context); val snapshot = File.createTempFile("myaccounts-custody-import-snapshot-", ".myaccounts", context.cacheDir); val snapshotUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", snapshot); try { val snapshotResult = ScopedBackupManager.createBackup(context, snapshotUri, BackupScope.CUSTODY); if (snapshotResult.isFailure && snapshotResult.exceptionOrNull()?.message != "لم يتم العثور على أي بيانات لحفظها في النسخة الاحتياطية.") snapshotResult.getOrThrow(); try { CustodyTwoSheetExcelDataManager.import(context, uri).getOrThrow() } catch (failure: Throwable) { val rollback = ScopedBackupManager.restoreBackup(context, snapshotUri, BackupScope.CUSTODY); if (rollback.isFailure) throw IllegalStateException("فشل استيراد بيانات العُهَد وفشلت محاولة التراجع عن التغييرات: ${rollback.exceptionOrNull()?.message ?: "خطأ غير معروف"}", failure); throw failure } } finally { snapshot.delete() } }; message = r.fold({ "تم الاستيراد: ${it.custodiesAdded} عهدة، ${it.peopleAdded} أشخاص، ${it.accountsAdded} حسابات، ${it.transactionsAdded} عمليات." }, { "تعذر استيراد بيانات العُهَد: ${it.message ?: "ملف غير صالح"}" }); busy = false } }) { Text("استيراد") } }, dismissButton = { TextButton(enabled = !busy, onClick = { pendingImport = null }) { Text("إلغاء") } }) }
    pendingRestore?.let { uri -> AlertDialog(onDismissRequest = { if (!busy) pendingRestore = null }, title = { Text("تأكيد استعادة العُهَد") }, text = { Text("سيتم استبدال بيانات العُهَد الحالية بالبيانات الموجودة في النسخة المحددة، مع إعادة المرفقات التابعة لها. كما يدعم التطبيق النسخ القديمة الخاصة بالعُهَد عند الحاجة. لن يتم تعديل بيانات دفتر الحسابات.") }, confirmButton = { TextButton(enabled = !busy, onClick = { pendingRestore = null; busy = true; scope.launch(Dispatchers.IO) { CustodyAttachmentStore.ensureSchema(context); val scoped = ScopedBackupManager.restoreBackup(context, uri, BackupScope.CUSTODY); val result = if (scoped.isSuccess) scoped else CustodyBackupManager.restoreBackup(context, uri).map { Unit }; message = result.fold({ "تمت استعادة العُهَد والمرفقات بنجاح." }, { "تعذرت استعادة نسخة العُهَد: ${it.message ?: "الملف غير صالح"}" }); busy = false } }) { Text("استعادة") } }, dismissButton = { TextButton(enabled = !busy, onClick = { pendingRestore = null }) { Text("إلغاء") } }) }
    message?.let { t -> AlertDialog(onDismissRequest = { message = null }, text = { Text(t) }, confirmButton = { TextButton(onClick = { message = null }) { Text("موافق") } }) }
}

@Composable
private fun CustodyTransferCard(custody: CustodyEntity, vm: CustodyViewModel, onMessage: (String) -> Unit, onBusy: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tx by vm.transactions(custody.id).collectAsState(initial = emptyList())
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(custody.name, style = MaterialTheme.typography.titleMedium)
        Text("الجهة: ${custody.organizationName}")
        Button(enabled = tx.isNotEmpty(), onClick = { onBusy(true); scope.launch(Dispatchers.IO) { val r = CustodyReportExporter.exportExcel(context, custody, tx, "ALL"); onMessage(r.fold({ "تم إنشاء Excel للعهدة ${custody.name}." }, { "تعذر إنشاء Excel: ${it.message ?: "خطأ غير معروف"}" })); onBusy(false) } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, null); Text("تصدير Excel لهذه العهدة") }
        OutlinedButton(enabled = tx.isNotEmpty(), onClick = { onBusy(true); scope.launch(Dispatchers.IO) { val r = CustodyReportExporter.exportPdf(context, custody, tx, "ALL"); onMessage(r.fold({ "تم إنشاء PDF للعهدة ${custody.name}." }, { "تعذر إنشاء PDF: ${it.message ?: "خطأ غير معروف"}" })); onBusy(false) } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PictureAsPdf, null); Text("تصدير PDF للعهدة ${custody.name}" ) }
        OutlinedButton(enabled = tx.isNotEmpty(), onClick = { onBusy(true); scope.launch(Dispatchers.IO) { val r = ReportShareUtil.shareGeneratedReport(context, "MyAccounts_تقرير_عهدة", PDF_MIME, true) { CustodyReportExporter.exportPdf(context, custody, tx, "ALL") }; onMessage(r.fold({ "تم فتح خيارات مشاركة تقرير ${custody.name}." }, { "تعذرت مشاركة التقرير: ${it.message ?: "خطأ غير معروف"}" })); onBusy(false) } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null); Text("إنشاء التقرير ومشاركته") }
    }
}
