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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.myaccounts.app.security.AppSecurityManager
import com.myaccounts.app.util.DatabaseBackupManager
import com.myaccounts.app.util.ManualSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val BACKUP_PREFS = "myaccounts_backup_preferences"
private const val LAST_BACKUP_URI = "last_backup_uri"
private const val BACKUP_EMAIL = "backup_email"
private const val SYNC_FOLDER_URI = "sync_folder_uri"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val security = remember { AppSecurityManager(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val preferences = remember { context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf(preferences.getString(BACKUP_EMAIL, "") ?: "") }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var lastBackupUri by remember { mutableStateOf(preferences.getString(LAST_BACKUP_URI, null)?.let(Uri::parse)) }
    var syncFolderUri by remember { mutableStateOf(preferences.getString(SYNC_FOLDER_URI, null)?.let(Uri::parse)) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch(Dispatchers.IO) {
                val result = DatabaseBackupManager.createBackup(context, uri)
                busy = false
                result.fold(
                    onSuccess = {
                        lastBackupUri = uri
                        preferences.edit().putString(LAST_BACKUP_URI, uri.toString()).apply()
                        message = "تم إنشاء النسخة الاحتياطية الكاملة بنجاح، وتشمل البيانات والمرفقات."
                    },
                    onFailure = { message = "تعذر إنشاء النسخة الاحتياطية: ${it.message ?: "خطأ غير معروف"}" }
                )
            }
        }
    }

    val syncFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                syncFolderUri = uri
                preferences.edit().putString(SYNC_FOLDER_URI, uri.toString()).apply()
                message = "تم حفظ مجلد المزامنة. يمكنك اختيار مجلد داخل Google Drive ثم الضغط على مزامنة الآن."
            } catch (exception: Exception) {
                message = "تعذر حفظ صلاحية مجلد المزامنة: ${exception.message ?: "خطأ غير معروف"}"
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    fun syncNow() {
        val folderUri = syncFolderUri
        if (folderUri == null) {
            message = "اختر مجلد المزامنة أولاً. يمكنك اختيار مجلد داخل Google Drive أو أي مساحة تخزين متاحة."
            return
        }
        busy = true
        scope.launch(Dispatchers.IO) {
            val result = ManualSyncManager.syncToFolder(context, folderUri)
            busy = false
            result.fold(
                onSuccess = { uri ->
                    lastBackupUri = uri
                    preferences.edit().putString(LAST_BACKUP_URI, uri.toString()).apply()
                    message = "تمت المزامنة اليدوية بنجاح وإنشاء نسخة جديدة داخل مجلد المزامنة."
                },
                onFailure = { error -> message = "تعذرت المزامنة: ${error.message ?: "خطأ غير معروف"}" }
            )
        }
    }

    fun shareBackup() {
        val uri = lastBackupUri
        if (uri == null) {
            message = "لا توجد نسخة احتياطية محفوظة للمشاركة. أنشئ نسخة احتياطية أولاً."
            return
        }
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية من دفتر الحسابات")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة النسخة الاحتياطية").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (exception: Exception) {
            message = "تعذر فتح خيارات المشاركة: ${exception.message ?: "خطأ غير معروف"}"
        }
    }

    fun sendBackupByEmail() {
        val uri = lastBackupUri
        val normalizedEmail = email.trim()
        if (uri == null) {
            message = "أنشئ نسخة احتياطية أولاً قبل إرسالها إلى البريد الإلكتروني."
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
            message = "أدخل عنوان بريد إلكتروني صحيحًا."
            return
        }
        preferences.edit().putString(BACKUP_EMAIL, normalizedEmail).apply()
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(normalizedEmail))
                putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية من دفتر الحسابات")
                putExtra(Intent.EXTRA_TEXT, "مرفق نسخة احتياطية من تطبيق دفتر الحسابات.")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "إرسال النسخة الاحتياطية بالبريد").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (exception: Exception) {
            message = "تعذر فتح تطبيق البريد أو المشاركة: ${exception.message ?: "خطأ غير معروف"}"
        }
    }

    LaunchedEffect(Unit) {
        if (lastBackupUri != null) snackbarHostState.showSnackbar("يمكنك إنشاء نسخة جديدة أو مزامنتها يدويًا أو إرسالها بالبريد.")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("النسخ الاحتياطي والمزامنة") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "النسخة الاحتياطية الكاملة تشمل بيانات الأشخاص والحسابات والعمليات والمرفقات الفعلية مثل الصور وملفات PDF والمستندات.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            ExcelTransferControls()
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("البريد الإلكتروني (اختياري)") },
                placeholder = { Text("example@email.com") },
                supportingText = { Text("يستخدم فقط عند الإرسال اليدوي للنسخة الاحتياطية") }
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { createDocumentLauncher.launch(DatabaseBackupManager.suggestedFileName()) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Backup, null); Spacer(Modifier.padding(horizontal = 4.dp)); Text("إنشاء نسخة احتياطية")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { syncNow() }, enabled = !busy && syncFolderUri != null, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CloudUpload, null); Spacer(Modifier.padding(horizontal = 4.dp)); Text("مزامنة الآن")
            }
            OutlinedButton(onClick = { syncFolderLauncher.launch(null) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Folder, null); Spacer(Modifier.padding(horizontal = 4.dp)); Text(if (syncFolderUri == null) "اختيار مجلد المزامنة" else "تغيير مجلد المزامنة")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { sendBackupByEmail() }, enabled = !busy && lastBackupUri != null, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Email, null); Spacer(Modifier.padding(horizontal = 4.dp)); Text("إرسال النسخة الاحتياطية بالبريد")
            }
            OutlinedButton(onClick = { shareBackup() }, enabled = !busy && lastBackupUri != null, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share, null); Spacer(Modifier.padding(horizontal = 4.dp)); Text("مشاركة النسخة الاحتياطية")
            }
            OutlinedButton(onClick = { security.markExternalActivityPending(); openDocumentLauncher.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Restore, null); Spacer(Modifier.padding(horizontal = 4.dp)); Text("استعادة نسخة احتياطية")
            }
            if (busy) { Spacer(Modifier.height(18.dp)); CircularProgressIndicator() }
        }
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("تأكيد الاستعادة") },
            text = { Text("سيتم استبدال البيانات الحالية بالبيانات الموجودة في النسخة الاحتياطية، بما فيها المرفقات. هل تريد المتابعة؟") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri = null
                    busy = true
                    scope.launch(Dispatchers.IO) {
                        val result = DatabaseBackupManager.restoreBackup(context, uri)
                        busy = false
                        message = result.fold(
                            onSuccess = { "تمت استعادة النسخة الاحتياطية والمرفقات بنجاح." },
                            onFailure = { "تعذر استعادة النسخة الاحتياطية: ${it.message ?: "الملف غير صالح"}" }
                        )
                    }
                }) { Text("استعادة") }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text("إلغاء") } }
        )
    }

    message?.let { text ->
        AlertDialog(onDismissRequest = { message = null }, text = { Text(text) }, confirmButton = { TextButton(onClick = { message = null }) { Text("موافق") } })
    }
}
