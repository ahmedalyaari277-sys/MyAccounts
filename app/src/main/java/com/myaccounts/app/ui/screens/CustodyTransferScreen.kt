package com.myaccounts.app.ui.screens

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
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import com.myaccounts.app.util.CustodyBackupManager
import com.myaccounts.app.util.CustodyExcelDataManager
import com.myaccounts.app.util.CustodyReportExporter
import com.myaccounts.app.util.ReportShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val PDF_MIME = "application/pdf"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyTransferScreen(vm: CustodyViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val custodies by vm.custodies.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var lastBackupUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(CustodyExcelDataManager.MIME_TYPE)
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch(Dispatchers.IO) {
                val r = CustodyExcelDataManager.exportActive(context, uri)
                message = r.fold(
                    { "تم تصدير ${it.custodies} عهدة و${it.transactions} عملية إلى Excel." },
                    { "تعذر تصدير بيانات العُهَد: ${it.message ?: "خطأ غير معروف"}" }
                )
                busy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(CustodyBackupManager.MIME_TYPE)
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch(Dispatchers.IO) {
                val r = CustodyBackupManager.createBackup(context, uri)
                if (r.isSuccess) lastBackupUri = uri
                message = r.fold(
                    { "تم إنشاء نسخة احتياطية للعُهَد فقط: ${it.custodies} عهدة، ${it.people} أشخاص، ${it.transactions} عمليات، ${it.attachments} مرفقات." },
                    { "تعذر إنشاء النسخة الاحتياطية للعُهَد: ${it.message ?: "خطأ غير معروف"}" }
                )
                busy = false
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("نقل بيانات العُهَد") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text("نقل بيانات العُهَد", style = MaterialTheme.typography.titleLarge) }
            item { Text("هذه الوظائف مستقلة عن دفتر الحسابات وتتعامل مع بيانات العُهَد فقط.") }
            item {
                Button(
                    enabled = !busy,
                    onClick = { exportLauncher.launch(CustodyExcelDataManager.SUGGESTED_FILE_NAME) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileDownload, null)
                    Text("تصدير جميع العُهَد إلى Excel")
                }
            }
            item {
                OutlinedButton(
                    enabled = !busy,
                    onClick = { importLauncher.launch(arrayOf(CustodyExcelDataManager.MIME_TYPE)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileUpload, null)
                    Text("استيراد العُهَد من Excel")
                }
            }
            item {
                Button(
                    enabled = !busy,
                    onClick = { backupLauncher.launch(CustodyBackupManager.SUGGESTED_FILE_NAME) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Backup, null)
                    Text("نسخ احتياطي للعُهَد فقط")
                }
            }
            item {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        restoreLauncher.launch(
                            arrayOf(CustodyBackupManager.MIME_TYPE, "application/octet-stream", "application/zip")
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Restore, null)
                    Text("استعادة نسخة العُهَد")
                }
            }
            item {
                OutlinedButton(
                    enabled = !busy && lastBackupUri != null,
                    onClick = {
                        val uri = lastBackupUri ?: return@OutlinedButton
                        try {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = CustodyBackupManager.MIME_TYPE
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية للعُهَد")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "مشاركة نسخة العُهَد"))
                        } catch (e: Exception) {
                            message = "تعذرت مشاركة نسخة العُهَد: ${e.message ?: "خطأ غير معروف"}"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, null)
                    Text("مشاركة آخر نسخة للعُهَد")
                }
            }
            item { Text("التقارير والمشاركة", style = MaterialTheme.typography.titleMedium) }
            items(custodies, key = { it.id }) { c ->
                CustodyTransferCard(c, vm, { message = it }, { busy = it })
            }
            if (custodies.isEmpty()) item { Text("لا توجد عُهَد نشطة.") }
            if (busy) item { CircularProgressIndicator() }
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingImport = null },
            title = { Text("تأكيد استيراد العُهَد") },
            text = { Text("سيتم فحص ملف Excel ثم استيراد بيانات العُهَد الصالحة فقط. لن تتأثر بيانات دفتر الحسابات.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    pendingImport = null
                    busy = true
                    scope.launch(Dispatchers.IO) {
                        val r = runCatching {
                            val p = CustodyExcelDataManager.previewImport(context, uri).getOrThrow()
                            check(p.isValid) { p.errors.joinToString("\n") }
                            CustodyExcelDataManager.import(context, uri).getOrThrow()
                        }
                        message = r.fold(
                            { "تم الاستيراد: ${it.custodiesAdded} عهدة، ${it.peopleAdded} أشخاص، ${it.accountsAdded} حسابات، ${it.transactionsAdded} عمليات." },
                            { "تعذر استيراد بيانات العُهَد: ${it.message ?: "ملف غير صالح"}" }
                        )
                        busy = false
                    }
                }) { Text("استيراد") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pendingImport = null }) { Text("إلغاء") }
            }
        )
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingRestore = null },
            title = { Text("تأكيد استعادة العُهَد") },
            text = { Text("سيتم استيراد بيانات العُهَد من النسخة الاحتياطية، مع إعادة المرفقات إن وجدت. لن يتم استبدال أو تعديل بيانات دفتر الحسابات.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    pendingRestore = null
                    busy = true
                    scope.launch(Dispatchers.IO) {
                        val r = CustodyBackupManager.restoreBackup(context, uri)
                        message = r.fold(
                            { "تمت استعادة العُهَد: ${it.custodies} عهدة، ${it.people} أشخاص، ${it.transactions} عمليات، ${it.attachments} مرفقات." },
                            { "تعذرت استعادة نسخة العُهَد: ${it.message ?: "الملف غير صالح"}" }
                        )
                        busy = false
                    }
                }) { Text("استعادة") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pendingRestore = null }) { Text("إلغاء") }
            }
        )
    }

    message?.let { t ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(t) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("موافق") } }
        )
    }
}

@Composable
private fun CustodyTransferCard(
    custody: CustodyEntity,
    vm: CustodyViewModel,
    onMessage: (String) -> Unit,
    onBusy: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tx by vm.transactions(custody.id).collectAsState(initial = emptyList())

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(custody.name, style = MaterialTheme.typography.titleMedium)
        Text("الجهة: ${custody.organizationName}")
        Button(
            enabled = tx.isNotEmpty(),
            onClick = {
                onBusy(true)
                scope.launch(Dispatchers.IO) {
                    val r = CustodyReportExporter.exportExcel(context, custody, tx, "ALL")
                    onMessage(r.fold(
                        { "تم إنشاء Excel للعهدة ${custody.name}." },
                        { "تعذر إنشاء Excel: ${it.message ?: "خطأ غير معروف"}" }
                    ))
                    onBusy(false)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileDownload, null)
            Text("تصدير Excel لهذه العهدة")
        }
        OutlinedButton(
            enabled = tx.isNotEmpty(),
            onClick = {
                onBusy(true)
                scope.launch(Dispatchers.IO) {
                    val r = CustodyReportExporter.exportPdf(context, custody, tx, "ALL")
                    onMessage(r.fold(
                        { "تم إنشاء PDF للعهدة ${custody.name}." },
                        { "تعذر إنشاء PDF: ${it.message ?: "خطأ غير معروف"}" }
                    ))
                    onBusy(false)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, null)
            Text("تصدير PDF لهذه العهدة")
        }
        OutlinedButton(
            enabled = tx.isNotEmpty(),
            onClick = {
                onBusy(true)
                scope.launch(Dispatchers.IO) {
                    val r = ReportShareUtil.shareGeneratedReport(
                        context,
                        "MyAccounts_تقرير_عهدة",
                        PDF_MIME,
                        true
                    ) { CustodyReportExporter.exportPdf(context, custody, tx, "ALL") }
                    onMessage(r.fold(
                        { "تم فتح خيارات مشاركة تقرير ${custody.name}." },
                        { "تعذرت مشاركة التقرير: ${it.message ?: "خطأ غير معروف"}" }
                    ))
                    onBusy(false)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Share, null)
            Text("إنشاء التقرير ومشاركته")
        }
    }
}
