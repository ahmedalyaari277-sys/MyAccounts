package com.myaccounts.app.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.myaccounts.app.data.local.TransactionAttachmentEntity
import com.myaccounts.app.security.AppSecurityManager
import com.myaccounts.app.util.TransactionAttachmentStorage
import java.io.File

@Composable
fun TransactionAttachmentPicker(
    selectedAttachments: List<TransactionAttachmentStorage.SelectedAttachment>,
    onAttachmentsChanged: (List<TransactionAttachmentStorage.SelectedAttachment>) -> Unit
) {
    val context = LocalContext.current
    val security = remember(context) { AppSecurityManager(context.applicationContext) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var showCameraPermissionDialog by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        security.clearExternalActivityPending()
        val file = cameraFile
        cameraFile = null
        if (success && file != null && file.exists() && file.length() > 0L) {
            val current = selectedAttachments.toMutableList()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            current += TransactionAttachmentStorage.SelectedAttachment(
                uri = uri,
                fileName = "صورة_${System.currentTimeMillis()}.jpg",
                mimeType = "image/jpeg"
            )
            onAttachmentsChanged(current)
        } else {
            file?.delete()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            createAndLaunchCamera(
                context = context,
                onFileCreated = { file ->
                    cameraFile = file
                    try {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        security.markExternalActivityPending()
                        cameraLauncher.launch(uri)
                    } catch (_: Throwable) {
                        security.clearExternalActivityPending()
                        cameraFile = null
                        file.delete()
                        showCameraPermissionDialog = true
                    }
                },
                onFailure = { showCameraPermissionDialog = true }
            )
        } else {
            showCameraPermissionDialog = true
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        security.clearExternalActivityPending()
        if (uris.isNotEmpty()) {
            val current = selectedAttachments.toMutableList()
            uris.forEach { uri ->
                val fileName = queryDisplayName(context, uri)
                val mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
                if (current.none { it.uri == uri }) {
                    current += TransactionAttachmentStorage.SelectedAttachment(uri = uri, fileName = fileName, mimeType = mimeType)
                }
            }
            onAttachmentsChanged(current)
        }
    }

    if (showCameraPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showCameraPermissionDialog = false },
            title = { Text("صلاحية الكاميرا") },
            text = { Text("لا يمكن التقاط صورة دون السماح للتطبيق باستخدام الكاميرا. يمكنك السماح بها من إعدادات التطبيق.") },
            confirmButton = {
                TextButton(onClick = {
                    showCameraPermissionDialog = false
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${context.packageName}") })
                }) { Text("فتح الإعدادات") }
            },
            dismissButton = { TextButton(onClick = { showCameraPermissionDialog = false }) { Text("إلغاء") } }
        )
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        createAndLaunchCamera(
                            context = context,
                            onFileCreated = { file ->
                                cameraFile = file
                                try {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    security.markExternalActivityPending()
                                    cameraLauncher.launch(uri)
                                } catch (_: Throwable) {
                                    security.clearExternalActivityPending()
                                    cameraFile = null
                                    file.delete()
                                    showCameraPermissionDialog = true
                                }
                            },
                            onFailure = { showCameraPermissionDialog = true }
                        )
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier
                    .semantics { contentDescription = "الكاميرا" }
                    .padding(2.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }

            IconButton(
                onClick = {
                    security.markExternalActivityPending()
                    documentLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier
                    .semantics { contentDescription = if (selectedAttachments.isEmpty()) "إرفاق ملف" else "إرفاق ملف آخر" }
                    .padding(2.dp)
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }

            if (selectedAttachments.isNotEmpty()) {
                Text(
                    "${selectedAttachments.size} مرفق",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        if (selectedAttachments.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(items = selectedAttachments, key = { index, attachment -> "${attachment.uri}-$index" }) { index, attachment ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (attachment.mimeType.startsWith("image/")) Icons.Default.Image else Icons.Default.Description,
                                    contentDescription = null
                                )
                                Text(text = attachment.fileName, modifier = Modifier.padding(start = 8.dp), maxLines = 2)
                            }
                            IconButton(onClick = { onAttachmentsChanged(selectedAttachments.filterIndexed { i, _ -> i != index }) }) {
                                Icon(Icons.Default.Delete, contentDescription = "إزالة المرفق")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun createAndLaunchCamera(
    context: android.content.Context,
    onFileCreated: (File) -> Unit,
    onFailure: () -> Unit
) {
    try {
        val directory = File(context.cacheDir, "transaction_camera")
        if (!directory.exists() && !directory.mkdirs()) {
            onFailure()
            return
        }
        val file = File.createTempFile("camera_", ".jpg", directory)
        onFileCreated(file)
    } catch (_: Throwable) {
        onFailure()
    }
}

@Composable
fun TransactionAttachmentsDialog(
    transactionId: Long,
    attachments: List<TransactionAttachmentEntity>,
    onDismiss: () -> Unit,
    onDelete: (TransactionAttachmentEntity) -> Unit
) {
    val context = LocalContext.current
    val security = remember(context) { AppSecurityManager(context.applicationContext) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مرفقات العملية") },
        text = {
            Column {
                if (attachments.isEmpty()) {
                    Text("لا توجد مرفقات لهذه العملية.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(items = attachments, key = { _, attachment -> attachment.id }) { _, attachment ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(modifier = Modifier.weight(1f)) {
                                        Icon(if (attachment.mimeType.startsWith("image/")) Icons.Default.Image else Icons.Default.Description, contentDescription = null)
                                        Text(attachment.fileName, modifier = Modifier.padding(start = 8.dp), maxLines = 2)
                                    }
                                    IconButton(onClick = {
                                        try {
                                            val file = TransactionAttachmentStorage.fileFor(context, attachment)
                                            if (!file.exists()) {
                                                errorMessage = "الملف غير موجود داخل التطبيق."
                                            } else {
                                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, attachment.mimeType)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                try {
                                                    security.markExternalActivityPending()
                                                    context.startActivity(Intent.createChooser(intent, "فتح المرفق").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                                } catch (_: ActivityNotFoundException) {
                                                    security.clearExternalActivityPending()
                                                    errorMessage = "لا يوجد تطبيق قادر على فتح هذا الملف."
                                                }
                                            }
                                        } catch (_: Throwable) {
                                            security.clearExternalActivityPending()
                                            errorMessage = "تعذر فتح المرفق."
                                        }
                                    }) { Icon(Icons.Default.OpenInNew, contentDescription = "فتح المرفق") }
                                    IconButton(onClick = { onDelete(attachment) }) { Icon(Icons.Default.Delete, contentDescription = "حذف المرفق") }
                                }
                            }
                        }
                    }
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    return runCatching {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "مرفق"
}
