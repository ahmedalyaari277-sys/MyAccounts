package com.myaccounts.app.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.myaccounts.app.data.local.TransactionAttachmentEntity
import com.myaccounts.app.security.AppSecurityManager
import com.myaccounts.app.util.TransactionAttachmentStorage
import java.io.File
import java.util.UUID

@Composable
fun TransactionAttachmentPicker(
    selectedAttachments: List<TransactionAttachmentStorage.SelectedAttachment>,
    onAttachmentsChanged: (List<TransactionAttachmentStorage.SelectedAttachment>) -> Unit
) {
    val context = LocalContext.current
    val security = remember(context) { AppSecurityManager(context.applicationContext) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        security.clearExternalActivityPending()
        if (uris.isNotEmpty()) {
            val current = selectedAttachments.toMutableList()
            uris.forEach { uri ->
                val fileName = queryDisplayName(context, uri)
                val mimeType = context.contentResolver.getType(uri).orEmpty()
                    .ifBlank { "application/octet-stream" }
                if (current.none { it.uri == uri }) {
                    current += TransactionAttachmentStorage.SelectedAttachment(uri, fileName, mimeType)
                }
            }
            onAttachmentsChanged(current)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        security.clearExternalActivityPending()
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            val current = selectedAttachments.toMutableList()
            current += TransactionAttachmentStorage.SelectedAttachment(
                uri = uri,
                fileName = "صورة_${System.currentTimeMillis()}.jpg",
                mimeType = "image/jpeg"
            )
            onAttachmentsChanged(current)
        } else if (uri != null) {
            deleteTemporaryCameraFile(context, uri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            security.clearExternalActivityPending()
            cameraError = "لم يتم منح إذن الكاميرا."
        } else {
            val uri = createTemporaryCameraUri(context)
            if (uri == null) {
                security.clearExternalActivityPending()
                cameraError = "تعذر تجهيز الكاميرا."
            } else {
                pendingCameraUri = uri
                security.markExternalActivityPending()
                cameraLauncher.launch(uri)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = {
                    security.markExternalActivityPending()
                    documentLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Text("الملفات", modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(
                onClick = {
                    cameraError = null
                    security.markExternalActivityPending()
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Text("الكاميرا", modifier = Modifier.padding(start = 6.dp))
            }
        }

        if (selectedAttachments.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    items = selectedAttachments,
                    key = { index, attachment -> "${attachment.uri}-$index" }
                ) { index, attachment ->
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
                                Text(attachment.fileName, modifier = Modifier.padding(start = 8.dp), maxLines = 2)
                            }
                            IconButton(onClick = {
                                onAttachmentsChanged(selectedAttachments.filterIndexed { i, _ -> i != index })
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "إزالة المرفق")
                            }
                        }
                    }
                }
            }
        }

        cameraError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = "يمكن إرفاق ملفات أو تصوير مستند مباشرة. تحفظ المرفقات داخل التطبيق ولا تحتاج إلى إنترنت.",
            style = MaterialTheme.typography.bodySmall
        )
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
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(modifier = Modifier.weight(1f)) {
                                        Icon(
                                            imageVector = if (attachment.mimeType.startsWith("image/")) Icons.Default.Image else Icons.Default.Description,
                                            contentDescription = null
                                        )
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
                                    }) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = "فتح المرفق")
                                    }
                                    IconButton(onClick = { onDelete(attachment) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف المرفق")
                                    }
                                }
                            }
                        }
                    }
                }
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

private fun createTemporaryCameraUri(context: android.content.Context): Uri? = runCatching {
    val directory = File(context.cacheDir, "camera_attachments")
    if (!directory.exists()) directory.mkdirs()
    val file = File(directory, "camera_${UUID.randomUUID()}.jpg")
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.getOrNull()

private fun deleteTemporaryCameraFile(context: android.content.Context, uri: Uri) {
    runCatching {
        val file = File(context.cacheDir, uri.lastPathSegment ?: return)
        file.delete()
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    return runCatching {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "مرفق"
}
