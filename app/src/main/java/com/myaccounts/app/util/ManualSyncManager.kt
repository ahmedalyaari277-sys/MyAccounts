package com.myaccounts.app.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Manual sync is a safe, one-way snapshot operation: it writes the selected
 * scope to the chosen SAF folder. It intentionally does not merge or overwrite
 * database data on the device, so it cannot corrupt local financial records.
 */
object ManualSyncManager {
    private const val BACKUP_MIME_TYPE = "application/octet-stream"

    suspend fun syncToFolder(context: Context, treeUri: Uri, scope: BackupScope = BackupScope.ALL): Result<Uri> = runCatching {
        require(DocumentsContract.isTreeUri(treeUri)) { "مجلد المزامنة غير صالح." }
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val backupUri = DocumentsContract.createDocument(context.contentResolver, documentUri, BACKUP_MIME_TYPE, ScopedBackupManager.suggestedFileName(scope))
            ?: error("تعذر إنشاء ملف النسخة الاحتياطية داخل مجلد المزامنة.")
        val result = ScopedBackupManager.createBackup(context, backupUri, scope)
        if (result.isFailure) {
            runCatching { context.contentResolver.delete(backupUri, null, null) }
            result.getOrThrow()
        }
        backupUri
    }
}
