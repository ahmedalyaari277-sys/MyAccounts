package com.myaccounts.app.util

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.myaccounts.app.data.custody.CustodyTransactionAttachmentEntity
import java.io.File
import java.io.IOException
import java.util.UUID

object CustodyAttachmentStorage {
    data class Selected(val uri: Uri, val fileName: String, val mimeType: String)

    fun saveAttachments(context: Context, transactionId: Long, selected: List<Selected>): List<CustodyTransactionAttachmentEntity> {
        if (selected.isEmpty()) return emptyList()
        val dir = File(context.filesDir, "custody_transaction_attachments/$transactionId")
        if (!dir.exists() && !dir.mkdirs()) throw IOException("تعذر إنشاء مجلد مرفقات العهدة")
        val saved = mutableListOf<CustodyTransactionAttachmentEntity>()
        try {
            selected.forEach { item ->
                val safe = item.fileName.trim().ifBlank { "مرفق" }.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(180)
                val ext = safe.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".$it" }
                    ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(item.mimeType)?.let { ".${it.lowercase()}" }.orEmpty()
                val stored = UUID.randomUUID().toString() + ext.lowercase()
                val dest = File(dir, stored)
                context.contentResolver.openInputStream(item.uri).use { input ->
                    requireNotNull(input) { "تعذر قراءة المرفق: $safe" }
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                saved += CustodyTransactionAttachmentEntity(
                    transactionId = transactionId,
                    fileName = safe,
                    mimeType = item.mimeType.ifBlank { "application/octet-stream" },
                    relativePath = "custody_transaction_attachments/$transactionId/$stored",
                    sizeBytes = dest.length()
                )
            }
            return saved
        } catch (e: Throwable) {
            saved.forEach { deleteFile(context, it) }
            throw e
        }
    }

    fun fileFor(context: Context, attachment: CustodyTransactionAttachmentEntity): File = File(context.filesDir, attachment.relativePath)

    fun deleteFile(context: Context, attachment: CustodyTransactionAttachmentEntity) {
        fileFor(context, attachment).delete()
        File(context.filesDir, attachment.relativePath).parentFile?.let { if (it.isDirectory && it.list().isNullOrEmpty()) it.delete() }
    }

    fun deleteTransactionFiles(context: Context, transactionId: Long, attachments: List<CustodyTransactionAttachmentEntity>) {
        attachments.forEach { deleteFile(context, it) }
        File(context.filesDir, "custody_transaction_attachments/$transactionId").deleteRecursively()
    }
}
