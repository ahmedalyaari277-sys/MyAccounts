package com.myaccounts.app.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

object ManualSyncManager {
    private const val BACKUP_MIME_TYPE = "application/octet-stream"

    suspend fun syncToFolder(context: Context, treeUri: Uri): Result<Uri> = runCatching {
        require(DocumentsContract.isTreeUri(treeUri)) { "مجلد المزامنة غير صالح." }

        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )

        val backupUri = DocumentsContract.createDocument(
            context.contentResolver,
            documentUri,
            BACKUP_MIME_TYPE,
            DatabaseBackupManager.suggestedFileName()
        ) ?: error("تعذر إنشاء ملف النسخة الاحتياطية داخل مجلد المزامنة.")

        DatabaseBackupManager.createBackup(context, backupUri).getOrThrow()
        backupUri
    }
}
