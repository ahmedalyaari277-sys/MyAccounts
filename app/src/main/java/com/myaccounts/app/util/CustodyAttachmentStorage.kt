package com.myaccounts.app.util

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.myaccounts.app.data.custody.CustodyTransactionAttachmentEntity
import java.io.File
import java.util.UUID

object CustodyAttachmentStorage {
    data class Selected(val uri: Uri, val fileName: String, val mimeType: String)

    fun saveAttachments(context: Context, transactionId: Long, selected: List<Selected>): List<CustodyTransactionAttachmentEntity> {
        if (selected.isEmpty()) return emptyList()
        val dir = File(context.filesDir, "custody_transaction_attachments/$transactionId")
        require(dir.exists() || dir.mkdirs()) { "تعذر إنشاء مجلد مرفقات العهدة" }
        val saved = mutableListOf<CustodyTransactionAttachmentEntity>()
        try {
            selected.forEach { item ->
                val original = item.fileName.trim().ifBlank { "مرفق" }
                val safe = original.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(180)
                val ext = safe.substringAfterLast('.', "").takeIf { it.isNotBlank() }
                    ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(item.mimeType).orEmpty()
                val storedName = UUID.randomUUID().toString() + if (ext.isBlank()) "" else ".${ext.lowercase()}"
                val destination = File(dir, storedName)
                context.contentResolver.openInputStream(item.uri).use { input ->
                    requireNotNull(input) { "تعذر قراءة المرفق: $safe" }
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
                saved += CustodyTransactionAttachmentEntity(
                    transactionId = transactionId,
                    fileName = safe,
                    mimeType = item.mimeType.ifBlank { "application/octet-stream" },
                    relativePath = "custody_transaction_attachments/$transactionId/$storedName",
                    sizeBytes = destination.length()
                )
            }
            return saved
        } catch (t: Throwable) {
            saved.forEach { deleteFile(context, it) }
            throw t
        }
    }

    fun fileFor(context: Context, attachment: CustodyTransactionAttachmentEntity): File = File(context.filesDir, attachment.relativePath)

    fun deleteFile(context: Context, attachment: CustodyTransactionAttachmentEntity) {
        val file = fileFor(context, attachment)
        file.delete()
        file.parentFile?.let { if (it.isDirectory && it.list().isNullOrEmpty()) it.delete() }
    }

    fun deleteTransactionFiles(context: Context, transactionId: Long, attachments: List<CustodyTransactionAttachmentEntity>) {
        attachments.forEach { deleteFile(context, it) }
        File(context.filesDir, "custody_transaction_attachments/$transactionId").deleteRecursively()
    }
}
