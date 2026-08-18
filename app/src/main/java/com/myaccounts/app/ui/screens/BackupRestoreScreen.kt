package com.myaccounts.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.myaccounts.app.util.DatabaseBackupManager
import kotlinx.coroutines.launch

private const val BACKUP_PREFS = "myaccounts_backup_preferences"
private const val LAST_BACKUP_URI = "last_backup_uri"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var lastBackupUri by remember {
        mutableStateOf(
            context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE)
                .getString(LAST_BACKUP_URI, null)
                ?.let(Uri::parse)
        )
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val result = DatabaseBackupManager.createBackup(context, uri)
                busy = false
                result.fold(
                    onSuccess = {
                        lastBackupUri = uri
                        context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putString(LAST_BACKUP_URI, uri.toString())
                            .apply()
                        message = "تم إنشاء النسخة الاحتياطية الكاملة بنجاح، وتشمل البيانات والمرفقات. يمكنك الآن مشاركتها يدويًا مع أي تطبيق أو بريد إلكتروني."
                    },
                    onFailure = { message = "تعذر إنشاء النسخة الاحتياطية: ${it.message ?: "خطأ غير معروف"}" }
                )
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingRestoreUri = uri
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

    LaunchedEffect(Unit) {
        if (lastBackupUri != null) {
            snackbarHostState.showSnackbar("يمكنك مشاركة آخر نسخة احتياطية يدويًا من زر المشاركة.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("النسخ الاحتياطي والاستعادة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "النسخة الاحتياطية الكاملة تشمل بيانات الأشخاص والحسابات والعمليات والمرفقات الفعلية مثل الصور وملفات PDF والمستندات.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    createDocumentLauncher.launch(DatabaseBackupManager.suggestedFileName())
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Backup, null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("إنشاء نسخة احتياطية")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { shareBackup() },
                enabled = !busy && lastBackupUri != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("مشاركة النسخة الاحتياطية")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    openDocumentLauncher.launch(arrayOf("*/*"))
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Restore, null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("استعادة نسخة احتياطية")
            }

            if (busy) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator()
            }
        }
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("تأكيد الاستعادة") },
            text = {
                Text("سيتم استبدال البيانات الحالية بالبيانات الموجودة في النسخة الاحتياطية، بما فيها المرفقات. هل تريد المتابعة؟")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestoreUri = null
                        busy = true
                        scope.launch {
                            val result = DatabaseBackupManager.restoreBackup(context, uri)
                            busy = false
                            message = result.fold(
                                onSuccess = { "تمت استعادة النسخة الاحتياطية والمرفقات بنجاح." },
                                onFailure = { "تعذر استعادة النسخة الاحتياطية: ${it.message ?: "الملف غير صالح"}" }
                            )
                        }
                    }
                ) { Text("استعادة") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("إلغاء") }
            }
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { message = null }) { Text("موافق") }
            }
        )
    }
}
