package com.myaccounts.app.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val result = DatabaseBackupManager.createBackup(context, uri)
                busy = false
                message = result.fold(
                    onSuccess = { "تم إنشاء النسخة الاحتياطية الكاملة بنجاح، وتشمل المرفقات." },
                    onFailure = { "تعذر إنشاء النسخة الاحتياطية: ${it.message ?: "خطأ غير معروف"}" }
                )
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingRestoreUri = uri
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
        }
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
