package com.myaccounts.app.util

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.myaccounts.app.data.local.TransactionAttachmentEntity
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * تخزين محلي للمرفقات المرتبطة بالعمليات.
 * الملفات تبقى داخل مساحة التطبيق ولا تعتمد على صلاحيات التخزين العامة.
 */
object TransactionAttachmentStorage {

    data class SelectedAttachment(
        val uri: Uri,
        val fileName: String,
        val mimeType: String
    )

    fun saveAttachments(
        context: Context,
        transactionId: Long,
        selectedAttachments: List<SelectedAttachment>
    ): List<TransactionAttachmentEntity> {
        if (selectedAttachments.isEmpty()) return emptyList()

        val transactionDirectory = File(
            context.filesDir,
            "transaction_attachments/$transactionId"
        )
        if (!transactionDirectory.exists() && !transactionDirectory.mkdirs()) {
            throw IOException("تعذر إنشاء مجلد مرفقات العملية")
        }

        val saved = mutableListOf<TransactionAttachmentEntity>()

        try {
            selectedAttachments.forEach { selected ->
                val safeName = sanitizeFileName(selected.fileName)
                val extension = extensionFor(selected.mimeType, safeName)
                val storedName = buildStoredName(extension)
                val destination = File(transactionDirectory, storedName)

                context.contentResolver.openInputStream(selected.uri).use { input ->
                    requireNotNull(input) { "تعذر قراءة المرفق: ${selected.fileName}" }
                    destination.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                saved += TransactionAttachmentEntity(
                    transactionId = transactionId,
                    fileName = safeName,
                    mimeType = selected.mimeType.ifBlank { "application/octet-stream" },
                    relativePath = "transaction_attachments/$transactionId/$storedName",
                    sizeBytes = destination.length()
                )
            }
        } catch (error: Throwable) {
            saved.forEach { deleteFile(context, it) }
            throw error
        }

        return saved
    }

    fun fileFor(
        context: Context,
        attachment: TransactionAttachmentEntity
    ): File {
        return File(context.filesDir, attachment.relativePath)
    }

    fun deleteFile(
        context: Context,
        attachment: TransactionAttachmentEntity
    ) {
        fileFor(context, attachment).delete()
        cleanupEmptyDirectories(context, attachment.relativePath)
    }

    fun deleteTransactionFiles(
        context: Context,
        transactionId: Long,
        attachments: List<TransactionAttachmentEntity>
    ) {
        attachments.forEach { deleteFile(context, it) }
        File(context.filesDir, "transaction_attachments/$transactionId")
            .deleteRecursively()
    }

    private fun sanitizeFileName(name: String): String {
        val trimmed = name.trim().ifBlank { "مرفق" }
        return trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(180)
    }

    private fun extensionFor(mimeType: String, fileName: String): String {
        val fromName = fileName.substringAfterLast('.', "")
        if (fromName.isNotBlank() && fromName.length <= 8) {
            return ".${fromName.lowercase()}"
        }
        return MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?.let { ".${it.lowercase()}" }
            ?: ""
    }

    private fun buildStoredName(extension: String): String {
        return "${UUID.randomUUID()}$extension"
    }

    private fun cleanupEmptyDirectories(
        context: Context,
        relativePath: String
    ) {
        val parent = File(context.filesDir, relativePath).parentFile
        if (parent != null && parent.isDirectory && parent.list().isNullOrEmpty()) {
            parent.delete()
        }
    }
}
